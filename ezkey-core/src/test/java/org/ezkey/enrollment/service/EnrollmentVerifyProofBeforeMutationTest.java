/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: EnrollmentVerifyProofBeforeMutationTest
 * Description: Unproven verify must not invalidate; a forged device key is rejected (#1024).
 */

package org.ezkey.enrollment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.enrollment.domain.EnrollmentVerifyRequest;
import org.ezkey.enrollment.domain.EnrollmentVerifyResponse;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.exception.auth.EnrollmentVerifyFailedException;
import org.ezkey.exception.auth.EnrollmentVerifyStateConflictException;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.ezkey.service.EntityEligibilityService;
import org.ezkey.signature.ECP256KeyPair;
import org.ezkey.signature.Ed25519KeyPair;
import org.ezkey.signature.SignatureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Reproduces the unauthenticated verify burn (#1024) with real ECDSA, not a stubbed verifier.
 *
 * <p>A keypair the caller generates can sign a payload they choose. Verification uses the
 * server-built payload, which includes the stored proof token, so that signature does not prove
 * possession and must not invalidate the row. An expired row is not marked {@code EXPIRED} on that
 * failure. An unknown id gets the same state-conflict refusal (HTTP 409) and writes nothing. A
 * signature over the real payload still completes enrollment, and a wrong challenge after that
 * proof still invalidates once.
 *
 * @since 2026
 */
@ExtendWith(MockitoExtension.class)
class EnrollmentVerifyProofBeforeMutationTest {

  private static final int ENROLLMENT_ID = 42;
  private static final int CHALLENGE = 654321;
  private static final String PROOF_TOKEN = "stored-proof-token";

  @Mock private EnrollmentRepository enrollmentRepository;
  @Mock private EzkeyAdminRepository ezkeyAdminRepository;
  @Mock private EnrollmentTxHelper enrollmentTxHelper;

  private SignatureService signatureService;
  private EnrollmentVerifyService verifyService;
  private Ed25519KeyPair integrationKeys;
  private ECP256KeyPair deviceKeys;

  @BeforeEach
  void setUp() {
    signatureService = new SignatureService();
    verifyService =
        new EnrollmentVerifyService(
            enrollmentRepository,
            ezkeyAdminRepository,
            new EntityEligibilityService(),
            signatureService,
            enrollmentTxHelper);
    integrationKeys = signatureService.generateEd25519KeyPair();
    deviceKeys = signatureService.generateECP256KeyPair();
  }

  @Test
  @DisplayName("CREATED pending enrollment stays CREATED after an unauthenticated verify")
  void unauthenticatedVerifyOnCreatedLeavesEnrollmentUsable() {
    Enrollment enrollment = pending(EnrollmentStatus.CREATED);
    when(enrollmentRepository.findById(ENROLLMENT_ID)).thenReturn(Optional.of(enrollment));

    EnrollmentVerifyRequest attack = request(0, "not-a-key", "not-a-signature");

    assertThrows(EnrollmentVerifyStateConflictException.class, () -> verifyService.verify(attack));

    assertEquals(EnrollmentStatus.CREATED, enrollment.getStatus());
    assertEquals(CHALLENGE, enrollment.getEnrollmentChallenge());
    verify(enrollmentTxHelper, never()).markInvalidAndClear(any());
    verify(enrollmentTxHelper, never()).markExpiredAndEmitAudit(any(), any(), any(), anyString());
  }

  @Test
  @DisplayName("forged device key on BOUND is rejected and the enrollment stays BOUND")
  void forgedKeyOnBoundIsRejectedWithoutInvalidating() {
    Enrollment enrollment = pending(EnrollmentStatus.BOUND);
    when(enrollmentRepository.findById(ENROLLMENT_ID)).thenReturn(Optional.of(enrollment));

    ECP256KeyPair attacker = signatureService.generateECP256KeyPair();
    String attackerPayload =
        EnrollmentSignaturePayload.buildVerifyDevicePayload(
            "guessed-token", ENROLLMENT_ID, CHALLENGE, attacker.base64PublicKey());
    String attackerSignature =
        signatureService.signEcdsaSha256(attackerPayload, attacker.base64PrivateKey());
    assertTrue(
        signatureService.validateSignature(
            attackerPayload, attackerSignature, attacker.base64PublicKey()),
        "the forged signature is valid under the attacker key for the payload they chose");

    EnrollmentVerifyRequest attack =
        request(CHALLENGE, attacker.base64PublicKey(), attackerSignature);

    assertThrows(EnrollmentVerifyStateConflictException.class, () -> verifyService.verify(attack));

    assertEquals(EnrollmentStatus.BOUND, enrollment.getStatus());
    assertEquals(PROOF_TOKEN, enrollment.getEnrollmentProofToken());
    assertEquals(CHALLENGE, enrollment.getEnrollmentChallenge());
    verify(enrollmentTxHelper, never()).markInvalidAndClear(any());
  }

  @Test
  @DisplayName("expired BOUND with a forged signature is not marked EXPIRED")
  void forgedSignatureOnExpiredBoundIsNotMarkedExpired() {
    Enrollment enrollment = pending(EnrollmentStatus.BOUND);
    enrollment.setExpiresAt(OffsetDateTime.now().minusMinutes(1));
    when(enrollmentRepository.findById(ENROLLMENT_ID)).thenReturn(Optional.of(enrollment));

    ECP256KeyPair attacker = signatureService.generateECP256KeyPair();
    String attackerPayload =
        EnrollmentSignaturePayload.buildVerifyDevicePayload(
            "guessed-token", ENROLLMENT_ID, CHALLENGE, attacker.base64PublicKey());
    String attackerSignature =
        signatureService.signEcdsaSha256(attackerPayload, attacker.base64PrivateKey());

    assertThrows(
        EnrollmentVerifyStateConflictException.class,
        () ->
            verifyService.verify(
                request(CHALLENGE, attacker.base64PublicKey(), attackerSignature)));

    assertEquals(EnrollmentStatus.BOUND, enrollment.getStatus());
    verify(enrollmentTxHelper, never()).markExpiredAndEmitAudit(any(), any(), any(), any());
    verify(enrollmentTxHelper, never()).markInvalidAndClear(any());
  }

  @Test
  @DisplayName("unknown id gets the same 409 refusal as a forged signature and writes nothing")
  void unknownIdGetsSameRefusalAsForgedSignature() {
    when(enrollmentRepository.findById(ENROLLMENT_ID)).thenReturn(Optional.empty());
    EnrollmentVerifyStateConflictException unknown =
        assertThrows(
            EnrollmentVerifyStateConflictException.class,
            () -> verifyService.verify(request(CHALLENGE, "not-a-key", "not-a-signature")));

    Enrollment enrollment = pending(EnrollmentStatus.BOUND);
    when(enrollmentRepository.findById(ENROLLMENT_ID)).thenReturn(Optional.of(enrollment));
    ECP256KeyPair attacker = signatureService.generateECP256KeyPair();
    String attackerPayload =
        EnrollmentSignaturePayload.buildVerifyDevicePayload(
            "guessed-token", ENROLLMENT_ID, CHALLENGE, attacker.base64PublicKey());
    String attackerSignature =
        signatureService.signEcdsaSha256(attackerPayload, attacker.base64PrivateKey());
    EnrollmentVerifyStateConflictException forged =
        assertThrows(
            EnrollmentVerifyStateConflictException.class,
            () ->
                verifyService.verify(
                    request(CHALLENGE, attacker.base64PublicKey(), attackerSignature)));

    assertEquals(forged.getClass(), unknown.getClass());
    assertEquals(forged.getMessage(), unknown.getMessage());
    assertEquals("Enrollment verification failed", unknown.getMessage());
    assertEquals(EnrollmentStatus.BOUND, enrollment.getStatus());
    verify(enrollmentRepository, never()).save(any());
    verify(enrollmentRepository, never()).saveAndFlush(any());
    verify(enrollmentTxHelper, never()).markInvalidAndClear(any());
    verify(enrollmentTxHelper, never()).markExpiredAndEmitAudit(any(), any(), any(), any());
  }

  @Test
  @DisplayName("legitimate signature over the stored proof token still verifies the enrollment")
  void legitimateSignatureStillVerifies() {
    Enrollment enrollment = pending(EnrollmentStatus.BOUND);
    when(enrollmentRepository.findById(ENROLLMENT_ID)).thenReturn(Optional.of(enrollment));
    when(ezkeyAdminRepository.findByEnrollmentId(ENROLLMENT_ID)).thenReturn(Optional.empty());
    when(enrollmentRepository.existsByDevicePublicKeyHash(anyString())).thenReturn(false);
    when(enrollmentRepository.findAndLockBoundById(ENROLLMENT_ID))
        .thenReturn(Optional.of(enrollment));
    when(enrollmentRepository.findByIntegrationIdAndEnrollmentNameAndStatusAndEnrollmentIdNot(
            eq(7), eq("Device"), eq(EnrollmentStatus.VERIFIED), eq(ENROLLMENT_ID)))
        .thenReturn(List.of());

    String payload =
        EnrollmentSignaturePayload.buildVerifyDevicePayload(
            PROOF_TOKEN, ENROLLMENT_ID, CHALLENGE, deviceKeys.base64PublicKey());
    String signature = signatureService.signEcdsaSha256(payload, deviceKeys.base64PrivateKey());

    EnrollmentVerifyResponse response =
        verifyService.verify(request(CHALLENGE, deviceKeys.base64PublicKey(), signature));

    assertTrue(response.isActive());
    assertEquals(EnrollmentStatus.VERIFIED, enrollment.getStatus());
    assertEquals(Boolean.TRUE, enrollment.getActive());
    verify(enrollmentTxHelper, never()).markInvalidAndClear(any());
    verify(enrollmentRepository).saveAndFlush(enrollment);
  }

  @Test
  @DisplayName("wrong challenge after a valid signature still invalidates once")
  void wrongChallengeAfterProofStillInvalidates() {
    Enrollment enrollment = pending(EnrollmentStatus.BOUND);
    when(enrollmentRepository.findById(ENROLLMENT_ID)).thenReturn(Optional.of(enrollment));
    when(ezkeyAdminRepository.findByEnrollmentId(ENROLLMENT_ID)).thenReturn(Optional.empty());
    when(enrollmentRepository.existsByDevicePublicKeyHash(anyString())).thenReturn(false);

    int wrongChallenge = CHALLENGE + 1;
    String payload =
        EnrollmentSignaturePayload.buildVerifyDevicePayload(
            PROOF_TOKEN, ENROLLMENT_ID, wrongChallenge, deviceKeys.base64PublicKey());
    String signature = signatureService.signEcdsaSha256(payload, deviceKeys.base64PrivateKey());

    assertThrows(
        EnrollmentVerifyFailedException.class,
        () ->
            verifyService.verify(request(wrongChallenge, deviceKeys.base64PublicKey(), signature)));

    verify(enrollmentTxHelper).markInvalidAndClear(ENROLLMENT_ID);
  }

  private Enrollment pending(EnrollmentStatus status) {
    Enrollment enrollment = new Enrollment();
    enrollment.setEnrollmentId(ENROLLMENT_ID);
    enrollment.setIntegrationId(7);
    enrollment.setEnrollmentName("Device");
    enrollment.setStatus(status);
    enrollment.setActive(false);
    enrollment.setEnrollmentProofToken(PROOF_TOKEN);
    enrollment.setEnrollmentChallenge(CHALLENGE);
    enrollment.setIntegrationPrivateKey(integrationKeys.base64PrivateKey());
    enrollment.setIntegrationPublicKey(integrationKeys.base64UrlPublicKey());
    enrollment.setCreatedAt(OffsetDateTime.now().minusMinutes(5));
    enrollment.setExpiresAt(OffsetDateTime.now().plusDays(1));
    return enrollment;
  }

  private static EnrollmentVerifyRequest request(
      int challengeResponse, String devicePublicKey, String signature) {
    EnrollmentVerifyRequest request = new EnrollmentVerifyRequest();
    request.setEnrollmentId(ENROLLMENT_ID);
    request.setChallengeResponse(challengeResponse);
    request.setDevicePublicKey(devicePublicKey);
    request.setEnrollmentProofTokenSigned(signature);
    return request;
  }
}
