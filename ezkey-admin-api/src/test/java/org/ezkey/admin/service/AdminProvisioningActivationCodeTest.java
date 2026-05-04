/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AdminProvisioningActivationCodeTest
 * Description: Focused tests for activation-code provisioning mode.
 */

package org.ezkey.admin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.ezkey.admin.config.AdminSecurityProperties;
import org.ezkey.admin.constants.AdminAuditConstants;
import org.ezkey.admin.domain.AdminOnboardingMode;
import org.ezkey.admin.security.AdminPrincipal;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.integration.domain.entity.AdminToken;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminLifecycleStatus;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminType;
import org.ezkey.integration.domain.entity.Tenant;
import org.ezkey.integration.domain.repository.AdminTokenRepository;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.ezkey.integration.domain.repository.IntegrationRepository;
import org.ezkey.integration.domain.repository.TenantRepository;
import org.ezkey.signature.SignatureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class AdminProvisioningActivationCodeTest {

  @Mock private TenantRepository tenantRepository;
  @Mock private EzkeyAdminRepository adminRepository;
  @Mock private IntegrationRepository integrationRepository;
  @Mock private EnrollmentRepository enrollmentRepository;
  @Mock private AdminRecoveryService recoveryService;
  @Mock private SignatureService signatureService;
  @Mock private AdminSecurityProperties securityProperties;
  @Mock private AdminTokenRepository tokenRepository;

  private AdminProvisioningService service;

  @BeforeEach
  void setUp() {
    service =
        new AdminProvisioningService(
            tenantRepository,
            adminRepository,
            integrationRepository,
            enrollmentRepository,
            recoveryService,
            signatureService,
            securityProperties,
            tokenRepository);
  }

  @Test
  @DisplayName("createGlobalAdmin activation mode creates pending admin and activation code")
  void createGlobalAdminActivationModeCreatesPendingAdmin() {
    Tenant systemTenant = new Tenant("System", "system");
    systemTenant.setTenantId(1);
    systemTenant.setIsSystemTenant(true);

    EzkeyAdmin creator = new EzkeyAdmin("root", AdminType.GLOBAL_ADMIN);
    creator.setAdminId(1);

    when(adminRepository.countByAdminTypeAndActiveTrue(AdminType.GLOBAL_ADMIN)).thenReturn(1L);
    when(securityProperties.getMaxGlobalAdmins()).thenReturn(5);
    when(adminRepository.existsByUsername("pending.global")).thenReturn(false);
    when(adminRepository.existsByEmail("pending@example.com")).thenReturn(false);
    when(adminRepository.findById(1)).thenReturn(Optional.of(creator));
    when(tenantRepository.findByIsSystemTenantTrue()).thenReturn(Optional.of(systemTenant));
    when(adminRepository.save(any(EzkeyAdmin.class)))
        .thenAnswer(
            invocation -> {
              EzkeyAdmin admin = invocation.getArgument(0);
              if (admin.getAdminId() == null) {
                admin.setAdminId(42);
              }
              return admin;
            });
    when(tokenRepository.save(any(AdminToken.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    AdminProvisioningService.ProvisioningResult result =
        service.createGlobalAdmin(
            "pending.global",
            "pending@example.com",
            "+15145550101",
            "Pending",
            "Global",
            AdminOnboardingMode.ACTIVATION_CODE,
            new AdminPrincipal(1, AdminType.GLOBAL_ADMIN, null, null));

    assertEquals(AdminLifecycleStatus.PENDING_ACTIVATION, result.admin().getLifecycleStatus());
    assertEquals(AdminOnboardingMode.ACTIVATION_CODE, result.onboardingMode());
    assertNull(result.enrollment());
    assertNull(result.recoveryCodes());
    assertNotNull(result.activationCode());
    assertNotNull(result.activationCodeExpiresAt());
    verify(recoveryService, never()).generateRecoveryCodes();
    verify(enrollmentRepository, never()).save(any());

    ArgumentCaptor<AdminToken> tokenCaptor = ArgumentCaptor.forClass(AdminToken.class);
    verify(tokenRepository).save(tokenCaptor.capture());
    assertEquals(42, tokenCaptor.getValue().getAdmin().getAdminId());
  }

  @Test
  @DisplayName("regenerateRecoveryCodes rejects pending activation administrators")
  void regenerateRecoveryCodesRejectsPendingActivationAdministrators() {
    EzkeyAdmin target = new EzkeyAdmin("pending.admin", AdminType.TENANT_ADMIN);
    target.setAdminId(7);
    target.setActive(true);
    target.setLifecycleStatus(AdminLifecycleStatus.PENDING_ACTIVATION);

    when(adminRepository.findById(7)).thenReturn(Optional.of(target));

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                service.regenerateRecoveryCodes(
                    7, new AdminPrincipal(1, AdminType.GLOBAL_ADMIN, null, null)));

    assertEquals(
        "Cannot manage recovery codes before administrator activation is complete",
        exception.getMessage());
    verify(recoveryService, never()).rotateRecoveryCodes(any());
  }

  @Test
  @DisplayName(
      "reissueActivationCode deactivates prior issuance tokens and returns a fresh activation code")
  void reissueActivationCodeRevokesPriorTokensAndReturnsNewCode() {
    Tenant tenant = new Tenant("Acme", "acme");
    tenant.setTenantId(3);
    tenant.setActive(true);

    EzkeyAdmin target = new EzkeyAdmin("pending.admin", AdminType.TENANT_ADMIN);
    target.setAdminId(7);
    target.setActive(true);
    target.setLifecycleStatus(AdminLifecycleStatus.PENDING_ACTIVATION);
    target.setEnrollment(null);
    target.setTenant(tenant);

    when(adminRepository.findById(7)).thenReturn(Optional.of(target));
    when(tokenRepository.deactivateAllTokensForAdmin(7)).thenReturn(2);
    when(tokenRepository.save(any(AdminToken.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    AdminProvisioningService.ActivationCodeReissueResult result =
        service.reissueActivationCode(7, new AdminPrincipal(1, AdminType.GLOBAL_ADMIN, null, null));

    assertEquals(7, result.admin().getAdminId());
    assertTrue(result.activationCode().startsWith(AdminAuditConstants.ACTIVATION_TOKEN_PREFIX));
    assertNotNull(result.activationCodeExpiresAt());
    assertTrue(result.invalidatedPreviousTokens());
    assertEquals(2, result.deactivatedActiveTokenCount());
    verify(tokenRepository).deactivateAllTokensForAdmin(7);
    verify(tokenRepository).save(any(AdminToken.class));
  }

  @Test
  @DisplayName("reissueActivationCode rejects non-global administrators")
  void reissueActivationCodeRejectsNonGlobalAdministrator() {
    assertThrows(
        AccessDeniedException.class,
        () ->
            service.reissueActivationCode(
                7, new AdminPrincipal(99, AdminType.TENANT_ADMIN, 5, null)));
    verify(adminRepository, never()).findById(7);
  }

  @Test
  @DisplayName("reissueActivationCode rejects administrators not pending activation")
  void reissueActivationCodeRejectsNonPendingAdministrators() {
    EzkeyAdmin target = new EzkeyAdmin("active.admin", AdminType.TENANT_ADMIN);
    target.setAdminId(7);
    target.setActive(true);
    target.setLifecycleStatus(AdminLifecycleStatus.ACTIVE);
    target.setTenant(new Tenant());

    when(adminRepository.findById(7)).thenReturn(Optional.of(target));

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                service.reissueActivationCode(
                    7, new AdminPrincipal(1, AdminType.GLOBAL_ADMIN, null, null)));

    assertEquals(
        "Activation codes can only be re-issued while the administrator is pending activation",
        exception.getMessage());
    verify(tokenRepository, never()).deactivateAllTokensForAdmin(7);
  }

  @Test
  @DisplayName("reissueActivationCode rejects when first enrollment already exists")
  void reissueActivationCodeRejectsWhenEnrollmentAlreadyExists() {
    EzkeyAdmin target = new EzkeyAdmin("pending.with.enrollment", AdminType.TENANT_ADMIN);
    target.setAdminId(7);
    target.setActive(true);
    target.setLifecycleStatus(AdminLifecycleStatus.PENDING_ACTIVATION);
    target.setEnrollment(new org.ezkey.enrollment.domain.entity.Enrollment());

    when(adminRepository.findById(7)).thenReturn(Optional.of(target));

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                service.reissueActivationCode(
                    7, new AdminPrincipal(1, AdminType.GLOBAL_ADMIN, null, null)));

    assertEquals(
        "Cannot re-issue activation codes after the first enrollment already exists",
        exception.getMessage());
    verify(tokenRepository, never()).deactivateAllTokensForAdmin(7);
  }

  @Test
  @DisplayName("reissueActivationCode rejects inactive tenant-backed administrators")
  void reissueActivationCodeRejectsInactiveTenantScope() {
    Tenant tenant = new Tenant("Suspended", "suspended");
    tenant.setTenantId(3);
    tenant.setActive(false);

    EzkeyAdmin target = new EzkeyAdmin("tenant.pending", AdminType.TENANT_ADMIN);
    target.setAdminId(7);
    target.setActive(true);
    target.setLifecycleStatus(AdminLifecycleStatus.PENDING_ACTIVATION);
    target.setTenant(tenant);

    when(adminRepository.findById(7)).thenReturn(Optional.of(target));

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                service.reissueActivationCode(
                    7, new AdminPrincipal(1, AdminType.GLOBAL_ADMIN, null, null)));

    assertEquals(
        "Cannot re-issue activation codes while the administrator's tenant is inactive",
        exception.getMessage());
    verify(tokenRepository, never()).deactivateAllTokensForAdmin(7);
  }
}
