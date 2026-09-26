/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: EnrollmentVerifyServiceEligibilityTest
 * Description: Verifies verify rejection for admin-linked enrollments when the admin is not operational.
 */

package org.ezkey.enrollment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminLifecycleStatus;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminType;
import org.ezkey.integration.domain.repository.AdminTokenRepository;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.ezkey.service.EntityEligibilityService;
import org.ezkey.signature.SignatureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EnrollmentVerifyServiceEligibilityTest {

  @Mock private EnrollmentRepository enrollmentRepository;
  @Mock private EzkeyAdminRepository ezkeyAdminRepository;
  @Mock private AdminTokenRepository adminTokenRepository;
  @Mock private SignatureService signatureService;
  @Mock private EnrollmentTxHelper enrollmentTxHelper;

  private final EntityEligibilityService eligibilityService = new EntityEligibilityService();

  private EnrollmentVerifyService enrollmentVerifyService;

  @BeforeEach
  void setUp() {
    enrollmentVerifyService =
        new EnrollmentVerifyService(
            enrollmentRepository,
            ezkeyAdminRepository,
            adminTokenRepository,
            eligibilityService,
            signatureService,
            enrollmentTxHelper);
  }

  @Test
  @DisplayName("verify rejects an admin-linked enrollment when the admin is pending activation")
  void verifyRejectsPendingActivationAdminEnrollment() {
    EnrollmentVerifyRequest verifyRequest = new EnrollmentVerifyRequest();
    verifyRequest.setEnrollmentId(100);
    verifyRequest.setDevicePublicKey("device-public-key");
    verifyRequest.setEnrollmentProofTokenSigned("signature");
    verifyRequest.setChallengeResponse(123456);

    Enrollment enrollment = new Enrollment();
    enrollment.setEnrollmentId(100);
    enrollment.setStatus(EnrollmentStatus.BOUND);
    enrollment.setEnrollmentProofToken("token");
    enrollment.setEnrollmentChallenge(123456);
    enrollment.setCreatedAt(OffsetDateTime.now());
    enrollment.setExpiresAt(OffsetDateTime.now().plusHours(1));

    EzkeyAdmin admin = new EzkeyAdmin("pending.admin", AdminType.TENANT_ADMIN);
    admin.setAdminId(5);
    admin.setActive(true);
    admin.setLifecycleStatus(AdminLifecycleStatus.PENDING_ACTIVATION);

    when(enrollmentRepository.findById(100)).thenReturn(Optional.of(enrollment));
    when(ezkeyAdminRepository.findByEnrollmentId(100)).thenReturn(Optional.of(admin));

    EnrollmentVerifyFailedException exception =
        assertThrows(
            EnrollmentVerifyFailedException.class,
            () -> enrollmentVerifyService.verify(verifyRequest));

    assertEquals(
        "Enrollment verification failed: linked administrator lifecycle status is"
            + " PENDING_ACTIVATION",
        exception.getMessage());

    verify(ezkeyAdminRepository).findByEnrollmentId(100);
    verify(signatureService, never())
        .validateSignature(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
  }
}
