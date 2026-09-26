/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: EnrollmentVerifyServiceTest
 * Description: Unit tests for enrollment verification state and error messages.
 */

package org.ezkey.enrollment.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.enrollment.domain.EnrollmentVerifyRequest;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.exception.auth.EnrollmentVerifyFailedException;
import org.ezkey.exception.auth.EnrollmentVerifyStateConflictException;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.ezkey.service.EntityEligibilityService;
import org.ezkey.signature.SignatureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataAccessResourceFailureException;

/**
 * Unit tests for {@link EnrollmentVerifyService} state validation and error messages.
 *
 * <p>Validates that the service returns status-appropriate messages when enrollment is not in BOUND
 * state (e.g. CREATED, INVALID after failed verification).
 *
 * @since 2025
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Enrollment Verify Service State Tests")
class EnrollmentVerifyServiceTest {

  @Mock private EnrollmentRepository enrollmentRepository;

  @Mock private SignatureService signatureService;

  @Mock private EzkeyAdminRepository ezkeyAdminRepository;

  @Mock private EntityEligibilityService eligibilityService;

  @Mock private EnrollmentTxHelper enrollmentTxHelper;

  @InjectMocks private EnrollmentVerifyService enrollmentVerifyService;

  private EnrollmentVerifyRequest verifyRequest;

  @BeforeEach
  void setUp() {
    when(ezkeyAdminRepository.findByEnrollmentId(100)).thenReturn(Optional.empty());

    verifyRequest = new EnrollmentVerifyRequest();
    verifyRequest.setEnrollmentId(100);
    verifyRequest.setDevicePublicKey("device-public-key");
    verifyRequest.setEnrollmentProofTokenSigned("signature");
    verifyRequest.setChallengeResponse(123456);
  }

  @Test
  @DisplayName("verify() - When enrollment ID is missing should throw EnrollmentVerifyFailed")
  void verify_WhenEnrollmentIdMissing_ShouldThrowFailed() {
    verifyRequest.setEnrollmentId(null);

    assertThrows(
        EnrollmentVerifyFailedException.class, () -> enrollmentVerifyService.verify(verifyRequest));

    verify(enrollmentRepository, never()).findById(any());
  }

  @Test
  @DisplayName("verify() - When enrollment is CREATED should throw with must be bound message")
  void verify_WhenEnrollmentCreated_ShouldThrowMustBeBoundMessage() {
    Enrollment enrollment = new Enrollment();
    enrollment.setEnrollmentId(100);
    enrollment.setStatus(EnrollmentStatus.CREATED);
    enrollment.setEnrollmentProofToken("token");
    enrollment.setEnrollmentChallenge(123456);
    enrollment.setCreatedAt(OffsetDateTime.now());

    when(enrollmentRepository.findById(100)).thenReturn(Optional.of(enrollment));

    EnrollmentVerifyStateConflictException exception =
        assertThrows(
            EnrollmentVerifyStateConflictException.class,
            () -> enrollmentVerifyService.verify(verifyRequest));

    assertTrue(
        exception.getMessage().contains("Enrollment must be bound before verification"),
        "Message should indicate enrollment must be bound: " + exception.getMessage());
    verify(enrollmentTxHelper).markInvalidAndClear(100);
  }

  @Test
  @DisplayName("verify() - When enrollment is INVALID should throw with invalidated message")
  void verify_WhenEnrollmentInvalid_ShouldThrowWithInvalidatedMessage() {
    Enrollment enrollment = new Enrollment();
    enrollment.setEnrollmentId(100);
    enrollment.setStatus(EnrollmentStatus.INVALID);
    enrollment.setEnrollmentProofToken("token");
    enrollment.setEnrollmentChallenge(null);
    enrollment.setCreatedAt(OffsetDateTime.now());

    when(enrollmentRepository.findById(100)).thenReturn(Optional.of(enrollment));

    EnrollmentVerifyStateConflictException exception =
        assertThrows(
            EnrollmentVerifyStateConflictException.class,
            () -> enrollmentVerifyService.verify(verifyRequest));

    assertTrue(
        exception.getMessage().contains("invalidated"),
        "Message should mention invalidated: " + exception.getMessage());
    assertTrue(
        exception.getMessage().contains("previous failed verification"),
        "Message should mention previous failed verification: " + exception.getMessage());
    verify(enrollmentTxHelper).markInvalidAndClear(100);
  }

  @Test
  @DisplayName("verify() - When enrollment is REVOKED should throw with revoked message")
  void verify_WhenEnrollmentRevoked_ShouldThrowWithRevokedMessage() {
    Enrollment enrollment = new Enrollment();
    enrollment.setEnrollmentId(100);
    enrollment.setStatus(EnrollmentStatus.REVOKED);
    enrollment.setEnrollmentProofToken("token");
    enrollment.setEnrollmentChallenge(null);
    enrollment.setCreatedAt(OffsetDateTime.now());

    when(enrollmentRepository.findById(100)).thenReturn(Optional.of(enrollment));

    EnrollmentVerifyStateConflictException exception =
        assertThrows(
            EnrollmentVerifyStateConflictException.class,
            () -> enrollmentVerifyService.verify(verifyRequest));

    assertTrue(
        exception.getMessage().contains("revoked"),
        "Message should mention revoked: " + exception.getMessage());
    verify(enrollmentTxHelper).markInvalidAndClear(100);
  }

  @Test
  @DisplayName("verify() - When expired cleanup fails should still return expired invitation")
  void verify_WhenExpiredCleanupFails_ShouldThrowExpiredInvitationMessage() {
    OffsetDateTime expiresAt = OffsetDateTime.now().minusMinutes(1);
    Enrollment enrollment = new Enrollment();
    enrollment.setEnrollmentId(100);
    enrollment.setIntegrationId(7);
    enrollment.setStatus(EnrollmentStatus.BOUND);
    enrollment.setEnrollmentProofToken("token");
    enrollment.setEnrollmentChallenge(123456);
    enrollment.setCreatedAt(OffsetDateTime.now().minusHours(1));
    enrollment.setExpiresAt(expiresAt);

    when(enrollmentRepository.findById(100)).thenReturn(Optional.of(enrollment));
    doThrow(new DataAccessResourceFailureException("repository unavailable"))
        .when(enrollmentTxHelper)
        .markExpiredAndEmitAudit(100, 7, expiresAt, "enrollment_expired_verify_rejected");

    EnrollmentVerifyFailedException exception =
        assertThrows(
            EnrollmentVerifyFailedException.class,
            () -> enrollmentVerifyService.verify(verifyRequest));

    assertTrue(
        exception.getMessage().contains("Enrollment invitation has expired"),
        "Message should preserve expired-invitation contract: " + exception.getMessage());
    verify(enrollmentTxHelper)
        .markExpiredAndEmitAudit(100, 7, expiresAt, "enrollment_expired_verify_rejected");
    verify(signatureService, never())
        .validateSignature(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
  }
}
