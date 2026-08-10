/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.enrollment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.ezkey.enrollment.domain.EnrollmentInstanceInfoResponse;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.exception.auth.EnrollmentInstanceInfoFailedException;
import org.ezkey.instance.dto.PublicInstanceInfoResponseDto;
import org.ezkey.instance.service.PublicInstanceInfoService;
import org.ezkey.security.SensitiveDataHasher;
import org.ezkey.signature.Ed25519KeyPair;
import org.ezkey.signature.SignatureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link EnrollmentInstanceInfoService}. */
class EnrollmentInstanceInfoServiceTest {

  private EnrollmentRepository enrollmentRepository;
  private PublicInstanceInfoService publicInstanceInfoService;
  private SignatureService signatureService;
  private EnrollmentInstanceInfoService service;

  private Ed25519KeyPair keyPair;
  private String proofToken;

  @BeforeEach
  void setUp() {
    enrollmentRepository = mock(EnrollmentRepository.class);
    publicInstanceInfoService = mock(PublicInstanceInfoService.class);
    signatureService = new SignatureService();
    service =
        new EnrollmentInstanceInfoService(
            enrollmentRepository, publicInstanceInfoService, signatureService);
    keyPair = signatureService.generateEd25519KeyPair();
    proofToken = "proof.token.example";
  }

  @Test
  @DisplayName("returns branding signed with enrollment integration key")
  void getSignedInstanceInfo_Success() {
    Enrollment enrollment = new Enrollment();
    enrollment.setEnrollmentId(42);
    enrollment.setEnrollmentProofToken(proofToken);
    enrollment.setIntegrationPrivateKey(keyPair.base64PrivateKey());
    enrollment.setIntegrationPublicKey(keyPair.base64UrlPublicKey());

    when(enrollmentRepository.findByEnrollmentProofTokenHashAndActive(
            SensitiveDataHasher.sha256Hex(proofToken), true))
        .thenReturn(Optional.of(enrollment));
    when(publicInstanceInfoService.getPublicInstanceInfo())
        .thenReturn(
            new PublicInstanceInfoResponseDto(
                "https://auth.example", "Acme", "Desc", "https://about.example"));

    EnrollmentInstanceInfoResponse response = service.getSignedInstanceInfo(proofToken);

    assertEquals(42, response.enrollmentId());
    assertEquals("Acme", response.instanceName());
    String payload =
        EnrollmentSignaturePayload.buildInstanceInfoPayload(
            proofToken, 42, "https://auth.example", "Acme", "Desc", "https://about.example");
    assertTrue(
        signatureService.verifyIntegrationSignature(
            payload,
            response.instanceInfoPayloadSignedByIntegration(),
            keyPair.base64UrlPublicKey()));
  }

  @Test
  @DisplayName("rejects unknown proof token with generic failure")
  void getSignedInstanceInfo_UnknownToken() {
    when(enrollmentRepository.findByEnrollmentProofTokenHashAndActive(anyString(), anyBoolean()))
        .thenReturn(Optional.empty());
    assertThrows(
        EnrollmentInstanceInfoFailedException.class,
        () -> service.getSignedInstanceInfo("unknown.token"));
  }

  @Test
  @DisplayName("rejects blank proof token")
  void getSignedInstanceInfo_BlankToken() {
    assertThrows(
        EnrollmentInstanceInfoFailedException.class, () -> service.getSignedInstanceInfo("  "));
  }
}
