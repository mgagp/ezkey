/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AuthAttemptPendingOwnershipRateLimitTest
 * Description: Pending per-enrollment budget is charged only after proof (#1124).
 */

package org.ezkey.authattempt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.OptionalLong;
import org.ezkey.audit.util.ClientContext;
import org.ezkey.authattempt.domain.AuthAttemptPendingRequest;
import org.ezkey.authattempt.domain.AuthAttemptPendingResponse;
import org.ezkey.authattempt.domain.AuthAttemptStatus;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.ezkey.authattempt.domain.repository.AuthAttemptRepository;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.exception.auth.AuthAttemptRequestFailedException;
import org.ezkey.exception.auth.AuthPendingRateLimitExceededException;
import org.ezkey.signature.SignatureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Ownership gate for the pending per-enrollment rate limit.
 *
 * @since 2026
 */
@ExtendWith(MockitoExtension.class)
class AuthAttemptPendingOwnershipRateLimitTest {

  @Mock private AuthAttemptRepository authAttemptRepository;
  @Mock private EnrollmentRepository enrollmentRepository;
  @Mock private SignatureService signatureService;
  @Mock private ObjectProvider<AuthPendingOwnershipRateLimiter> ownershipRateLimiter;

  private AuthAttemptPendingService service;

  @BeforeEach
  void setUp() {
    service =
        new AuthAttemptPendingService(
            authAttemptRepository, enrollmentRepository, signatureService, ownershipRateLimiter);
  }

  @Test
  @DisplayName("Invalid proof token does not debit the enrollment budget")
  void invalidProof_doesNotDebitEnrollmentBudget() {
    when(enrollmentRepository.findByEnrollmentProofTokenHashAndActive(any(), eq(true)))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.pending(request(), client()))
        .isInstanceOf(AuthAttemptRequestFailedException.class);
    verify(ownershipRateLimiter, never()).getIfAvailable();
  }

  @Test
  @DisplayName("Invalid device signature does not debit the enrollment budget")
  void invalidSignature_doesNotDebitEnrollmentBudget() {
    Enrollment enrollment = enrollment();
    when(enrollmentRepository.findByEnrollmentProofTokenHashAndActive(any(), eq(true)))
        .thenReturn(Optional.of(enrollment));
    when(signatureService.validateSignature(any(), any(), eq("device-pk"))).thenReturn(false);

    assertThatThrownBy(() -> service.pending(request(), client()))
        .isInstanceOf(AuthAttemptRequestFailedException.class);
    verify(ownershipRateLimiter, never()).getIfAvailable();
  }

  @Test
  @DisplayName("Proven pending uses the validated enrollment id and can still claim the prompt")
  void provenPending_debitsValidatedEnrollmentAndReturnsPrompt() {
    Enrollment enrollment = enrollment();
    when(enrollment.getAuthAttemptChallengeRequired()).thenReturn(false);
    when(enrollment.getIntegrationPrivateKey()).thenReturn("int-priv");
    when(enrollment.getIntegrationPublicKey()).thenReturn("int-pub");
    AuthPendingOwnershipRateLimiter limiter = mock(AuthPendingOwnershipRateLimiter.class);
    when(ownershipRateLimiter.getIfAvailable()).thenReturn(limiter);
    when(limiter.tryConsumeAfterOwnership(42)).thenReturn(OptionalLong.empty());
    when(enrollmentRepository.findByEnrollmentProofTokenHashAndActive(any(), eq(true)))
        .thenReturn(Optional.of(enrollment));
    when(signatureService.validateSignature(any(), any(), eq("device-pk"))).thenReturn(true);
    when(authAttemptRepository.existsByDeviceProofTokenHash(any())).thenReturn(false);
    when(authAttemptRepository.findAndLockMostRecentValidByEnrollmentIdAndStatus(
            eq(42), eq(AuthAttemptStatus.PENDING.name()), any()))
        .thenReturn(Optional.of(attempt()));
    when(authAttemptRepository.save(any(AuthAttempt.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(signatureService.signIntegrationPayload(any(), eq("int-priv"))).thenReturn("signed");
    when(signatureService.normalizeIntegrationPublicKeyToBase64("int-pub")).thenReturn("int-pub");
    when(signatureService.verifyIntegrationSignature(any(), eq("signed"), eq("int-pub")))
        .thenReturn(true);

    AuthAttemptPendingResponse response = service.pending(request(), client());

    assertThat(response.getAuthAttemptId()).isEqualTo(7);
    assertThat(response.getAuthAttemptProofTokenSignedByIntegration()).isEqualTo("signed");
    InOrder order = inOrder(limiter);
    order.verify(limiter).refundFailIpAfterOwnership("198.51.100.9");
    order.verify(limiter).tryConsumeAfterOwnership(42);
  }

  @Test
  @DisplayName("Exhausted post-proof budget blocks the claim")
  void provenPending_whenBudgetExhausted_doesNotClaim() {
    Enrollment enrollment = enrollment();
    AuthPendingOwnershipRateLimiter limiter = mock(AuthPendingOwnershipRateLimiter.class);
    when(ownershipRateLimiter.getIfAvailable()).thenReturn(limiter);
    when(limiter.tryConsumeAfterOwnership(42)).thenReturn(OptionalLong.of(45L));
    when(enrollmentRepository.findByEnrollmentProofTokenHashAndActive(any(), eq(true)))
        .thenReturn(Optional.of(enrollment));
    when(signatureService.validateSignature(any(), any(), eq("device-pk"))).thenReturn(true);
    when(authAttemptRepository.existsByDeviceProofTokenHash(any())).thenReturn(false);

    assertThatThrownBy(() -> service.pending(request(), client()))
        .isInstanceOf(AuthPendingRateLimitExceededException.class)
        .satisfies(ex ->
            assertThat(((AuthPendingRateLimitExceededException) ex).getRetryAfterSeconds())
                .isEqualTo(45L));
    verify(authAttemptRepository, never())
        .findAndLockMostRecentValidByEnrollmentIdAndStatus(any(), any(), any());
    verify(limiter).refundFailIpAfterOwnership("198.51.100.9");
  }

  private static AuthAttemptPendingRequest request() {
    AuthAttemptPendingRequest request = new AuthAttemptPendingRequest();
    request.setEnrollmentId(42);
    request.setEnrollmentProofToken("proof-token");
    request.setDeviceProofToken("device-token");
    request.setDeviceProofTokenSigned("device-sig");
    return request;
  }

  private static ClientContext client() {
    return new ClientContext("198.51.100.9", "EzkeyTestAgent/1.0");
  }

  private static Enrollment enrollment() {
    Enrollment enrollment = mock(Enrollment.class);
    when(enrollment.getEnrollmentId()).thenReturn(42);
    when(enrollment.getDevicePublicKey()).thenReturn("device-pk");
    return enrollment;
  }

  private static AuthAttempt attempt() {
    AuthAttempt attempt = new AuthAttempt();
    attempt.setAuthAttemptId(7);
    attempt.setEnrollmentId(42);
    attempt.setCreatedAt(OffsetDateTime.parse("2026-11-13T12:00:00Z"));
    attempt.setAuthAttemptStatus(AuthAttemptStatus.PENDING);
    attempt.setAuthAttemptProofToken("attempt-proof");
    return attempt;
  }
}
