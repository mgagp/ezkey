/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: EvaluatorSelfRegistrationServiceTest
 * Description: Unit tests for anonymous evaluator self-registration service.
 */

package org.ezkey.admin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import org.ezkey.admin.config.EvaluatorSelfRegistrationProperties;
import org.ezkey.admin.domain.AdminOnboardingMode;
import org.ezkey.admin.security.AdminPrincipal;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminType;
import org.ezkey.integration.domain.entity.Tenant;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.ezkey.integration.domain.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("EvaluatorSelfRegistrationService")
class EvaluatorSelfRegistrationServiceTest {

  @Mock private EvaluatorSelfRegistrationRateLimiter rateLimiter;
  @Mock private AdminProvisioningService provisioningService;
  @Mock private EzkeyAdminRepository adminRepository;
  @Mock private TenantRepository tenantRepository;

  private EvaluatorSelfRegistrationProperties properties;
  private EvaluatorSelfRegistrationService service;

  @BeforeEach
  void setUp() {
    properties = new EvaluatorSelfRegistrationProperties();
    properties.setEnabled(true);
    properties.setAdminUiUrl("https://exp1-admin-ui.ezkey.org");
    properties.setGuidedTourUrl("https://ezkey.org/exp1-guided-tour.html");
    service =
        new EvaluatorSelfRegistrationService(
            properties, rateLimiter, provisioningService, adminRepository, tenantRepository);
  }

  @Test
  @DisplayName("validateTenantLabel rejects email-like input")
  void validateTenantLabel_rejectsEmail() {
    assertThrows(
        IllegalArgumentException.class,
        () -> EvaluatorSelfRegistrationService.validateTenantLabel("team@example.com"));
  }

  @Test
  @DisplayName("register provisions empty tenant and activation-code admin")
  void register_happyPath() {
    EzkeyAdmin globalAdmin = new EzkeyAdmin("bootstrap", AdminType.GLOBAL_ADMIN);
    globalAdmin.setAdminId(1);
    when(adminRepository.findByAdminTypeAndActiveTrue(AdminType.GLOBAL_ADMIN))
        .thenReturn(List.of(globalAdmin));
    when(tenantRepository.existsByTenantName(any())).thenReturn(false);

    Tenant tenant = new Tenant("eval-deadbeef", "desc");
    tenant.setTenantId(42);
    when(provisioningService.createTenant(
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(AdminPrincipal.class)))
        .thenReturn(tenant);

    OffsetDateTime expiresAt = OffsetDateTime.now().plusDays(7);
    AdminProvisioningService.ProvisioningResult provisioningResult =
        new AdminProvisioningService.ProvisioningResult(
            new EzkeyAdmin("eval-admin-deadbeef", AdminType.TENANT_ADMIN),
            null,
            null,
            null,
            null,
            AdminOnboardingMode.ACTIVATION_CODE,
            "ABCD-1234",
            expiresAt);
    when(provisioningService.createTenantAdmin(
            any(),
            eq(null),
            eq(null),
            any(),
            any(),
            eq(42),
            eq(AdminOnboardingMode.ACTIVATION_CODE),
            any(AdminPrincipal.class)))
        .thenReturn(provisioningResult);

    var response = service.register("My lab", "203.0.113.5");

    assertEquals("ABCD-1234", response.activationCode());
    assertEquals(expiresAt, response.activationCodeExpiresAt());
    assertEquals("https://exp1-admin-ui.ezkey.org", response.adminUiUrl());
    assertEquals("https://ezkey.org/exp1-guided-tour.html", response.guidedTourUrl());
    org.junit.jupiter.api.Assertions.assertTrue(response.tenantLabel().startsWith("eval-"));
    verify(rateLimiter).verifyAndRecordSuccess("203.0.113.5");
  }
}
