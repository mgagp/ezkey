/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: EnrollmentInstanceInfoService
 * Description: Returns integration-signed installation branding for enrolled Auth API clients.
 */

package org.ezkey.enrollment.service;

import org.ezkey.enrollment.domain.EnrollmentInstanceInfoResponse;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.exception.auth.EnrollmentInstanceInfoFailedException;
import org.ezkey.instance.dto.PublicInstanceInfoResponseDto;
import org.ezkey.instance.service.PublicInstanceInfoService;
import org.ezkey.security.SensitiveDataHasher;
import org.ezkey.signature.SignatureService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds integration-signed instance branding for enrolled mobile clients.
 *
 * <p>Lookup is by enrollment proof-token hash only (anti-enumeration). The response is signed with
 * the enrollment's integration Ed25519 private key over the canonical {@code INSTANCE_INFO}
 * payload.
 *
 * @since 2026
 * @see EnrollmentSignaturePayload#buildInstanceInfoPayload
 */
@Service
@ConditionalOnBean(PublicInstanceInfoService.class)
public class EnrollmentInstanceInfoService {

  private static final Logger logger = LoggerFactory.getLogger(EnrollmentInstanceInfoService.class);

  private static final String GENERIC_FAILURE = "Enrollment instance info request failed";

  private final EnrollmentRepository enrollmentRepository;
  private final PublicInstanceInfoService publicInstanceInfoService;
  private final SignatureService signatureService;

  /**
   * Constructs the enrolled instance-info service.
   *
   * @param enrollmentRepository enrollment persistence
   * @param publicInstanceInfoService branding source (same fields as public GET)
   * @param signatureService integration Ed25519 signing
   */
  public EnrollmentInstanceInfoService(
      EnrollmentRepository enrollmentRepository,
      PublicInstanceInfoService publicInstanceInfoService,
      SignatureService signatureService) {
    this.enrollmentRepository = enrollmentRepository;
    this.publicInstanceInfoService = publicInstanceInfoService;
    this.signatureService = signatureService;
  }

  /**
   * Returns signed instance branding for the enrollment identified by {@code enrollmentProofToken}.
   *
   * @param enrollmentProofToken enrollment proof token from the mobile client
   * @return signed branding response
   * @throws EnrollmentInstanceInfoFailedException when the token is invalid or signing is
   *     unavailable (generic client-facing mapping)
   */
  @Transactional(readOnly = true)
  public EnrollmentInstanceInfoResponse getSignedInstanceInfo(String enrollmentProofToken) {
    if (enrollmentProofToken == null || enrollmentProofToken.isBlank()) {
      throw new EnrollmentInstanceInfoFailedException(GENERIC_FAILURE);
    }

    String proofTokenHash = SensitiveDataHasher.sha256Hex(enrollmentProofToken.trim());
    if (proofTokenHash == null) {
      throw new EnrollmentInstanceInfoFailedException(GENERIC_FAILURE);
    }

    Enrollment enrollment =
        enrollmentRepository
            .findByEnrollmentProofTokenHashAndActive(proofTokenHash, true)
            .orElse(null);
    if (enrollment == null) {
      logger.warn("Invalid enrollment proof token for instance-info");
      throw new EnrollmentInstanceInfoFailedException(GENERIC_FAILURE);
    }

    String storedToken = enrollment.getEnrollmentProofToken();
    if (storedToken == null || !storedToken.equals(enrollmentProofToken.trim())) {
      logger.warn(
          "Enrollment proof token mismatch for instance-info, enrollmentId={}",
          enrollment.getEnrollmentId());
      throw new EnrollmentInstanceInfoFailedException(GENERIC_FAILURE);
    }

    String integrationPrivateKey = enrollment.getIntegrationPrivateKey();
    if (integrationPrivateKey == null || integrationPrivateKey.isBlank()) {
      logger.error(
          "Missing integration private key for instance-info, enrollmentId={}",
          enrollment.getEnrollmentId());
      throw new EnrollmentInstanceInfoFailedException(GENERIC_FAILURE);
    }

    PublicInstanceInfoResponseDto branding = publicInstanceInfoService.getPublicInstanceInfo();
    String payload =
        EnrollmentSignaturePayload.buildInstanceInfoPayload(
            storedToken,
            enrollment.getEnrollmentId(),
            branding.authApiPublicBaseUrl(),
            branding.instanceName(),
            branding.instanceDescription(),
            branding.aboutUrl());
    String signature = signatureService.signIntegrationPayload(payload, integrationPrivateKey);

    String integrationPublicKey = enrollment.getIntegrationPublicKey();
    if (integrationPublicKey != null
        && !signatureService.verifyIntegrationSignature(payload, signature, integrationPublicKey)) {
      logger.error(
          "Instance-info integration signature self-verification failed for enrollmentId={}",
          enrollment.getEnrollmentId());
      throw new EnrollmentInstanceInfoFailedException(GENERIC_FAILURE);
    }

    logger.debug("Signed instance-info issued for enrollmentId={}", enrollment.getEnrollmentId());
    return new EnrollmentInstanceInfoResponse(
        enrollment.getEnrollmentId(),
        branding.authApiPublicBaseUrl(),
        branding.instanceName(),
        branding.instanceDescription(),
        branding.aboutUrl(),
        signature);
  }
}
