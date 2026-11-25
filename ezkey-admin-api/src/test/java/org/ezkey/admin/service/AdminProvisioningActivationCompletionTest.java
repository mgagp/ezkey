/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AdminProvisioningActivationCompletionTest
 * Description: Focused tests for activation-code consumption and first enrollment creation.
 */

package org.ezkey.admin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.ezkey.admin.config.AdminSecurityProperties;
import org.ezkey.admin.constants.AdminAuditConstants;
import org.ezkey.admin.exception.AuthenticationException;
import org.ezkey.admin.util.AdminEnrollmentDisplayNames;
import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.integration.domain.AdminTokenPurpose;
import org.ezkey.integration.domain.IntegrationLifecycleStatus;
import org.ezkey.integration.domain.entity.AdminToken;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminLifecycleStatus;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminType;
import org.ezkey.integration.domain.entity.Integration;
import org.ezkey.integration.domain.entity.Tenant;
import org.ezkey.integration.domain.repository.AdminTokenRepository;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.ezkey.integration.domain.repository.IntegrationRepository;
import org.ezkey.integration.domain.repository.TenantRepository;
import org.ezkey.security.SensitiveDataHasher;
import org.ezkey.signature.Ed25519KeyPair;
import org.ezkey.signature.SignatureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminProvisioningActivationCompletionTest {

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
  @DisplayName("activatePendingAdmin consumes activation code and creates first enrollment")
  void activatePendingAdminConsumesActivationCode() {
    Tenant tenant = new Tenant("Acme", "acme");
    tenant.setTenantId(2);
    tenant.setActive(true);

    Integration integration = new Integration();
    integration.setId(99);
    integration.setLifecycleStatus(IntegrationLifecycleStatus.ACTIVE);

    EzkeyAdmin admin = new EzkeyAdmin("pending.admin", AdminType.TENANT_ADMIN);
    admin.setAdminId(7);
    admin.setTenant(tenant);
    admin.setActive(true);
    admin.setFirstName("Pending");
    admin.setLastName("Admin");
    admin.setLifecycleStatus(AdminLifecycleStatus.PENDING_ACTIVATION);

    String activationCode = AdminAuditConstants.ACTIVATION_TOKEN_PREFIX + "abc123xyz";
    String tokenHash = SensitiveDataHasher.sha256Hex(activationCode);
    AdminToken token =
        new AdminToken(
            tokenHash,
            admin,
            admin.getAdminType().name(),
            OffsetDateTime.now().plusDays(1),
            AdminTokenPurpose.ACTIVATION);
    token.setActive(true);
    token.setTenant(tenant);

    when(tokenRepository.findByBearerTokenHashAndActiveTrueWithRelations(tokenHash))
        .thenReturn(Optional.of(token));
    when(integrationRepository.findByIsSystemIntegrationTrueAndLifecycleStatus(
            IntegrationLifecycleStatus.ACTIVE))
        .thenReturn(Optional.of(integration));
    when(signatureService.generateEd25519KeyPair())
        .thenReturn(new Ed25519KeyPair("privb64", "pubb64"));
    when(signatureService.generateProofToken()).thenReturn("ezkey_proof_demo");
    when(signatureService.generateSecureChallenge(6)).thenReturn(654321);
    when(enrollmentRepository.findByIntegrationIdAndEnrollmentNameAndStatus(
            integration.getId(),
            "Pending Admin",
            org.ezkey.enrollment.domain.EnrollmentStatus.VERIFIED))
        .thenReturn(List.of());
    when(enrollmentRepository.save(any(Enrollment.class)))
        .thenAnswer(invocation -> {
          Enrollment enrollment = invocation.getArgument(0);
          enrollment.setEnrollmentId(123);
          return enrollment;
        });
    when(adminRepository.save(any(EzkeyAdmin.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(tokenRepository.save(any(AdminToken.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    AdminProvisioningService.ProvisioningResult result =
        service.activatePendingAdmin(activationCode);

    assertEquals(AdminLifecycleStatus.ACTIVE, result.admin().getLifecycleStatus());
    assertNotNull(result.enrollment());
    assertEquals(123, result.enrollment().getEnrollmentId());
    assertEquals("ezkey_proof_demo", result.enrollmentProofToken());
    assertEquals(654321, result.enrollmentChallenge());
    assertEquals(null, result.recoveryCodes());
    verify(recoveryService, never()).generateRecoveryCodes();
    verify(tokenRepository).save(any(AdminToken.class));
    verify(tokenRepository).deactivateTokensForAdminByPurpose(7, AdminTokenPurpose.ACTIVATION);
  }

  @Test
  @DisplayName("activatePendingAdmin caps a 100+100 person name at the enrollment column limit")
  void activatePendingAdminTruncatesLongPersonName() {
    String username = "long.name.admin";
    Enrollment saved = activateWithNames(username, "A".repeat(100), "B".repeat(100), List.of());

    assertEquals("A".repeat(64), saved.getEnrollmentName());
    verify(enrollmentRepository)
        .findByIntegrationIdAndEnrollmentNameAndStatus(
            99, "A".repeat(64), EnrollmentStatus.VERIFIED);
  }

  @Test
  @DisplayName("activatePendingAdmin keeps the (username) suffix whole when a long name is taken")
  void activatePendingAdminKeepsUsernameSuffixWhenTruncating() {
    String username = "u".repeat(50);
    Enrollment taken = new Enrollment();
    taken.setEnrollmentName("A".repeat(64));
    taken.setActive(true);

    Enrollment saved =
        activateWithNames(username, "A".repeat(100), "B".repeat(100), List.of(taken));

    String name = saved.getEnrollmentName();
    assertEquals(AdminEnrollmentDisplayNames.MAX_ENROLLMENT_NAME_LENGTH, name.length());
    assertEquals("A".repeat(11) + " (" + username + ")", name);
  }

  private Enrollment activateWithNames(
      String username, String firstName, String lastName, List<Enrollment> personNameHits) {
    Tenant tenant = new Tenant("Acme", "acme");
    tenant.setTenantId(2);
    tenant.setActive(true);

    Integration integration = new Integration();
    integration.setId(99);
    integration.setLifecycleStatus(IntegrationLifecycleStatus.ACTIVE);

    EzkeyAdmin admin = new EzkeyAdmin(username, AdminType.TENANT_ADMIN);
    admin.setAdminId(8);
    admin.setTenant(tenant);
    admin.setActive(true);
    admin.setFirstName(firstName);
    admin.setLastName(lastName);
    admin.setLifecycleStatus(AdminLifecycleStatus.PENDING_ACTIVATION);

    String activationCode = AdminAuditConstants.ACTIVATION_TOKEN_PREFIX + "longname123";
    String tokenHash = SensitiveDataHasher.sha256Hex(activationCode);
    AdminToken token =
        new AdminToken(
            tokenHash,
            admin,
            admin.getAdminType().name(),
            OffsetDateTime.now().plusDays(1),
            AdminTokenPurpose.ACTIVATION);
    token.setActive(true);
    token.setTenant(tenant);

    when(tokenRepository.findByBearerTokenHashAndActiveTrueWithRelations(tokenHash))
        .thenReturn(Optional.of(token));
    when(integrationRepository.findByIsSystemIntegrationTrueAndLifecycleStatus(
            IntegrationLifecycleStatus.ACTIVE))
        .thenReturn(Optional.of(integration));
    when(signatureService.generateEd25519KeyPair())
        .thenReturn(new Ed25519KeyPair("privb64", "pubb64"));
    when(signatureService.generateProofToken()).thenReturn("ezkey_proof_demo");
    when(signatureService.generateSecureChallenge(6)).thenReturn(654321);
    when(enrollmentRepository.findByIntegrationIdAndEnrollmentNameAndStatus(
            anyInt(), any(String.class), eq(EnrollmentStatus.VERIFIED)))
        .thenAnswer(invocation ->
            "A".repeat(64).equals(invocation.getArgument(1)) ? personNameHits : List.of());
    when(enrollmentRepository.save(any(Enrollment.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(adminRepository.save(any(EzkeyAdmin.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(tokenRepository.save(any(AdminToken.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    AdminProvisioningService.ProvisioningResult result =
        service.activatePendingAdmin(activationCode);

    assertNotNull(result.enrollment());
    return result.enrollment();
  }

  @Test
  @DisplayName("activatePendingAdmin rejects expired activation code")
  void activatePendingAdminRejectsExpiredCode() {
    EzkeyAdmin admin = new EzkeyAdmin("pending.admin", AdminType.GLOBAL_ADMIN);
    admin.setAdminId(7);
    admin.setActive(true);
    admin.setLifecycleStatus(AdminLifecycleStatus.PENDING_ACTIVATION);

    String activationCode = AdminAuditConstants.ACTIVATION_TOKEN_PREFIX + "expired123";
    String tokenHash = SensitiveDataHasher.sha256Hex(activationCode);
    AdminToken token =
        new AdminToken(
            tokenHash,
            admin,
            admin.getAdminType().name(),
            OffsetDateTime.now().minusMinutes(1),
            AdminTokenPurpose.ACTIVATION);
    token.setActive(true);

    when(tokenRepository.findByBearerTokenHashAndActiveTrueWithRelations(tokenHash))
        .thenReturn(Optional.of(token));

    AuthenticationException exception =
        assertThrows(
            AuthenticationException.class, () -> service.activatePendingAdmin(activationCode));

    assertEquals("Activation code has expired", exception.getMessage());
  }

  @Test
  @DisplayName("#1122: activatePendingAdmin rejects a matching row whose purpose is not ACTIVATION")
  void activatePendingAdminRejectsNonActivationPurposeRow() {
    EzkeyAdmin admin = new EzkeyAdmin("pending.admin", AdminType.GLOBAL_ADMIN);
    admin.setAdminId(7);
    admin.setActive(true);
    admin.setLifecycleStatus(AdminLifecycleStatus.PENDING_ACTIVATION);

    String activationCode = AdminAuditConstants.ACTIVATION_TOKEN_PREFIX + "session123";
    String tokenHash = SensitiveDataHasher.sha256Hex(activationCode);
    AdminToken token =
        new AdminToken(
            tokenHash,
            admin,
            admin.getAdminType().name(),
            OffsetDateTime.now().plusDays(1),
            AdminTokenPurpose.SESSION);
    token.setActive(true);

    when(tokenRepository.findByBearerTokenHashAndActiveTrueWithRelations(tokenHash))
        .thenReturn(Optional.of(token));

    AuthenticationException exception =
        assertThrows(
            AuthenticationException.class, () -> service.activatePendingAdmin(activationCode));

    assertEquals("Invalid activation code", exception.getMessage());
    assertEquals(AdminLifecycleStatus.PENDING_ACTIVATION, admin.getLifecycleStatus());
    verify(enrollmentRepository, never()).save(any());
    verify(tokenRepository, never()).save(any());
    verify(tokenRepository, never()).deactivateTokensForAdminByPurpose(any(), any());
  }
}
