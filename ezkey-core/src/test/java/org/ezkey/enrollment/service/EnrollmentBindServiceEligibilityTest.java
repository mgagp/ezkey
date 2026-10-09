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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Optional;
import org.ezkey.enrollment.domain.EnrollmentBindRequest;
import org.ezkey.enrollment.domain.EnrollmentBindResponse;
import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.exception.auth.EnrollmentBindingFailedException;
import org.ezkey.exception.auth.EnrollmentInvitationExpiredException;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminLifecycleStatus;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminType;
import org.ezkey.integration.domain.entity.Integration;
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
import org.springframework.dao.DataAccessResourceFailureException;

@ExtendWith(MockitoExtension.class)
class EnrollmentBindServiceEligibilityTest {

  @Mock private EnrollmentRepository enrollmentRepository;
  @Mock private IntegrationRepository integrationRepository;
  @Mock private EzkeyAdminRepository ezkeyAdminRepository;
  @Mock private EnrollmentTxHelper enrollmentTxHelper;
  @Mock private SignatureService signatureService;
  @Mock private BootstrapCredentialsPostBindCleaner bootstrapCredentialsPostBindCleaner;

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
            signatureService,
            bootstrapCredentialsPostBindCleaner);
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

    EnrollmentBindingFailedException exception =
        assertThrows(
            EnrollmentBindingFailedException.class, () -> enrollmentBindService.bind(request));

    assertEquals(
        "Enrollment binding failed: linked administrator lifecycle status is PENDING_ACTIVATION",
        exception.getMessage());

    verify(ezkeyAdminRepository).findByEnrollmentId(101);
    verify(enrollmentRepository, never()).findAndLockUnreadById(101);
  }

  @Test
  @DisplayName("bind refuses enrollment after expiresAt (bootstrap invitation TTL)")
  void bindRefusesAfterEnrollmentExpiry() {
    EnrollmentBindRequest request = new EnrollmentBindRequest();
    request.setEnrollmentId(303);
    request.setEnrollmentProofToken("bootstrap-expired-proof");

    OffsetDateTime expiresAt = OffsetDateTime.now().minusMinutes(5);
    Enrollment enrollment = new Enrollment();
    enrollment.setEnrollmentId(303);
    enrollment.setStatus(EnrollmentStatus.CREATED);
    enrollment.setIntegrationId(7);
    enrollment.setCreatedAt(OffsetDateTime.now().minusHours(25));
    enrollment.setExpiresAt(expiresAt);

    when(enrollmentRepository.findByEnrollmentIdAndEnrollmentProofTokenHash(
            303, org.ezkey.security.SensitiveDataHasher.sha256Hex("bootstrap-expired-proof")))
        .thenReturn(Optional.of(enrollment));

    EnrollmentInvitationExpiredException exception =
        assertThrows(
            EnrollmentInvitationExpiredException.class, () -> enrollmentBindService.bind(request));

    assertEquals("Enrollment invitation has expired", exception.getMessage());
    verify(enrollmentTxHelper)
        .markExpiredAndEmitAudit(303, 7, expiresAt, "enrollment_expired_bind_rejected");
    verify(enrollmentRepository, never()).findAndLockUnreadById(303);
  }

  @Test
  @DisplayName(
      "bind succeeds after recover→reset refreshed expiresAt (EXPIRED → CREATED + new TTL)")
  void bindSucceedsAfterInvitationTtlRefresh() {
    EnrollmentBindRequest request = new EnrollmentBindRequest();
    request.setEnrollmentId(404);
    request.setEnrollmentProofToken("reissued-proof");

    Enrollment enrollment = new Enrollment();
    enrollment.setEnrollmentId(404);
    enrollment.setEnrollmentName("Global Admin");
    enrollment.setStatus(EnrollmentStatus.CREATED);
    enrollment.setIntegrationId(1);
    enrollment.setCreatedAt(OffsetDateTime.now().minusHours(30));
    enrollment.setExpiresAt(OffsetDateTime.now().plusHours(24));
    enrollment.setEnrollmentProofToken("reissued-proof");
    enrollment.setIntegrationPublicKey("dGVzdC1wdWItYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWE");

    Integration integration = new Integration();
    integration.setId(1);
    integration.setName("System");
    integration.setDescription("System");
    integration.setIsSystemIntegration(true);

    when(enrollmentRepository.findByEnrollmentIdAndEnrollmentProofTokenHash(
            404, org.ezkey.security.SensitiveDataHasher.sha256Hex("reissued-proof")))
        .thenReturn(Optional.of(enrollment));
    when(ezkeyAdminRepository.findByEnrollmentId(404)).thenReturn(Optional.empty());
    when(integrationRepository.findById(1)).thenReturn(Optional.of(integration));
    when(enrollmentRepository.findAndLockUnreadById(404)).thenReturn(Optional.of(enrollment));
    when(enrollmentRepository.save(enrollment)).thenReturn(enrollment);
    when(signatureService.normalizeIntegrationPublicKeyToBase64(
            enrollment.getIntegrationPublicKey()))
        .thenReturn(enrollment.getIntegrationPublicKey());
    when(ezkeyAdminRepository.findTenantInfoByAdminEnrollmentId(404))
        .thenReturn(Collections.emptyList());
    when(integrationRepository.findTenantInfoByIntegrationId(1))
        .thenReturn(Collections.emptyList());

    EnrollmentBindResponse response = enrollmentBindService.bind(request);

    assertEquals(404, response.getEnrollmentId());
    assertEquals(EnrollmentStatus.BOUND, enrollment.getStatus());
    verify(enrollmentTxHelper, never())
        .markExpiredAndEmitAudit(
            org.mockito.ArgumentMatchers.anyInt(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyString());
    verify(bootstrapCredentialsPostBindCleaner).afterEnrollmentBound(404);
  }

  @Test
  @DisplayName("bind returns invitation expired when expired cleanup cannot be persisted")
  void bindExpiredEnrollment_WhenCleanupFails_StillReturnsInvitationExpired() {
    EnrollmentBindRequest request = new EnrollmentBindRequest();
    request.setEnrollmentId(202);
    request.setEnrollmentProofToken("expired-proof-token");

    OffsetDateTime expiresAt = OffsetDateTime.now().minusMinutes(1);
    Enrollment enrollment = new Enrollment();
    enrollment.setEnrollmentId(202);
    enrollment.setStatus(EnrollmentStatus.CREATED);
    enrollment.setIntegrationId(7);
    enrollment.setCreatedAt(OffsetDateTime.now().minusHours(1));
    enrollment.setExpiresAt(expiresAt);

    when(enrollmentRepository.findByEnrollmentIdAndEnrollmentProofTokenHash(
            202, org.ezkey.security.SensitiveDataHasher.sha256Hex("expired-proof-token")))
        .thenReturn(Optional.of(enrollment));
    doThrow(new DataAccessResourceFailureException("repository unavailable"))
        .when(enrollmentTxHelper)
        .markExpiredAndEmitAudit(202, 7, expiresAt, "enrollment_expired_bind_rejected");

    EnrollmentInvitationExpiredException exception =
        assertThrows(
            EnrollmentInvitationExpiredException.class, () -> enrollmentBindService.bind(request));

    assertEquals("Enrollment invitation has expired", exception.getMessage());
    verify(enrollmentTxHelper)
        .markExpiredAndEmitAudit(202, 7, expiresAt, "enrollment_expired_bind_rejected");
    verify(integrationRepository, never()).findById(7);
    verify(enrollmentRepository, never()).findAndLockUnreadById(202);
  }
}
