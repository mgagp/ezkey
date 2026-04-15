/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: EnrollmentRevocationServiceTest
 * Description: Unit tests for enrollment revocation, deactivation, and reactivation lifecycle.
 */

package org.ezkey.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.ezkey.admin.exception.EnrollmentLinkedAsAdminException;
import org.ezkey.admin.exception.SelfRevocationNotAllowedException;
import org.ezkey.admin.exception.SystemIntegrationRevocationException;
import org.ezkey.admin.security.AdminPrincipal;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.audit.util.ClientContext;
import org.ezkey.authattempt.domain.repository.AuthAttemptRepository;
import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.exception.ResourceNotFoundException;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminType;
import org.ezkey.integration.domain.entity.Integration;
import org.ezkey.integration.domain.repository.AdminTokenRepository;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.ezkey.integration.domain.repository.IntegrationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link EnrollmentRevocationService}.
 *
 * <p>Tests focus on the security guards (self-revocation, system integration protection), state
 * transitions (VERIFIED → REVOKED, active flag), and idempotency guarantees. Audit log calls are
 * verified for presence but not for exact content (content is stable and tested via integration
 * tests).
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EnrollmentRevocationService Tests")
class EnrollmentRevocationServiceTest {

  @Mock private EnrollmentRepository enrollmentRepository;

  @Mock private EzkeyAdminRepository adminRepository;

  @Mock private AdminTokenRepository adminTokenRepository;

  @Mock private IntegrationRepository integrationRepository;

  @Mock private AuditLogService auditLogService;

  @Mock private AuthAttemptRepository authAttemptRepository;

  @Mock private EzkeyAdmin ownerAdmin;

  @Mock private Integration integration;

  private EnrollmentRevocationService service;

  private AdminPrincipal globalAdminPrincipal;

  private ClientContext clientContext;

  @BeforeEach
  void setUp() {
    service =
        new EnrollmentRevocationService(
            enrollmentRepository,
            adminRepository,
            adminTokenRepository,
            integrationRepository,
            auditLogService,
            authAttemptRepository);

    globalAdminPrincipal = new AdminPrincipal(1, AdminType.GLOBAL_ADMIN, null, null);
    clientContext = new ClientContext("127.0.0.1", "test-agent/1.0");
  }

  // -------------------------------------------------------------------------
  // revoke()
  // -------------------------------------------------------------------------

  @Nested
  @DisplayName("revoke()")
  class RevokeTests {

    @Test
    @DisplayName("Should permanently set status to REVOKED and active=false")
    void revoke_shouldSetStatusRevokedAndInactive_onHappyPath() {
      Enrollment enrollment = activeVerifiedEnrollment(1);
      when(enrollmentRepository.findById(1)).thenReturn(Optional.of(enrollment));
      when(adminRepository.findByEnrollmentId(1)).thenReturn(Optional.empty());

      service.revoke(
          1, globalAdminPrincipal, "Security incident - revocation required", clientContext, null);

      assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.REVOKED);
      assertThat(enrollment.getActive()).isFalse();
      assertThat(enrollment.getRevokedAt()).isNotNull();
      assertThat(enrollment.getRevokedByAdminId()).isEqualTo(1);
      verify(enrollmentRepository).save(enrollment);
      verify(auditLogService).log(any());
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when enrollment not found")
    void revoke_shouldThrow_whenEnrollmentNotFound() {
      when(enrollmentRepository.findById(99)).thenReturn(Optional.empty());

      assertThatThrownBy(
              () ->
                  service.revoke(
                      99, globalAdminPrincipal, "Valid reason here", clientContext, null))
          .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Should throw SelfRevocationNotAllowedException when admin revokes own enrollment")
    void revoke_shouldThrow_whenAdminRevokesOwnEnrollment() {
      Enrollment enrollment = activeVerifiedEnrollment(1);
      when(enrollmentRepository.findById(1)).thenReturn(Optional.of(enrollment));
      when(adminRepository.findByEnrollmentId(1)).thenReturn(Optional.of(ownerAdmin));
      when(ownerAdmin.getAdminId()).thenReturn(1); // Same as globalAdminPrincipal.adminId()

      assertThatThrownBy(
              () ->
                  service.revoke(1, globalAdminPrincipal, "Valid reason here", clientContext, null))
          .isInstanceOf(SelfRevocationNotAllowedException.class);

      verify(enrollmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should be idempotent — no save or audit when enrollment already REVOKED")
    void revoke_shouldBeIdempotent_whenAlreadyRevoked() {
      Enrollment enrollment = revokedEnrollment(1);
      when(enrollmentRepository.findById(1)).thenReturn(Optional.of(enrollment));
      when(adminRepository.findByEnrollmentId(1)).thenReturn(Optional.empty());

      service.revoke(1, globalAdminPrincipal, "Valid reason here", clientContext, null);

      verify(enrollmentRepository, never()).save(any());
      verify(auditLogService, never()).log(any());
    }
  }

  // -------------------------------------------------------------------------
  // deactivate()
  // -------------------------------------------------------------------------

  @Nested
  @DisplayName("deactivate()")
  class DeactivateTests {

    @Test
    @DisplayName("Should set active=false and record deactivation audit fields")
    void deactivate_shouldSetInactiveAndRecordAuditFields_onHappyPath() {
      Enrollment enrollment = activeVerifiedEnrollment(2);
      when(enrollmentRepository.findById(2)).thenReturn(Optional.of(enrollment));
      when(adminRepository.findByEnrollmentId(2)).thenReturn(Optional.empty());

      service.deactivate(2, globalAdminPrincipal, "Suspicious activity", clientContext, null);

      assertThat(enrollment.getActive()).isFalse();
      assertThat(enrollment.getDeactivatedAt()).isNotNull();
      assertThat(enrollment.getDeactivatedByAdminId()).isEqualTo(1);
      verify(enrollmentRepository).save(enrollment);
      verify(auditLogService).log(any());
    }

    @Test
    @DisplayName("Should throw IllegalStateException when enrollment is permanently REVOKED")
    void deactivate_shouldThrow_whenEnrollmentAlreadyRevoked() {
      Enrollment enrollment = revokedEnrollment(2);
      when(enrollmentRepository.findById(2)).thenReturn(Optional.of(enrollment));
      when(adminRepository.findByEnrollmentId(2)).thenReturn(Optional.empty());

      assertThatThrownBy(
              () -> service.deactivate(2, globalAdminPrincipal, "reason", clientContext, null))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("permanently revoked");

      verify(enrollmentRepository, never()).save(any());
    }

    @Test
    @DisplayName(
        "Should throw SelfRevocationNotAllowedException when admin deactivates own enrollment")
    void deactivate_shouldThrow_whenAdminDeactivatesOwnEnrollment() {
      Enrollment enrollment = activeVerifiedEnrollment(2);
      when(enrollmentRepository.findById(2)).thenReturn(Optional.of(enrollment));
      when(adminRepository.findByEnrollmentId(2)).thenReturn(Optional.of(ownerAdmin));
      when(ownerAdmin.getAdminId()).thenReturn(1); // Same as principal

      assertThatThrownBy(
              () -> service.deactivate(2, globalAdminPrincipal, "reason", clientContext, null))
          .isInstanceOf(SelfRevocationNotAllowedException.class);

      verify(enrollmentRepository, never()).save(any());
    }
  }

  // -------------------------------------------------------------------------
  // reactivate()
  // -------------------------------------------------------------------------

  @Nested
  @DisplayName("reactivate()")
  class ReactivateTests {

    @Test
    @DisplayName("Should set active=true and clear deactivation audit fields")
    void reactivate_shouldSetActiveAndClearAuditFields_onHappyPath() {
      Enrollment enrollment = inactiveVerifiedEnrollment(3);
      when(enrollmentRepository.findById(3)).thenReturn(Optional.of(enrollment));

      service.reactivate(3, globalAdminPrincipal, "Resolved incident", clientContext, null);

      assertThat(enrollment.getActive()).isTrue();
      assertThat(enrollment.getDeactivatedAt()).isNull();
      assertThat(enrollment.getDeactivatedByAdminId()).isNull();
      verify(enrollmentRepository).save(enrollment);
      verify(auditLogService).log(any());
    }

    @Test
    @DisplayName(
        "Should throw IllegalStateException when trying to reactivate a REVOKED enrollment")
    void reactivate_shouldThrow_whenEnrollmentIsPermanentlyRevoked() {
      Enrollment enrollment = revokedEnrollment(3);
      when(enrollmentRepository.findById(3)).thenReturn(Optional.of(enrollment));

      assertThatThrownBy(
              () -> service.reactivate(3, globalAdminPrincipal, "reason", clientContext, null))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("permanently revoked");

      verify(enrollmentRepository, never()).save(any());
    }
  }

  // -------------------------------------------------------------------------
  // revokeAllByIntegration()
  // -------------------------------------------------------------------------

  @Nested
  @DisplayName("revokeAllByIntegration()")
  class RevokeAllByIntegrationTests {

    @Test
    @DisplayName("Should throw SystemIntegrationRevocationException for system integrations")
    void revokeAll_shouldThrow_whenIntegrationIsSystemIntegration() {
      when(integrationRepository.findById(10)).thenReturn(Optional.of(integration));
      when(integration.getIsSystemIntegration()).thenReturn(true);

      assertThatThrownBy(
              () ->
                  service.revokeAllByIntegration(
                      10, globalAdminPrincipal, "Valid long reason here", clientContext, null))
          .isInstanceOf(SystemIntegrationRevocationException.class);

      verify(enrollmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should revoke VERIFIED, CREATED, and BOUND enrollments for a normal integration")
    void revokeAll_shouldRevokeAllRevocableEnrollments_forNonSystemIntegration() {
      Enrollment enrollmentA = activeVerifiedEnrollment(101);
      Enrollment enrollmentB = inactiveVerifiedEnrollment(102);
      Enrollment enrollmentC = createdEnrollment(103);
      Enrollment enrollmentD = boundEnrollment(104);

      when(integrationRepository.findById(10)).thenReturn(Optional.of(integration));
      when(integration.getIsSystemIntegration()).thenReturn(false);
      when(enrollmentRepository.findByIntegrationIdAndStatusIn(
              10,
              List.of(EnrollmentStatus.CREATED, EnrollmentStatus.BOUND, EnrollmentStatus.VERIFIED)))
          .thenReturn(List.of(enrollmentA, enrollmentB, enrollmentC, enrollmentD));
      when(adminRepository.findByEnrollmentId(any())).thenReturn(Optional.empty());

      EnrollmentRevocationService.BulkEnrollmentOperationResult result =
          service.revokeAllByIntegration(
              10, globalAdminPrincipal, "Valid long reason here", clientContext, null);

      assertThat(result.affectedCount()).isEqualTo(4);
      assertThat(result.skippedCount()).isZero();
      assertThat(result.noOp()).isFalse();
      assertThat(enrollmentA.getStatus()).isEqualTo(EnrollmentStatus.REVOKED);
      assertThat(enrollmentA.getActive()).isFalse();
      assertThat(enrollmentB.getStatus()).isEqualTo(EnrollmentStatus.REVOKED);
      assertThat(enrollmentB.getActive()).isFalse();
      assertThat(enrollmentC.getStatus()).isEqualTo(EnrollmentStatus.REVOKED);
      assertThat(enrollmentC.getActive()).isFalse();
      assertThat(enrollmentD.getStatus()).isEqualTo(EnrollmentStatus.REVOKED);
      assertThat(enrollmentD.getActive()).isFalse();
      verify(enrollmentRepository).save(enrollmentA);
      verify(enrollmentRepository).save(enrollmentB);
      verify(enrollmentRepository).save(enrollmentC);
      verify(enrollmentRepository).save(enrollmentD);
      verify(auditLogService).log(any());
    }

    @Test
    @DisplayName("Should be no-op when integration has no revocable enrollments")
    void revokeAll_shouldBeNoOp_whenNoRevocableEnrollments() {
      when(integrationRepository.findById(10)).thenReturn(Optional.of(integration));
      when(integration.getIsSystemIntegration()).thenReturn(false);
      when(enrollmentRepository.findByIntegrationIdAndStatusIn(
              10,
              List.of(EnrollmentStatus.CREATED, EnrollmentStatus.BOUND, EnrollmentStatus.VERIFIED)))
          .thenReturn(List.of());

      EnrollmentRevocationService.BulkEnrollmentOperationResult result =
          service.revokeAllByIntegration(
              10, globalAdminPrincipal, "Valid long reason here", clientContext, null);

      assertThat(result.affectedCount()).isZero();
      assertThat(result.skippedCount()).isZero();
      assertThat(result.noOp()).isTrue();
      verify(enrollmentRepository, never()).save(any());
      verify(auditLogService, never()).log(any());
    }

    @Test
    @DisplayName("Should skip calling admin self enrollment but revoke other revocable rows")
    void revokeAll_shouldSkipSelfEnrollment_andStillRevokeInactiveAndInflightRows() {
      Enrollment selfEnrollment = activeVerifiedEnrollment(101);
      Enrollment inactiveVerified = inactiveVerifiedEnrollment(102);
      Enrollment created = createdEnrollment(103);

      when(integrationRepository.findById(10)).thenReturn(Optional.of(integration));
      when(integration.getIsSystemIntegration()).thenReturn(false);
      when(enrollmentRepository.findByIntegrationIdAndStatusIn(
              10,
              List.of(EnrollmentStatus.CREATED, EnrollmentStatus.BOUND, EnrollmentStatus.VERIFIED)))
          .thenReturn(List.of(selfEnrollment, inactiveVerified, created));
      when(adminRepository.findByEnrollmentId(101)).thenReturn(Optional.of(ownerAdmin));
      when(ownerAdmin.getAdminId()).thenReturn(1);
      when(adminRepository.findByEnrollmentId(102)).thenReturn(Optional.empty());
      when(adminRepository.findByEnrollmentId(103)).thenReturn(Optional.empty());

      EnrollmentRevocationService.BulkEnrollmentOperationResult result =
          service.revokeAllByIntegration(
              10, globalAdminPrincipal, "Valid long reason here", clientContext, null);

      assertThat(result.affectedCount()).isEqualTo(2);
      assertThat(result.skippedCount()).isEqualTo(1);
      assertThat(selfEnrollment.getStatus()).isEqualTo(EnrollmentStatus.VERIFIED);
      assertThat(inactiveVerified.getStatus()).isEqualTo(EnrollmentStatus.REVOKED);
      assertThat(created.getStatus()).isEqualTo(EnrollmentStatus.REVOKED);
      verify(enrollmentRepository, never()).save(selfEnrollment);
      verify(enrollmentRepository).save(inactiveVerified);
      verify(enrollmentRepository).save(created);
    }
  }

  // -------------------------------------------------------------------------
  // deactivateAllByIntegration()
  // -------------------------------------------------------------------------

  @Nested
  @DisplayName("deactivateAllByIntegration()")
  class DeactivateAllByIntegrationTests {

    @Test
    @DisplayName("Should throw SystemIntegrationRevocationException for system integrations")
    void deactivateAll_shouldThrow_whenIntegrationIsSystemIntegration() {
      when(integrationRepository.findById(10)).thenReturn(Optional.of(integration));
      when(integration.getIsSystemIntegration()).thenReturn(true);

      assertThatThrownBy(
              () ->
                  service.deactivateAllByIntegration(
                      10, globalAdminPrincipal, "Optional reason", clientContext, null))
          .isInstanceOf(SystemIntegrationRevocationException.class);

      verify(enrollmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should deactivate all active VERIFIED enrollments for a normal integration")
    void deactivateAll_shouldDeactivateAllActiveEnrollments_forNonSystemIntegration() {
      Enrollment enrollmentA = activeVerifiedEnrollment(101);
      Enrollment enrollmentB = activeVerifiedEnrollment(102);

      when(integrationRepository.findById(10)).thenReturn(Optional.of(integration));
      when(integration.getIsSystemIntegration()).thenReturn(false);
      when(enrollmentRepository.findByIntegrationIdAndStatusAndActive(
              10, EnrollmentStatus.VERIFIED, true))
          .thenReturn(List.of(enrollmentA, enrollmentB));
      when(adminRepository.findByEnrollmentId(any())).thenReturn(Optional.empty());

      EnrollmentRevocationService.BulkEnrollmentOperationResult result =
          service.deactivateAllByIntegration(
              10, globalAdminPrincipal, "Emergency lockdown", clientContext, null);

      assertThat(result.affectedCount()).isEqualTo(2);
      assertThat(result.skippedCount()).isZero();
      assertThat(result.noOp()).isFalse();
      assertThat(enrollmentA.getStatus()).isEqualTo(EnrollmentStatus.VERIFIED);
      assertThat(enrollmentA.getActive()).isFalse();
      assertThat(enrollmentA.getDeactivatedAt()).isNotNull();
      assertThat(enrollmentA.getDeactivatedByAdminId()).isEqualTo(1);
      assertThat(enrollmentB.getStatus()).isEqualTo(EnrollmentStatus.VERIFIED);
      assertThat(enrollmentB.getActive()).isFalse();
      assertThat(enrollmentB.getDeactivatedByAdminId()).isEqualTo(1);
      verify(enrollmentRepository).save(enrollmentA);
      verify(enrollmentRepository).save(enrollmentB);
      verify(auditLogService).log(any());
    }

    @Test
    @DisplayName("Should skip calling admin's own enrollment and deactivate others")
    void deactivateAll_shouldSkipSelfEnrollment_andDeactivateOthers() {
      Enrollment selfEnrollment = activeVerifiedEnrollment(101);
      Enrollment otherEnrollment = activeVerifiedEnrollment(102);

      when(integrationRepository.findById(10)).thenReturn(Optional.of(integration));
      when(integration.getIsSystemIntegration()).thenReturn(false);
      when(enrollmentRepository.findByIntegrationIdAndStatusAndActive(
              10, EnrollmentStatus.VERIFIED, true))
          .thenReturn(List.of(selfEnrollment, otherEnrollment));
      when(adminRepository.findByEnrollmentId(101)).thenReturn(Optional.of(ownerAdmin));
      when(ownerAdmin.getAdminId()).thenReturn(1);
      when(adminRepository.findByEnrollmentId(102)).thenReturn(Optional.empty());

      EnrollmentRevocationService.BulkEnrollmentOperationResult result =
          service.deactivateAllByIntegration(
              10, globalAdminPrincipal, "Lockdown", clientContext, null);

      assertThat(result.affectedCount()).isEqualTo(1);
      assertThat(result.skippedCount()).isEqualTo(1);
      assertThat(result.noOp()).isFalse();
      assertThat(selfEnrollment.getActive()).isTrue();
      verify(enrollmentRepository, never()).save(selfEnrollment);
      assertThat(otherEnrollment.getActive()).isFalse();
      verify(enrollmentRepository).save(otherEnrollment);
      verify(auditLogService).log(any());
    }

    @Test
    @DisplayName("Should be no-op when integration has no active enrollments")
    void deactivateAll_shouldBeNoOp_whenNoActiveEnrollments() {
      when(integrationRepository.findById(10)).thenReturn(Optional.of(integration));
      when(integration.getIsSystemIntegration()).thenReturn(false);
      when(enrollmentRepository.findByIntegrationIdAndStatusAndActive(
              10, EnrollmentStatus.VERIFIED, true))
          .thenReturn(List.of());

      EnrollmentRevocationService.BulkEnrollmentOperationResult result =
          service.deactivateAllByIntegration(10, globalAdminPrincipal, null, clientContext, null);

      assertThat(result.affectedCount()).isZero();
      assertThat(result.skippedCount()).isZero();
      assertThat(result.noOp()).isTrue();
      verify(enrollmentRepository, never()).save(any());
      verify(auditLogService, never()).log(any());
    }
  }

  // -------------------------------------------------------------------------
  // reactivateAllByIntegration()
  // -------------------------------------------------------------------------

  @Nested
  @DisplayName("reactivateAllByIntegration()")
  class ReactivateAllByIntegrationTests {

    @Test
    @DisplayName("Should reactivate all inactive VERIFIED enrollments")
    void reactivateAll_shouldReactivateAllInactiveEnrollments() {
      Enrollment enrollmentA = inactiveVerifiedEnrollment(201);
      Enrollment enrollmentB = inactiveVerifiedEnrollment(202);

      when(integrationRepository.findById(10)).thenReturn(Optional.of(integration));
      when(enrollmentRepository.findByIntegrationIdAndStatusAndActive(
              10, EnrollmentStatus.VERIFIED, false))
          .thenReturn(List.of(enrollmentA, enrollmentB));

      EnrollmentRevocationService.BulkEnrollmentOperationResult result =
          service.reactivateAllByIntegration(
              10, globalAdminPrincipal, "Threat cleared", clientContext, null);

      assertThat(result.affectedCount()).isEqualTo(2);
      assertThat(result.skippedCount()).isZero();
      assertThat(result.noOp()).isFalse();
      assertThat(enrollmentA.getActive()).isTrue();
      assertThat(enrollmentA.getDeactivatedAt()).isNull();
      assertThat(enrollmentA.getDeactivatedByAdminId()).isNull();
      assertThat(enrollmentB.getActive()).isTrue();
      assertThat(enrollmentB.getDeactivatedAt()).isNull();
      verify(enrollmentRepository).save(enrollmentA);
      verify(enrollmentRepository).save(enrollmentB);
      verify(auditLogService).log(any());
    }

    @Test
    @DisplayName("Should be no-op when integration has no inactive VERIFIED enrollments")
    void reactivateAll_shouldBeNoOp_whenNoInactiveEnrollments() {
      when(integrationRepository.findById(10)).thenReturn(Optional.of(integration));
      when(enrollmentRepository.findByIntegrationIdAndStatusAndActive(
              10, EnrollmentStatus.VERIFIED, false))
          .thenReturn(List.of());

      EnrollmentRevocationService.BulkEnrollmentOperationResult result =
          service.reactivateAllByIntegration(10, globalAdminPrincipal, null, clientContext, null);

      assertThat(result.affectedCount()).isZero();
      assertThat(result.skippedCount()).isZero();
      assertThat(result.noOp()).isTrue();
      verify(enrollmentRepository, never()).save(any());
      verify(auditLogService, never()).log(any());
    }
  }

  // -------------------------------------------------------------------------
  // assertNotSelfDeletion() / assertNotLinkedAsAdmin() (delete guards)
  // -------------------------------------------------------------------------

  @Nested
  @DisplayName("assertNotSelfDeletion()")
  class AssertNotSelfDeletionTests {

    @Test
    @DisplayName("Should throw when principal is the owner of the enrollment")
    void assertNotSelfDeletion_throws_whenEnrollmentIsCallerMfa() {
      when(adminRepository.findByEnrollmentId(42)).thenReturn(Optional.of(ownerAdmin));
      when(ownerAdmin.getAdminId()).thenReturn(1);

      assertThatThrownBy(() -> service.assertNotSelfDeletion(globalAdminPrincipal, 42))
          .isInstanceOf(SelfRevocationNotAllowedException.class)
          .hasMessageContaining("Cannot delete your own enrollment");
    }

    @Test
    @DisplayName("Should not throw when enrollment is another admin's enrollment")
    void assertNotSelfDeletion_doesNotThrow_whenEnrollmentIsOtherAdminMfa() {
      when(adminRepository.findByEnrollmentId(42)).thenReturn(Optional.of(ownerAdmin));
      when(ownerAdmin.getAdminId()).thenReturn(999); // different from globalAdminPrincipal (1)

      service.assertNotSelfDeletion(globalAdminPrincipal, 42);
    }

    @Test
    @DisplayName("Should not throw when enrollment is not linked to an admin")
    void assertNotSelfDeletion_doesNotThrow_whenEnrollmentNotLinked() {
      when(adminRepository.findByEnrollmentId(42)).thenReturn(Optional.empty());

      service.assertNotSelfDeletion(globalAdminPrincipal, 42);
    }
  }

  @Nested
  @DisplayName("assertNotLinkedAsAdmin()")
  class AssertNotLinkedAsAdminTests {

    @Test
    @DisplayName("Should throw when enrollment is linked to an administrator")
    void assertNotLinkedAsAdmin_throws_whenEnrollmentIsAdminEnrollment() {
      when(adminRepository.findByEnrollmentId(42)).thenReturn(Optional.of(ownerAdmin));

      assertThatThrownBy(() -> service.assertNotLinkedAsAdmin(42))
          .isInstanceOf(EnrollmentLinkedAsAdminException.class)
          .hasMessageContaining("linked to an administrator")
          .hasMessageContaining("recovery flow");
    }

    @Test
    @DisplayName("Should not throw when enrollment is not linked to an administrator")
    void assertNotLinkedAsAdmin_doesNotThrow_whenEnrollmentNotLinked() {
      when(adminRepository.findByEnrollmentId(42)).thenReturn(Optional.empty());

      service.assertNotLinkedAsAdmin(42);
    }
  }

  // -------------------------------------------------------------------------
  // Enrollment factories
  // -------------------------------------------------------------------------

  private Enrollment activeVerifiedEnrollment(Integer id) {
    Enrollment e = new Enrollment();
    e.setEnrollmentId(id);
    e.setStatus(EnrollmentStatus.VERIFIED);
    e.setActive(true);
    e.setEnrollmentName("test-enrollment-" + id);
    e.setIntegrationId(10);
    return e;
  }

  private Enrollment inactiveVerifiedEnrollment(Integer id) {
    Enrollment e = new Enrollment();
    e.setEnrollmentId(id);
    e.setStatus(EnrollmentStatus.VERIFIED);
    e.setActive(false);
    e.setEnrollmentName("test-enrollment-" + id);
    e.setIntegrationId(10);
    return e;
  }

  private Enrollment revokedEnrollment(Integer id) {
    Enrollment e = new Enrollment();
    e.setEnrollmentId(id);
    e.setStatus(EnrollmentStatus.REVOKED);
    e.setActive(false);
    e.setEnrollmentName("test-enrollment-" + id);
    e.setIntegrationId(10);
    return e;
  }

  private Enrollment createdEnrollment(Integer id) {
    Enrollment e = new Enrollment();
    e.setEnrollmentId(id);
    e.setStatus(EnrollmentStatus.CREATED);
    e.setActive(false);
    e.setEnrollmentName("test-enrollment-" + id);
    e.setIntegrationId(10);
    return e;
  }

  private Enrollment boundEnrollment(Integer id) {
    Enrollment e = new Enrollment();
    e.setEnrollmentId(id);
    e.setStatus(EnrollmentStatus.BOUND);
    e.setActive(false);
    e.setEnrollmentName("test-enrollment-" + id);
    e.setIntegrationId(10);
    return e;
  }
}
