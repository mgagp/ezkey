/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 *
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.connector.mock;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Implements the delivery contract v1 and plays a scenario chosen by the recipient's local part, so
 * it keeps no state. It logs only the send id and the scenario.
 */
@RestController
public class MockDeliveryController {

  private static final Logger logger = LoggerFactory.getLogger(MockDeliveryController.class);

  private static final Set<String> JOB_FIELDS =
      Set.of(
          "sendId",
          "trigger",
          "tenantRef",
          "template",
          "locale",
          "recipient",
          "organisationName",
          "expectedServer",
          "qrPngBase64");
  private static final Set<String> OPTIONAL_JOB_FIELDS = Set.of("expiresAt", "timeZone");
  private static final Pattern TIME_ZONE =
      Pattern.compile("^[A-Za-z0-9_+\\-]+(/[A-Za-z0-9_+\\-]+)*$");
  private static final Set<String> LOCALES = Set.of("en", "fr");
  private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+$");
  private static final Pattern UUID_FORMAT =
      Pattern.compile("^[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}$");
  private static final int QR_MAX_LENGTH = 87_384;
  private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final List<byte[]> tokens;
  private final boolean degraded;
  private final Duration slow;

  MockDeliveryController(
      @Value("${ezkey.connector.mock.token}") String token,
      @Value("${ezkey.connector.mock.next-token}") String nextToken,
      @Value("${ezkey.connector.mock.degraded}") boolean degraded,
      @Value("${ezkey.connector.mock.slow-seconds}") long slowSeconds) {
    this.tokens =
        nextToken.isBlank() ? List.of(bytes(token)) : List.of(bytes(token), bytes(nextToken));
    this.degraded = degraded;
    this.slow = Duration.ofSeconds(slowSeconds);
  }

  /**
   * Reads state and capabilities.
   *
   * @param request the request
   * @return the health
   */
  @GetMapping("/v1/health")
  ResponseEntity<Map<String, Object>> health(HttpServletRequest request) {
    if (!authorized(request)) {
      return error(HttpStatus.UNAUTHORIZED, "unauthorized", null);
    }
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("status", degraded ? "degraded" : "ok");
    body.put("connectorName", "ezkey-connector-mock");
    body.put("connectorVersion", ConnectorVersion.VALUE);
    body.put("contractVersion", "v1");
    body.put("channel", "email");
    body.put(
        "templates", List.of(Map.of("template", "enrollment-qr", "locales", List.of("en", "fr"))));
    body.put("testMode", true);
    return ResponseEntity.ok(body);
  }

  /**
   * Plays one delivery scenario.
   *
   * @param request the request
   * @param rawBody the raw job, parsed strictly
   * @return the contract's answer for the scenario
   * @throws InterruptedException if interrupted during a slow scenario
   */
  @PostMapping("/v1/deliveries")
  ResponseEntity<?> deliver(HttpServletRequest request, @RequestBody String rawBody)
      throws InterruptedException {
    if (!authorized(request)) {
      return error(HttpStatus.UNAUTHORIZED, "unauthorized", null);
    }
    JsonNode job;
    try {
      job = objectMapper.readTree(rawBody);
    } catch (JacksonException e) {
      return error(HttpStatus.BAD_REQUEST, "invalid-request", null);
    }
    String sendId = job.path("sendId").asString("");
    if (!UUID_FORMAT.matcher(sendId).matches()) {
      return error(HttpStatus.BAD_REQUEST, "invalid-request", null);
    }
    String invalid = validate(job);
    if (invalid != null) {
      logger.info("Delivery {}: refused, {}", sendId, invalid);
      HttpStatus status =
          "payload-too-large".equals(invalid)
              ? HttpStatus.PAYLOAD_TOO_LARGE
              : "invalid-request".equals(invalid)
                  ? HttpStatus.BAD_REQUEST
                  : HttpStatus.UNPROCESSABLE_CONTENT;
      return error(status, invalid, sendId);
    }
    String recipient = job.path("recipient").asString("");
    String scenario = recipient.substring(0, recipient.indexOf('@'));
    logger.info("Delivery {}: scenario {}", sendId, scenarioLabel(scenario));
    return play(scenario, sendId);
  }

  private ResponseEntity<?> play(String scenario, String sendId) throws InterruptedException {
    return switch (scenario) {
      case "mock-provider-rejected" ->
          error(HttpStatus.UNPROCESSABLE_CONTENT, "provider-rejected", sendId);
      case "mock-recipient-refused" ->
          error(HttpStatus.UNPROCESSABLE_CONTENT, "recipient-refused", sendId);
      case "mock-provider-unavailable" ->
          error(HttpStatus.UNPROCESSABLE_CONTENT, "provider-unavailable", sendId);
      case "mock-cap-reached" ->
          error(HttpStatus.UNPROCESSABLE_CONTENT, "connector-cap-reached", sendId);
      case "mock-provider-timeout" -> error(HttpStatus.GATEWAY_TIMEOUT, "provider-timeout", sendId);
      case "mock-server-error" -> ResponseEntity.internalServerError().build();
      case "mock-redirect" ->
          ResponseEntity.status(HttpStatus.FOUND).header("Location", "/v1/health").build();
      case "mock-unreadable" -> ResponseEntity.ok("not json");
      case "mock-slow" -> {
        Thread.sleep(slow);
        yield accepted(sendId);
      }
      default -> accepted(sendId);
    };
  }

  private static ResponseEntity<Map<String, Object>> accepted(String sendId) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("outcome", "accepted");
    body.put("sendId", sendId);
    body.put("providerReference", "mock-" + sendId);
    body.put("testMode", true);
    return ResponseEntity.ok(body);
  }

  /** Scenario names are safe to log. Any other local part is a recipient and is not logged. */
  private static String scenarioLabel(String scenario) {
    return scenario.startsWith("mock-") && scenario.matches("[a-z-]{1,40}") ? scenario : "accepted";
  }

  /** Every required field, and nothing outside the contract. */
  private static boolean fieldsMatch(JsonNode job) {
    Set<String> fields = Set.copyOf(job.propertyNames());
    return fields.containsAll(JOB_FIELDS)
        && fields.stream()
            .allMatch(field -> JOB_FIELDS.contains(field) || OPTIONAL_JOB_FIELDS.contains(field));
  }

  /** The optional deadline is an ISO date-time, and the optional zone an IANA-shaped name. */
  private static boolean deadlineValid(JsonNode job) {
    if (job.has("expiresAt")) {
      try {
        OffsetDateTime.parse(job.path("expiresAt").asString(""));
      } catch (DateTimeParseException e) {
        return false;
      }
    }
    if (!job.has("timeZone")) {
      return true;
    }
    String zone = job.path("timeZone").asString("");
    return inRange(zone, 64) && TIME_ZONE.matcher(zone).matches();
  }

  /** Checks the job against the contract's schema, so core tests catch a drift. */
  private static String validate(JsonNode job) {
    if (!job.isObject() || !fieldsMatch(job) || !deadlineValid(job)) {
      return "invalid-request";
    }
    if (!"operator".equals(job.path("trigger").asString(""))
        || !inRange(job.path("tenantRef").asString(""), 64)
        || !inRange(job.path("organisationName").asString(""), 120)
        || !inRange(job.path("expectedServer").asString(""), 253)) {
      return "invalid-request";
    }
    if (!"enrollment-qr".equals(job.path("template").asString(""))) {
      return "unsupported-template";
    }
    if (!LOCALES.contains(job.path("locale").asString(""))) {
      return "unsupported-locale";
    }
    String recipient = job.path("recipient").asString("");
    if (recipient.length() > 255 || !EMAIL.matcher(recipient).matches()) {
      return "invalid-recipient";
    }
    String qr = job.path("qrPngBase64").asString("");
    if (qr.length() > QR_MAX_LENGTH) {
      return "payload-too-large";
    }
    return isPng(qr) ? null : "invalid-qr";
  }

  private static boolean inRange(String value, int max) {
    return !value.isEmpty() && value.length() <= max;
  }

  private static boolean isPng(String base64) {
    try {
      byte[] decoded = Base64.getDecoder().decode(base64);
      return decoded.length > PNG_SIGNATURE.length
          && Arrays.equals(Arrays.copyOf(decoded, PNG_SIGNATURE.length), PNG_SIGNATURE);
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  private boolean authorized(HttpServletRequest request) {
    String header = request.getHeader("Authorization");
    if (header == null || !header.startsWith("Bearer ")) {
      return false;
    }
    byte[] presented = bytes(header.substring("Bearer ".length()));
    boolean match = false;
    for (byte[] token : tokens) {
      match |= MessageDigest.isEqual(token, presented);
    }
    return match;
  }

  private static byte[] bytes(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }

  private static ResponseEntity<Map<String, Object>> error(
      HttpStatus status, String code, String sendId) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("code", code);
    if (sendId != null) {
      body.put("sendId", sendId);
    }
    return ResponseEntity.status(status).body(body);
  }
}
