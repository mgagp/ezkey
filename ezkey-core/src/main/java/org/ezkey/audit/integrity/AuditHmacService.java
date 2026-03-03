/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: AuditHmacService
 * Description: HMAC-SHA256 signing and verification for audit log entries.
 */

package org.ezkey.audit.integrity;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.ezkey.audit.domain.entity.AuditLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * HMAC-SHA256 signing and verification service for audit log entries.
 *
 * <p>Provides tamper-evidence for audit log entries in self-hosted deployments by computing and
 * verifying HMAC-SHA256 signatures. This is the cryptographic foundation for SOC 2 audit log
 * integrity (CC7.2, CC6.1).
 *
 * <p><b>Design Decisions:</b>
 *
 * <ul>
 *   <li><b>Algorithm:</b> HMAC-SHA256 via {@code javax.crypto.Mac} (no Tink dependency --
 *       separation of concerns between integrity and confidentiality)
 *   <li><b>Key:</b> Dedicated 256-bit key, separate from the Tink encryption master key
 *   <li><b>Canonical form:</b> Pipe-delimited fields, null as empty string, timestamps in ISO-8601
 *       UTC -- deterministic and unambiguous
 *   <li><b>Inspired by:</b> HashiCorp Vault audit backend (per-entry HMAC, proven SOC 2 acceptable)
 * </ul>
 *
 * <p><b>Thread Safety:</b> This service is thread-safe. Each HMAC computation creates a new {@link
 * Mac} instance (Mac is not thread-safe, but creation is lightweight).
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2026
 */
@Service
public class AuditHmacService {

  private static final Logger logger = LoggerFactory.getLogger(AuditHmacService.class);

  private static final String HMAC_ALGORITHM = "HmacSHA256";
  private static final String FIELD_SEPARATOR = "|";
  private static final DateTimeFormatter UTC_FORMATTER =
      DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneOffset.UTC);

  private final AuditHmacProperties properties;
  private volatile SecretKeySpec hmacKeySpec;

  public AuditHmacService(AuditHmacProperties properties) {
    this.properties = properties;
  }

  /**
   * Initializes the HMAC key from the configured key file at startup.
   *
   * <p>The key file must contain a Base64-encoded 256-bit (32-byte) secret. If the key file is not
   * configured or cannot be read, HMAC signing is disabled gracefully with a warning.
   */
  @PostConstruct
  void init() {
    if (!properties.isEnabled()) {
      logger.info("Audit HMAC integrity signing is disabled");
      return;
    }

    String keyFilePath = properties.getHmacKeyFile();
    if (keyFilePath == null || keyFilePath.isBlank()) {
      logger.warn(
          "Audit HMAC key file not configured (ezkey.audit.integrity.hmac-key-file). "
              + "HMAC signing will be disabled. Configure a key file for SOC 2 compliance.");
      return;
    }

    try {
      Path path = Path.of(keyFilePath);
      if (!Files.exists(path)) {
        logger.info("Audit HMAC key file not found: {}. Generating new 256-bit key.", keyFilePath);
        generateHmacKeyFile(path);
      }

      String keyBase64 = Files.readString(path, StandardCharsets.UTF_8).trim();
      byte[] keyBytes = Base64.getDecoder().decode(keyBase64);

      if (keyBytes.length < 32) {
        logger.error(
            "Audit HMAC key is too short ({} bytes). Minimum 32 bytes (256 bits) required. "
                + "HMAC signing will be disabled.",
            keyBytes.length);
        return;
      }

      hmacKeySpec = new SecretKeySpec(keyBytes, HMAC_ALGORITHM);
      logger.info(
          "Audit HMAC integrity signing initialized (key: {} bytes, instance: {})",
          keyBytes.length,
          properties.getInstanceId() != null ? properties.getInstanceId() : "default");
    } catch (IOException e) {
      logger.error("Failed to read audit HMAC key file: {}. HMAC signing disabled.", keyFilePath);
    } catch (IllegalArgumentException e) {
      logger.error(
          "Invalid Base64 in audit HMAC key file: {}. HMAC signing disabled.", keyFilePath);
    }
  }

  /**
   * Returns whether HMAC signing is active (enabled and key loaded).
   *
   * @return true if HMAC signing is ready
   */
  public boolean isActive() {
    return properties.isEnabled() && hmacKeySpec != null;
  }

  /**
   * Returns the configured instance ID for this application instance.
   *
   * @return instance ID, or null if not configured or blank
   */
  public String getInstanceId() {
    String id = properties.getInstanceId();
    return (id == null || id.isBlank()) ? null : id;
  }

  /**
   * Computes the HMAC-SHA256 signature of an audit log entry.
   *
   * <p>The HMAC is computed over a canonical pipe-delimited representation of the entry fields.
   * Null fields are represented as empty strings. Timestamps are normalized to UTC ISO-8601 format
   * for deterministic output regardless of the JVM's default timezone.
   *
   * @param entry the audit log entry to sign
   * @return Base64-encoded HMAC-SHA256 signature, or null if signing is not active
   */
  public String computeHmac(AuditLog entry) {
    if (!isActive()) {
      return null;
    }

    try {
      String canonical = buildCanonicalForm(entry);
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(hmacKeySpec);
      byte[] hmacBytes = mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(hmacBytes);
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      logger.error("Failed to compute audit HMAC: {}", e.getMessage());
      return null;
    }
  }

  /**
   * Computes HMAC-SHA256 over arbitrary data (used by chain checkpoint service).
   *
   * @param data the string data to sign
   * @return Base64-encoded HMAC-SHA256 signature, or null if signing is not active
   */
  public String computeHmac(String data) {
    if (!isActive()) {
      return null;
    }

    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(hmacKeySpec);
      byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(hmacBytes);
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      logger.error("Failed to compute HMAC: {}", e.getMessage());
      return null;
    }
  }

  /**
   * Verifies the HMAC signature of an audit log entry.
   *
   * @param entry the audit log entry to verify (must have entry_hmac set)
   * @return true if the HMAC matches, false if it does not match or signing is not active
   */
  public boolean verifyHmac(AuditLog entry) {
    if (!isActive()) {
      return false;
    }

    if (entry.getEntryHmac() == null) {
      return false;
    }

    String computed = computeHmac(entry);
    return computed != null && computed.equals(entry.getEntryHmac());
  }

  /**
   * Builds the canonical pipe-delimited string representation of an audit log entry for HMAC
   * computation.
   *
   * <p>Field order is fixed and deterministic. Null values are represented as empty strings.
   * Timestamps are normalized to UTC ISO-8601 format.
   *
   * @param entry the audit log entry
   * @return canonical string representation
   */
  String buildCanonicalForm(AuditLog entry) {
    StringBuilder sb = new StringBuilder(512);
    sb.append(nullSafe(entry.getAuditLogId()));
    sb.append(FIELD_SEPARATOR);
    sb.append(nullSafe(entry.getEventType()));
    sb.append(FIELD_SEPARATOR);
    sb.append(nullSafe(entry.getEventAction()));
    sb.append(FIELD_SEPARATOR);
    sb.append(nullSafe(entry.getEventStatus()));
    sb.append(FIELD_SEPARATOR);
    sb.append(nullSafe(entry.getApiName()));
    sb.append(FIELD_SEPARATOR);
    sb.append(nullSafe(entry.getIpAddress()));
    sb.append(FIELD_SEPARATOR);
    sb.append(nullSafe(entry.getAdminId()));
    sb.append(FIELD_SEPARATOR);
    sb.append(nullSafe(getIntegrationIdForCanonical(entry)));
    sb.append(FIELD_SEPARATOR);
    sb.append(nullSafe(getEnrollmentIdForCanonical(entry)));
    sb.append(FIELD_SEPARATOR);
    sb.append(nullSafe(entry.getTenantId()));
    sb.append(FIELD_SEPARATOR);
    sb.append(nullSafe(entry.getEventDetails()));
    sb.append(FIELD_SEPARATOR);
    sb.append(nullSafe(entry.getErrorMessage()));
    sb.append(FIELD_SEPARATOR);
    sb.append(formatTimestamp(entry.getCreatedAt()));
    sb.append(FIELD_SEPARATOR);
    sb.append(nullSafe(entry.getInstanceId()));
    // Field 15 — reason (optional justification for sensitive operations; null →
    // empty string)
    sb.append(FIELD_SEPARATOR);
    sb.append(nullSafe(entry.getReason()));
    // Field 16 — target_admin_id (admin who is the subject of the event; null → empty string)
    sb.append(FIELD_SEPARATOR);
    sb.append(nullSafe(entry.getTargetAdminId()));
    return sb.toString();
  }

  private static Integer getIntegrationIdForCanonical(AuditLog entry) {
    Integer snapshot = entry.getIntegrationIdHmacSnapshot();
    return snapshot != null ? snapshot : entry.getIntegrationId();
  }

  private static Integer getEnrollmentIdForCanonical(AuditLog entry) {
    Integer snapshot = entry.getEnrollmentIdHmacSnapshot();
    return snapshot != null ? snapshot : entry.getEnrollmentId();
  }

  private static String nullSafe(Object value) {
    return value == null ? "" : value.toString();
  }

  private static String formatTimestamp(OffsetDateTime ts) {
    if (ts == null) {
      return "";
    }
    // Truncate to microseconds before formatting to match PostgreSQL TIMESTAMPTZ
    // precision.
    // Java's OffsetDateTime.now() can carry sub-microsecond nanoseconds that
    // PostgreSQL
    // silently truncates. Without this, the canonical form at sign-time
    // (nanoseconds present)
    // differs from the form at verify-time (value read back from DB, microseconds
    // only),
    // causing every HMAC to fail verification systematically.
    return UTC_FORMATTER.format(
        ts.withOffsetSameInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS));
  }

  /**
   * Generates a new 256-bit HMAC key file at the specified path.
   *
   * <p>Creates parent directories if they do not exist and writes a Base64-encoded 32-byte key.
   * This auto-generation mirrors the Tink master key behavior for first-startup convenience.
   *
   * @param path the file path to write the key to
   * @throws IOException if the file cannot be written
   */
  private void generateHmacKeyFile(Path path) throws IOException {
    Files.createDirectories(path.getParent());
    byte[] keyBytes = new byte[32];
    new SecureRandom().nextBytes(keyBytes);
    String keyBase64 = Base64.getEncoder().encodeToString(keyBytes);
    Files.writeString(path, keyBase64, StandardCharsets.UTF_8);
    logger.info("Generated new audit HMAC key file: {}", path);
  }
}
