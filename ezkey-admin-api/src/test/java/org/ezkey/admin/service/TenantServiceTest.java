/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: TenantServiceTest
 * Description: Unit tests for TenantService tenant deactivation and tenant-active enforcement.
 */

package org.ezkey.admin.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.ezkey.admin.exception.TenantInactiveException;
import org.ezkey.admin.exception.TenantNotAllowedException;
import org.ezkey.admin.security.AdminPrincipal;
import org.ezkey.exception.ResourceNotFoundException;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminType;
import org.ezkey.integration.domain.entity.Tenant;
import org.ezkey.integration.domain.repository.AdminTokenRepository;
import org.ezkey.integration.domain.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for TenantService.
 *
 * <p>This test class validates tenant deactivation business rules and tenant-active enforcement
 * logic including system tenant protection, token revocation, idempotency, and the
 * ensureTenantActive guard.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TenantService Tests")
class TenantServiceTest {

  @Mock private TenantRepository tenantRepository;

  @Mock private AdminTokenRepository tokenRepository;

  private TenantService tenantService;

  private AdminPrincipal globalAdminPrincipal;

  @BeforeEach
  void setUp() {
    tenantService = new TenantService(tenantRepository, tokenRepository);
    globalAdminPrincipal = new AdminPrincipal(1, AdminType.GLOBAL_ADMIN, null, null);
  }

  @Nested
  @DisplayName("Tenant Deactivation Tests")
  class TenantDeactivationTests {

    @Test
    @DisplayName("Global admin can deactivate an application tenant")
    void globalAdminCanDeactivateApplicationTenant() {
      // Arrange
      Tenant tenant = new Tenant();
      tenant.setTenantId(2);
      tenant.setTenantName("Acme Corp");
      tenant.setActive(true);
      tenant.setIsSystemTenant(false);
      tenant.setCreatedAt(OffsetDateTime.now());

      when(tenantRepository.findById(2)).thenReturn(Optional.of(tenant));
      when(tokenRepository.deactivateAllTokensForTenant(2)).thenReturn(3);

      // Act
      tenantService.deactivateTenant(2, globalAdminPrincipal);

      // Assert
      assertFalse(tenant.getActive());
      verify(tenantRepository).save(tenant);
      verify(tokenRepository).deactivateAllTokensForTenant(2);
    }

    @Test
    @DisplayName("Cannot deactivate the system tenant")
    void cannotDeactivateSystemTenant() {
      // Arrange
      Tenant systemTenant = new Tenant();
      systemTenant.setTenantId(1);
      systemTenant.setTenantName("Ezkey System");
      systemTenant.setActive(true);
      systemTenant.setIsSystemTenant(true);
      systemTenant.setCreatedAt(OffsetDateTime.now());

      when(tenantRepository.findById(1)).thenReturn(Optional.of(systemTenant));

      // Act & Assert
      TenantNotAllowedException exception =
          assertThrows(
              TenantNotAllowedException.class,
              () -> tenantService.deactivateTenant(1, globalAdminPrincipal));

      assertEquals("Cannot deactivate the system tenant", exception.getMessage());
      verify(tenantRepository, never()).save(any());
      verify(tokenRepository, never()).deactivateAllTokensForTenant(any());
    }

    @Test
    @DisplayName("Deactivation is idempotent - already inactive tenant")
    void deactivationIsIdempotent() {
      // Arrange
      Tenant tenant = new Tenant();
      tenant.setTenantId(2);
      tenant.setTenantName("Acme Corp");
      tenant.setActive(false); // Already inactive
      tenant.setIsSystemTenant(false);
      tenant.setCreatedAt(OffsetDateTime.now());

      when(tenantRepository.findById(2)).thenReturn(Optional.of(tenant));

      // Act
      tenantService.deactivateTenant(2, globalAdminPrincipal);

      // Assert
      verify(tenantRepository, never()).save(any());
      verify(tokenRepository, never()).deactivateAllTokensForTenant(any());
    }

    @Test
    @DisplayName("Deactivation throws ResourceNotFoundException for unknown tenant")
    void deactivationThrowsForUnknownTenant() {
      // Arrange
      when(tenantRepository.findById(999)).thenReturn(Optional.empty());

      // Act & Assert
      assertThrows(
          ResourceNotFoundException.class,
          () -> tenantService.deactivateTenant(999, globalAdminPrincipal));
    }

    @Test
    @DisplayName("Tokens are revoked on deactivation")
    void tokensAreRevokedOnDeactivation() {
      // Arrange
      Tenant tenant = new Tenant();
      tenant.setTenantId(2);
      tenant.setTenantName("Acme Corp");
      tenant.setActive(true);
      tenant.setIsSystemTenant(false);
      tenant.setCreatedAt(OffsetDateTime.now());

      when(tenantRepository.findById(2)).thenReturn(Optional.of(tenant));
      when(tokenRepository.deactivateAllTokensForTenant(2)).thenReturn(5);

      // Act
      tenantService.deactivateTenant(2, globalAdminPrincipal);

      // Assert
      verify(tokenRepository).deactivateAllTokensForTenant(2);
    }
  }

  @Nested
  @DisplayName("ensureTenantActive Tests")
  class EnsureTenantActiveTests {

    @Test
    @DisplayName("Active tenant passes the check")
    void activeTenantPassesCheck() {
      // Arrange
      Tenant tenant = new Tenant();
      tenant.setTenantId(2);
      tenant.setTenantName("Acme Corp");
      tenant.setActive(true);
      tenant.setCreatedAt(OffsetDateTime.now());

      when(tenantRepository.findById(2)).thenReturn(Optional.of(tenant));

      // Act & Assert
      assertDoesNotThrow(() -> tenantService.ensureTenantActive(2));
    }

    @Test
    @DisplayName("Inactive tenant throws TenantInactiveException")
    void inactiveTenantThrowsException() {
      // Arrange
      Tenant tenant = new Tenant();
      tenant.setTenantId(2);
      tenant.setTenantName("Acme Corp");
      tenant.setActive(false);
      tenant.setCreatedAt(OffsetDateTime.now());

      when(tenantRepository.findById(2)).thenReturn(Optional.of(tenant));

      // Act & Assert
      TenantInactiveException exception =
          assertThrows(TenantInactiveException.class, () -> tenantService.ensureTenantActive(2));

      assertEquals("Tenant is inactive. Contact your Ezkey administrator.", exception.getMessage());
    }

    @Test
    @DisplayName("Null tenantId is allowed (global admin operations)")
    void nullTenantIdIsAllowed() {
      // Act & Assert
      assertDoesNotThrow(() -> tenantService.ensureTenantActive(null));
      verify(tenantRepository, never()).findById(any());
    }

    @Test
    @DisplayName("Unknown tenantId throws ResourceNotFoundException")
    void unknownTenantIdThrows() {
      // Arrange
      when(tenantRepository.findById(999)).thenReturn(Optional.empty());

      // Act & Assert
      assertThrows(ResourceNotFoundException.class, () -> tenantService.ensureTenantActive(999));
    }
  }
}
