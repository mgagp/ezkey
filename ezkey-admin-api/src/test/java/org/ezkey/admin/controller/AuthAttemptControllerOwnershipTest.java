/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors Licensed under the MIT License. See LICENSE file in the
 * project root for full license information.
 *
 * Class: AuthAttemptControllerOwnershipTest Description: Unit tests for ownership validation in
 * AuthAttemptController.
 */

package org.ezkey.admin.controller;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.ezkey.admin.security.AccessControlService;
import org.ezkey.admin.security.RateLimitService;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.authattempt.domain.AuthAttemptCreateRequest;
import org.ezkey.authattempt.domain.AuthAttemptCreateResponse;
import org.ezkey.authattempt.dto.AuthAttemptCreateRequestDto;
import org.ezkey.authattempt.dto.AuthAttemptCreateResponseDto;
import org.ezkey.authattempt.mapper.AuthAttemptAdminApiMapper;
import org.ezkey.authattempt.service.AuthAttemptService;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Unit tests for ownership validation in AuthAttemptController.
 *
 * <p>This test class focuses specifically on testing the ownership validation logic that ensures
 * API keys can only create auth attempts for enrollments belonging to their associated integration.
 *
 * <p><b>Test Coverage:</b>
 *
 * <ul>
 *   <li><b>Valid Ownership:</b> API key can create auth attempts for own integration
 *   <li><b>Invalid Ownership:</b> API key cannot create auth attempts for other integrations
 *   <li><b>Enrollment Not Found:</b> Proper error handling for non-existent enrollments
 *   <li><b>Admin Bypass:</b> Admin users can create auth attempts for any enrollment
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
@DisplayName("AuthAttemptController Ownership Tests")
class AuthAttemptControllerOwnershipTest {

  @Mock private AuthAttemptService authAttemptService;

  @Mock private AuthAttemptAdminApiMapper authAttemptMapper;

  @Mock private AuditLogService auditLogService;

  @Mock private RateLimitService rateLimitService;

  @Mock private EnrollmentRepository enrollmentRepository;

  @Mock private AccessControlService accessControlService;

  @Mock private org.ezkey.integration.domain.repository.IntegrationRepository integrationRepository;

  private AuthAttemptController controller;

  @BeforeEach
  void setUp() {
    controller =
        new AuthAttemptController(
            authAttemptService,
            authAttemptMapper,
            auditLogService,
            rateLimitService,
            enrollmentRepository,
            accessControlService,
            integrationRepository);
  }

  @Test
  @DisplayName("API key can create auth attempt for enrollment belonging to own integration")
  void apiKeyCanCreateAuthAttemptForOwnIntegration() {
    // Arrange
    Integer apiKeyIntegrationId = 2;
    Integer enrollmentId = 100;

    // Setup motivation context with API key authentication
    setupApiKeyAuthentication(apiKeyIntegrationId);

    // Mock enrollment belonging to the same integration
    Enrollment enrollment = new Enrollment();
    enrollment.setIntegrationId(apiKeyIntegrationId);
    when(enrollmentRepository.findById(enrollmentId)).thenReturn(Optional.of(enrollment));

    // Mock rate limiting to allow the operation
    when(rateLimitService.canCreateAuthAttempt(any())).thenReturn(true);

    // Mock successful auth attempt creation
    AuthAttemptCreateResponse mockResponse = createMockResponse();
    when(authAttemptService.create(any(AuthAttemptCreateRequest.class))).thenReturn(mockResponse);
    when(authAttemptMapper.toAuthAttemptCreateResponseDto(mockResponse))
        .thenReturn(
            new AuthAttemptCreateResponseDto(
                1, null, 120, OffsetDateTime.now().plusSeconds(120), null, null));

    AuthAttemptCreateRequestDto request =
        new AuthAttemptCreateRequestDto(enrollmentId, null, null, false, null, null);

    // Act
    ResponseEntity<?> response = controller.create(request, null);

    // Assert
    assert response.getStatusCode() == HttpStatus.CREATED;
    // Called three times: validateEnrollmentOwnership, resolveTenantIdFromEnrollment,
    // resolveIntegrationIdFromEnrollment
    verify(enrollmentRepository, times(3)).findById(enrollmentId);
  }

  @Test
  @DisplayName("API key cannot create auth attempt for enrollment belonging to other integration")
  void apiKeyCannotCreateAuthAttemptForOtherIntegration() {
    // Arrange
    Integer apiKeyIntegrationId = 2;
    Integer otherIntegrationId = 3;
    Integer enrollmentId = 100;

    // Setup motivation context with API key authentication
    setupApiKeyAuthentication(apiKeyIntegrationId);

    // Mock enrollment belonging to different integration
    Enrollment enrollment = new Enrollment();
    enrollment.setIntegrationId(otherIntegrationId);
    when(enrollmentRepository.findById(enrollmentId)).thenReturn(Optional.of(enrollment));

    // Mock rate limiting to allow the operation
    when(rateLimitService.canCreateAuthAttempt(any())).thenReturn(true);

    AuthAttemptCreateRequestDto request =
        new AuthAttemptCreateRequestDto(enrollmentId, null, null, false, null, null);

    // Act & Assert
    assertThrows(
        AuthorizationDeniedException.class,
        () -> {
          controller.create(request, null);
        });

    verify(enrollmentRepository).findById(enrollmentId);
  }

  @Test
  @DisplayName("API key gets ResourceNotFoundException for non-existent enrollment")
  void apiKeyGetsResourceNotFoundExceptionForNonExistentEnrollment() {
    // Arrange
    Integer apiKeyIntegrationId = 2;
    Integer enrollmentId = 999;

    // Setup motivation context with API key authentication
    setupApiKeyAuthentication(apiKeyIntegrationId);

    // Mock enrollment not found
    when(enrollmentRepository.findById(enrollmentId)).thenReturn(Optional.empty());

    // Mock rate limiting to allow the operation
    when(rateLimitService.canCreateAuthAttempt(any())).thenReturn(true);

    AuthAttemptCreateRequestDto request =
        new AuthAttemptCreateRequestDto(enrollmentId, null, null, false, null, null);

    // Act & Assert
    assertThrows(
        ResourceNotFoundException.class,
        () -> {
          controller.create(request, null);
        });

    verify(enrollmentRepository).findById(enrollmentId);
  }

  @Test
  @DisplayName("Admin can create auth attempt for any enrollment (no ownership check)")
  void adminCanCreateAuthAttemptForAnyEnrollment() {
    // Arrange
    Integer enrollmentId = 100;

    // Setup motivation context with admin authentication
    setupAdminAuthentication();

    // Mock rate limiting (admin bypass) - lenient since admin might not trigger this
    lenient().when(rateLimitService.canCreateAuthAttempt(any())).thenReturn(true);

    // Mock access control to allow admin access to enrollment
    when(accessControlService.canAccessEnrollment(any(), eq(enrollmentId))).thenReturn(true);

    // Mock successful auth attempt creation
    AuthAttemptCreateResponse mockResponse = createMockResponse();
    when(authAttemptService.create(any(AuthAttemptCreateRequest.class))).thenReturn(mockResponse);
    when(authAttemptMapper.toAuthAttemptCreateResponseDto(mockResponse))
        .thenReturn(
            new AuthAttemptCreateResponseDto(
                1, null, 120, OffsetDateTime.now().plusSeconds(120), null, null));

    AuthAttemptCreateRequestDto request =
        new AuthAttemptCreateRequestDto(enrollmentId, null, null, false, null, null);

    // Act
    ResponseEntity<?> response = controller.create(request, null);

    // Assert
    assert response.getStatusCode() == HttpStatus.CREATED;
    // Admin should not trigger enrollment repository lookup for ownership check
    // (since extractIntegrationId() returns null for admin authentication)
  }

  /**
   * Sets up API key authentication context with the specified integration ID.
   *
   * @param integrationId the integration ID for the API key
   */
  private void setupApiKeyAuthentication(Integer integrationId) {
    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(
            integrationId, // Principal: Integration ID
            null, // Credentials
            List.of(new SimpleGrantedAuthority("ROLE_API_KEY")));

    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  /** Sets up admin authentication context. */
  private void setupAdminAuthentication() {
    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(
            "admin", // Principal: Admin username
            null, // Credentials
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  /**
   * Creates a mock AuthAttemptCreateResponse for testing.
   *
   * @return mock response object
   */
  private AuthAttemptCreateResponse createMockResponse() {
    AuthAttemptCreateResponse response = new AuthAttemptCreateResponse();
    response.setAuthAttemptId(1);
    response.setAuthAttemptChallenge(null);
    response.setTimeoutSeconds(120);
    response.setExpiresAt(OffsetDateTime.now().plusSeconds(120));
    return response;
  }
}
