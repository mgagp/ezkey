/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors Licensed under the MIT License. See LICENSE file in the
 * project root for full license information.
 *
 * Test: AccessControlServiceTest Description: Unit tests for AccessControlService access control
 * logic.
 */

package org.ezkey.admin.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.ezkey.authattempt.domain.repository.AuthAttemptRepository;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.integration.domain.repository.IntegrationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Unit tests for AccessControlService.
 *
 * <p>This test class validates the access control logic for administrator authentication contexts
 * and resource ownership checks.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AccessControlService Tests")
class AccessControlServiceTest {

  @Mock private AuthAttemptRepository authAttemptRepository;

  @Mock private EnrollmentRepository enrollmentRepository;

  @Mock private IntegrationRepository integrationRepository;
  @Mock private org.ezkey.integration.domain.repository.EzkeyAdminRepository adminRepository;

  private AccessControlService accessControlService;

  @BeforeEach
  void setUp() {
    accessControlService =
        new AccessControlService(
            authAttemptRepository, enrollmentRepository, integrationRepository, adminRepository);
  }

  @Nested
  @DisplayName("Admin Access Tests")
  class AdminAccessTests {

    @Test
    @DisplayName("Global admin can access any auth attempt")
    void globalAdminCanAccessAnyAuthAttempt() {
      // Arrange
      Authentication adminAuth = createGlobalAdminAuthentication();

      // Act & Assert
      assertTrue(accessControlService.canAccessAuthAttempt(adminAuth, 101));
    }

    @Test
    @DisplayName("Global admin can access any enrollment")
    void globalAdminCanAccessAnyEnrollment() {
      // Arrange
      Authentication adminAuth = createGlobalAdminAuthentication();

      // Act & Assert
      assertTrue(accessControlService.canAccessEnrollment(adminAuth, 789));
    }

    @Test
    @DisplayName("Global admin can access any integration")
    void globalAdminCanAccessAnyIntegration() {
      // Arrange
      Authentication adminAuth = createGlobalAdminAuthentication();

      // Act & Assert
      assertTrue(accessControlService.canAccessIntegration(adminAuth, 123));
      assertTrue(accessControlService.canAccessIntegration(adminAuth, 456));
    }
  }

  @Nested
  @DisplayName("Invalid Authentication Tests")
  class InvalidAuthenticationTests {

    @Test
    @DisplayName("Null authentication is denied")
    void nullAuthenticationIsDenied() {
      // Act & Assert
      assertFalse(accessControlService.canAccessAuthAttempt(null, 101));
      assertFalse(accessControlService.canAccessEnrollment(null, 789));
      assertFalse(accessControlService.canAccessIntegration(null, 123));
    }

    @Test
    @DisplayName("Unauthenticated context is denied")
    void unauthenticatedContextIsDenied() {
      // Arrange
      Authentication unauthenticated = new UsernamePasswordAuthenticationToken("user", "pass");

      // Act & Assert
      assertFalse(accessControlService.canAccessAuthAttempt(unauthenticated, 101));
      assertFalse(accessControlService.canAccessEnrollment(unauthenticated, 789));
      assertFalse(accessControlService.canAccessIntegration(unauthenticated, 123));
    }

    @Test
    @DisplayName("Unknown role is denied")
    void unknownRoleIsDenied() {
      // Arrange
      Authentication unknownRole =
          new UsernamePasswordAuthenticationToken(
              "user", "pass", java.util.List.of(new SimpleGrantedAuthority("ROLE_UNKNOWN")));

      // Act & Assert
      assertFalse(accessControlService.canAccessAuthAttempt(unknownRole, 101));
      assertFalse(accessControlService.canAccessEnrollment(unknownRole, 789));
      assertFalse(accessControlService.canAccessIntegration(unknownRole, 123));
    }
  }

  /**
   * Creates a global admin authentication context.
   *
   * @return authentication with ROLE_GLOBAL_ADMIN
   */
  private Authentication createGlobalAdminAuthentication() {
    AdminPrincipal principal =
        new AdminPrincipal(
            1, org.ezkey.integration.domain.entity.EzkeyAdmin.AdminType.GLOBAL_ADMIN, null, null);
    return new UsernamePasswordAuthenticationToken(
        principal,
        null,
        java.util.List.of(
            new SimpleGrantedAuthority("ROLE_ADMIN"),
            new SimpleGrantedAuthority("ROLE_GLOBAL_ADMIN")));
  }
}
