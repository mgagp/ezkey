/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AdminProvisioningServiceBootstrapOnboardingTest
 * Description: BOOTSTRAP sessions cannot fetch other admins' onboarding/QR credentials.
 */
package org.ezkey.admin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.ezkey.admin.config.AdminSecurityProperties;
import org.ezkey.admin.security.AdminPrincipal;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminType;
import org.ezkey.integration.domain.repository.AdminTokenRepository;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.ezkey.integration.domain.repository.IntegrationRepository;
import org.ezkey.integration.domain.repository.TenantRepository;
import org.ezkey.signature.SignatureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminProvisioningService BOOTSTRAP onboarding self-only")
class AdminProvisioningServiceBootstrapOnboardingTest {

  @Mock private TenantRepository tenantRepository;
  @Mock private EzkeyAdminRepository adminRepository;
  @Mock private IntegrationRepository integrationRepository;
  @Mock private EnrollmentRepository enrollmentRepository;
  @Mock private AdminRecoveryService recoveryService;
  @Mock private SignatureService signatureService;
  @Mock private AdminSecurityProperties securityProperties;
  @Mock private AdminTokenRepository tokenRepository;

  private AdminProvisioningService provisioningService;

  @BeforeEach
  void setUp() {
    provisioningService =
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
  @DisplayName("BOOTSTRAP cannot retrieve another admin's onboarding credentials")
  void bootstrapCannotFetchOtherAdminOnboarding() {
    AdminPrincipal bootstrapPrincipal = new AdminPrincipal(11, AdminType.TENANT_ADMIN, 5, null);
    EzkeyAdmin other = new EzkeyAdmin("other.admin", AdminType.GLOBAL_ADMIN);
    other.setAdminId(99);
    when(adminRepository.findById(99)).thenReturn(Optional.of(other));

    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class,
            () -> provisioningService.getAdminOnboarding(99, bootstrapPrincipal, true));
    assertEquals(
        "Bootstrap sessions may only retrieve the authenticated administrator's onboarding"
            + " credentials",
        ex.getMessage());
  }

  @Test
  @DisplayName("BOOTSTRAP can retrieve own onboarding credentials")
  void bootstrapCanFetchOwnOnboarding() {
    AdminPrincipal bootstrapPrincipal = new AdminPrincipal(11, AdminType.TENANT_ADMIN, 5, null);
    Enrollment enrollment = new Enrollment();
    enrollment.setEnrollmentId(77);
    enrollment.setEnrollmentProofToken("ezkey_proof_own");
    enrollment.setEnrollmentChallenge(123456);
    EzkeyAdmin self = new EzkeyAdmin("eval-admin-cafebabe", AdminType.TENANT_ADMIN);
    self.setAdminId(11);
    self.setEnrollment(enrollment);
    when(adminRepository.findById(11)).thenReturn(Optional.of(self));
    when(enrollmentRepository.findById(77)).thenReturn(Optional.of(enrollment));

    AdminProvisioningService.OnboardingCredentialsResult result =
        provisioningService.getAdminOnboarding(11, bootstrapPrincipal, true);

    assertEquals(77, result.enrollmentId());
    assertEquals("ezkey_proof_own", result.enrollmentProofToken());
  }

  @Test
  @DisplayName("non-BOOTSTRAP Global Admin may still read another admin (unchanged SESSION path)")
  void sessionGlobalAdminCanFetchOtherAdminOnboarding() {
    AdminPrincipal global = new AdminPrincipal(1, AdminType.GLOBAL_ADMIN, null, null);
    Enrollment enrollment = new Enrollment();
    enrollment.setEnrollmentId(77);
    enrollment.setEnrollmentProofToken("ezkey_proof_other");
    enrollment.setEnrollmentChallenge(654321);
    EzkeyAdmin other = new EzkeyAdmin("pending.admin", AdminType.TENANT_ADMIN);
    other.setAdminId(99);
    other.setEnrollment(enrollment);
    when(adminRepository.findById(99)).thenReturn(Optional.of(other));
    when(enrollmentRepository.findById(77)).thenReturn(Optional.of(enrollment));

    AdminProvisioningService.OnboardingCredentialsResult result =
        provisioningService.getAdminOnboarding(99, global, false);

    assertEquals("ezkey_proof_other", result.enrollmentProofToken());
  }
}
