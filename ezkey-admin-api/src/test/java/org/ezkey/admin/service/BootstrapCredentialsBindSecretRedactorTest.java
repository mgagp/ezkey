/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: BootstrapCredentialsBindSecretRedactorTest
 * Description: Admin-API redacts bind secrets once bootstrap enrollment is BOUND (idempotent).
 */

package org.ezkey.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.ezkey.admin.config.AdminMfaProperties;
import org.ezkey.admin.config.BootstrapCredentialsOutputMode;
import org.ezkey.admin.config.BootstrapExportProperties;
import org.ezkey.admin.config.InitialGlobalAdminProperties;
import org.ezkey.config.QrCodeProperties;
import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminType;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Covers BOUND → strip token/challenge, keep recovery codes, idempotent second pass.
 *
 * @since 2026
 */
@ExtendWith(MockitoExtension.class)
class BootstrapCredentialsBindSecretRedactorTest {

  private static final String USERNAME = "admin.docker";

  @TempDir Path tempDir;

  @Mock private EzkeyAdminRepository adminRepository;

  private BootstrapExportProperties exportProperties;
  private InitialGlobalAdminProperties initialGlobalAdminProperties;
  private BootstrapCredentialsFileExporter exporter;
  private BootstrapCredentialsBindSecretRedactor redactor;
  private Path credentialsFile;

  @BeforeEach
  void setUp() {
    credentialsFile = tempDir.resolve("bootstrap-credentials.json");
    exportProperties = new BootstrapExportProperties();
    exportProperties.setEnabled(true);
    exportProperties.setPath(credentialsFile.toString());

    initialGlobalAdminProperties = new InitialGlobalAdminProperties();
    initialGlobalAdminProperties.setUsername(USERNAME);

    AdminMfaProperties mfaProperties = new AdminMfaProperties();
    mfaProperties.getBootstrap().setCredentialsOutputMode(BootstrapCredentialsOutputMode.FULL);

    exporter =
        new BootstrapCredentialsFileExporter(
            exportProperties, new QrCodeProperties(), mfaProperties);
    redactor =
        new BootstrapCredentialsBindSecretRedactor(
            exportProperties, initialGlobalAdminProperties, adminRepository, exporter);
  }

  @Test
  @DisplayName("BOUND enrollment strips proof token/challenge and keeps recovery codes")
  void boundEnrollmentRedactsBindSecrets() throws Exception {
    Enrollment enrollment = enrollment(EnrollmentStatus.BOUND);
    seedCredentialsFile(enrollment);
    when(adminRepository.findByUsernameWithEnrollment(USERNAME))
        .thenReturn(Optional.of(admin(enrollment)));

    redactor.redactIfBootstrapEnrollmentPastBind();

    ObjectNode json = (ObjectNode) new ObjectMapper().readTree(credentialsFile.toFile());
    assertThat(json.has("enrollmentProofToken")).isFalse();
    assertThat(json.has("enrollmentChallengeCode")).isFalse();
    assertThat(json.has("authUrl")).isFalse();
    assertThat(json.get("recoveryCodes").get(0).asString()).isEqualTo("AAAA-BBBB-CCCC-DDDD");
    assertThat(json.get("enrollmentId").asInt()).isEqualTo(7);
  }

  @Test
  @DisplayName("second redact pass is idempotent (no further rewrite of already-clean file)")
  void secondPassIdempotent() throws Exception {
    Enrollment enrollment = enrollment(EnrollmentStatus.VERIFIED);
    seedCredentialsFile(enrollment);
    when(adminRepository.findByUsernameWithEnrollment(USERNAME))
        .thenReturn(Optional.of(admin(enrollment)));

    redactor.redactIfBootstrapEnrollmentPastBind();
    long sizeAfterFirst = Files.size(credentialsFile);
    long mtimeAfterFirst = Files.getLastModifiedTime(credentialsFile).toMillis();

    Thread.sleep(20);
    redactor.redactIfBootstrapEnrollmentPastBind();

    ObjectNode json = (ObjectNode) new ObjectMapper().readTree(credentialsFile.toFile());
    assertThat(json.has("enrollmentProofToken")).isFalse();
    assertThat(json.get("recoveryCodes")).hasSize(1);
    assertThat(Files.size(credentialsFile)).isEqualTo(sizeAfterFirst);
    assertThat(Files.getLastModifiedTime(credentialsFile).toMillis()).isEqualTo(mtimeAfterFirst);
    verify(adminRepository, times(2)).findByUsernameWithEnrollment(USERNAME);
  }

  @Test
  @DisplayName("CREATED enrollment leaves bind secrets untouched")
  void createdEnrollmentDoesNotRedact() throws Exception {
    Enrollment enrollment = enrollment(EnrollmentStatus.CREATED);
    seedCredentialsFile(enrollment);
    when(adminRepository.findByUsernameWithEnrollment(USERNAME))
        .thenReturn(Optional.of(admin(enrollment)));

    redactor.redactIfBootstrapEnrollmentPastBind();

    ObjectNode json = (ObjectNode) new ObjectMapper().readTree(credentialsFile.toFile());
    assertThat(json.get("enrollmentProofToken").asString()).isEqualTo("proof.tokenValue");
    assertThat(json.get("enrollmentChallengeCode").asInt()).isEqualTo(112233);
  }

  @Test
  @DisplayName("missing admin is a no-op")
  void missingAdminNoOp() {
    when(adminRepository.findByUsernameWithEnrollment(USERNAME)).thenReturn(Optional.empty());
    redactor.redactIfBootstrapEnrollmentPastBind();
    verify(adminRepository).findByUsernameWithEnrollment(USERNAME);
    assertThat(Files.exists(credentialsFile)).isFalse();
  }

  private void seedCredentialsFile(Enrollment enrollment) {
    exporter.exportIfEnabled(
        enrollment, "proof.tokenValue", USERNAME, List.of("AAAA-BBBB-CCCC-DDDD"));
  }

  private static EzkeyAdmin admin(Enrollment enrollment) {
    EzkeyAdmin admin = new EzkeyAdmin(USERNAME, AdminType.GLOBAL_ADMIN);
    admin.setEnrollment(enrollment);
    return admin;
  }

  private static Enrollment enrollment(EnrollmentStatus status) {
    Enrollment enrollment = new Enrollment();
    enrollment.setEnrollmentId(7);
    enrollment.setStatus(status);
    enrollment.setEnrollmentChallenge(112233);
    return enrollment;
  }
}
