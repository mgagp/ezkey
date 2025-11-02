/*
 * Ezkey - Open Source MFA/Passkey Alternative
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
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.ezkey.authattempt.domain.repository.AuthAttemptRepository;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.integration.domain.entity.Integration;
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
 * <p>
 * This test class validates the access control logic for different authentication contexts (admin
 * vs API key) and resource ownership checks.
 *
 * <p>
 * <b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p>
 * <b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@ExtendWith(MockitoExtension.class) @DisplayName("AccessControlService Tests")
class AccessControlServiceTest{

  @Mock
  private AuthAttemptRepository authAttemptRepository;

  @Mock
  private EnrollmentRepository enrollmentRepository;

  private AccessControlService accessControlService;

  private Integration testIntegration;

  private Integration otherIntegration;

  private AuthAttempt testAuthAttempt;

  private Enrollment testEnrollment;

  @BeforeEach
  void setUp(){
    accessControlService = new AccessControlService(authAttemptRepository,enrollmentRepository);

    // Setup test integration
    testIntegration = new Integration();
    testIntegration.setId(123);

    // Setup other integration
    otherIntegration = new Integration();
    otherIntegration.setId(456);

    // Setup test enrollment
    testEnrollment = new Enrollment();
    testEnrollment.setEnrollmentId(789);
    testEnrollment.setIntegrationId(123); // Belongs to testIntegration

    // Setup test auth attempt
    testAuthAttempt = new AuthAttempt();
    testAuthAttempt.setAuthAttemptId(101);
    testAuthAttempt.setEnrollmentId(789);
  }

  @Nested @DisplayName("Admin Access Tests")
  class AdminAccessTests{

    @Test @DisplayName("Admin can access any auth attempt")
    void adminCanAccessAnyAuthAttempt(){
      // Arrange
      Authentication adminAuth = createAdminAuthentication();

      // Act & Assert
      assertTrue(accessControlService.canAccessAuthAttempt(adminAuth,101));
    }

    @Test @DisplayName("Admin can access any enrollment")
    void adminCanAccessAnyEnrollment(){
      // Arrange
      Authentication adminAuth = createAdminAuthentication();

      // Act & Assert
      assertTrue(accessControlService.canAccessEnrollment(adminAuth,789));
    }

    @Test @DisplayName("Admin can access any integration")
    void adminCanAccessAnyIntegration(){
      // Arrange
      Authentication adminAuth = createAdminAuthentication();

      // Act & Assert
      assertTrue(accessControlService.canAccessIntegration(adminAuth,123));
      assertTrue(accessControlService.canAccessIntegration(adminAuth,456));
    }
  }

  @Nested @DisplayName("API Key Access Tests")
  class ApiKeyAccessTests{

    @Test @DisplayName("API key can access auth attempt for its integration")
    void apiKeyCanAccessOwnIntegrationAuthAttempt(){
      // Arrange
      Authentication apiKeyAuth = createApiKeyAuthentication(testIntegration);
      when(authAttemptRepository.findById(101)).thenReturn(Optional.of(testAuthAttempt));
      when(enrollmentRepository.findById(789)).thenReturn(Optional.of(testEnrollment));

      // Act & Assert
      assertTrue(accessControlService.canAccessAuthAttempt(apiKeyAuth,101));
    }

    @Test @DisplayName("API key cannot access auth attempt for other integration")
    void apiKeyCannotAccessOtherIntegrationAuthAttempt(){
      // Arrange
      Authentication apiKeyAuth = createApiKeyAuthentication(testIntegration);

      // Create auth attempt for other integration
      AuthAttempt otherAuthAttempt = new AuthAttempt();
      otherAuthAttempt.setAuthAttemptId(102);
      otherAuthAttempt.setEnrollmentId(999);

      Enrollment otherEnrollment = new Enrollment();
      otherEnrollment.setEnrollmentId(999);
      otherEnrollment.setIntegrationId(456); // Belongs to otherIntegration

      when(authAttemptRepository.findById(102)).thenReturn(Optional.of(otherAuthAttempt));
      when(enrollmentRepository.findById(999)).thenReturn(Optional.of(otherEnrollment));

      // Act & Assert
      assertFalse(accessControlService.canAccessAuthAttempt(apiKeyAuth,102));
    }

    @Test @DisplayName("API key cannot access enrollments")
    void apiKeyCannotAccessEnrollments(){
      // Arrange
      Authentication apiKeyAuth = createApiKeyAuthentication(testIntegration);

      // Act & Assert
      assertFalse(accessControlService.canAccessEnrollment(apiKeyAuth,789));
    }

    @Test @DisplayName("API key can access its own integration")
    void apiKeyCanAccessOwnIntegration(){
      // Arrange
      Authentication apiKeyAuth = createApiKeyAuthentication(testIntegration);

      // Act & Assert
      assertTrue(accessControlService.canAccessIntegration(apiKeyAuth,123));
    }

    @Test @DisplayName("API key cannot access other integration")
    void apiKeyCannotAccessOtherIntegration(){
      // Arrange
      Authentication apiKeyAuth = createApiKeyAuthentication(testIntegration);

      // Act & Assert
      assertFalse(accessControlService.canAccessIntegration(apiKeyAuth,456));
    }

    @Test @DisplayName("API key access fails when auth attempt not found")
    void apiKeyAccessFailsWhenAuthAttemptNotFound(){
      // Arrange
      Authentication apiKeyAuth = createApiKeyAuthentication(testIntegration);
      when(authAttemptRepository.findById(999)).thenReturn(Optional.empty());

      // Act & Assert
      assertFalse(accessControlService.canAccessAuthAttempt(apiKeyAuth,999));
    }

    @Test @DisplayName("API key access fails when enrollment not found")
    void apiKeyAccessFailsWhenEnrollmentNotFound(){
      // Arrange
      Authentication apiKeyAuth = createApiKeyAuthentication(testIntegration);
      when(authAttemptRepository.findById(101)).thenReturn(Optional.of(testAuthAttempt));
      when(enrollmentRepository.findById(789)).thenReturn(Optional.empty());

      // Act & Assert
      assertFalse(accessControlService.canAccessAuthAttempt(apiKeyAuth,101));
    }
  }

  @Nested @DisplayName("Invalid Authentication Tests")
  class InvalidAuthenticationTests{

    @Test @DisplayName("Null authentication is denied")
    void nullAuthenticationIsDenied(){
      // Act & Assert
      assertFalse(accessControlService.canAccessAuthAttempt(null,101));
      assertFalse(accessControlService.canAccessEnrollment(null,789));
      assertFalse(accessControlService.canAccessIntegration(null,123));
    }

    @Test @DisplayName("Unauthenticated context is denied")
    void unauthenticatedContextIsDenied(){
      // Arrange
      Authentication unauthenticated = new UsernamePasswordAuthenticationToken("user","pass");

      // Act & Assert
      assertFalse(accessControlService.canAccessAuthAttempt(unauthenticated,101));
      assertFalse(accessControlService.canAccessEnrollment(unauthenticated,789));
      assertFalse(accessControlService.canAccessIntegration(unauthenticated,123));
    }

    @Test @DisplayName("Unknown role is denied")
    void unknownRoleIsDenied(){
      // Arrange
      Authentication unknownRole = new UsernamePasswordAuthenticationToken("user","pass",
          java.util.List.of(new SimpleGrantedAuthority("ROLE_UNKNOWN")));

      // Act & Assert
      assertFalse(accessControlService.canAccessAuthAttempt(unknownRole,101));
      assertFalse(accessControlService.canAccessEnrollment(unknownRole,789));
      assertFalse(accessControlService.canAccessIntegration(unknownRole,123));
    }
  }

  /**
   * Creates an admin authentication context.
   *
   * @return authentication with ROLE_ADMIN
   */
  private Authentication createAdminAuthentication(){
    return new UsernamePasswordAuthenticationToken("admin","password",
        java.util.List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
  }

  /**
   * Creates an API key authentication context.
   *
   * @param integration the integration for the API key
   * @return authentication with ROLE_API_KEY and integration as principal
   */
  private Authentication createApiKeyAuthentication(Integration integration){
    return new UsernamePasswordAuthenticationToken(integration.getId(),null,
        java.util.List.of(new SimpleGrantedAuthority("ROLE_API_KEY")));
  }
}
