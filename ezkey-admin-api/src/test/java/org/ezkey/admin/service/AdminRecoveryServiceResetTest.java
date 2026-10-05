/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AdminRecoveryServiceResetTest
 * Description: Regression tests for recovery enrollment reset session invalidation.
 */

package org.ezkey.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Regression coverage for recovery reset token invalidation. */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminRecoveryService enrollment reset")
class AdminRecoveryServiceResetTest {

  @Mock private EzkeyAdminRepository adminRepository;
  @Mock private AdminTokenRepository tokenRepository;
  @Mock private EnrollmentRepository enrollmentRepository;
  @Mock private SignatureService signatureService;
  @Mock private BCryptPasswordEncoder passwordEncoder;
  @Mock private AdminRecoveryProperties recoveryProperties;

  @InjectMocks private AdminRecoveryService recoveryService;

  @Test
  @DisplayName("resetEnrollment revokes active session-like tokens for recovered admin")
  void resetEnrollmentRevokesSessionTokens() {
    Enrollment enrollment = enrollment();
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

  private static EzkeyAdmin admin(Enrollment enrollment) {
    EzkeyAdmin admin = new EzkeyAdmin("recovering.admin", AdminType.GLOBAL_ADMIN);
    admin.setAdminId(7);
    admin.setEnrollment(enrollment);
    return admin;
  }

  private static Enrollment enrollment() {
    Enrollment enrollment = new Enrollment();
    enrollment.setEnrollmentId(10);
    enrollment.setStatus(EnrollmentStatus.VERIFIED);
    enrollment.setActive(true);
    enrollment.setDevicePublicKey("old-device-public-key");
    enrollment.setEnrollmentProofToken("old-proof-token");
    enrollment.setEnrollmentChallenge(123456);
    return enrollment;
  }
}
