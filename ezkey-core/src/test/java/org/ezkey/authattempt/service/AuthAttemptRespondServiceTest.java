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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.ezkey.authattempt.domain.AuthAttemptRespondRequest;
import org.ezkey.authattempt.domain.AuthAttemptRespondResponse;
import org.ezkey.authattempt.domain.AuthAttemptStatus;
import org.ezkey.authattempt.domain.AuthenticationResult;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.ezkey.authattempt.domain.repository.AuthAttemptRepository;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.exception.ResourceNotFoundException;
import org.ezkey.exception.auth.AuthAttemptRespondBeforeReadException;
import org.ezkey.exception.auth.AuthAttemptStateConflictException;
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

  private AuthAttemptRespondService respondService;

  @BeforeEach
  void setUp() {
    respondService =
        new AuthAttemptRespondService(
            authAttemptRepository, enrollmentRepository, signatureService, authAttemptTxHelper);
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
            eq(42), eq(AuthAttemptStatus.READ), eq(AuthAttemptStatus.ACCEPTED)))
        .thenReturn(1);

    AuthAttemptRespondResponse response = respondService.respond(approveRequest());

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
        .updateStatusIfCurrent(42, AuthAttemptStatus.READ, AuthAttemptStatus.ACCEPTED);
    verify(authAttemptRepository, never()).save(any());
  }

  @Test
  @DisplayName(
      "Invalid device signature returns signed FAILED (non-regression #844) — never unsigned")
  void respond_invalidSignature_returnsSignedFailedResult() {
    AuthAttempt attempt = readAttempt();
    Enrollment enrollment = enrollmentWithKeys();

    when(authAttemptRepository.findById(42)).thenReturn(Optional.of(attempt));
    when(enrollmentRepository.findById(7)).thenReturn(Optional.of(enrollment));
    when(authAttemptRepository.findNewerAttemptByEnrollmentId(any(), any()))
        .thenReturn(Optional.empty());
    when(signatureService.validateSignature(any(), any(), eq(DEVICE_PUBLIC_KEY))).thenReturn(false);
    when(signatureService.signIntegrationPayload(any(), eq(INTEGRATION_PRIVATE_KEY)))
        .thenReturn("signed-failed-result-b64");

    AuthAttemptRespondResponse response = respondService.respond(approveRequest());

    assertThat(response.getResult()).isEqualTo(AuthenticationResult.FAILED);
    assertThat(response.getMessage()).isEqualTo("The response could not be processed.");
    assertThat(response.getAuthAttemptProofTokenResultSignedByIntegration())
        .isEqualTo("signed-failed-result-b64");
    verify(authAttemptTxHelper).markAsInvalid(42);
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

    AuthAttemptRespondResponse response = respondService.respond(request);

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

    assertThatThrownBy(() -> respondService.respond(approveRequest()))
        .isInstanceOf(AuthAttemptStateConflictException.class)
        .hasMessageContaining(terminal.name());
    verify(signatureService, never()).signIntegrationPayload(any(), any());
  }

  @Test
  @DisplayName("PENDING respond → respond-before-read protocol violation (#844)")
  void respond_pendingStatus_raisesRespondBeforeRead() {
    AuthAttempt attempt = readAttempt();
    attempt.setAuthAttemptStatus(AuthAttemptStatus.PENDING);
    when(authAttemptRepository.findById(42)).thenReturn(Optional.of(attempt));

    assertThatThrownBy(() -> respondService.respond(approveRequest()))
        .isInstanceOf(AuthAttemptRespondBeforeReadException.class);
    verify(signatureService, never()).signIntegrationPayload(any(), any());
  }

  @Test
  @DisplayName("Attempt not found → ResourceNotFoundException (404), not unsigned FAILED (#844)")
  void respond_attemptNotFound_raisesResourceNotFound() {
    when(authAttemptRepository.findById(42)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> respondService.respond(approveRequest()))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("AuthAttempt");
    verify(signatureService, never()).signIntegrationPayload(any(), any());
  }

  @Test
  @DisplayName("Enrollment not found → ResourceNotFoundException (404) (#844)")
  void respond_enrollmentNotFound_raisesResourceNotFound() {
    AuthAttempt attempt = readAttempt();
    when(authAttemptRepository.findById(42)).thenReturn(Optional.of(attempt));
    when(authAttemptRepository.findNewerAttemptByEnrollmentId(any(), any()))
        .thenReturn(Optional.empty());
    when(enrollmentRepository.findById(7)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> respondService.respond(approveRequest()))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("Enrollment");
    verify(signatureService, never()).signIntegrationPayload(any(), any());
  }

  @Test
  @DisplayName(
      "Missing integration private key refuses unsigned 200 — IntegrationSigningUnavailable (#844)")
  void respond_missingIntegrationKey_refusesUnsignedAndFailsSigningStart() {
    AuthAttempt attempt = readAttempt();
    Enrollment enrollment = enrollmentWithKeys();
    enrollment.setIntegrationPrivateKey(null);

    when(authAttemptRepository.findById(42)).thenReturn(Optional.of(attempt));
    when(enrollmentRepository.findById(7)).thenReturn(Optional.of(enrollment));
    when(authAttemptRepository.findNewerAttemptByEnrollmentId(any(), any()))
        .thenReturn(Optional.empty());
    when(signatureService.validateSignature(any(), any(), eq(DEVICE_PUBLIC_KEY))).thenReturn(true);
    when(authAttemptRepository.updateStatusIfCurrent(
            eq(42), eq(AuthAttemptStatus.READ), eq(AuthAttemptStatus.ACCEPTED)))
        .thenReturn(1);

    assertThatThrownBy(() -> respondService.respond(approveRequest()))
        .isInstanceOf(IntegrationSigningUnavailableException.class)
        .hasMessageContaining("missing or blank");
    verify(signatureService, never()).signIntegrationPayload(any(), any());
  }

  @Test
  @DisplayName("Blank integration private key on FAILED path also refuses unsigned 200 (#844)")
  void respond_blankKeyOnFailedPath_refusesUnsigned() {
    AuthAttempt attempt = readAttempt();
    Enrollment enrollment = enrollmentWithKeys();
    enrollment.setIntegrationPrivateKey("   ");

    when(authAttemptRepository.findById(42)).thenReturn(Optional.of(attempt));
    when(enrollmentRepository.findById(7)).thenReturn(Optional.of(enrollment));
    when(authAttemptRepository.findNewerAttemptByEnrollmentId(any(), any()))
        .thenReturn(Optional.empty());
    when(signatureService.validateSignature(any(), any(), eq(DEVICE_PUBLIC_KEY))).thenReturn(false);

    assertThatThrownBy(() -> respondService.respond(approveRequest()))
        .isInstanceOf(IntegrationSigningUnavailableException.class);
    verify(signatureService, never()).signIntegrationPayload(any(), any());
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
            eq(42), eq(AuthAttemptStatus.READ), eq(AuthAttemptStatus.ACCEPTED)))
        .thenReturn(0);

    assertThatThrownBy(() -> respondService.respond(approveRequest()))
        .isInstanceOf(AuthAttemptStateConflictException.class)
        .hasMessageContaining("no longer READ");
    verify(authAttemptRepository, never()).save(any());
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

    assertThatThrownBy(() -> respondService.respond(approveRequest()))
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
}
