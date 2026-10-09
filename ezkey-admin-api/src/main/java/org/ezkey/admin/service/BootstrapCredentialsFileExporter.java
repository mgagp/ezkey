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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
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
 * <p>Writes bootstrap material to a JSON file with owner-only permissions ({@code 0600}) so
 * operators and Docker automation can retrieve secrets that must not appear in container logs.
 * Recovery codes are stored in this file (never in logs). Enrollment proof token and challenge are
 * included only in {@link BootstrapCredentialsOutputMode#FULL}.
 *
 * <p><b>Idempotent:</b> If the file already exists and contains the same enrollmentId, it will not
 * be overwritten to avoid unnecessary churn (except when new plaintext recovery codes must be
 * persisted).
 *
 * <p><b>Security:</b> Prefer enabling this only in Docker/demo profiles. The file mode is always
 * tightened to {@code rw-------} after write when the filesystem supports POSIX permissions.
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
          logger.warn(
              "Failed to read existing bootstrap credentials file, will overwrite: {}",
              e.getMessage());
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

      objectMapper.writerWithDefaultPrettyPrinter().writeValue(filePath.toFile(), jsonNode);
      applyOwnerReadWriteOnly(filePath);

      logger.info(
          "✅ Bootstrap credentials exported to file: {} (enrollmentId: {}, mode: {}, 0600)",
          exportProperties.getPath(),
          enrollment.getEnrollmentId(),
          mode);
    } catch (IOException e) {
      logger.error(
          "Failed to export bootstrap credentials to file: {} - {}",
          exportProperties.getPath(),
          e.getMessage(),
          e);
      if (hasRecoveryCodes) {
        logger.error(
            "Plaintext recovery codes could not be persisted to disk and are not written to logs."
                + " Enable a writable ezkey.admin.bootstrap.export.path and restart bootstrap, or"
                + " regenerate codes after first successful admin login.");
      }
    }
  }

  /**
   * Restricts the credentials file to owner read/write when the filesystem supports POSIX modes.
   *
   * @param filePath path of the written credentials file
   */
  private void applyOwnerReadWriteOnly(Path filePath) {
    try {
      Files.setPosixFilePermissions(filePath, OWNER_READ_WRITE_ONLY);
    } catch (UnsupportedOperationException e) {
      logger.warn(
          "POSIX permissions not supported for {}; ensure the host restricts access to"
              + " bootstrap-credentials.json",
          filePath);
    } catch (IOException e) {
      logger.warn("Failed to set 0600 on {}: {}", filePath, e.getMessage());
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
