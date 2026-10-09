/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: BootstrapCredentialsFileExporterTest
 * Description: Credentials file includes recovery codes and is written with 0600 (#750).
 */

package org.ezkey.admin.service;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Verifies the bootstrap credentials file channel for recovery codes (issue #750).
 *
 * @since 2026
 */
class BootstrapCredentialsFileExporterTest {

  @TempDir Path tempDir;

  @Test
  void exportWritesRecoveryCodesWithOwnerOnlyPermissions() throws Exception {
    Path file = tempDir.resolve("bootstrap-credentials.json");
    BootstrapExportProperties exportProperties = new BootstrapExportProperties();
    exportProperties.setEnabled(true);
    exportProperties.setPath(file.toString());

    AdminMfaProperties mfaProperties = new AdminMfaProperties();
    mfaProperties.getBootstrap().setCredentialsOutputMode(BootstrapCredentialsOutputMode.FULL);

    BootstrapCredentialsFileExporter exporter =
        new BootstrapCredentialsFileExporter(
            exportProperties, new QrCodeProperties(), mfaProperties);

    Enrollment enrollment = new Enrollment();
    enrollment.setEnrollmentId(7);
    enrollment.setEnrollmentChallenge(112233);

    exporter.exportIfEnabled(
        enrollment, "proof.tokenValue", "admin.docker", List.of("AAAA-BBBB-CCCC-DDDD"));

    assertThat(file).exists();
    ObjectNode json = (ObjectNode) new ObjectMapper().readTree(file.toFile());
    assertThat(json.get("enrollmentId").asInt()).isEqualTo(7);
    assertThat(json.get("enrollmentProofToken").asString()).isEqualTo("proof.tokenValue");
    assertThat(json.get("recoveryCodes").get(0).asString()).isEqualTo("AAAA-BBBB-CCCC-DDDD");

    try {
      Set<PosixFilePermission> perms = Files.getPosixFilePermissions(file);
      assertThat(perms)
          .isEqualTo(EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
    } catch (UnsupportedOperationException ignored) {
      // Non-POSIX CI hosts: content assertions above still apply
    }
  }

  @Test
  void recoveryPrimaryOmitsEnrollmentSecretsButKeepsRecoveryCodes() throws Exception {
    Path file = tempDir.resolve("bootstrap-credentials.json");
    BootstrapExportProperties exportProperties = new BootstrapExportProperties();
    exportProperties.setEnabled(true);
    exportProperties.setPath(file.toString());

    AdminMfaProperties mfaProperties = new AdminMfaProperties();
    mfaProperties
        .getBootstrap()
        .setCredentialsOutputMode(BootstrapCredentialsOutputMode.RECOVERY_PRIMARY);

    BootstrapCredentialsFileExporter exporter =
        new BootstrapCredentialsFileExporter(
            exportProperties, new QrCodeProperties(), mfaProperties);

    Enrollment enrollment = new Enrollment();
    enrollment.setEnrollmentId(3);
    enrollment.setEnrollmentChallenge(999999);

    exporter.exportIfEnabled(
        enrollment, "must-not-appear", "admin.docker", List.of("ZZZZ-YYYY-XXXX-WWWW"));

    ObjectNode json = (ObjectNode) new ObjectMapper().readTree(file.toFile());
    assertThat(json.has("enrollmentProofToken")).isFalse();
    assertThat(json.has("enrollmentChallengeCode")).isFalse();
    assertThat(json.get("recoveryCodes").get(0).asString()).isEqualTo("ZZZZ-YYYY-XXXX-WWWW");
    assertThat(exporter.recoveryCodesLogPointer()).contains("0600");
  }

  @Test
  void removeBindSecretsKeepsRecoveryCodes() throws Exception {
    Path file = tempDir.resolve("bootstrap-credentials.json");
    BootstrapExportProperties exportProperties = new BootstrapExportProperties();
    exportProperties.setEnabled(true);
    exportProperties.setPath(file.toString());

    AdminMfaProperties mfaProperties = new AdminMfaProperties();
    mfaProperties.getBootstrap().setCredentialsOutputMode(BootstrapCredentialsOutputMode.FULL);

    BootstrapCredentialsFileExporter exporter =
        new BootstrapCredentialsFileExporter(
            exportProperties, new QrCodeProperties(), mfaProperties);

    Enrollment enrollment = new Enrollment();
    enrollment.setEnrollmentId(7);
    enrollment.setEnrollmentChallenge(112233);
    exporter.exportIfEnabled(
        enrollment, "proof.tokenValue", "admin.docker", List.of("AAAA-BBBB-CCCC-DDDD"));

    exporter.removeBindSecretsKeepRecoveryCodes(7);

    ObjectNode json = (ObjectNode) new ObjectMapper().readTree(file.toFile());
    assertThat(json.has("enrollmentProofToken")).isFalse();
    assertThat(json.has("enrollmentChallengeCode")).isFalse();
    assertThat(json.get("recoveryCodes").get(0).asString()).isEqualTo("AAAA-BBBB-CCCC-DDDD");
    assertThat(json.get("enrollmentId").asInt()).isEqualTo(7);
  }
}
