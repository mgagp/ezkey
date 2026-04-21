/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: EnrollmentBindServiceEligibilityTest
 * Description: Verifies bind rejection for admin-linked enrollments when the admin is not operational.
 */

package org.ezkey.enrollment.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.ezkey.enrollment.domain.EnrollmentBindRequest;
import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.exception.auth.EnrollmentBindingFailedException;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminLifecycleStatus;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminType;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.ezkey.integration.domain.repository.IntegrationRepository;
import org.ezkey.service.EntityEligibilityService;
import org.ezkey.signature.SignatureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EnrollmentBindServiceEligibilityTest {

  @Mock private EnrollmentRepository enrollmentRepository;
  @Mock private IntegrationRepository integrationRepository;
  @Mock private EzkeyAdminRepository ezkeyAdminRepository;
  @Mock private EnrollmentTxHelper enrollmentTxHelper;
  @Mock private SignatureService signatureService;

  private final EntityEligibilityService eligibilityService = new EntityEligibilityService();

  private EnrollmentBindService enrollmentBindService;

  @BeforeEach
  void setUp() {
    enrollmentBindService =
        new EnrollmentBindService(
            enrollmentRepository,
            integrationRepository,
            ezkeyAdminRepository,
            eligibilityService,
            enrollmentTxHelper,
            signatureService);
  }

  @Test
  @DisplayName("bind rejects an admin-linked enrollment when the admin is pending activation")
  void bindRejectsPendingActivationAdminEnrollment() {
    EnrollmentBindRequest request = new EnrollmentBindRequest();
    request.setEnrollmentId(101);
    request.setEnrollmentProofToken("proof-token");

    Enrollment enrollment = new Enrollment();
    enrollment.setEnrollmentId(101);
    enrollment.setStatus(EnrollmentStatus.CREATED);
    enrollment.setIntegrationId(7);
    enrollment.setCreatedAt(OffsetDateTime.now());
    enrollment.setExpiresAt(OffsetDateTime.now().plusHours(1));

    EzkeyAdmin admin = new EzkeyAdmin("pending.admin", AdminType.TENANT_ADMIN);
    admin.setAdminId(9);
    admin.setActive(true);
    admin.setLifecycleStatus(AdminLifecycleStatus.PENDING_ACTIVATION);

    when(enrollmentRepository.findByEnrollmentIdAndEnrollmentProofTokenHash(
            101, org.ezkey.security.SensitiveDataHasher.sha256Hex("proof-token")))
        .thenReturn(Optional.of(enrollment));
    when(ezkeyAdminRepository.findByEnrollmentId(101)).thenReturn(Optional.of(admin));

    assertThrows(EnrollmentBindingFailedException.class, () -> enrollmentBindService.bind(request));

    verify(ezkeyAdminRepository).findByEnrollmentId(101);
    verify(enrollmentRepository, never()).findAndLockUnreadById(101);
  }
}
