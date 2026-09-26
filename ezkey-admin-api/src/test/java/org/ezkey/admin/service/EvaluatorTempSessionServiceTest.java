/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: EvaluatorTempSessionServiceTest
 * Description: Unit tests for Mode C EVALUATOR_TEMP mint and expiry predicate.
 */

package org.ezkey.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.ezkey.admin.config.EvaluatorSelfRegistrationProperties;
import org.ezkey.admin.exception.AuthenticationException;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.integration.domain.AdminTokenPurpose;
import org.ezkey.integration.domain.entity.AdminToken;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminLifecycleStatus;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminType;
import org.ezkey.integration.domain.entity.Tenant;
import org.ezkey.integration.domain.repository.AdminTokenRepository;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.ezkey.security.SensitiveDataHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for {@link EvaluatorTempSessionService}. */
@ExtendWith(MockitoExtension.class)
class EvaluatorTempSessionServiceTest {

  @Mock private EvaluatorSelfRegistrationProperties properties;
  @Mock private EnrollmentRepository enrollmentRepository;
  @Mock private EzkeyAdminRepository adminRepository;
  @Mock private AdminTokenRepository tokenRepository;
  @Mock private TenantService tenantService;
  @Mock private AuditLogService auditLogService;

  private EvaluatorTempSessionService service;

  @BeforeEach
  void setUp() {
    service =
        new EvaluatorTempSessionService(
            properties,
            enrollmentRepository,
            adminRepository,
            tokenRepository,
            tenantService,
            auditLogService);
  }

  @Test
  @DisplayName("mint fails closed when self-registration flag is OFF")
  void mint_failsClosed_whenFlagOff() {
    when(properties.isEnabled()).thenReturn(false);

    assertThatThrownBy(() -> service.mint(1, "proof"))
        .isInstanceOf(AuthenticationException.class)
        .hasMessageContaining("not available");
    verify(tokenRepository, never()).save(any());
  }

  @Test
  @DisplayName("mint issues EVALUATOR_TEMP with absolute TTL when flag ON")
  void mint_issuesEvaluatorTemp_whenEligible() {
    when(properties.isEnabled()).thenReturn(true);
    when(properties.getTemporarySessionTtlHours()).thenReturn(8);

    Enrollment enrollment = enrollment(10, EnrollmentStatus.CREATED, "proof-token");
    Tenant tenant = tenant(3, true);
    EzkeyAdmin admin = tenantAdmin(20, tenant, enrollment);

    String proofHash = SensitiveDataHasher.sha256Hex("proof-token");
    when(enrollmentRepository.findByEnrollmentIdAndEnrollmentProofTokenHash(10, proofHash))
        .thenReturn(Optional.of(enrollment));
    when(adminRepository.findByEnrollmentId(10)).thenReturn(Optional.of(admin));
    when(tokenRepository.existsByAdminAdminIdAndTokenPurpose(20, AdminTokenPurpose.EVALUATOR_TEMP))
        .thenReturn(false);
    when(tokenRepository.save(any(AdminToken.class))).thenAnswer(inv -> inv.getArgument(0));

    EvaluatorTempSessionService.MintResult result = service.mint(10, "proof-token");

    assertThat(result.plainToken()).startsWith("ezkey_");
    assertThat(result.admin().getAdminId()).isEqualTo(20);
    assertThat(result.expiresAt()).isAfter(OffsetDateTime.now().plusHours(7));

    ArgumentCaptor<AdminToken> captor = ArgumentCaptor.forClass(AdminToken.class);
    verify(tokenRepository).save(captor.capture());
    assertThat(captor.getValue().getTokenPurpose()).isEqualTo(AdminTokenPurpose.EVALUATOR_TEMP);
  }

  @Test
  @DisplayName("mint refuses re-issue when EVALUATOR_TEMP already existed (one-shot)")
  void mint_refusesReissue_whenAlreadyMinted() {
    when(properties.isEnabled()).thenReturn(true);

    Enrollment enrollment = enrollment(10, EnrollmentStatus.CREATED, "proof-token");
    Tenant tenant = tenant(3, true);
    EzkeyAdmin admin = tenantAdmin(20, tenant, enrollment);
    String proofHash = SensitiveDataHasher.sha256Hex("proof-token");
    when(enrollmentRepository.findByEnrollmentIdAndEnrollmentProofTokenHash(10, proofHash))
        .thenReturn(Optional.of(enrollment));
    when(adminRepository.findByEnrollmentId(10)).thenReturn(Optional.of(admin));
    when(tokenRepository.existsByAdminAdminIdAndTokenPurpose(20, AdminTokenPurpose.EVALUATOR_TEMP))
        .thenReturn(true);

    assertThatThrownBy(() -> service.mint(10, "proof-token"))
        .isInstanceOf(AuthenticationException.class)
        .hasMessageContaining("already issued");
  }

  @Test
  @DisplayName("supersedeOnVerifiedBind revokes EVALUATOR_TEMP (never promotes cookie)")
  void supersede_revokesEvaluatorTempTokens() {
    Tenant tenant = tenant(5, true);
    Enrollment enrollment = enrollment(10, EnrollmentStatus.VERIFIED, "proof");
    EzkeyAdmin admin = tenantAdmin(20, tenant, enrollment);
    when(adminRepository.findByEnrollmentId(10)).thenReturn(Optional.of(admin));
    when(tokenRepository.deactivateTokensForAdminByPurpose(20, AdminTokenPurpose.EVALUATOR_TEMP))
        .thenReturn(1);

    int revoked = service.supersedeOnVerifiedBind(10);

    assertThat(revoked).isEqualTo(1);
    verify(tokenRepository).deactivateTokensForAdminByPurpose(20, AdminTokenPurpose.EVALUATOR_TEMP);
  }

  @Test
  @DisplayName(
      "expiry YES path: other VERIFIED admin → ADMIN_DEACTIVATED only, no deactivateTenant")
  void expiry_otherVerifiedAdmin_deactivatesIdentityOnly() {
    Tenant tenant = tenant(5, true);
    Enrollment tempEnrollment = enrollment(1, EnrollmentStatus.CREATED, "a");
    EzkeyAdmin tempAdmin = tenantAdmin(100, tenant, tempEnrollment);

    Enrollment peerEnrollment = enrollment(2, EnrollmentStatus.VERIFIED, "b");
    peerEnrollment.setActive(true);
    EzkeyAdmin peer = tenantAdmin(200, tenant, peerEnrollment);

    AdminToken expired = expiredTempToken(tempAdmin, tenant);

    when(tokenRepository.findActiveExpiredByPurposeWithRelations(
            eq(AdminTokenPurpose.EVALUATOR_TEMP), any(OffsetDateTime.class)))
        .thenReturn(List.of(expired));
    when(adminRepository.findByTenantAndAdminTypeAndActive(5, AdminType.TENANT_ADMIN, true))
        .thenReturn(List.of(tempAdmin, peer));

    int processed = service.processExpiredTempSessions();

    assertThat(processed).isEqualTo(1);
    verify(tenantService, never()).deactivateTenantAsSystem(any());
    assertThat(tempAdmin.getActive()).isFalse();
    assertThat(tempAdmin.getLifecycleStatus()).isEqualTo(AdminLifecycleStatus.DEACTIVATED);

    ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
    verify(auditLogService, times(1)).log(auditCaptor.capture());
    assertThat(auditCaptor.getValue().getEventType()).isEqualTo(EventType.ADMIN_DEACTIVATED);
  }

  @Test
  @DisplayName("expiry: PENDING peer is not bound — soft-deactivates tenant (Bound=MFA VERIFIED)")
  void expiry_pendingPeer_notBound_softDeactivatesTenant() {
    Tenant tenant = tenant(5, true);
    Enrollment tempEnrollment = enrollment(1, EnrollmentStatus.CREATED, "a");
    EzkeyAdmin tempAdmin = tenantAdmin(100, tenant, tempEnrollment);

    Enrollment pendingPeerEnrollment = enrollment(2, EnrollmentStatus.CREATED, "b");
    pendingPeerEnrollment.setActive(true);
    EzkeyAdmin pendingPeer = tenantAdmin(200, tenant, pendingPeerEnrollment);

    AdminToken expired = expiredTempToken(tempAdmin, tenant);

    when(tokenRepository.findActiveExpiredByPurposeWithRelations(
            eq(AdminTokenPurpose.EVALUATOR_TEMP), any(OffsetDateTime.class)))
        .thenReturn(List.of(expired));
    when(adminRepository.findByTenantAndAdminTypeAndActive(5, AdminType.TENANT_ADMIN, true))
        .thenReturn(List.of(tempAdmin, pendingPeer));
    when(tenantService.deactivateTenantAsSystem(5)).thenReturn(true);

    int processed = service.processExpiredTempSessions();

    assertThat(processed).isEqualTo(1);
    verify(tenantService).deactivateTenantAsSystem(5);
  }

  @Test
  @DisplayName("expiry NO path: soft deactivateTenant + ADMIN_DEACTIVATED + TENANT_DEACTIVATED")
  void expiry_noOtherVerifiedAdmin_softDeactivatesTenant() {
    Tenant tenant = tenant(5, true);
    Enrollment tempEnrollment = enrollment(1, EnrollmentStatus.CREATED, "a");
    EzkeyAdmin tempAdmin = tenantAdmin(100, tenant, tempEnrollment);

    AdminToken expired = expiredTempToken(tempAdmin, tenant);

    when(tokenRepository.findActiveExpiredByPurposeWithRelations(
            eq(AdminTokenPurpose.EVALUATOR_TEMP), any(OffsetDateTime.class)))
        .thenReturn(List.of(expired));
    when(adminRepository.findByTenantAndAdminTypeAndActive(5, AdminType.TENANT_ADMIN, true))
        .thenReturn(List.of(tempAdmin));
    when(tenantService.deactivateTenantAsSystem(5)).thenReturn(true);

    int processed = service.processExpiredTempSessions();

    assertThat(processed).isEqualTo(1);
    verify(tenantService).deactivateTenantAsSystem(5);

    ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
    verify(auditLogService, times(2)).log(auditCaptor.capture());
    assertThat(auditCaptor.getAllValues())
        .extracting(AuditLog::getEventType)
        .containsExactlyInAnyOrder(EventType.ADMIN_DEACTIVATED, EventType.TENANT_DEACTIVATED);
  }

  private static AdminToken expiredTempToken(EzkeyAdmin tempAdmin, Tenant tenant) {
    AdminToken expired = new AdminToken();
    expired.setActive(true);
    expired.setTokenPurpose(AdminTokenPurpose.EVALUATOR_TEMP);
    expired.setExpiresAt(OffsetDateTime.now().minusMinutes(1));
    expired.setAdmin(tempAdmin);
    expired.setTenant(tenant);
    return expired;
  }

  private static Enrollment enrollment(int id, EnrollmentStatus status, String proof) {
    Enrollment e = new Enrollment();
    e.setEnrollmentId(id);
    e.setStatus(status);
    e.setActive(true);
    e.setEnrollmentProofToken(proof);
    return e;
  }

  private static Tenant tenant(int id, boolean active) {
    Tenant t = new Tenant();
    t.setTenantId(id);
    t.setTenantName("Lab");
    t.setActive(active);
    t.setIsSystemTenant(false);
    return t;
  }

  private static EzkeyAdmin tenantAdmin(int id, Tenant tenant, Enrollment enrollment) {
    EzkeyAdmin a = new EzkeyAdmin();
    a.setAdminId(id);
    a.setUsername("eval-" + id);
    a.setAdminType(AdminType.TENANT_ADMIN);
    a.setActive(true);
    a.setLifecycleStatus(AdminLifecycleStatus.ACTIVE);
    a.setTenant(tenant);
    a.setEnrollment(enrollment);
    return a;
  }
}
