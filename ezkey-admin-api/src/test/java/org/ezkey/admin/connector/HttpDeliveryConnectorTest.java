/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 *
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.admin.connector;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.ezkey.admin.connector.DeliveryConnectorPort.ConnectorHealth;
import org.ezkey.admin.connector.DeliveryConnectorPort.DeliveryJob;
import org.ezkey.admin.connector.DeliveryConnectorPort.DeliveryResult;
import org.ezkey.admin.connector.DeliveryConnectorPort.Outcome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Reads every kind of connector answer against a real local HTTP server. */
@DisplayName("HttpDeliveryConnector maps connector answers to outcomes")
class HttpDeliveryConnectorTest {

  private static final DeliveryJob JOB =
      new DeliveryJob(
          "3f0e2f6a-9b1c-4d7e-8a55-2c1d9e7b6a10",
          "42",
          "fr",
          "person@example.com",
          "Acme",
          "auth.example.com",
          "iVBORw0KGgoAAAA");

  private HttpServer server;
  private final AtomicReference<Integer> status = new AtomicReference<>(200);
  private final AtomicReference<String> responseBody =
      new AtomicReference<>("{\"outcome\":\"accepted\",\"sendId\":\"x\",\"testMode\":false}");
  private final AtomicReference<String> receivedBody = new AtomicReference<>();
  private final AtomicReference<String> receivedAuth = new AtomicReference<>();
  private final AtomicReference<Long> delayMillis = new AtomicReference<>(0L);
  private final AtomicBoolean redirectTargetHit = new AtomicBoolean();

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
    server.createContext(
        "/redirect-target",
        exchange -> {
          redirectTargetHit.set(true);
          exchange.sendResponseHeaders(200, -1);
          exchange.close();
        });
    server.createContext(
        "/v0/",
        exchange -> {
          receivedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
          receivedBody.set(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          try {
            Thread.sleep(delayMillis.get());
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          if (status.get() == 302) {
            exchange.getResponseHeaders().add("Location", baseUrl() + "/redirect-target");
          }
          byte[] bytes = responseBody.get().getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(status.get(), bytes.length == 0 ? -1 : bytes.length);
          if (bytes.length > 0) {
            exchange.getResponseBody().write(bytes);
          }
          exchange.close();
        });
    server.start();
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  private String baseUrl() {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  private HttpDeliveryConnector connector(String url) {
    DeliveryConnectorProperties properties = new DeliveryConnectorProperties();
    properties.setUrl(url);
    properties.setToken("test-token");
    properties.setReadTimeout(Duration.ofMillis(400));
    properties.setHealthTimeout(Duration.ofMillis(400));
    properties.setConnectTimeout(Duration.ofMillis(400));
    return new HttpDeliveryConnector(properties);
  }

  private void answer(int httpStatus, String body) {
    status.set(httpStatus);
    responseBody.set(body);
  }

  private DeliveryResult send() {
    return connector(baseUrl()).send(JOB);
  }

  @Test
  @DisplayName("sends exactly the contract fields with the bearer token")
  void sendsContractFields() {
    answer(200, "{\"outcome\":\"accepted\",\"sendId\":\"x\",\"testMode\":true}");

    DeliveryResult result = send();

    assertThat(result.outcome()).isEqualTo(Outcome.ACCEPTED);
    assertThat(result.testMode()).isTrue();
    assertThat(receivedAuth.get()).isEqualTo("Bearer test-token");
    JsonNode body = new ObjectMapper().readTree(receivedBody.get());
    assertThat(body.propertyNames())
        .containsExactlyInAnyOrder(
            "sendId",
            "trigger",
            "tenantRef",
            "template",
            "locale",
            "recipient",
            "organisationName",
            "expectedServer",
            "qrPngBase64");
    assertThat(body.path("trigger").asString()).isEqualTo("operator");
    assertThat(body.path("template").asString()).isEqualTo("enrollment-qr");
  }

  @Test
  @DisplayName("keeps a well-formed provider reference and drops anything else")
  void providerReference() {
    answer(
        200,
        "{\"outcome\":\"accepted\",\"sendId\":\"x\",\"testMode\":false,"
            + "\"providerReference\":\"<abc.123@mail.example.com>\"}");
    assertThat(send().providerReference()).isEqualTo("<abc.123@mail.example.com>");

    answer(
        200,
        "{\"outcome\":\"accepted\",\"sendId\":\"x\",\"testMode\":false,"
            + "\"providerReference\":\"550 mailbox of person@example.com full\"}");
    assertThat(send().providerReference()).isNull();
  }

  @Test
  @DisplayName("a refusal from the connector is rejected, with its typed code")
  void rejected() {
    for (int httpStatus : new int[] {400, 401, 413, 422}) {
      answer(httpStatus, "{\"code\":\"provider-rejected\"}");
      DeliveryResult result = send();
      assertThat(result.outcome()).isEqualTo(Outcome.REJECTED);
      assertThat(result.code()).isEqualTo("provider-rejected");
    }
  }

  @Test
  @DisplayName("a code outside the contract never reaches the caller")
  void unknownCodeIsNotKept() {
    answer(422, "{\"code\":\"550 no such user person@example.com\"}");

    DeliveryResult result = send();

    assertThat(result.outcome()).isEqualTo(Outcome.REJECTED);
    assertThat(result.code()).isEqualTo(HttpDeliveryConnector.CODE_OUT_OF_CONTRACT);
  }

  @Test
  @DisplayName("a provider timeout is unknown, never rejected")
  void providerTimeout() {
    answer(504, "{\"code\":\"provider-timeout\"}");

    DeliveryResult result = send();

    assertThat(result.outcome()).isEqualTo(Outcome.UNKNOWN);
    assertThat(result.code()).isEqualTo("provider-timeout");
  }

  @Test
  @DisplayName("a server error is unknown, since the email may have left")
  void serverError() {
    answer(500, "");
    assertThat(send().outcome()).isEqualTo(Outcome.UNKNOWN);
  }

  @Test
  @DisplayName("a 200 outside the contract is unknown, not accepted")
  void unreadableSuccess() {
    answer(200, "not json");
    assertThat(send().outcome()).isEqualTo(Outcome.UNKNOWN);

    answer(200, "{\"status\":\"fine\"}");
    assertThat(send().outcome()).isEqualTo(Outcome.UNKNOWN);
  }

  @Test
  @DisplayName("a redirect is not followed, so the QR does not leave for another address")
  void redirectNotFollowed() {
    answer(302, "");

    DeliveryResult result = send();

    assertThat(result.outcome()).isEqualTo(Outcome.UNKNOWN);
    assertThat(redirectTargetHit).isFalse();
  }

  @Test
  @DisplayName("no answer before the read timeout is unknown")
  void readTimeout() {
    delayMillis.set(1_500L);

    DeliveryResult result = send();

    assertThat(result.outcome()).isEqualTo(Outcome.UNKNOWN);
    assertThat(result.code()).isEqualTo(HttpDeliveryConnector.CODE_NO_ANSWER);
  }

  @Test
  @DisplayName("a refused connection is rejected, since the job never reached the connector")
  void connectionRefused() throws IOException {
    int closedPort;
    try (ServerSocket socket = new ServerSocket(0)) {
      closedPort = socket.getLocalPort();
    }

    DeliveryResult result = connector("http://127.0.0.1:" + closedPort).send(JOB);

    assertThat(result.outcome()).isEqualTo(Outcome.REJECTED);
    assertThat(result.code()).isEqualTo(HttpDeliveryConnector.CODE_UNREACHABLE);
  }

  @Test
  @DisplayName("health reads state, version, locales and test mode")
  void health() {
    answer(
        200,
        "{\"status\":\"ok\",\"connectorName\":\"mock\",\"connectorVersion\":\"1\","
            + "\"contractVersion\":\"v0\",\"channel\":\"email\",\"testMode\":true,"
            + "\"templates\":[{\"template\":\"enrollment-qr\",\"locales\":[\"en\",\"fr\"]}]}");

    ConnectorHealth health = connector(baseUrl()).readHealth().orElseThrow();

    assertThat(health.status()).isEqualTo("ok");
    assertThat(health.contractVersion()).isEqualTo("v0");
    assertThat(health.locales()).containsExactly("en", "fr");
    assertThat(health.testMode()).isTrue();
    assertThat(receivedAuth.get()).isEqualTo("Bearer test-token");
  }

  @Test
  @DisplayName("health is empty when refused, unreadable or too slow")
  void healthUnavailable() {
    answer(401, "{\"code\":\"unauthorized\"}");
    assertThat(connector(baseUrl()).readHealth()).isEmpty();

    answer(200, "not json");
    assertThat(connector(baseUrl()).readHealth()).isEmpty();

    answer(200, "{}");
    delayMillis.set(1_500L);
    assertThat(connector(baseUrl()).readHealth()).isEmpty();
  }

  @Test
  @DisplayName("the job shows neither the QR nor the recipient in toString")
  void jobToStringIsSafe() {
    assertThat(JOB.toString()).doesNotContain("person@example.com", "iVBORw0KGgo", "Acme");
  }
}
