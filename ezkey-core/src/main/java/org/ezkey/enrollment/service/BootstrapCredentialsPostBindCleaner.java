/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: BootstrapCredentialsPostBindCleaner
 * Description: After bind, strip proof token / challenge from bootstrap-credentials.json.
 */

package org.ezkey.enrollment.service;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Removes enrollment bind secrets from {@code bootstrap-credentials.json} after a successful bind,
 * keeping recovery codes for the operator. No-op when the export path is unset or the file is
 * absent / for a different enrollment.
 *
 * @since 2026
 */
@Component
public class BootstrapCredentialsPostBindCleaner {

  private static final Logger logger =
      LoggerFactory.getLogger(BootstrapCredentialsPostBindCleaner.class);

  private static final Set<PosixFilePermission> OWNER_READ_WRITE_ONLY =
      EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

  private final String exportPath;
  private final ObjectMapper objectMapper = new ObjectMapper();

  /**
   * Creates the cleaner.
   *
   * @param exportPath {@code ezkey.admin.bootstrap.export.path}; empty disables
   */
  public BootstrapCredentialsPostBindCleaner(
      @Value("${ezkey.admin.bootstrap.export.path:}") String exportPath) {
    this.exportPath = exportPath == null ? "" : exportPath.strip();
  }

  /**
   * Redacts proof token and challenge for the bound enrollment when the credentials file matches.
   *
   * @param enrollmentId enrollment that was just bound
   */
  public void afterEnrollmentBound(Integer enrollmentId) {
    if (enrollmentId == null || exportPath.isEmpty()) {
      return;
    }
    Path filePath = Path.of(exportPath);
    if (!Files.isRegularFile(filePath)) {
      return;
    }
    try {
      JsonNode root = objectMapper.readTree(filePath.toFile());
      if (!(root instanceof ObjectNode json)
          || !json.has("enrollmentId")
          || json.get("enrollmentId").asInt() != enrollmentId) {
        return;
      }
      if (!json.has("enrollmentProofToken") && !json.has("enrollmentChallengeCode")) {
        return;
      }
      json.remove("enrollmentProofToken");
      json.remove("enrollmentChallengeCode");
      json.remove("authUrl");
      writeOwnerOnly(filePath, json);
      logger.info(
          "Removed bind secrets from bootstrap credentials file (enrollmentId: {}); delete the"
              + " file after copying recovery codes to a safe store",
          enrollmentId);
    } catch (UnsupportedOperationException e) {
      logger.error(
          "POSIX permissions unsupported; could not redact bind secrets from {}", filePath);
    } catch (IOException e) {
      logger.error("Failed to redact bind secrets from {}: {}", filePath, e.getMessage());
    }
  }

  private void writeOwnerOnly(Path filePath, ObjectNode json) throws IOException {
    Path parent = filePath.getParent() != null ? filePath.getParent() : Path.of(".");
    Path tmp = parent.resolve("bootstrap-credentials." + java.util.UUID.randomUUID() + ".tmp");
    Files.createFile(tmp, PosixFilePermissions.asFileAttribute(OWNER_READ_WRITE_ONLY));
    try {
      objectMapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), json);
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
}
