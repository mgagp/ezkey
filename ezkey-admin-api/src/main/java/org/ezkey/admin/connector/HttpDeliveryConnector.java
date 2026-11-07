/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 *
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Adapter: HttpDeliveryConnector
 */

package org.ezkey.admin.connector;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * HTTP adapter for the delivery contract v0. It follows no redirect, since the job carries the QR.
 *
 * <p>Logs carry only the send id, the outcome and typed codes: never the job, the recipient, a
 * response body or an exception message.
 *
 * @since 2026
 */
@Component
public class HttpDeliveryConnector implements DeliveryConnectorPort {

  /** The connector did not answer before the read timeout, or the connection was lost. */
  static final String CODE_NO_ANSWER = "connector-no-answer";

  /** No connection could be made, so the job never reached the connector. */
  static final String CODE_UNREACHABLE = "connector-unreachable";

  /** The connector answered something outside the contract. */
  static final String CODE_OUT_OF_CONTRACT = "connector-out-of-contract";

  private static final Logger logger = LoggerFactory.getLogger(HttpDeliveryConnector.class);

  private static final Set<String> CONTRACT_CODES =
      Set.of(
          "invalid-request",
          "unauthorized",
          "payload-too-large",
          "invalid-qr",
          "unsupported-template",
          "unsupported-locale",
          "invalid-recipient",
          "recipient-refused",
          "provider-rejected",
          "provider-unavailable",
          "connector-cap-reached",
          "provider-timeout");

  private static final Set<Integer> REJECTED_STATUSES = Set.of(400, 401, 413, 422);

  private static final Pattern PROVIDER_REFERENCE = Pattern.compile("^[\\w.@<>:+=/-]{1,255}$");

  private final DeliveryConnectorProperties properties;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final HttpClient httpClient;

  /**
   * Builds the adapter.
   *
   * @param properties connector settings
   */
  public HttpDeliveryConnector(DeliveryConnectorProperties properties) {
    this.properties = properties;
    this.httpClient =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(properties.getConnectTimeout())
            .build();
  }

  @Override
  public DeliveryResult send(DeliveryJob job) {
    HttpResponse<String> response;
    try {
      response =
          httpClient.send(
              HttpRequest.newBuilder(uri("/v0/deliveries"))
                  .timeout(properties.getReadTimeout())
                  .header("Authorization", "Bearer " + properties.getToken())
                  .header("Content-Type", "application/json")
                  .POST(HttpRequest.BodyPublishers.ofString(body(job)))
                  .build(),
              HttpResponse.BodyHandlers.ofString());
    } catch (ConnectException | HttpConnectTimeoutException e) {
      logger.warn("Delivery {}: connector unreachable", job.sendId());
      return new DeliveryResult(Outcome.REJECTED, CODE_UNREACHABLE, null, false);
    } catch (HttpTimeoutException e) {
      logger.warn("Delivery {}: no answer before the read timeout", job.sendId());
      return new DeliveryResult(Outcome.UNKNOWN, CODE_NO_ANSWER, null, false);
    } catch (IOException e) {
      logger.warn("Delivery {}: connection lost ({})", job.sendId(), e.getClass().getSimpleName());
      return new DeliveryResult(Outcome.UNKNOWN, CODE_NO_ANSWER, null, false);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return new DeliveryResult(Outcome.UNKNOWN, CODE_NO_ANSWER, null, false);
    }
    return readSendResponse(job.sendId(), response.statusCode(), response.body());
  }

  /** Maps a response to an outcome, as listed in {@code TB-2026-11-06}. */
  private DeliveryResult readSendResponse(String sendId, int status, String body) {
    Optional<JsonNode> json = parse(body);
    if (status == 200) {
      boolean accepted =
          json.map(node -> "accepted".equals(node.path("outcome").asString(""))).orElse(false);
      if (!accepted) {
        logger.warn("Delivery {}: 200 outside the contract", sendId);
        return new DeliveryResult(Outcome.UNKNOWN, CODE_OUT_OF_CONTRACT, null, false);
      }
      JsonNode node = json.get();
      return new DeliveryResult(
          Outcome.ACCEPTED,
          null,
          providerReference(node.path("providerReference").asString("")),
          node.path("testMode").asBoolean(false));
    }
    String code =
        json.map(node -> node.path("code").asString(""))
            .filter(CONTRACT_CODES::contains)
            .orElse(CODE_OUT_OF_CONTRACT);
    if (REJECTED_STATUSES.contains(status)) {
      return new DeliveryResult(Outcome.REJECTED, code, null, false);
    }
    logger.warn("Delivery {}: HTTP {} read as unknown", sendId, status);
    return new DeliveryResult(Outcome.UNKNOWN, code, null, false);
  }

  @Override
  public Optional<ConnectorHealth> readHealth() {
    try {
      HttpResponse<String> response =
          httpClient.send(
              HttpRequest.newBuilder(uri("/v0/health"))
                  .timeout(properties.getHealthTimeout())
                  .header("Authorization", "Bearer " + properties.getToken())
                  .GET()
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        logger.warn("Connector health: HTTP {}", response.statusCode());
        return Optional.empty();
      }
      return parse(response.body()).map(HttpDeliveryConnector::toHealth);
    } catch (IOException e) {
      logger.debug("Connector health unreachable ({})", e.getClass().getSimpleName());
      return Optional.empty();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Optional.empty();
    }
  }

  private static ConnectorHealth toHealth(JsonNode node) {
    List<String> locales = new ArrayList<>();
    for (JsonNode template : node.path("templates")) {
      if (TEMPLATE_ENROLLMENT_QR.equals(template.path("template").asString(""))) {
        template.path("locales").forEach(locale -> locales.add(locale.asString("")));
      }
    }
    return new ConnectorHealth(
        node.path("status").asString(""),
        node.path("connectorName").asString(""),
        node.path("contractVersion").asString(""),
        node.path("channel").asString(""),
        List.copyOf(locales),
        node.path("testMode").asBoolean(false));
  }

  private String body(DeliveryJob job) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("sendId", job.sendId());
    body.put("trigger", "operator");
    body.put("tenantRef", job.tenantRef());
    body.put("template", TEMPLATE_ENROLLMENT_QR);
    body.put("locale", job.locale());
    body.put("recipient", job.recipient());
    body.put("organisationName", job.organisationName());
    body.put("expectedServer", job.expectedServer());
    body.put("qrPngBase64", job.qrPngBase64());
    return objectMapper.writeValueAsString(body);
  }

  private Optional<JsonNode> parse(String body) {
    if (body == null || body.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.of(objectMapper.readTree(body));
    } catch (JacksonException e) {
      return Optional.empty();
    }
  }

  private static String providerReference(String value) {
    return PROVIDER_REFERENCE.matcher(value).matches() ? value : null;
  }

  private URI uri(String path) {
    return URI.create(properties.getUrl().strip().replaceAll("/+$", "") + path);
  }
}
