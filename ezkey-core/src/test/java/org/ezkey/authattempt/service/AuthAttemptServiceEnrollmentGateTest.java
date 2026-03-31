/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AuthAttemptServiceEnrollmentGateTest
 * Description: Unit tests for the enrollment active/VERIFIED security gate in AuthAttemptService.
 */

package org.ezkey.authattempt.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.ezkey.authattempt.domain.AuthAttemptCreateRequest;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.ezkey.authattempt.domain.repository.AuthAttemptRepository;
import org.ezkey.config.EzkeyCoreProperties;
import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.exception.EnrollmentInactiveException;
import org.ezkey.signature.SignatureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the enrollment security gate in {@link AuthAttemptService#create}.
 *
 * <p>Verifies that authentication attempts are rejected when the target enrollment is inactive
 * (active=false) or not in VERIFIED status. This gate prevents revoked or deactivated enrollments
 * from being used to initiate new authentication flows.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthAttemptService Enrollment Security Gate Tests")
class AuthAttemptServiceEnrollmentGateTest {

  @Mock private AuthAttemptRepository authAttemptRepository;

  @Mock private EnrollmentRepository enrollmentRepository;

  @Mock private SignatureService signatureService;

  @Mock private AuthAttemptPendingService pendingService;

  @Mock private AuthAttemptRespondService respondService;

  @Mock private AuthAttemptWaitService waitService;

  private AuthAttemptService authAttemptService;

  @BeforeEach
  void setUp() {
    EzkeyCoreProperties properties = new EzkeyCoreProperties();
    authAttemptService =
        new AuthAttemptService(
            authAttemptRepository,
            enrollmentRepository,
            signatureService,
            properties,
            pendingService,
            respondService,
            waitService);
  }

  @Test
  @DisplayName("Should throw EnrollmentInactiveException when enrollment is not active")
  void create_shouldThrow_whenEnrollmentIsNotActive() {
    Enrollment enrollment = verifiedButInactiveEnrollment();
    when(enrollmentRepository.findById(1)).thenReturn(Optional.of(enrollment));

    assertThatThrownBy(() -> authAttemptService.create(createRequest()))
        .isInstanceOf(EnrollmentInactiveException.class)
        .hasMessageContaining("1");
  }

  @Test
  @DisplayName("Should throw EnrollmentInactiveException when enrollment is not VERIFIED")
  void create_shouldThrow_whenEnrollmentIsNotVerified() {
    Enrollment enrollment = activeButNotVerifiedEnrollment();
    when(enrollmentRepository.findById(1)).thenReturn(Optional.of(enrollment));

    assertThatThrownBy(() -> authAttemptService.create(createRequest()))
        .isInstanceOf(EnrollmentInactiveException.class)
        .hasMessageContaining("BOUND");
  }

  @Test
  @DisplayName("Should proceed when enrollment is active and VERIFIED")
  void create_shouldProceed_whenEnrollmentIsActiveAndVerified() {
    Enrollment enrollment = activeAndVerifiedEnrollment();
    when(enrollmentRepository.findById(1)).thenReturn(Optional.of(enrollment));
    when(signatureService.generateProofToken()).thenReturn("proof-token");

    AuthAttempt savedAttempt = new AuthAttempt();
    savedAttempt.setAuthAttemptId(10);
    when(authAttemptRepository.save(any(AuthAttempt.class))).thenReturn(savedAttempt);

    assertThatCode(() -> authAttemptService.create(createRequest())).doesNotThrowAnyException();
  }

  // --- Factories ---

  private Enrollment verifiedButInactiveEnrollment() {
    Enrollment e = new Enrollment();
    e.setEnrollmentId(1);
    e.setStatus(EnrollmentStatus.VERIFIED);
    e.setActive(false);
    return e;
  }

  private Enrollment activeButNotVerifiedEnrollment() {
    Enrollment e = new Enrollment();
    e.setEnrollmentId(1);
    e.setStatus(EnrollmentStatus.BOUND);
    e.setActive(true);
    return e;
  }

  private Enrollment activeAndVerifiedEnrollment() {
    Enrollment e = new Enrollment();
    e.setEnrollmentId(1);
    e.setStatus(EnrollmentStatus.VERIFIED);
    e.setActive(true);
    e.setAuthAttemptChallengeRequired(false);
    return e;
  }

  private AuthAttemptCreateRequest createRequest() {
    AuthAttemptCreateRequest request = new AuthAttemptCreateRequest();
    request.setEnrollmentId(1);
    request.setChallengeRequested(false);
    return request;
  }
}
