/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AdminAuthServiceOnboardingResumeTest
 * Description: Unit tests for onboarding-resume mint and redeem.
 */
package org.ezkey.admin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.ezkey.admin.config.AdminTokenRotationProperties;
import org.ezkey.admin.constants.AdminAuditConstants;
import org.ezkey.admin.exception.AuthenticationException;
import org.ezkey.admin.service.AdminAuthService.TokenIssueResult;
import org.ezkey.authattempt.domain.repository.AuthAttemptRepository;
import org.ezkey.authattempt.service.AuthAttemptService;
import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.enrollment.domain.entity.Enrollment;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminAuthService onboarding-resume")
class AdminAuthServiceOnboardingResumeTest {

  @Mock private EzkeyAdminRepository adminRepository;
  @Mock private AdminTokenRepository tokenRepository;
  @Mock private AuthAttemptService authAttemptService;
  @Mock private AuthAttemptRepository authAttemptRepository;
  @Mock private AdminTokenRotationProperties rotationProperties;
  @Mock private AdminAuthAttemptTxHelper authAttemptTxHelper;

  private AdminAuthService service;

  @BeforeEach
  void setUp() {
    service =
        new AdminAuthService(
            adminRepository,
            tokenRepository,
            rotationProperties,
            authAttemptService,
            authAttemptRepository,
            authAttemptTxHelper);
  }

  @Test
  @DisplayName("issueOnboardingResumeSecret stores hashed ONBOARDING_RESUME with 8h TTL")
  void issueOnboardingResumeSecret_hashesAndSetsPurpose() {
    EzkeyAdmin admin = activeAdminWithCreatedEnrollment();
    when(tokenRepository.deactivateActiveTokensForAdminByPurpose(
            eq(11), eq(AdminTokenPurpose.ONBOARDING_RESUME)))
        .thenReturn(0);
    when(tokenRepository.save(any(AdminToken.class))).thenAnswer(inv -> inv.getArgument(0));

    TokenIssueResult issued = service.issueOnboardingResumeSecret(admin);

    assertTrue(issued.plainToken().startsWith(AdminAuditConstants.ONBOARDING_RESUME_TOKEN_PREFIX));
    ArgumentCaptor<AdminToken> captor = ArgumentCaptor.forClass(AdminToken.class);
    verify(tokenRepository).save(captor.capture());
    AdminToken saved = captor.getValue();
    assertEquals(AdminTokenPurpose.ONBOARDING_RESUME, saved.getTokenPurpose());
    assertEquals(0, saved.getTokenUseCount());
    assertEquals(SensitiveDataHasher.sha256Hex(issued.plainToken()), saved.getBearerTokenHash());
    assertTrue(saved.getExpiresAt().isAfter(OffsetDateTime.now().plusHours(7)));
  }

  @Test
  @DisplayName("redeemOnboardingResume remints 2h BOOTSTRAP and increments use count")
  void redeemOnboardingResume_remintsBootstrap() {
    EzkeyAdmin admin = activeAdminWithCreatedEnrollment();
    String plain =
        AdminAuditConstants.ONBOARDING_RESUME_TOKEN_PREFIX + "abcdef0123456789abcdef0123456789";
    String hash = SensitiveDataHasher.sha256Hex(plain);
    AdminToken resume =
        new AdminToken(
            hash,
            admin,
            AdminType.TENANT_ADMIN.name(),
            OffsetDateTime.now().plusHours(8),
            AdminTokenPurpose.ONBOARDING_RESUME);
    resume.setTokenUseCount(0);
    resume.setActive(true);
    resume.setTenant(admin.getTenant());

    when(tokenRepository.findByBearerTokenHashAndActiveTrueWithRelations(hash))
        .thenReturn(Optional.of(resume));
    when(tokenRepository.save(any(AdminToken.class))).thenAnswer(inv -> inv.getArgument(0));
    when(tokenRepository.deactivateActiveTokensForAdminByPurpose(
            eq(11), eq(AdminTokenPurpose.BOOTSTRAP)))
        .thenReturn(0);

    TokenIssueResult bootstrap = service.redeemOnboardingResume(plain);

    assertTrue(bootstrap.plainToken().startsWith(AdminAuditConstants.BOOTSTRAP_TOKEN_PREFIX));
    assertEquals(AdminTokenPurpose.BOOTSTRAP, bootstrap.token().getTokenPurpose());
    assertEquals(1, resume.getTokenUseCount());
    assertTrue(bootstrap.token().getExpiresAt().isBefore(OffsetDateTime.now().plusHours(3)));
    assertTrue(bootstrap.token().getExpiresAt().isAfter(OffsetDateTime.now().plusHours(1)));
  }

  @Test
  @DisplayName(
      "redeemOnboardingResume remints BOOTSTRAP when enrollment is BOUND (claim, not verified)")
  void redeemOnboardingResume_allowsBoundEnrollment() {
    EzkeyAdmin admin = activeAdminWithCreatedEnrollment();
    admin.getEnrollment().setStatus(EnrollmentStatus.BOUND);
    String plain =
        AdminAuditConstants.ONBOARDING_RESUME_TOKEN_PREFIX + "boundfeedboundfeedboundfeedboundfe";
    String hash = SensitiveDataHasher.sha256Hex(plain);
    AdminToken resume =
        new AdminToken(
            hash,
            admin,
            AdminType.TENANT_ADMIN.name(),
            OffsetDateTime.now().plusHours(8),
            AdminTokenPurpose.ONBOARDING_RESUME);
    resume.setTokenUseCount(0);
    resume.setActive(true);
    resume.setTenant(admin.getTenant());

    when(tokenRepository.findByBearerTokenHashAndActiveTrueWithRelations(hash))
        .thenReturn(Optional.of(resume));
    when(tokenRepository.save(any(AdminToken.class))).thenAnswer(inv -> inv.getArgument(0));
    when(tokenRepository.deactivateActiveTokensForAdminByPurpose(
            eq(11), eq(AdminTokenPurpose.BOOTSTRAP)))
        .thenReturn(0);

    TokenIssueResult bootstrap = service.redeemOnboardingResume(plain);

    assertTrue(bootstrap.plainToken().startsWith(AdminAuditConstants.BOOTSTRAP_TOKEN_PREFIX));
    assertEquals(AdminTokenPurpose.BOOTSTRAP, bootstrap.token().getTokenPurpose());
    assertEquals(1, resume.getTokenUseCount());
  }

  @Test
  @DisplayName("redeemOnboardingResume rejects verified enrollment with opaque failure")
  void redeemOnboardingResume_rejectsVerifiedEnrollment() {
    assertOpaqueReject(EnrollmentStatus.VERIFIED);
  }

  @Test
  @DisplayName("redeemOnboardingResume rejects EXPIRED enrollment with opaque failure")
  void redeemOnboardingResume_rejectsExpiredEnrollment() {
    assertOpaqueReject(EnrollmentStatus.EXPIRED);
  }

  @Test
  @DisplayName("issueOnboardingResumeSecret rejects GLOBAL_ADMIN")
  void issueOnboardingResumeSecret_rejectsGlobalAdmin() {
    EzkeyAdmin global = new EzkeyAdmin("ops.global", AdminType.GLOBAL_ADMIN);
    global.setAdminId(1);
    assertThrows(IllegalArgumentException.class, () -> service.issueOnboardingResumeSecret(global));
  }

  @Test
  @DisplayName("redeemOnboardingResume rejects non-evaluator admin with opaque failure")
  void redeemOnboardingResume_rejectsNonEvaluatorAdmin() {
    Tenant tenant = new Tenant("acme", "corp");
    tenant.setTenantId(9);
    tenant.setActive(true);
    Enrollment enrollment = new Enrollment();
    enrollment.setEnrollmentId(5);
    enrollment.setStatus(EnrollmentStatus.CREATED);
    enrollment.setActive(true);
    EzkeyAdmin admin = new EzkeyAdmin("tenant.ops", AdminType.TENANT_ADMIN);
    admin.setAdminId(22);
    admin.setActive(true);
    admin.setLifecycleStatus(AdminLifecycleStatus.ACTIVE);
    admin.setTenant(tenant);
    admin.setEnrollment(enrollment);

    String plain =
        AdminAuditConstants.ONBOARDING_RESUME_TOKEN_PREFIX + "nonevaluatornonevaluatornonvalu";
    String hash = SensitiveDataHasher.sha256Hex(plain);
    AdminToken resume =
        new AdminToken(
            hash,
            admin,
            AdminType.TENANT_ADMIN.name(),
            OffsetDateTime.now().plusHours(8),
            AdminTokenPurpose.ONBOARDING_RESUME);
    resume.setTokenUseCount(0);
    when(tokenRepository.findByBearerTokenHashAndActiveTrueWithRelations(hash))
        .thenReturn(Optional.of(resume));

    AuthenticationException ex =
        assertThrows(AuthenticationException.class, () -> service.redeemOnboardingResume(plain));
    assertEquals("Invalid or expired onboarding resume secret", ex.getMessage());
  }

  private void assertOpaqueReject(EnrollmentStatus status) {
    EzkeyAdmin admin = activeAdminWithCreatedEnrollment();
    admin.getEnrollment().setStatus(status);
    final String plain =
        AdminAuditConstants.ONBOARDING_RESUME_TOKEN_PREFIX
            + "deadbeefdeadbeefdeadbeef"
            + status.name().toLowerCase()
            + "0123456789abcdef";
    String hash = SensitiveDataHasher.sha256Hex(plain);
    AdminToken resume =
        new AdminToken(
            hash,
            admin,
            AdminType.TENANT_ADMIN.name(),
            OffsetDateTime.now().plusHours(8),
            AdminTokenPurpose.ONBOARDING_RESUME);
    resume.setTokenUseCount(0);
    when(tokenRepository.findByBearerTokenHashAndActiveTrueWithRelations(hash))
        .thenReturn(Optional.of(resume));

    AuthenticationException ex =
        assertThrows(AuthenticationException.class, () -> service.redeemOnboardingResume(plain));
    assertEquals("Invalid or expired onboarding resume secret", ex.getMessage());
  }

  private static EzkeyAdmin activeAdminWithCreatedEnrollment() {
    Tenant tenant = new Tenant("eval-cafebabe", "desc");
    tenant.setTenantId(5);
    tenant.setActive(true);
    Enrollment enrollment = new Enrollment();
    enrollment.setEnrollmentId(77);
    enrollment.setStatus(EnrollmentStatus.CREATED);
    enrollment.setActive(true);
    EzkeyAdmin admin = new EzkeyAdmin("eval-admin-cafebabe", AdminType.TENANT_ADMIN);
    admin.setAdminId(11);
    admin.setActive(true);
    admin.setLifecycleStatus(AdminLifecycleStatus.ACTIVE);
    admin.setTenant(tenant);
    admin.setEnrollment(enrollment);
    return admin;
  }
}
