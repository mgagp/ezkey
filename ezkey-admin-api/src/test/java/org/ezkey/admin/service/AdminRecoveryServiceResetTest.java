/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AdminRecoveryServiceResetTest
 * Description: Regression tests for recovery enrollment reset (session revoke + TTL refresh).
 */

package org.ezkey.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.ezkey.admin.config.AdminMfaProperties;
import org.ezkey.admin.config.AdminRecoveryProperties;
import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.integration.domain.AdminTokenPurpose;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminType;
import org.ezkey.integration.domain.repository.AdminTokenRepository;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.ezkey.signature.SignatureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Regression coverage for recovery reset token invalidation and invitation TTL refresh. */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminRecoveryService enrollment reset")
class AdminRecoveryServiceResetTest {

  @Mock private EzkeyAdminRepository adminRepository;
  @Mock private AdminTokenRepository tokenRepository;
  @Mock private EnrollmentRepository enrollmentRepository;
  @Mock private SignatureService signatureService;
  @Mock private BCryptPasswordEncoder passwordEncoder;
  @Mock private AdminRecoveryProperties recoveryProperties;

  private AdminMfaProperties mfaProperties;
  private AdminRecoveryService recoveryService;

  @BeforeEach
  void setUp() {
    mfaProperties = new AdminMfaProperties();
    mfaProperties.getBootstrap().setEnrollmentExpirationHours(24);
    recoveryService =
        new AdminRecoveryService(
            adminRepository,
            tokenRepository,
            enrollmentRepository,
            signatureService,
            passwordEncoder,
            recoveryProperties,
            mfaProperties);
  }

  @Test
  @DisplayName("resetEnrollment revokes active session-like tokens for recovered admin")
  void resetEnrollmentRevokesSessionTokens() {
    Enrollment enrollment = enrollment(EnrollmentStatus.VERIFIED, null);
    EzkeyAdmin admin = admin(enrollment);

    when(enrollmentRepository.findById(10)).thenReturn(Optional.of(enrollment));
    when(signatureService.generateProofToken()).thenReturn("new-proof-token");
    when(signatureService.generateSecureChallenge(6)).thenReturn(654321);
    when(enrollmentRepository.save(enrollment)).thenReturn(enrollment);
    when(tokenRepository.deactivateTokensForAdminByPurpose(7, AdminTokenPurpose.SESSION))
        .thenReturn(1);
    when(tokenRepository.deactivateTokensForAdminByPurpose(7, AdminTokenPurpose.EVALUATOR_TEMP))
        .thenReturn(1);

    Enrollment reset = recoveryService.resetEnrollment(10, admin);

    assertThat(reset.getStatus()).isEqualTo(EnrollmentStatus.CREATED);
    assertThat(reset.getDevicePublicKey()).isNull();
    assertThat(reset.getEnrollmentProofToken()).isEqualTo("new-proof-token");
    assertThat(reset.getEnrollmentChallenge()).isEqualTo(654321);
    verify(tokenRepository).deactivateTokensForAdminByPurpose(7, AdminTokenPurpose.SESSION);
    verify(tokenRepository).deactivateTokensForAdminByPurpose(7, AdminTokenPurpose.EVALUATOR_TEMP);
  }

  @Test
  @DisplayName(
      "EXPIRED → reset: status CREATED + expiresAt refreshed (recover ignores enrollment status)")
  void resetEnrollmentFromExpiredRefreshesInvitationTtl() {
    OffsetDateTime past = OffsetDateTime.now().minusHours(1);
    Enrollment enrollment = enrollment(EnrollmentStatus.EXPIRED, past);
    EzkeyAdmin admin = admin(enrollment);

    when(enrollmentRepository.findById(10)).thenReturn(Optional.of(enrollment));
    when(signatureService.generateProofToken()).thenReturn("reissued-proof");
    when(signatureService.generateSecureChallenge(6)).thenReturn(111222);
    when(enrollmentRepository.save(enrollment)).thenReturn(enrollment);
    when(tokenRepository.deactivateTokensForAdminByPurpose(7, AdminTokenPurpose.SESSION))
        .thenReturn(0);
    when(tokenRepository.deactivateTokensForAdminByPurpose(7, AdminTokenPurpose.EVALUATOR_TEMP))
        .thenReturn(0);

    Enrollment reset = recoveryService.resetEnrollment(10, admin);

    assertThat(reset.getStatus()).isEqualTo(EnrollmentStatus.CREATED);
    assertThat(reset.getExpiresAt()).isNotNull();
    assertThat(reset.getExpiresAt())
        .isAfter(OffsetDateTime.now().plusHours(23))
        .isBefore(OffsetDateTime.now().plusHours(25));
    assertThat(reset.isExpired(OffsetDateTime.now())).isFalse();
    assertThat(reset.getEnrollmentProofToken()).isEqualTo("reissued-proof");
  }

  private static EzkeyAdmin admin(Enrollment enrollment) {
    EzkeyAdmin admin = new EzkeyAdmin("recovering.admin", AdminType.GLOBAL_ADMIN);
    admin.setAdminId(7);
    admin.setEnrollment(enrollment);
    return admin;
  }

  private static Enrollment enrollment(EnrollmentStatus status, OffsetDateTime expiresAt) {
    Enrollment enrollment = new Enrollment();
    enrollment.setEnrollmentId(10);
    enrollment.setStatus(status);
    enrollment.setActive(true);
    enrollment.setDevicePublicKey("old-device-public-key");
    enrollment.setEnrollmentProofToken("old-proof-token");
    enrollment.setEnrollmentChallenge(123456);
    enrollment.setExpiresAt(expiresAt);
    return enrollment;
  }
}
