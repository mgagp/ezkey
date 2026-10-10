/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: BootstrapCredentialsFileExporter
 * Description: Service for exporting bootstrap credentials to a file (Docker-only).
 */

package org.ezkey.admin.service;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.ezkey.admin.config.AdminMfaProperties;
import org.ezkey.admin.config.BootstrapCredentialsOutputMode;
import org.ezkey.admin.config.BootstrapExportProperties;
import org.ezkey.config.QrCodeProperties;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Service for exporting bootstrap credentials to a file.
 *
 * <p>Writes bootstrap material to a JSON file created with owner-only permissions ({@code 0600}) so
 * operators and Docker automation can retrieve secrets that must not appear in container logs.
 * Recovery codes are stored in this file (never in logs). Enrollment proof token and challenge are
 * included only in {@link BootstrapCredentialsOutputMode#FULL} and are removed after a successful
 * bind (recovery codes remain until the operator deletes the file).
 *
 * <p><b>Idempotent:</b> If the file already exists and contains the same enrollmentId, it will not
 * be overwritten to avoid unnecessary churn (except when new plaintext recovery codes must be
 * persisted).
 *
 * @since 2025
 */
@Component
public class BootstrapCredentialsFileExporter {

  private static final Logger logger =
      LoggerFactory.getLogger(BootstrapCredentialsFileExporter.class);

  private static final Set<PosixFilePermission> OWNER_READ_WRITE_ONLY =
      EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

  private final BootstrapExportProperties exportProperties;
  private final QrCodeProperties qrCodeProperties;
  private final AdminMfaProperties adminMfaProperties;
  private final ObjectMapper objectMapper;

  /** Last redact-failure kind logged; avoids ERROR spam every ShedLock tick on a stuck file. */
  private final AtomicReference<String> lastRedactFailureKind = new AtomicReference<>();

  /**
   * Creates the exporter.
   *
   * @param exportProperties export path and enablement
   * @param qrCodeProperties optional auth base URL for the JSON payload
   * @param adminMfaProperties bootstrap credentials output mode
   */
  public BootstrapCredentialsFileExporter(
      BootstrapExportProperties exportProperties,
      QrCodeProperties qrCodeProperties,
      AdminMfaProperties adminMfaProperties) {
    this.exportProperties = exportProperties;
    this.qrCodeProperties = qrCodeProperties;
    this.adminMfaProperties = adminMfaProperties;
    this.objectMapper = new ObjectMapper();
  }

  /**
   * Exports bootstrap credentials to a file when export is enabled, or when plaintext recovery
   * codes must be persisted (they are never written to logs).
   *
   * @param enrollment the enrollment with credentials to export
   * @param enrollmentProofToken the enrollment proof token (from before save, to ensure exact
   *     match); may be omitted from the file in recovery-primary mode
   * @param username the admin username
   * @param recoveryCodes plaintext recovery codes to persist (empty when re-exporting an existing
   *     enrollment that no longer has plaintext codes)
   */
  public void exportIfEnabled(
      Enrollment enrollment,
      String enrollmentProofToken,
      String username,
      List<String> recoveryCodes) {
    boolean hasRecoveryCodes = recoveryCodes != null && !recoveryCodes.isEmpty();
    if (!exportProperties.isEnabled() && !hasRecoveryCodes) {
      logger.debug("Bootstrap credentials file export is disabled");
      return;
    }

    BootstrapCredentialsOutputMode mode =
        adminMfaProperties.getBootstrap().getCredentialsOutputMode();
    boolean recoveryPrimary = mode == BootstrapCredentialsOutputMode.RECOVERY_PRIMARY;

    if (recoveryPrimary && !hasRecoveryCodes && !exportProperties.isEnabled()) {
      logger.debug("Bootstrap credentials file export skipped (recovery_primary, no new codes)");
      return;
    }

    try {
      Path filePath = Path.of(exportProperties.getPath());
      Path parentDir = filePath.getParent();

      if (Files.exists(filePath) && !hasRecoveryCodes) {
        try {
          ObjectNode existingJson = (ObjectNode) objectMapper.readTree(filePath.toFile());
          Integer existingEnrollmentId = existingJson.get("enrollmentId").asInt();
          if (existingEnrollmentId.equals(enrollment.getEnrollmentId())) {
            logger.debug(
                "Bootstrap credentials file already exists with enrollmentId {} - skipping export",
                existingEnrollmentId);
            return;
          }
        } catch (Exception e) { // CHECKSTYLE IGNORE IllegalCatch
          // Jackson 3 / ClassCastException are unchecked; never log message (may contain file
          // body).
          logger.warn(
              "Failed to read existing bootstrap credentials file, will overwrite ({})",
              e.getClass().getSimpleName());
        }
      }

      if (parentDir != null && !Files.exists(parentDir)) {
        Files.createDirectories(parentDir);
        logger.debug("Created bootstrap export directory: {}", parentDir);
      }

      ObjectNode jsonNode = objectMapper.createObjectNode();
      jsonNode.put("enrollmentId", enrollment.getEnrollmentId());
      jsonNode.put("username", username);

      if (!recoveryPrimary) {
        jsonNode.put("enrollmentProofToken", enrollmentProofToken);
        jsonNode.put("enrollmentChallengeCode", enrollment.getEnrollmentChallenge());
        String authBaseUrl = qrCodeProperties.getAuthBaseUrl();
        if (authBaseUrl != null && !authBaseUrl.isBlank()) {
          jsonNode.put("authUrl", authBaseUrl.strip());
        }
      }

      if (hasRecoveryCodes) {
        ArrayNode codesArray = jsonNode.putArray("recoveryCodes");
        for (String code : recoveryCodes) {
          codesArray.add(code);
        }
      }

      writeAtomicallyOwnerOnly(filePath, jsonNode);

      logger.info(
          "✅ Bootstrap credentials exported to file: {} (enrollmentId: {}, mode: {}, 0600)",
          exportProperties.getPath(),
          enrollment.getEnrollmentId(),
          mode);
    } catch (UnsupportedOperationException e) {
      logger.error(
          "POSIX permissions unsupported at {}; refusing to write bootstrap-credentials.json"
              + " without atomic owner-only mode",
          exportProperties.getPath());
      failClosedIfRecoveryCodesRequired(hasRecoveryCodes, e);
    } catch (IOException | RuntimeException e) { // CHECKSTYLE IGNORE IllegalCatch
      // Jackson 3 unchecked + I/O: never log message/stack (path + type only).
      logger.error(
          "Failed to export bootstrap credentials to file: {} ({})",
          exportProperties.getPath(),
          e.getClass().getSimpleName());
      failClosedIfRecoveryCodesRequired(hasRecoveryCodes, e);
    }
  }

  /**
   * When plaintext recovery codes must reach disk ({@code recovery_primary} or any mode with new
   * codes), export failure must abort bootstrap — codes are never written to logs.
   *
   * @param hasRecoveryCodes whether this export carried plaintext recovery codes
   * @param cause underlying failure (type used in operator message; message/stack omitted)
   */
  private void failClosedIfRecoveryCodesRequired(boolean hasRecoveryCodes, Exception cause) {
    if (!hasRecoveryCodes) {
      return;
    }
    throw new IllegalStateException(
        "Bootstrap recovery codes could not be persisted to "
            + exportProperties.getPath()
            + " ("
            + cause.getClass().getSimpleName()
            + "); refusing to complete bootstrap. Codes are not written to logs — fix the export"
            + " path (POSIX 0600 writable) and restart Admin API.",
        cause);
  }

  /**
   * After a successful bind, removes enrollment proof token and challenge from the credentials file
   * while keeping recovery codes (and username / enrollmentId).
   *
   * @param enrollmentId enrollment that was bound
   * @return {@code true} when the file was rewritten; {@code false} when skipped (missing, wrong
   *     enrollment, already clean, or I/O / POSIX failure)
   */
  public boolean removeBindSecretsKeepRecoveryCodes(Integer enrollmentId) {
    if (enrollmentId == null) {
      return false;
    }
    Path filePath = Path.of(exportProperties.getPath());
    if (!Files.exists(filePath)) {
      return false;
    }
    try {
      ObjectNode json = (ObjectNode) objectMapper.readTree(filePath.toFile());
      if (!json.has("enrollmentId") || json.get("enrollmentId").asInt() != enrollmentId) {
        clearRedactFailureKind();
        return false;
      }
      boolean hadSecrets =
          json.has("enrollmentProofToken")
              || json.has("enrollmentChallengeCode")
              || json.has("authUrl");
      if (!hadSecrets) {
        logger.debug(
            "Bootstrap credentials already free of bind secrets (enrollmentId: {})", enrollmentId);
        clearRedactFailureKind();
        return false;
      }
      json.remove("enrollmentProofToken");
      json.remove("enrollmentChallengeCode");
      json.remove("authUrl");
      writeAtomicallyOwnerOnly(filePath, json);
      clearRedactFailureKind();
      logger.info(
          "Removed bind secrets from bootstrap credentials file (enrollmentId: {}); recovery codes"
              + " retained — delete the file after copying codes to a safe store",
          enrollmentId);
      return true;
    } catch (IOException | RuntimeException e) { // CHECKSTYLE IGNORE IllegalCatch
      // Jackson 3 throws unchecked parse errors; ObjectNode cast can throw ClassCastException.
      // Never log e.getMessage() / stack — corrupt files may contain recovery codes or tokens.
      logRedactFailureOnce(filePath, e.getClass().getSimpleName());
      return false;
    }
  }

  /**
   * Logs a redact failure at most once per distinct exception class until a successful/skip path
   * clears the state (ShedLock tick must not spam ERROR every 60s).
   *
   * @param filePath credentials file path (path only; never contents)
   * @param exceptionSimpleName {@link Class#getSimpleName()} of the failure
   */
  private void logRedactFailureOnce(Path filePath, String exceptionSimpleName) {
    String kind = Objects.requireNonNullElse(exceptionSimpleName, "Unknown");
    String previous = lastRedactFailureKind.get();
    if (kind.equals(previous)) {
      return;
    }
    lastRedactFailureKind.set(kind);
    logger.error("Failed to redact bind secrets from {} ({})", filePath, kind);
  }

  private void clearRedactFailureKind() {
    lastRedactFailureKind.set(null);
  }

  /**
   * Writes JSON via a temp file created with {@code rw-------}, then atomic replace.
   *
   * @param filePath destination path
   * @param jsonNode content to persist
   * @throws IOException on I/O failure
   * @throws UnsupportedOperationException when POSIX file permissions are unavailable
   */
  private void writeAtomicallyOwnerOnly(Path filePath, ObjectNode jsonNode) throws IOException {
    Path parent = filePath.getParent() != null ? filePath.getParent() : Path.of(".");
    FileAttribute<Set<PosixFilePermission>> attr =
        PosixFilePermissions.asFileAttribute(OWNER_READ_WRITE_ONLY);
    Path tmp = parent.resolve("bootstrap-credentials." + java.util.UUID.randomUUID() + ".tmp");
    Files.createFile(tmp, attr);
    try {
      objectMapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), jsonNode);
      try {
        Files.move(
            tmp, filePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(tmp, filePath, StandardCopyOption.REPLACE_EXISTING);
      }
      tmp = null;
    } finally {
      if (tmp != null) {
        Files.deleteIfExists(tmp);
      }
    }
  }

  /**
   * Returns a short operator-facing pointer to the credentials file (for log lines).
   *
   * @return pointer string including path and mode hint
   */
  public String recoveryCodesLogPointer() {
    return "<see " + exportProperties.getPath() + " (0600)>";
  }
}
