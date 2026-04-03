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
import org.ezkey.exception.auth.AuthAttemptStateConflictException;
import org.ezkey.signature.SignatureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for integration-signed Respond result payloads in {@link AuthAttemptRespondService}.
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
    AuthAttempt attempt = new AuthAttempt();
    attempt.setAuthAttemptId(42);
    attempt.setEnrollmentId(7);
    attempt.setAuthAttemptStatus(AuthAttemptStatus.READ);
    attempt.setAuthAttemptProofToken("proof-token-xyz");
    attempt.setExpiresAt(OffsetDateTime.now().plusHours(1));
    attempt.setCreatedAt(OffsetDateTime.parse("2025-01-01T12:00:00Z"));

    Enrollment enrollment = new Enrollment();
    enrollment.setEnrollmentId(7);
    enrollment.setDevicePublicKey(DEVICE_PUBLIC_KEY);
    enrollment.setIntegrationPrivateKey(INTEGRATION_PRIVATE_KEY);

    when(authAttemptRepository.findById(42)).thenReturn(Optional.of(attempt));
    when(enrollmentRepository.findById(7)).thenReturn(Optional.of(enrollment));
    when(authAttemptRepository.findNewerAttemptByEnrollmentId(any(), any()))
        .thenReturn(Optional.empty());
    when(signatureService.validateSignature(any(), any(), eq(DEVICE_PUBLIC_KEY))).thenReturn(true);
    when(signatureService.signIntegrationPayload(any(), eq(INTEGRATION_PRIVATE_KEY)))
        .thenReturn("signed-result-b64");

    AuthAttemptRespondRequest request = new AuthAttemptRespondRequest();
    request.setAuthAttemptId(42);
    request.setAuthAttemptAccepted(true);
    request.setAuthAttemptProofTokenSignedByDevice("device-sig");

    AuthAttemptRespondResponse response = respondService.respond(request);

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
  }

  @Test
  @DisplayName(
      "Invalid signature returns FAILED result with safe message and integration signature")
  void respond_invalidSignature_returnsFailedResult() {
    AuthAttempt attempt = new AuthAttempt();
    attempt.setAuthAttemptId(42);
    attempt.setEnrollmentId(7);
    attempt.setAuthAttemptStatus(AuthAttemptStatus.READ);
    attempt.setAuthAttemptProofToken("proof-token-xyz");
    attempt.setExpiresAt(OffsetDateTime.now().plusHours(1));
    attempt.setCreatedAt(OffsetDateTime.parse("2025-01-01T12:00:00Z"));

    Enrollment enrollment = new Enrollment();
    enrollment.setEnrollmentId(7);
    enrollment.setDevicePublicKey(DEVICE_PUBLIC_KEY);
    enrollment.setIntegrationPrivateKey(INTEGRATION_PRIVATE_KEY);

    when(authAttemptRepository.findById(42)).thenReturn(Optional.of(attempt));
    when(enrollmentRepository.findById(7)).thenReturn(Optional.of(enrollment));
    when(authAttemptRepository.findNewerAttemptByEnrollmentId(any(), any()))
        .thenReturn(Optional.empty());
    when(signatureService.validateSignature(any(), any(), eq(DEVICE_PUBLIC_KEY))).thenReturn(false);
    when(signatureService.signIntegrationPayload(any(), eq(INTEGRATION_PRIVATE_KEY)))
        .thenReturn("signed-failed-result-b64");

    AuthAttemptRespondRequest request = new AuthAttemptRespondRequest();
    request.setAuthAttemptId(42);
    request.setAuthAttemptAccepted(true);
    request.setAuthAttemptProofTokenSignedByDevice("bad-device-sig");

    AuthAttemptRespondResponse response = respondService.respond(request);

    assertThat(response.getResult()).isEqualTo(AuthenticationResult.FAILED);
    assertThat(response.getMessage()).isEqualTo("The response could not be processed.");
    assertThat(response.getAuthAttemptProofTokenResultSignedByIntegration())
        .isEqualTo("signed-failed-result-b64");
  }

  @Test
  @DisplayName("Superseded attempt raises state conflict")
  void respond_supersededAttempt_raisesStateConflict() {
    AuthAttempt attempt = new AuthAttempt();
    attempt.setAuthAttemptId(42);
    attempt.setEnrollmentId(7);
    attempt.setAuthAttemptStatus(AuthAttemptStatus.READ);
    attempt.setAuthAttemptProofToken("proof-token-xyz");
    attempt.setExpiresAt(OffsetDateTime.now().plusHours(1));
    attempt.setCreatedAt(OffsetDateTime.parse("2025-01-01T12:00:00Z"));

    AuthAttempt newerAttempt = new AuthAttempt();
    newerAttempt.setAuthAttemptId(43);

    when(authAttemptRepository.findById(42)).thenReturn(Optional.of(attempt));
    when(authAttemptRepository.findNewerAttemptByEnrollmentId(any(), any()))
        .thenReturn(Optional.of(newerAttempt));

    AuthAttemptRespondRequest request = new AuthAttemptRespondRequest();
    request.setAuthAttemptId(42);
    request.setAuthAttemptAccepted(true);
    request.setAuthAttemptProofTokenSignedByDevice("device-sig");

    assertThatThrownBy(() -> respondService.respond(request))
        .isInstanceOf(AuthAttemptStateConflictException.class)
        .hasMessage("Authentication attempt superseded by newer request");
  }
}
