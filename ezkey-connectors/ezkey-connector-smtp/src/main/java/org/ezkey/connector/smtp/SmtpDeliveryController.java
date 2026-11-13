/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 *
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.connector.smtp;

import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.HttpServletRequest;
import java.io.UnsupportedEncodingException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Implements the delivery contract v1 over SMTP. It logs only the send id and a typed outcome:
 * never the recipient, the QR, a request body or provider text.
 */
@RestController
public class SmtpDeliveryController {

  private static final Logger logger = LoggerFactory.getLogger(SmtpDeliveryController.class);

  static final String CONNECTOR_NAME = "ezkey-connector-smtp";

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
  private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
  private static final Pattern HOSTNAME =
      Pattern.compile("^[A-Za-z0-9]([A-Za-z0-9.-]*[A-Za-z0-9])?$");
  private static final Pattern CONTROL = Pattern.compile("\\p{Cntrl}");
  private static final Pattern UUID_FORMAT =
      Pattern.compile("^[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}$");
  private static final int QR_MAX_LENGTH = 87_384;
  private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final SmtpConnectorSettings settings;
  private final JavaMailSender mailSender;
  private final DailyCap dailyCap;
  private final List<byte[]> tokens;

  @Autowired
  SmtpDeliveryController(SmtpConnectorSettings settings, JavaMailSender mailSender) {
    this(settings, mailSender, Clock.systemUTC());
  }

  SmtpDeliveryController(SmtpConnectorSettings settings, JavaMailSender mailSender, Clock clock) {
    this.settings = settings;
    this.mailSender = mailSender;
    this.dailyCap = new DailyCap(settings.dailyCap(), clock);
    this.tokens =
        SmtpConnectorSettings.isBlank(settings.nextToken())
            ? List.of(bytes(settings.token()))
            : List.of(bytes(settings.token()), bytes(settings.nextToken()));
  }

  /**
   * Reads state and capabilities, with no call to the provider.
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
    body.put("status", settings.configured() ? "ok" : "degraded");
    body.put("connectorName", CONNECTOR_NAME);
    body.put("connectorVersion", ConnectorVersion.VALUE);
    body.put("contractVersion", "v1");
    body.put("channel", "email");
    body.put(
        "templates", List.of(Map.of("template", "enrollment-qr", "locales", List.of("en", "fr"))));
    body.put("testMode", false);
    return ResponseEntity.ok(body);
  }

  /**
   * Sends one enrollment QR email.
   *
   * @param request the request
   * @param rawBody the raw job, parsed strictly
   * @return the contract's answer
   */
  @PostMapping("/v1/deliveries")
  ResponseEntity<?> deliver(HttpServletRequest request, @RequestBody String rawBody) {
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
      return refuse(sendId, invalid);
    }
    if (!settings.configured()) {
      return refuse(sendId, "provider-unavailable");
    }
    if (!dailyCap.tryAcquire()) {
      return refuse(sendId, "connector-cap-reached");
    }
    String code = send(job);
    if (code == null) {
      logger.info("Delivery {}: accepted", sendId);
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("outcome", "accepted");
      body.put("sendId", sendId);
      body.put("testMode", false);
      return ResponseEntity.ok(body);
    }
    if ("unknown".equals(code)) {
      // The message may have left, so it keeps counting against the cap.
      logger.info("Delivery {}: unknown", sendId);
      return ResponseEntity.internalServerError().build();
    }
    if (!"provider-timeout".equals(code)) {
      dailyCap.release();
    }
    return refuse(sendId, code);
  }

  /** Returns null when the provider took the message, or a typed code. */
  private String send(JsonNode job) {
    SmtpConnectorSettings s = settings;
    EnrollmentEmailRenderer.EnrollmentEmail email =
        EnrollmentEmailRenderer.render(
            job.path("locale").asString(),
            job.path("organisationName").asString(),
            job.path("expectedServer").asString(),
            job.has("expiresAt") ? OffsetDateTime.parse(job.path("expiresAt").asString()) : null,
            job.has("timeZone") ? job.path("timeZone").asString() : null);
    try {
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper =
          new MimeMessageHelper(
              message,
              MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
              StandardCharsets.UTF_8.name());
      helper.setFrom(
          new InternetAddress(s.fromAddress(), s.fromName(), StandardCharsets.UTF_8.name()));
      helper.setTo(job.path("recipient").asString());
      helper.setSubject(email.subject());
      helper.setText(email.text(), email.html());
      byte[] png = Base64.getDecoder().decode(job.path("qrPngBase64").asString());
      helper.addInline(
          EnrollmentEmailRenderer.QR_CONTENT_ID, new ByteArrayResource(png), "image/png");
      mailSender.send(message);
      return null;
    } catch (MessagingException | UnsupportedEncodingException e) {
      return "invalid-request";
    } catch (MailAuthenticationException e) {
      return "provider-rejected";
    } catch (MailException e) {
      return classify(e);
    }
  }

  /**
   * Maps a provider failure to the contract. Only failures where the message surely did not leave
   * become a rejection; anything else is unknown for the core.
   */
  static String classify(Throwable failure) {
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof SocketTimeoutException) {
        return "provider-timeout";
      }
      if (cause instanceof ConnectException || cause instanceof UnknownHostException) {
        return "provider-unavailable";
      }
      if (cause instanceof SendFailedException sendFailed
          && sendFailed.getInvalidAddresses() != null
          && sendFailed.getInvalidAddresses().length > 0) {
        return "recipient-refused";
      }
      if (cause.getClass().getSimpleName().equals("MailConnectException")) {
        return "provider-unavailable";
      }
      if (cause instanceof MessagingException messaging
          && messaging.getNextException() != null
          && messaging.getNextException() != cause.getCause()) {
        String nested = classify(messaging.getNextException());
        if (!"unknown".equals(nested)) {
          return nested;
        }
      }
    }
    return "unknown";
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

  /** Checks the job against the contract's schema. */
  private static String validate(JsonNode job) {
    if (!job.isObject() || !fieldsMatch(job) || !deadlineValid(job)) {
      return "invalid-request";
    }
    String organisationName = job.path("organisationName").asString("");
    String expectedServer = job.path("expectedServer").asString("");
    if (!"operator".equals(job.path("trigger").asString(""))
        || !inRange(job.path("tenantRef").asString(""), 64)
        || !inRange(organisationName, 120)
        || CONTROL.matcher(organisationName).find()
        || !inRange(expectedServer, 253)
        || !HOSTNAME.matcher(expectedServer).matches()) {
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

  private ResponseEntity<Map<String, Object>> refuse(String sendId, String code) {
    logger.info("Delivery {}: refused, {}", sendId, code);
    HttpStatus status =
        switch (code) {
          case "payload-too-large" -> HttpStatus.PAYLOAD_TOO_LARGE;
          case "invalid-request" -> HttpStatus.BAD_REQUEST;
          case "provider-timeout" -> HttpStatus.GATEWAY_TIMEOUT;
          default -> HttpStatus.UNPROCESSABLE_CONTENT;
        };
    return error(status, code, sendId);
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
    return value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
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
