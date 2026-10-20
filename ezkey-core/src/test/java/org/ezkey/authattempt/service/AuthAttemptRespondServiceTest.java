/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.authattempt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.OptionalLong;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.audit.util.ClientContext;
import org.ezkey.authattempt.domain.AuthAttemptRespondRequest;
import org.ezkey.authattempt.domain.AuthAttemptRespondResponse;
import org.ezkey.authattempt.domain.AuthAttemptStatus;
import org.ezkey.authattempt.domain.AuthenticationResult;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.ezkey.authattempt.domain.repository.AuthAttemptRepository;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.exception.auth.AuthAttemptExpiredException;
import org.ezkey.exception.auth.AuthAttemptStateConflictException;
import org.ezkey.exception.auth.AuthRespondRateLimitExceededException;
import org.ezkey.exception.auth.IntegrationSigningUnavailableException;
import org.ezkey.signature.SignatureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Unit tests for integration-signed Respond result payloads and #844 status gates in {@link
 * AuthAttemptRespondService}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthAttemptRespondService")
class AuthAttemptRespondServiceTest {

  private static final String INTEGRATION_PRIVATE_KEY = "dummy-integration-private-key";
  private static final String DEVICE_PUBLIC_KEY = "dummy-device-public-key";

  @Mock private AuthAttemptRepository authAttemptRepository;

  @Mock private EnrollmentRepository enrollmentRepository;

  @Mock private SignatureService signatureService;

  @Mock private AuthAttemptTxHelper authAttemptTxHelper;

  @Mock private AuditLogService auditLogService;

  @Mock private ObjectProvider<AuthRespondOwnershipRateLimiter> ownershipRateLimiter;

  private AuthAttemptRespondService respondService;

  @BeforeEach
  void setUp() {
    lenient().when(ownershipRateLimiter.getIfAvailable()).thenReturn(null);
    respondService =
        new AuthAttemptRespondService(
            authAttemptRepository,
            enrollmentRepository,
            signatureService,
            authAttemptTxHelper,
            auditLogService,
            ownershipRateLimiter);
  }

  @Test
  @DisplayName("Success path signs canonical respond result payload with integration private key")
  void respond_success_includes_integration_signature_over_result_payload() {
    AuthAttempt attempt = readAttempt();
    Enrollment enrollment = enrollmentWithKeys();

    when(authAttemptRepository.findById(42)).thenReturn(Optional.of(attempt));
    when(enrollmentRepository.findById(7)).thenReturn(Optional.of(enrollment));
    when(authAttemptRepository.findNewerAttemptByEnrollmentId(any(), any()))
        .thenReturn(Optional.empty());
    when(signatureService.validateSignature(any(), any(), eq(DEVICE_PUBLIC_KEY))).thenReturn(true);
    when(signatureService.signIntegrationPayload(any(), eq(INTEGRATION_PRIVATE_KEY)))
        .thenReturn("signed-result-b64");
    when(authAttemptRepository.updateStatusIfCurrent(
            eq(42), eq(AuthAttemptStatus.READ), eq(AuthAttemptStatus.ACCEPTED), any()))
        .thenReturn(1);

    AuthAttemptRespondResponse response = respondService.respond(approveRequest(), null);

    assertThat(response.getResult()).isEqualTo(AuthenticationResult.APPROVED);
    assertThat(response.getAuthAttemptProofTokenResultSignedByIntegration())
        .isEqualTo("signed-result-b64");

    ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
    verify(signatureService)
        .signIntegrationPayload(payloadCaptor.capture(), eq(INTEGRATION_PRIVATE_KEY));
    String expectedPayload =
        AuthAttemptSignaturePayload.buildRespondResultPayload(
            "proof-token-xyz", 42, AuthenticationResult.APPROVED, "Auth attempt completed");
    assertThat(payloadCaptor.getValue()).isEqualTo(expectedPayload);
    verify(authAttemptRepository)
        .updateStatusIfCurrent(
            eq(42), eq(AuthAttemptStatus.READ), eq(AuthAttemptStatus.ACCEPTED), any());
    verify(authAttemptRepository, never()).save(any());
  }

  @Test
  @DisplayName(
      "Invalid device signature → state-conflict, no INVALID, signature-rejected audit (#912)")
  void respond_invalidSignature_raisesStateConflictNoInvalid() {
    AuthAttempt attempt = readAttempt();
    Enrollment enrollment = enrollmentWithKeys();
    ClientContext clientContext = new ClientContext("203.0.113.10", "EzkeyTestAgent/1.0");

    when(authAttemptRepository.findById(42)).thenReturn(Optional.of(attempt));
    when(enrollmentRepository.findById(7)).thenReturn(Optional.of(enrollment));
    when(authAttemptRepository.findNewerAttemptByEnrollmentId(any(), any()))
        .thenReturn(Optional.empty());
    when(signatureService.validateSignature(any(), any(), eq(DEVICE_PUBLIC_KEY))).thenReturn(false);

    assertThatThrownBy(() -> respondService.respond(approveRequest(), clientContext))
        .isInstanceOf(AuthAttemptStateConflictException.class)
        .hasMessageContaining("not authorized");
    verify(authAttemptTxHelper, never()).markAsInvalid(any());
    verify(signatureService, never()).signIntegrationPayload(any(), any());
    ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
    verify(auditLogService).log(auditCaptor.capture());
    AuditLog audit = auditCaptor.getValue();
    assertThat(audit.getEventType()).isEqualTo(EventType.AUTH_ATTEMPT_RESPOND_SIGNATURE_REJECTED);
    assertThat(audit.getAuthAttemptId()).isEqualTo(42);
    assertThat(audit.getEnrollmentId()).isEqualTo(7);
    assertThat(audit.getIpAddress()).isEqualTo("203.0.113.10");
    assertThat(audit.getUserAgent()).isEqualTo("EzkeyTestAgent/1.0");
    assertThat(audit.getEventDetails()).doesNotContain("device-sig");
    assertThat(audit.getEventDetails()).doesNotContain("signature");
  }

  @Test
  @DisplayName(
      "Post-ownership budget exhausted → AuthRespondRateLimitExceededException (#912 Patrick M6)")
  void respond_validSignature_whenOwnershipBudgetExhausted_raises429Exception() {
    AuthAttempt attempt = readAttempt();
    Enrollment enrollment = enrollmentWithKeys();
    AuthRespondOwnershipRateLimiter limiter = mock(AuthRespondOwnershipRateLimiter.class);
    when(ownershipRateLimiter.getIfAvailable()).thenReturn(limiter);
    when(limiter.tryConsumeAfterOwnership(42)).thenReturn(OptionalLong.of(300L));

    when(authAttemptRepository.findById(42)).thenReturn(Optional.of(attempt));
    when(enrollmentRepository.findById(7)).thenReturn(Optional.of(enrollment));
    when(authAttemptRepository.findNewerAttemptByEnrollmentId(any(), any()))
        .thenReturn(Optional.empty());
    when(signatureService.validateSignature(any(), any(), eq(DEVICE_PUBLIC_KEY))).thenReturn(true);

    assertThatThrownBy(() -> respondService.respond(approveRequest(), null))
        .isInstanceOf(AuthRespondRateLimitExceededException.class)
        .satisfies(
            ex ->
                assertThat(((AuthRespondRateLimitExceededException) ex).getRetryAfterSeconds())
                    .isEqualTo(300L));
    verify(limiter).tryConsumeAfterOwnership(42);
    verify(authAttemptRepository, never()).updateStatusIfCurrent(any(), any(), any(), any());
    verify(signatureService, never()).signIntegrationPayload(any(), any());
  }

  @Test
  @DisplayName("Invalid signature never debits post-ownership limiter (#912 Patrick M6)")
  void respond_invalidSignature_doesNotCallOwnershipLimiter() {
    AuthAttempt attempt = readAttempt();
    Enrollment enrollment = enrollmentWithKeys();

    when(authAttemptRepository.findById(42)).thenReturn(Optional.of(attempt));
    when(enrollmentRepository.findById(7)).thenReturn(Optional.of(enrollment));
    when(authAttemptRepository.findNewerAttemptByEnrollmentId(any(), any()))
        .thenReturn(Optional.empty());
    when(signatureService.validateSignature(any(), any(), eq(DEVICE_PUBLIC_KEY))).thenReturn(false);

    assertThatThrownBy(() -> respondService.respond(approveRequest(), null))
        .isInstanceOf(AuthAttemptStateConflictException.class);
    verify(ownershipRateLimiter, never()).getIfAvailable();
  }

  /**
   * Security property for #912: a third-party (or otherwise invalid) signature on a READ,
   * non-expired attempt must not flip the row to INVALID and must stay on the generic
   * state-conflict surface.
   */
  @Test
  @DisplayName(
      "Invalid signature on READ non-expired attempt must not mark INVALID (#912 third-party DoS)")
  void respond_readNonExpired_invalidSignature_mustNotMarkInvalid() {
    AuthAttempt attempt = readAttempt();
    Enrollment enrollment = enrollmentWithKeys();

    when(authAttemptRepository.findById(42)).thenReturn(Optional.of(attempt));
    when(enrollmentRepository.findById(7)).thenReturn(Optional.of(enrollment));
    when(authAttemptRepository.findNewerAttemptByEnrollmentId(any(), any()))
        .thenReturn(Optional.empty());
    when(signatureService.validateSignature(any(), any(), eq(DEVICE_PUBLIC_KEY))).thenReturn(false);

    assertThatThrownBy(() -> respondService.respond(approveRequest(), null))
        .isInstanceOf(AuthAttemptStateConflictException.class)
        .hasMessageContaining("not authorized");
    verify(authAttemptTxHelper, never()).markAsInvalid(any());
    verify(authAttemptRepository, never()).updateStatusIfCurrent(any(), any(), any(), any());
    verify(signatureService, never()).signIntegrationPayload(any(), any());
    verify(auditLogService).log(any(AuditLog.class));
  }

  @Test
  @DisplayName(
      "Missing device public key on non-expired READ attempt → state-conflict, no INVALID (#912)")
  void respond_missingDeviceKey_raisesStateConflict() {
    AuthAttempt attempt = readAttempt();
    Enrollment enrollment = enrollmentWithKeys();
    enrollment.setDevicePublicKey(null);

    when(authAttemptRepository.findById(42)).thenReturn(Optional.of(attempt));
    when(enrollmentRepository.findById(7)).thenReturn(Optional.of(enrollment));
    when(authAttemptRepository.findNewerAttemptByEnrollmentId(any(), any()))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> respondService.respond(approveRequest(), null))
        .isInstanceOf(AuthAttemptStateConflictException.class)
        .hasMessageContaining("not authorized");
    verify(authAttemptTxHelper, never()).markAsInvalid(any());
    verify(signatureService, never()).validateSignature(any(), any(), any());
    verify(auditLogService, never()).log(any());
  }

  @Test
  @DisplayName(
      "Unknown authAttemptId + signature → unified 409 state-conflict (#912 no enrollment)")
  void respond_unknownAttemptIdWithSignature_raisesStateConflict() {
    when(authAttemptRepository.findById(999_999)).thenReturn(Optional.empty());

    AuthAttemptRespondRequest request = approveRequest();
    request.setAuthAttemptId(999_999);
    request.setAuthAttemptProofTokenSignedByDevice("junk-signature-for-unknown-id");

    assertThatThrownBy(() -> respondService.respond(request, null))
        .isInstanceOf(AuthAttemptStateConflictException.class)
        .hasMessageContaining("not found");
    verify(authAttemptTxHelper, never()).markAsInvalid(any());
    verify(signatureService, never()).validateSignature(any(), any(), any());
    verify(auditLogService, never()).log(any());
  }

  @Test
  @DisplayName("Wrong challenge returns signed FAILED (non-regression #844)")
  void respond_wrongChallenge_returnsSignedFailedResult() {
    AuthAttempt attempt = readAttempt();
    attempt.setAuthAttemptChallenge(42);
    Enrollment enrollment = enrollmentWithKeys();
    enrollment.setAuthAttemptChallengeRequired(true);

    when(authAttemptRepository.findById(42)).thenReturn(Optional.of(attempt));
    when(enrollmentRepository.findById(7)).thenReturn(Optional.of(enrollment));
    when(authAttemptRepository.findNewerAttemptByEnrollmentId(any(), any()))
        .thenReturn(Optional.empty());
    when(signatureService.validateSignature(any(), any(), eq(DEVICE_PUBLIC_KEY))).thenReturn(true);
    when(signatureService.signIntegrationPayload(any(), eq(INTEGRATION_PRIVATE_KEY)))
        .thenReturn("signed-failed-challenge-b64");

    AuthAttemptRespondRequest request = approveRequest();
    request.setAuthAttemptChallengeResponse(99);

    AuthAttemptRespondResponse response = respondService.respond(request, null);

    assertThat(response.getResult()).isEqualTo(AuthenticationResult.FAILED);
    assertThat(response.getAuthAttemptProofTokenResultSignedByIntegration())
        .isEqualTo("signed-failed-challenge-b64");
    verify(authAttemptTxHelper).markAsInvalid(42);
  }

  @ParameterizedTest
  @EnumSource(
      value = AuthAttemptStatus.class,
      names = {"EXPIRED", "ACCEPTED", "REJECTED", "INVALID"})
  @DisplayName("Terminal status (cancel-then-respond / already final) → state conflict (#844)")
  void respond_terminalStatus_raisesStateConflict(AuthAttemptStatus terminal) {
    AuthAttempt attempt = readAttempt();
    attempt.setAuthAttemptStatus(terminal);
    when(authAttemptRepository.findById(42)).thenReturn(Optional.of(attempt));

    assertThatThrownBy(() -> respondService.respond(approveRequest(), null))
        .isInstanceOf(AuthAttemptStateConflictException.class)
        .hasMessageContaining(terminal.name());
    verify(signatureService, never()).signIntegrationPayload(any(), any());
  }

  @Test
  @DisplayName("PENDING respond → unified 409 state-conflict (#844 Marc m1)")
  void respond_pendingStatus_raisesStateConflict() {
    AuthAttempt attempt = readAttempt();
    attempt.setAuthAttemptStatus(AuthAttemptStatus.PENDING);
    when(authAttemptRepository.findById(42)).thenReturn(Optional.of(attempt));

    assertThatThrownBy(() -> respondService.respond(approveRequest(), null))
        .isInstanceOf(AuthAttemptStateConflictException.class)
        .hasMessageContaining("PENDING");
    verify(signatureService, never()).signIntegrationPayload(any(), any());
  }

  @Test
  @DisplayName("Attempt not found → unified 409 state-conflict, not unsigned FAILED (#844 Marc m1)")
  void respond_attemptNotFound_raisesStateConflict() {
    when(authAttemptRepository.findById(42)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> respondService.respond(approveRequest(), null))
        .isInstanceOf(AuthAttemptStateConflictException.class)
        .hasMessageContaining("not found");
    verify(signatureService, never()).signIntegrationPayload(any(), any());
  }

  @Test
  @DisplayName("Enrollment not found → unified 409 state-conflict (#844 Marc m1)")
  void respond_enrollmentNotFound_raisesStateConflict() {
    AuthAttempt attempt = readAttempt();
    when(authAttemptRepository.findById(42)).thenReturn(Optional.of(attempt));
    when(authAttemptRepository.findNewerAttemptByEnrollmentId(any(), any()))
        .thenReturn(Optional.empty());
    when(enrollmentRepository.findById(7)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> respondService.respond(approveRequest(), null))
        .isInstanceOf(AuthAttemptStateConflictException.class)
        .hasMessageContaining("enrollment not found");
    verify(signatureService, never()).signIntegrationPayload(any(), any());
  }

  @Test
  @DisplayName(
      "Missing per-enrollment integration key → IntegrationSigningUnavailable (runtime 503,"
          + " no unsigned 200) (#844)")
  void respond_missingIntegrationKey_throwsUnavailableWithoutUnsigned200() {
    AuthAttempt attempt = readAttempt();
    Enrollment enrollment = enrollmentWithKeys();
    enrollment.setIntegrationPrivateKey(null);

    when(authAttemptRepository.findById(42)).thenReturn(Optional.of(attempt));
    when(enrollmentRepository.findById(7)).thenReturn(Optional.of(enrollment));
    when(authAttemptRepository.findNewerAttemptByEnrollmentId(any(), any()))
        .thenReturn(Optional.empty());
    when(signatureService.validateSignature(any(), any(), eq(DEVICE_PUBLIC_KEY))).thenReturn(true);
    when(authAttemptRepository.updateStatusIfCurrent(
            eq(42), eq(AuthAttemptStatus.READ), eq(AuthAttemptStatus.ACCEPTED), any()))
        .thenReturn(1);

    assertThatThrownBy(() -> respondService.respond(approveRequest(), null))
        .isInstanceOf(IntegrationSigningUnavailableException.class)
        .hasMessageContaining("missing or blank");
    verify(signatureService, never()).signIntegrationPayload(any(), any());
  }

  @Test
  @DisplayName(
      "Blank per-enrollment key on challenge-FAILED path → IntegrationSigningUnavailable"
          + " (no unsigned 200) (#844)")
  void respond_blankKeyOnFailedPath_throwsUnavailableWithoutUnsigned200() {
    AuthAttempt attempt = readAttempt();
    attempt.setAuthAttemptChallenge(42);
    Enrollment enrollment = enrollmentWithKeys();
    enrollment.setAuthAttemptChallengeRequired(true);
    enrollment.setIntegrationPrivateKey("   ");

    when(authAttemptRepository.findById(42)).thenReturn(Optional.of(attempt));
    when(enrollmentRepository.findById(7)).thenReturn(Optional.of(enrollment));
    when(authAttemptRepository.findNewerAttemptByEnrollmentId(any(), any()))
        .thenReturn(Optional.empty());
    when(signatureService.validateSignature(any(), any(), eq(DEVICE_PUBLIC_KEY))).thenReturn(true);

    AuthAttemptRespondRequest request = approveRequest();
    request.setAuthAttemptChallengeResponse(99);

    assertThatThrownBy(() -> respondService.respond(request, null))
        .isInstanceOf(IntegrationSigningUnavailableException.class);
    verify(signatureService, never()).signIntegrationPayload(any(), any());
    verify(authAttemptTxHelper).markAsInvalid(42);
  }

  @Test
  @DisplayName("Cancel wins race: updateStatusIfCurrent 0 rows → state conflict (#833 m1)")
  void respond_cancelWonRace_raisesStateConflict() {
    AuthAttempt attempt = readAttempt();
    Enrollment enrollment = enrollmentWithKeys();

    when(authAttemptRepository.findById(42)).thenReturn(Optional.of(attempt));
    when(enrollmentRepository.findById(7)).thenReturn(Optional.of(enrollment));
    when(authAttemptRepository.findNewerAttemptByEnrollmentId(any(), any()))
        .thenReturn(Optional.empty());
    when(signatureService.validateSignature(any(), any(), eq(DEVICE_PUBLIC_KEY))).thenReturn(true);
    when(authAttemptRepository.updateStatusIfCurrent(
            eq(42), eq(AuthAttemptStatus.READ), eq(AuthAttemptStatus.ACCEPTED), any()))
        .thenReturn(0);
    AuthAttempt cancelled = readAttempt();
    cancelled.setAuthAttemptStatus(AuthAttemptStatus.EXPIRED);
    when(authAttemptRepository.findById(42))
        .thenReturn(Optional.of(attempt))
        .thenReturn(Optional.of(cancelled));

    assertThatThrownBy(() -> respondService.respond(approveRequest(), null))
        .isInstanceOf(AuthAttemptStateConflictException.class)
        .hasMessageContaining("no longer READ");
    verify(authAttemptRepository, never()).save(any());
    verify(enrollmentRepository, never()).save(any());
  }

  @Test
  @DisplayName(
      "Respond after expiresAt → AuthAttemptExpiredException only after valid device signature"
          + " (#895 Christophe)")
  void respond_afterExpiresAt_raisesExpired() {
    AuthAttempt attempt = readAttempt();
    attempt.setExpiresAt(OffsetDateTime.now().minusSeconds(1));
    Enrollment enrollment = enrollmentWithKeys();

    when(authAttemptRepository.findById(42)).thenReturn(Optional.of(attempt));
    when(enrollmentRepository.findById(7)).thenReturn(Optional.of(enrollment));
    when(authAttemptRepository.findNewerAttemptByEnrollmentId(any(), any()))
        .thenReturn(Optional.empty());
    when(signatureService.validateSignature(any(), any(), eq(DEVICE_PUBLIC_KEY))).thenReturn(true);

    assertThatThrownBy(() -> respondService.respond(approveRequest(), null))
        .isInstanceOf(AuthAttemptExpiredException.class)
        .hasMessageContaining("expired");
    verify(signatureService).validateSignature(any(), any(), eq(DEVICE_PUBLIC_KEY));
    verify(authAttemptRepository, never()).updateStatusIfCurrent(any(), any(), any(), any());
    verify(signatureService, never()).signIntegrationPayload(any(), any());
    verify(authAttemptTxHelper, never()).markAsInvalid(any());
  }

  @Test
  @DisplayName(
      "Invalid signature on expired attempt → state-conflict (no typed expiry, no INVALID)"
          + " (#895 Christophe)")
  void respond_expiredInvalidSignature_raisesStateConflict() {
    AuthAttempt attempt = readAttempt();
    attempt.setExpiresAt(OffsetDateTime.now().minusSeconds(1));
    Enrollment enrollment = enrollmentWithKeys();

    when(authAttemptRepository.findById(42)).thenReturn(Optional.of(attempt));
    when(enrollmentRepository.findById(7)).thenReturn(Optional.of(enrollment));
    when(authAttemptRepository.findNewerAttemptByEnrollmentId(any(), any()))
        .thenReturn(Optional.empty());
    when(signatureService.validateSignature(any(), any(), eq(DEVICE_PUBLIC_KEY))).thenReturn(false);

    assertThatThrownBy(() -> respondService.respond(approveRequest(), null))
        .isInstanceOf(AuthAttemptStateConflictException.class)
        .hasMessageContaining("not authorized");
    verify(authAttemptTxHelper, never()).markAsInvalid(any());
    verify(authAttemptRepository, never()).updateStatusIfCurrent(any(), any(), any(), any());
    verify(signatureService, never()).signIntegrationPayload(any(), any());
    verify(auditLogService).log(any(AuditLog.class));
  }

  @Test
  @DisplayName(
      "Deny after expiresAt → AuthAttemptExpiredException after valid device signature"
          + " (#895 Christophe)")
  void respond_denyAfterExpiresAt_raisesExpired() {
    AuthAttempt attempt = readAttempt();
    attempt.setExpiresAt(OffsetDateTime.now().minusSeconds(1));
    Enrollment enrollment = enrollmentWithKeys();

    when(authAttemptRepository.findById(42)).thenReturn(Optional.of(attempt));
    when(enrollmentRepository.findById(7)).thenReturn(Optional.of(enrollment));
    when(authAttemptRepository.findNewerAttemptByEnrollmentId(any(), any()))
        .thenReturn(Optional.empty());
    when(signatureService.validateSignature(any(), any(), eq(DEVICE_PUBLIC_KEY))).thenReturn(true);

    assertThatThrownBy(() -> respondService.respond(denyRequest(), null))
        .isInstanceOf(AuthAttemptExpiredException.class)
        .hasMessageContaining("expired");
    verify(signatureService).validateSignature(any(), any(), eq(DEVICE_PUBLIC_KEY));
    verify(authAttemptRepository, never()).updateStatusIfCurrent(any(), any(), any(), any());
    verify(signatureService, never()).signIntegrationPayload(any(), any());
    verify(authAttemptTxHelper, never()).markAsInvalid(any());
  }

  @Test
  @DisplayName(
      "Owner + expired + wrong challenge → AuthAttemptExpiredException"
          + " (expiry between signature and challenge — #895 Patrick M1)")
  void respond_expiredWrongChallenge_raisesExpiredBeforeChallenge() {
    AuthAttempt attempt = readAttempt();
    attempt.setExpiresAt(OffsetDateTime.now().minusSeconds(1));
    attempt.setAuthAttemptChallenge(42);
    Enrollment enrollment = enrollmentWithKeys();
    enrollment.setAuthAttemptChallengeRequired(true);

    when(authAttemptRepository.findById(42)).thenReturn(Optional.of(attempt));
    when(enrollmentRepository.findById(7)).thenReturn(Optional.of(enrollment));
    when(authAttemptRepository.findNewerAttemptByEnrollmentId(any(), any()))
        .thenReturn(Optional.empty());
    when(signatureService.validateSignature(any(), any(), eq(DEVICE_PUBLIC_KEY))).thenReturn(true);

    AuthAttemptRespondRequest request = approveRequest();
    request.setAuthAttemptChallengeResponse(99);

    assertThatThrownBy(() -> respondService.respond(request, null))
        .isInstanceOf(AuthAttemptExpiredException.class)
        .hasMessageContaining("expired");
    verify(signatureService).validateSignature(any(), any(), eq(DEVICE_PUBLIC_KEY));
    verify(authAttemptTxHelper, never()).markAsInvalid(any());
    verify(authAttemptRepository, never()).updateStatusIfCurrent(any(), any(), any(), any());
    verify(signatureService, never()).signIntegrationPayload(any(), any());
  }

  @Test
  @DisplayName(
      "Missing device public key on expired attempt → state-conflict (no INVALID)"
          + " (#895 Patrick M2)")
  void respond_expiredMissingDeviceKey_raisesStateConflict() {
    AuthAttempt attempt = readAttempt();
    attempt.setExpiresAt(OffsetDateTime.now().minusSeconds(1));
    Enrollment enrollment = enrollmentWithKeys();
    enrollment.setDevicePublicKey(null);

    when(authAttemptRepository.findById(42)).thenReturn(Optional.of(attempt));
    when(enrollmentRepository.findById(7)).thenReturn(Optional.of(enrollment));
    when(authAttemptRepository.findNewerAttemptByEnrollmentId(any(), any()))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> respondService.respond(approveRequest(), null))
        .isInstanceOf(AuthAttemptStateConflictException.class)
        .hasMessageContaining("not authorized");
    verify(authAttemptTxHelper, never()).markAsInvalid(any());
    verify(signatureService, never()).validateSignature(any(), any(), any());
    verify(authAttemptRepository, never()).updateStatusIfCurrent(any(), any(), any(), any());
  }

  @Test
  @DisplayName("Conditional UPDATE 0 rows because expiresAt passed → AuthAttemptExpiredException")
  void respond_updateRefusedByExpiresAt_raisesExpired() {
    AuthAttempt attempt = readAttempt();
    Enrollment enrollment = enrollmentWithKeys();

    when(authAttemptRepository.findById(42)).thenReturn(Optional.of(attempt));
    when(enrollmentRepository.findById(7)).thenReturn(Optional.of(enrollment));
    when(authAttemptRepository.findNewerAttemptByEnrollmentId(any(), any()))
        .thenReturn(Optional.empty());
    when(signatureService.validateSignature(any(), any(), eq(DEVICE_PUBLIC_KEY))).thenReturn(true);
    when(authAttemptRepository.updateStatusIfCurrent(
            eq(42), eq(AuthAttemptStatus.READ), eq(AuthAttemptStatus.ACCEPTED), any()))
        .thenReturn(0);
    AuthAttempt expiredStillRead = readAttempt();
    expiredStillRead.setExpiresAt(OffsetDateTime.now().minusSeconds(1));
    when(authAttemptRepository.findById(42))
        .thenReturn(Optional.of(attempt))
        .thenReturn(Optional.of(expiredStillRead));

    assertThatThrownBy(() -> respondService.respond(approveRequest(), null))
        .isInstanceOf(AuthAttemptExpiredException.class);
    verify(enrollmentRepository, never()).save(any());
  }

  @Test
  @DisplayName("Superseded attempt raises state conflict")
  void respond_supersededAttempt_raisesStateConflict() {
    AuthAttempt attempt = readAttempt();
    AuthAttempt newerAttempt = new AuthAttempt();
    newerAttempt.setAuthAttemptId(43);

    when(authAttemptRepository.findById(42)).thenReturn(Optional.of(attempt));
    when(authAttemptRepository.findNewerAttemptByEnrollmentId(any(), any()))
        .thenReturn(Optional.of(newerAttempt));

    assertThatThrownBy(() -> respondService.respond(approveRequest(), null))
        .isInstanceOf(AuthAttemptStateConflictException.class)
        .hasMessage("Authentication attempt superseded by newer request");
  }

  private static AuthAttempt readAttempt() {
    AuthAttempt attempt = new AuthAttempt();
    attempt.setAuthAttemptId(42);
    attempt.setEnrollmentId(7);
    attempt.setAuthAttemptStatus(AuthAttemptStatus.READ);
    attempt.setAuthAttemptProofToken("proof-token-xyz");
    attempt.setExpiresAt(OffsetDateTime.now().plusHours(1));
    attempt.setCreatedAt(OffsetDateTime.parse("2025-01-01T12:00:00Z"));
    return attempt;
  }

  private static Enrollment enrollmentWithKeys() {
    Enrollment enrollment = new Enrollment();
    enrollment.setEnrollmentId(7);
    enrollment.setDevicePublicKey(DEVICE_PUBLIC_KEY);
    enrollment.setIntegrationPrivateKey(INTEGRATION_PRIVATE_KEY);
    return enrollment;
  }

  private static AuthAttemptRespondRequest approveRequest() {
    AuthAttemptRespondRequest request = new AuthAttemptRespondRequest();
    request.setAuthAttemptId(42);
    request.setAuthAttemptAccepted(true);
    request.setAuthAttemptProofTokenSignedByDevice("device-sig");
    return request;
  }

  private static AuthAttemptRespondRequest denyRequest() {
    AuthAttemptRespondRequest request = new AuthAttemptRespondRequest();
    request.setAuthAttemptId(42);
    request.setAuthAttemptAccepted(false);
    request.setAuthAttemptProofTokenSignedByDevice("device-sig");
    return request;
  }
}
