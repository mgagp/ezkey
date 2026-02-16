/*
 * Ezkey - Open Source MFA/Passkey Alternative
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
import org.ezkey.admin.config.BootstrapExportProperties;
import org.ezkey.admin.config.QrCodeProperties;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Service for exporting bootstrap credentials to a file.
 *
 * <p>This service writes bootstrap enrollment credentials (enrollmentId, enrollmentProofToken,
 * enrollmentChallengeCode, username) to a JSON file for Docker automation. Recovery codes are NOT
 * exported (they remain logs-only for security).
 *
 * <p><b>Idempotent:</b> If the file already exists and contains the same enrollmentId, it will not
 * be overwritten to avoid unnecessary churn.
 *
 * <p><b>Security:</b> This feature should only be enabled in Docker/demo profiles, never in
 * production.
 *
 * @since 2025
 */
@Component
public class BootstrapCredentialsFileExporter {

  private static final Logger logger =
      LoggerFactory.getLogger(BootstrapCredentialsFileExporter.class);

  private final BootstrapExportProperties exportProperties;
  private final QrCodeProperties qrCodeProperties;
  private final ObjectMapper objectMapper;

  public BootstrapCredentialsFileExporter(
      BootstrapExportProperties exportProperties, QrCodeProperties qrCodeProperties) {
    this.exportProperties = exportProperties;
    this.qrCodeProperties = qrCodeProperties;
    this.objectMapper = new ObjectMapper();
  }

  /**
   * Exports bootstrap credentials to a file if export is enabled.
   *
   * <p>This method is idempotent: if the file already exists and contains the same enrollmentId, it
   * will not be overwritten.
   *
   * @param enrollment the enrollment with credentials to export
   * @param enrollmentProofToken the enrollment proof token (from before save, to ensure exact
   *     match)
   * @param username the admin username
   */
  public void exportIfEnabled(Enrollment enrollment, String enrollmentProofToken, String username) {
    if (!exportProperties.isEnabled()) {
      logger.debug("Bootstrap credentials file export is disabled");
      return;
    }

    try {
      Path filePath = Path.of(exportProperties.getPath());
      Path parentDir = filePath.getParent();

      // Check if file already exists with same enrollmentId (idempotent check)
      if (Files.exists(filePath)) {
        try {
          ObjectNode existingJson = (ObjectNode) objectMapper.readTree(filePath.toFile());
          Integer existingEnrollmentId = existingJson.get("enrollmentId").asInt();
          if (existingEnrollmentId.equals(enrollment.getEnrollmentId())) {
            logger.debug(
                "Bootstrap credentials file already exists with enrollmentId {} - skipping export",
                existingEnrollmentId);
            return;
          }
        } catch (Exception e) {
          logger.warn(
              "Failed to read existing bootstrap credentials file, will overwrite: {}",
              e.getMessage());
        }
      }

      // Create parent directory if it doesn't exist
      if (parentDir != null && !Files.exists(parentDir)) {
        Files.createDirectories(parentDir);
        logger.debug("Created bootstrap export directory: {}", parentDir);
      }

      // Create JSON object (exclude recovery codes)
      ObjectNode jsonNode = objectMapper.createObjectNode();
      jsonNode.put("enrollmentId", enrollment.getEnrollmentId());
      jsonNode.put("enrollmentProofToken", enrollmentProofToken);
      jsonNode.put("enrollmentChallengeCode", enrollment.getEnrollmentChallenge());
      jsonNode.put("username", username);

      // Include auth-api URL if configured
      String authBaseUrl = qrCodeProperties.getAuthBaseUrl();
      if (authBaseUrl != null && !authBaseUrl.isBlank()) {
        jsonNode.put("authUrl", authBaseUrl.strip());
      }

      // Write file
      objectMapper.writerWithDefaultPrettyPrinter().writeValue(filePath.toFile(), jsonNode);

      logger.info(
          "✅ Bootstrap credentials exported to file: {} (enrollmentId: {})",
          exportProperties.getPath(),
          enrollment.getEnrollmentId());
    } catch (IOException e) {
      logger.error(
          "Failed to export bootstrap credentials to file: {} - {}",
          exportProperties.getPath(),
          e.getMessage(),
          e);
      // Don't throw - export failure should not prevent bootstrap from completing
    }
  }
}
