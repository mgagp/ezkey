/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: TenantControllerTest
 * Description: Unit tests for TenantController deactivation logic.
 */

package org.ezkey.admin.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.ezkey.admin.exception.SystemTenantDeactivationException;
import org.ezkey.admin.security.AdminPrincipal;
import org.ezkey.admin.service.AdminProvisioningService;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminType;
import org.ezkey.integration.domain.entity.Tenant;
import org.ezkey.integration.domain.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Unit tests for TenantController deactivation logic.
 *
 * <p>This test class validates the tenant deactivation endpoint, particularly focusing on the
 * system tenant protection mechanism. It ensures that:
 *
 * <ul>
 *   <li>Application tenants can be deactivated successfully
 *   <li>System tenants cannot be deactivated (throws SystemTenantDeactivationException)
 *   <li>Non-existent tenants return 404
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TenantController Deactivation Tests")
class TenantControllerTest {

  @Mock private AdminProvisioningService provisioningService;

  @Mock private TenantRepository tenantRepository;

  private TenantController controller;

  private Tenant applicationTenant;

  private Tenant systemTenant;

  @BeforeEach
  void setUp() {
    controller = new TenantController(provisioningService, tenantRepository);

    // Setup application tenant
    applicationTenant = new Tenant();
    applicationTenant.setTenantId(2);
    applicationTenant.setTenantName("Application Tenant");
    applicationTenant.setTenantDescription("Test application tenant");
    applicationTenant.setCreatedAt(OffsetDateTime.now());
    applicationTenant.setActive(true);
    applicationTenant.setIsSystemTenant(false);

    // Setup system tenant
    systemTenant = new Tenant();
    systemTenant.setTenantId(1);
    systemTenant.setTenantName("Ezkey System");
    systemTenant.setTenantDescription("System tenant");
    systemTenant.setCreatedAt(OffsetDateTime.now());
    systemTenant.setActive(true);
    systemTenant.setIsSystemTenant(true);
  }

  @Nested
  @DisplayName("Tenant Deactivation Tests")
  class DeactivationTests {

    @Test
    @DisplayName("Should successfully deactivate application tenant")
    void shouldDeactivateApplicationTenant() {
      // Arrange
      Integer tenantId = applicationTenant.getTenantId();
      when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(applicationTenant));
      when(tenantRepository.save(any(Tenant.class))).thenReturn(applicationTenant);

      AdminPrincipal principal = new AdminPrincipal(1, AdminType.GLOBAL_ADMIN, null, null);
      UsernamePasswordAuthenticationToken auth =
          new UsernamePasswordAuthenticationToken(
              principal, null, java.util.List.of(new SimpleGrantedAuthority("ROLE_GLOBAL_ADMIN")));

      // Act
      ResponseEntity<Void> response = controller.deactivateTenant(tenantId, auth);

      // Assert
      assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
      verify(tenantRepository).save(applicationTenant);
      assertEquals(false, applicationTenant.getActive());
    }

    @Test
    @DisplayName("Should throw exception when deactivating system tenant")
    void shouldThrowExceptionWhenDeactivatingSystemTenant() {
      // Arrange
      Integer tenantId = systemTenant.getTenantId();
      when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(systemTenant));

      AdminPrincipal principal = new AdminPrincipal(1, AdminType.GLOBAL_ADMIN, null, null);
      UsernamePasswordAuthenticationToken auth =
          new UsernamePasswordAuthenticationToken(
              principal, null, java.util.List.of(new SimpleGrantedAuthority("ROLE_GLOBAL_ADMIN")));

      // Act & Assert
      assertThrows(
          SystemTenantDeactivationException.class,
          () -> controller.deactivateTenant(tenantId, auth));

      // Verify tenant was not saved
      verify(tenantRepository, never()).save(any(Tenant.class));
      // Verify system tenant remains active
      assertEquals(true, systemTenant.getActive());
    }

    @Test
    @DisplayName("Should return 404 for non-existent tenant")
    void shouldReturn404ForNonExistentTenant() {
      // Arrange
      Integer nonExistentTenantId = 999;
      when(tenantRepository.findById(nonExistentTenantId)).thenReturn(Optional.empty());

      AdminPrincipal principal = new AdminPrincipal(1, AdminType.GLOBAL_ADMIN, null, null);
      UsernamePasswordAuthenticationToken auth =
          new UsernamePasswordAuthenticationToken(
              principal, null, java.util.List.of(new SimpleGrantedAuthority("ROLE_GLOBAL_ADMIN")));

      // Act
      ResponseEntity<Void> response = controller.deactivateTenant(nonExistentTenantId, auth);

      // Assert
      assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
      verify(tenantRepository, never()).save(any(Tenant.class));
    }
  }
}
