/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AdminProvisioningServiceTest
 * Description: Unit tests for AdminProvisioningService listAdmins method.
 */

package org.ezkey.admin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import org.ezkey.admin.config.AdminSecurityProperties;
import org.ezkey.admin.dto.request.AdminUpdateRequestDto;
import org.ezkey.admin.exception.AdminLimitException;
import org.ezkey.admin.exception.AdminNotAllowedException;
import org.ezkey.admin.security.AdminPrincipal;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.exception.ResourceNotFoundException;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminLifecycleStatus;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminType;
import org.ezkey.integration.domain.entity.Tenant;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.ezkey.integration.domain.repository.IntegrationRepository;
import org.ezkey.integration.domain.repository.TenantRepository;
import org.ezkey.signature.SignatureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;

/**
 * Unit tests for AdminProvisioningService listAdmins method.
 *
 * <p>This test class validates the tenant-based filtering logic for listing administrators. It
 * ensures that GlobalAdmin sees all administrators while TenantAdmin sees only administrators from
 * their tenant.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminProvisioningService listAdmins Tests")
class AdminProvisioningServiceTest {

  @Mock private TenantRepository tenantRepository;

  @Mock private EzkeyAdminRepository adminRepository;

  @Mock private IntegrationRepository integrationRepository;

  @Mock private EnrollmentRepository enrollmentRepository;

  @Mock private AdminRecoveryService recoveryService;

  @Mock private SignatureService signatureService;

  @Mock private AdminSecurityProperties securityProperties;

  @Mock private org.ezkey.integration.domain.repository.AdminTokenRepository tokenRepository;

  private AdminProvisioningService service;

  private Tenant testTenant;

  private EzkeyAdmin globalAdmin;

  private EzkeyAdmin tenantAdmin1;

  private EzkeyAdmin tenantAdmin2;

  private EzkeyAdmin otherTenantAdmin;

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

    // Setup test tenant
    testTenant = new Tenant();
    testTenant.setTenantId(1);
    testTenant.setTenantName("Test Tenant");

    Tenant otherTenant = new Tenant();
    otherTenant.setTenantId(2);
    otherTenant.setTenantName("Other Tenant");

    // Setup global admin
    globalAdmin = new EzkeyAdmin("globaladmin", AdminType.GLOBAL_ADMIN);
    globalAdmin.setAdminId(1);
    globalAdmin.setEmail("global@example.com");
    globalAdmin.setFirstName("Global");
    globalAdmin.setLastName("Admin");
    globalAdmin.setCreatedAt(OffsetDateTime.now());
    globalAdmin.setActive(true);

    // Setup tenant admins for test tenant
    tenantAdmin1 = new EzkeyAdmin("tenantadmin1", AdminType.TENANT_ADMIN);
    tenantAdmin1.setAdminId(2);
    tenantAdmin1.setEmail("tenant1@example.com");
    tenantAdmin1.setFirstName("Tenant");
    tenantAdmin1.setLastName("Admin1");
    tenantAdmin1.setTenant(testTenant);
    tenantAdmin1.setCreatedAt(OffsetDateTime.now());
    tenantAdmin1.setActive(true);

    tenantAdmin2 = new EzkeyAdmin("tenantadmin2", AdminType.TENANT_ADMIN);
    tenantAdmin2.setAdminId(3);
    tenantAdmin2.setEmail("tenant2@example.com");
    tenantAdmin2.setFirstName("Tenant");
    tenantAdmin2.setLastName("Admin2");
    tenantAdmin2.setTenant(testTenant);
    tenantAdmin2.setCreatedAt(OffsetDateTime.now());
    tenantAdmin2.setActive(true);

    // Setup tenant admin for other tenant
    otherTenantAdmin = new EzkeyAdmin("othertenantadmin", AdminType.TENANT_ADMIN);
    otherTenantAdmin.setAdminId(4);
    otherTenantAdmin.setEmail("other@example.com");
    otherTenantAdmin.setFirstName("Other");
    otherTenantAdmin.setLastName("Admin");
    otherTenantAdmin.setTenant(otherTenant);
    otherTenantAdmin.setCreatedAt(OffsetDateTime.now());
    otherTenantAdmin.setActive(true);
  }

  @Nested
  @DisplayName("GlobalAdmin Listing Tests")
  class GlobalAdminListingTests {

    @Test
    @DisplayName("GlobalAdmin sees all admins when tenantId is null")
    void globalAdminSeesAllAdmins() {
      // Arrange
      Pageable pageable = PageRequest.of(0, 20);
      List<EzkeyAdmin> allAdmins =
          List.of(globalAdmin, tenantAdmin1, tenantAdmin2, otherTenantAdmin);
      Page<EzkeyAdmin> expectedPage = new PageImpl<>(allAdmins, pageable, allAdmins.size());

      when(adminRepository.findAll(pageable)).thenReturn(expectedPage);

      // Act
      Page<EzkeyAdmin> result = service.listAdmins(null, pageable);

      // Assert
      assertNotNull(result);
      assertEquals(4, result.getTotalElements());
      assertEquals(allAdmins, result.getContent());
      verify(adminRepository).findAll(pageable);
      verify(adminRepository).findAll(any(Pageable.class));
    }

    @Test
    @DisplayName("GlobalAdmin pagination works correctly")
    void globalAdminPaginationWorks() {
      // Arrange
      Pageable pageable = PageRequest.of(0, 2);
      List<EzkeyAdmin> pageContent = List.of(globalAdmin, tenantAdmin1);
      Page<EzkeyAdmin> expectedPage = new PageImpl<>(pageContent, pageable, 4);

      when(adminRepository.findAll(pageable)).thenReturn(expectedPage);

      // Act
      Page<EzkeyAdmin> result = service.listAdmins(null, pageable);

      // Assert
      assertNotNull(result);
      assertEquals(4, result.getTotalElements());
      assertEquals(2, result.getContent().size());
      assertEquals(pageContent, result.getContent());
      verify(adminRepository).findAll(pageable);
    }
  }

  @Nested
  @DisplayName("TenantAdmin Listing Tests")
  class TenantAdminListingTests {

    @Test
    @DisplayName("TenantAdmin sees only admins from their tenant")
    void tenantAdminSeesOnlyOwnTenantAdmins() {
      // Arrange
      Integer tenantId = 1;
      Pageable pageable = PageRequest.of(0, 20);
      List<EzkeyAdmin> tenantAdmins = List.of(tenantAdmin1, tenantAdmin2);
      Page<EzkeyAdmin> expectedPage = new PageImpl<>(tenantAdmins, pageable, tenantAdmins.size());

      when(adminRepository.findByTenantTenantId(eq(tenantId), any(Pageable.class)))
          .thenReturn(expectedPage);

      // Act
      Page<EzkeyAdmin> result = service.listAdmins(tenantId, pageable);

      // Assert
      assertNotNull(result);
      assertEquals(2, result.getTotalElements());
      assertEquals(tenantAdmins, result.getContent());
      // Verify that only admins from the specified tenant are returned
      result.getContent().forEach(admin -> assertEquals(tenantId, admin.getTenant().getTenantId()));
      verify(adminRepository).findByTenantTenantId(eq(tenantId), eq(pageable));
    }

    @Test
    @DisplayName("TenantAdmin does not see admins from other tenants")
    void tenantAdminDoesNotSeeOtherTenantAdmins() {
      // Arrange
      Integer tenantId = 1;
      Pageable pageable = PageRequest.of(0, 20);
      List<EzkeyAdmin> tenantAdmins = List.of(tenantAdmin1, tenantAdmin2);
      Page<EzkeyAdmin> expectedPage = new PageImpl<>(tenantAdmins, pageable, tenantAdmins.size());

      when(adminRepository.findByTenantTenantId(eq(tenantId), any(Pageable.class)))
          .thenReturn(expectedPage);

      // Act
      Page<EzkeyAdmin> result = service.listAdmins(tenantId, pageable);

      // Assert
      assertNotNull(result);
      assertEquals(2, result.getTotalElements());
      // Verify otherTenantAdmin is not in the results
      boolean containsOtherTenantAdmin =
          result.getContent().stream()
              .anyMatch(admin -> admin.getAdminId().equals(otherTenantAdmin.getAdminId()));
      assertEquals(false, containsOtherTenantAdmin);
      verify(adminRepository).findByTenantTenantId(eq(tenantId), eq(pageable));
    }

    @Test
    @DisplayName("TenantAdmin pagination works correctly")
    void tenantAdminPaginationWorks() {
      // Arrange
      Integer tenantId = 1;
      Pageable pageable = PageRequest.of(0, 1);
      List<EzkeyAdmin> pageContent = List.of(tenantAdmin1);
      Page<EzkeyAdmin> expectedPage = new PageImpl<>(pageContent, pageable, 2);

      when(adminRepository.findByTenantTenantId(eq(tenantId), any(Pageable.class)))
          .thenReturn(expectedPage);

      // Act
      Page<EzkeyAdmin> result = service.listAdmins(tenantId, pageable);

      // Assert
      assertNotNull(result);
      assertEquals(2, result.getTotalElements());
      assertEquals(1, result.getContent().size());
      assertEquals(pageContent, result.getContent());
      verify(adminRepository).findByTenantTenantId(eq(tenantId), eq(pageable));
    }

    @Test
    @DisplayName("TenantAdmin with empty tenant sees empty results")
    void tenantAdminWithEmptyTenantSeesEmptyResults() {
      // Arrange
      Integer tenantId = 999; // Non-existent tenant
      Pageable pageable = PageRequest.of(0, 20);
      Page<EzkeyAdmin> expectedPage = new PageImpl<>(List.of(), pageable, 0);

      when(adminRepository.findByTenantTenantId(eq(tenantId), any(Pageable.class)))
          .thenReturn(expectedPage);

      // Act
      Page<EzkeyAdmin> result = service.listAdmins(tenantId, pageable);

      // Assert
      assertNotNull(result);
      assertEquals(0, result.getTotalElements());
      assertEquals(true, result.getContent().isEmpty());
      verify(adminRepository).findByTenantTenantId(eq(tenantId), eq(pageable));
    }
  }

  @Nested
  @DisplayName("Admin Deactivation Tests")
  class AdminDeactivationTests {

    @Test
    @DisplayName("Global admin can deactivate another global admin")
    void globalAdminCanDeactivateAnotherGlobalAdmin() {
      // Arrange
      EzkeyAdmin caller = new EzkeyAdmin();
      caller.setAdminId(1);
      caller.setUsername("admin1");
      caller.setAdminType(AdminType.GLOBAL_ADMIN);
      caller.setActive(true);

      EzkeyAdmin target = new EzkeyAdmin();
      target.setAdminId(2);
      target.setUsername("admin2");
      target.setAdminType(AdminType.GLOBAL_ADMIN);
      target.setActive(true);

      org.ezkey.admin.security.AdminPrincipal principal =
          new org.ezkey.admin.security.AdminPrincipal(1, AdminType.GLOBAL_ADMIN, null, null);

      when(adminRepository.findById(2)).thenReturn(java.util.Optional.of(target));
      when(adminRepository.countByAdminTypeAndActiveTrue(AdminType.GLOBAL_ADMIN)).thenReturn(2L);
      when(securityProperties.getMinGlobalAdmins()).thenReturn(1);
      when(tokenRepository.deactivateAllTokensForAdmin(2)).thenReturn(3);

      // Act
      service.deactivateAdmin(2, principal);

      // Assert
      verify(adminRepository).findById(2);
      verify(adminRepository).save(target);
      verify(tokenRepository).deactivateAllTokensForAdmin(2);
      assertFalse(target.getActive());
      assertEquals(AdminLifecycleStatus.DEACTIVATED, target.getLifecycleStatus());
    }

    @Test
    @DisplayName("Global admin cannot deactivate themselves")
    void globalAdminCannotDeactivateThemselves() {
      // Arrange
      EzkeyAdmin admin = new EzkeyAdmin();
      admin.setAdminId(1);
      admin.setUsername("admin1");
      admin.setAdminType(AdminType.GLOBAL_ADMIN);
      admin.setActive(true);

      org.ezkey.admin.security.AdminPrincipal principal =
          new org.ezkey.admin.security.AdminPrincipal(1, AdminType.GLOBAL_ADMIN, null, null);

      when(adminRepository.findById(1)).thenReturn(java.util.Optional.of(admin));

      // Act & Assert
      org.junit.jupiter.api.Assertions.assertThrows(
          AdminNotAllowedException.class, () -> service.deactivateAdmin(1, principal));
      verify(adminRepository).findById(1);
      verify(adminRepository, org.mockito.Mockito.never()).save(any());
      verify(tokenRepository, org.mockito.Mockito.never()).deactivateAllTokensForAdmin(any());
    }

    @Test
    @DisplayName("Global admin can deactivate tenant admin")
    void globalAdminCanDeactivateTenantAdmin() {
      // Arrange
      EzkeyAdmin target = new EzkeyAdmin();
      target.setAdminId(2);
      target.setUsername("tenantadmin1");
      target.setAdminType(AdminType.TENANT_ADMIN);
      target.setTenant(testTenant);
      target.setActive(true);

      org.ezkey.admin.security.AdminPrincipal principal =
          new org.ezkey.admin.security.AdminPrincipal(1, AdminType.GLOBAL_ADMIN, null, null);

      when(adminRepository.findById(2)).thenReturn(java.util.Optional.of(target));
      when(tokenRepository.deactivateAllTokensForAdmin(2)).thenReturn(1);

      // Act
      service.deactivateAdmin(2, principal);

      // Assert
      verify(adminRepository).findById(2);
      verify(adminRepository).save(target);
      verify(tokenRepository).deactivateAllTokensForAdmin(2);
      assertFalse(target.getActive());
    }

    @Test
    @DisplayName("Cannot deactivate when it violates min global admin limit")
    void cannotDeactivateWhenViolatesMinGlobalAdminLimit() {
      // Arrange
      EzkeyAdmin target = new EzkeyAdmin();
      target.setAdminId(2);
      target.setUsername("admin2");
      target.setAdminType(AdminType.GLOBAL_ADMIN);
      target.setActive(true);

      org.ezkey.admin.security.AdminPrincipal principal =
          new org.ezkey.admin.security.AdminPrincipal(1, AdminType.GLOBAL_ADMIN, null, null);

      when(adminRepository.findById(2)).thenReturn(java.util.Optional.of(target));
      when(adminRepository.countByAdminTypeAndActiveTrue(AdminType.GLOBAL_ADMIN)).thenReturn(1L);
      when(securityProperties.getMinGlobalAdmins()).thenReturn(1);

      // Act & Assert
      org.junit.jupiter.api.Assertions.assertThrows(
          AdminLimitException.class, () -> service.deactivateAdmin(2, principal));
      verify(adminRepository).findById(2);
      verify(adminRepository, org.mockito.Mockito.never()).save(any());
      verify(tokenRepository, org.mockito.Mockito.never()).deactivateAllTokensForAdmin(any());
    }

    @Test
    @DisplayName("Tokens are revoked on deactivation")
    void tokensAreRevokedOnDeactivation() {
      // Arrange
      EzkeyAdmin target = new EzkeyAdmin();
      target.setAdminId(2);
      target.setUsername("admin2");
      target.setAdminType(AdminType.GLOBAL_ADMIN);
      target.setActive(true);

      org.ezkey.admin.security.AdminPrincipal principal =
          new org.ezkey.admin.security.AdminPrincipal(1, AdminType.GLOBAL_ADMIN, null, null);

      when(adminRepository.findById(2)).thenReturn(java.util.Optional.of(target));
      when(adminRepository.countByAdminTypeAndActiveTrue(AdminType.GLOBAL_ADMIN)).thenReturn(3L);
      when(securityProperties.getMinGlobalAdmins()).thenReturn(1);
      when(tokenRepository.deactivateAllTokensForAdmin(2)).thenReturn(5);

      // Act
      service.deactivateAdmin(2, principal);

      // Assert
      verify(tokenRepository).deactivateAllTokensForAdmin(2);
    }

    @Test
    @DisplayName("Deactivation is idempotent - already inactive admin")
    void deactivationIsIdempotent() {
      // Arrange
      EzkeyAdmin target = new EzkeyAdmin();
      target.setAdminId(2);
      target.setUsername("admin2");
      target.setAdminType(AdminType.GLOBAL_ADMIN);
      target.setActive(false); // Already inactive

      org.ezkey.admin.security.AdminPrincipal principal =
          new org.ezkey.admin.security.AdminPrincipal(1, AdminType.GLOBAL_ADMIN, null, null);

      when(adminRepository.findById(2)).thenReturn(java.util.Optional.of(target));

      // Act
      service.deactivateAdmin(2, principal);

      // Assert
      verify(adminRepository).findById(2);
      verify(adminRepository, org.mockito.Mockito.never()).save(any());
      verify(tokenRepository, org.mockito.Mockito.never()).deactivateAllTokensForAdmin(any());
    }

    @Test
    @DisplayName("Global admin can deactivate the only tenant admin for a tenant")
    void globalAdminCanDeactivateOnlyTenantAdmin() {
      // Arrange
      EzkeyAdmin target = new EzkeyAdmin();
      target.setAdminId(2);
      target.setUsername("tenantadmin1");
      target.setAdminType(AdminType.TENANT_ADMIN);
      target.setTenant(testTenant);
      target.setActive(true);

      org.ezkey.admin.security.AdminPrincipal principal =
          new org.ezkey.admin.security.AdminPrincipal(1, AdminType.GLOBAL_ADMIN, null, null);

      when(adminRepository.findById(2)).thenReturn(java.util.Optional.of(target));
      when(tokenRepository.deactivateAllTokensForAdmin(2)).thenReturn(2);

      // Act
      service.deactivateAdmin(2, principal);

      // Assert
      verify(adminRepository).findById(2);
      verify(adminRepository).save(target);
      verify(tokenRepository).deactivateAllTokensForAdmin(2);
      assertFalse(target.getActive());
      // Note: No check for min tenant admin limit - this is allowed per requirements
    }
  }

  @Nested
  @DisplayName("Get Admin By ID Tests")
  class GetAdminByIdTests {

    @Test
    @DisplayName("GlobalAdmin can get any admin")
    void globalAdminCanGetAnyAdmin() {
      AdminPrincipal principal = new AdminPrincipal(1, AdminType.GLOBAL_ADMIN, null, null);
      when(adminRepository.findById(2)).thenReturn(java.util.Optional.of(tenantAdmin1));

      EzkeyAdmin result = service.getAdminById(2, principal);

      assertNotNull(result);
      assertSame(tenantAdmin1, result);
      verify(adminRepository).findById(2);
    }

    @Test
    @DisplayName("TenantAdmin can get admin in own tenant")
    void tenantAdminCanGetAdminInOwnTenant() {
      AdminPrincipal principal = new AdminPrincipal(1, AdminType.TENANT_ADMIN, 1, null);
      when(adminRepository.findById(2)).thenReturn(java.util.Optional.of(tenantAdmin1));

      EzkeyAdmin result = service.getAdminById(2, principal);

      assertNotNull(result);
      assertSame(tenantAdmin1, result);
      verify(adminRepository).findById(2);
    }

    @Test
    @DisplayName("TenantAdmin cannot get admin from other tenant")
    void tenantAdminCannotGetAdminFromOtherTenant() {
      AdminPrincipal principal = new AdminPrincipal(1, AdminType.TENANT_ADMIN, 1, null);
      when(adminRepository.findById(4)).thenReturn(java.util.Optional.of(otherTenantAdmin));

      assertThrows(IllegalArgumentException.class, () -> service.getAdminById(4, principal));
      verify(adminRepository).findById(4);
    }

    @Test
    @DisplayName("getAdminById throws when admin not found")
    void getAdminByIdThrowsWhenNotFound() {
      AdminPrincipal principal = new AdminPrincipal(1, AdminType.GLOBAL_ADMIN, null, null);
      when(adminRepository.findById(999)).thenReturn(java.util.Optional.empty());

      assertThrows(ResourceNotFoundException.class, () -> service.getAdminById(999, principal));
      verify(adminRepository).findById(999);
    }
  }

  @Nested
  @DisplayName("Activate Admin Tests")
  class ActivateAdminTests {

    @Test
    @DisplayName("activateAdmin sets active true when admin was inactive")
    void activateAdminSetsActiveWhenInactive() {
      EzkeyAdmin target = new EzkeyAdmin();
      target.setAdminId(2);
      target.setUsername("admin2");
      target.setAdminType(AdminType.GLOBAL_ADMIN);
      target.setActive(false);
      AdminPrincipal principal = new AdminPrincipal(1, AdminType.GLOBAL_ADMIN, null, null);

      when(adminRepository.findById(2)).thenReturn(java.util.Optional.of(target));

      service.activateAdmin(2, principal);

      assertTrue(target.getActive());
      assertEquals(AdminLifecycleStatus.ACTIVE, target.getLifecycleStatus());
      verify(adminRepository).findById(2);
      verify(adminRepository).save(target);
    }

    @Test
    @DisplayName("activateAdmin is idempotent when already active")
    void activateAdminIdempotentWhenAlreadyActive() {
      EzkeyAdmin target = new EzkeyAdmin();
      target.setAdminId(2);
      target.setUsername("admin2");
      target.setAdminType(AdminType.GLOBAL_ADMIN);
      target.setActive(true);
      AdminPrincipal principal = new AdminPrincipal(1, AdminType.GLOBAL_ADMIN, null, null);

      when(adminRepository.findById(2)).thenReturn(java.util.Optional.of(target));

      service.activateAdmin(2, principal);

      assertTrue(target.getActive());
      verify(adminRepository).findById(2);
      verify(adminRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    @DisplayName("activateAdmin throws when admin not found")
    void activateAdminThrowsWhenNotFound() {
      AdminPrincipal principal = new AdminPrincipal(1, AdminType.GLOBAL_ADMIN, null, null);
      when(adminRepository.findById(999)).thenReturn(java.util.Optional.empty());

      assertThrows(ResourceNotFoundException.class, () -> service.activateAdmin(999, principal));
      verify(adminRepository).findById(999);
    }
  }

  @Nested
  @DisplayName("Update Admin Profile Tests")
  class UpdateAdminTests {

    @Test
    @DisplayName("GlobalAdmin can update any admin profile")
    void globalAdminCanUpdateAnyAdmin() {
      EzkeyAdmin target = new EzkeyAdmin("target", AdminType.TENANT_ADMIN);
      target.setAdminId(2);
      target.setTenant(testTenant);
      target.setVersion(0L);
      AdminPrincipal principal = new AdminPrincipal(1, AdminType.GLOBAL_ADMIN, null, null);

      when(adminRepository.findById(2)).thenReturn(java.util.Optional.of(target));
      when(adminRepository.existsByEmailAndAdminIdNot("new@example.com", 2)).thenReturn(false);
      when(adminRepository.save(any(EzkeyAdmin.class))).thenAnswer(inv -> inv.getArgument(0));

      AdminUpdateRequestDto request =
          new AdminUpdateRequestDto(0L, "New", "Name", "new@example.com", null, null);

      EzkeyAdmin result = service.updateAdmin(2, request, principal);

      assertEquals("New", result.getFirstName());
      assertEquals("Name", result.getLastName());
      assertEquals("new@example.com", result.getEmail());
      verify(adminRepository).save(target);
    }

    @Test
    @DisplayName("Stale version throws ObjectOptimisticLockingFailureException")
    void staleVersionThrowsOptimisticLockConflict() {
      EzkeyAdmin target = new EzkeyAdmin("target", AdminType.TENANT_ADMIN);
      target.setAdminId(2);
      target.setTenant(testTenant);
      target.setVersion(5L);
      AdminPrincipal principal = new AdminPrincipal(1, AdminType.GLOBAL_ADMIN, null, null);

      when(adminRepository.findById(2)).thenReturn(java.util.Optional.of(target));

      AdminUpdateRequestDto request = new AdminUpdateRequestDto(3L, "New", null, null, null, null);

      assertThrows(
          ObjectOptimisticLockingFailureException.class,
          () -> service.updateAdmin(2, request, principal));
      verify(adminRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    @DisplayName("updateAdmin throws when admin not found")
    void updateAdminThrowsWhenNotFound() {
      AdminPrincipal principal = new AdminPrincipal(1, AdminType.GLOBAL_ADMIN, null, null);
      when(adminRepository.findById(999)).thenReturn(java.util.Optional.empty());

      AdminUpdateRequestDto request =
          new AdminUpdateRequestDto(null, "New", null, null, null, null);

      assertThrows(
          ResourceNotFoundException.class, () -> service.updateAdmin(999, request, principal));
    }
  }

  @Nested
  @DisplayName("Recovery Code Regeneration Tests")
  class RecoveryCodeRegenerationTests {

    @Test
    @DisplayName("GlobalAdmin can issue initial recovery codes after first login")
    void globalAdminCanIssueInitialRecoveryCodes() {
      AdminPrincipal principal = new AdminPrincipal(1, AdminType.GLOBAL_ADMIN, null, null);
      tenantAdmin1.setRecoveryCodes(null);
      tenantAdmin1.setLastLoginAt(java.time.OffsetDateTime.now());

      AdminRecoveryService.RecoveryCodesResult recoveryCodes =
          new AdminRecoveryService.RecoveryCodesResult(
              List.of("1111-2222-3333-4444-5555-6666-7777-8888"), List.of("$2a$10$newHash"));

      when(adminRepository.findById(2)).thenReturn(java.util.Optional.of(tenantAdmin1));
      when(recoveryService.generateRecoveryCodes()).thenReturn(recoveryCodes);

      AdminProvisioningService.RecoveryCodesRegenerationResult result =
          service.issueInitialRecoveryCodes(2, principal);

      assertEquals(0, result.previousCodesCount());
      assertEquals(tenantAdmin1, result.admin());
      assertEquals(recoveryCodes.getPlainCodes(), result.recoveryCodes());
      verify(adminRepository).findById(2);
      verify(adminRepository).save(tenantAdmin1);
      verify(recoveryService).generateRecoveryCodes();
    }

    @Test
    @DisplayName("GlobalAdmin can regenerate recovery codes for any admin")
    void globalAdminCanRegenerateRecoveryCodes() {
      AdminPrincipal principal = new AdminPrincipal(1, AdminType.GLOBAL_ADMIN, null, null);
      tenantAdmin1.setRecoveryCodes(new String[] {"old-1", "old-2"});

      AdminRecoveryService.RecoveryCodesResult recoveryCodes =
          new AdminRecoveryService.RecoveryCodesResult(
              List.of("1111-2222-3333-4444-5555-6666-7777-8888"), List.of("$2a$10$newHash"));

      when(adminRepository.findById(2)).thenReturn(java.util.Optional.of(tenantAdmin1));
      when(recoveryService.rotateRecoveryCodes(tenantAdmin1)).thenReturn(recoveryCodes);

      AdminProvisioningService.RecoveryCodesRegenerationResult result =
          service.regenerateRecoveryCodes(2, principal);

      assertEquals(2, result.previousCodesCount());
      assertEquals(tenantAdmin1, result.admin());
      assertEquals(recoveryCodes.getPlainCodes(), result.recoveryCodes());
      verify(adminRepository).findById(2);
      verify(recoveryService).rotateRecoveryCodes(tenantAdmin1);
    }

    @Test
    @DisplayName("TenantAdmin cannot regenerate recovery codes for admin in another tenant")
    void tenantAdminCannotRegenerateRecoveryCodesForOtherTenant() {
      AdminPrincipal principal = new AdminPrincipal(2, AdminType.TENANT_ADMIN, 1, null);
      when(adminRepository.findById(4)).thenReturn(java.util.Optional.of(otherTenantAdmin));

      assertThrows(
          AccessDeniedException.class, () -> service.regenerateRecoveryCodes(4, principal));
      verify(adminRepository).findById(4);
    }

    @Test
    @DisplayName("Inactive administrator cannot receive regenerated recovery codes")
    void inactiveAdministratorCannotReceiveRegeneratedRecoveryCodes() {
      AdminPrincipal principal = new AdminPrincipal(1, AdminType.GLOBAL_ADMIN, null, null);
      tenantAdmin1.setActive(false);
      when(adminRepository.findById(2)).thenReturn(java.util.Optional.of(tenantAdmin1));

      assertThrows(
          IllegalStateException.class, () -> service.regenerateRecoveryCodes(2, principal));
      verify(adminRepository).findById(2);
    }

    @Test
    @DisplayName("Regeneration is rejected before the initial recovery-code issuance")
    void regenerationRejectedBeforeInitialRecoveryCodeIssuance() {
      AdminPrincipal principal = new AdminPrincipal(1, AdminType.GLOBAL_ADMIN, null, null);
      tenantAdmin1.setRecoveryCodes(null);
      tenantAdmin1.setLastLoginAt(java.time.OffsetDateTime.now());
      when(adminRepository.findById(2)).thenReturn(java.util.Optional.of(tenantAdmin1));

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class, () -> service.regenerateRecoveryCodes(2, principal));

      assertEquals(
          "No recovery codes exist yet for this administrator; use initial issuance instead",
          exception.getMessage());
      verify(recoveryService, org.mockito.Mockito.never()).rotateRecoveryCodes(any());
    }
  }
}
