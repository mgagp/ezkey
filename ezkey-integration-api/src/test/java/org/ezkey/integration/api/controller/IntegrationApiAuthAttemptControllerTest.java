/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.integration.api.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.audit.support.AuditEntityFkResolver;
import org.ezkey.authattempt.domain.AuthAttemptCreateRequest;
import org.ezkey.authattempt.dto.AuthAttemptCreateRequestDto;
import org.ezkey.authattempt.mapper.AuthAttemptIntegrationApiMapper;
import org.ezkey.authattempt.service.AuthAttemptService;
import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.exception.EnrollmentInactiveException;
import org.ezkey.exception.auth.AuthAttemptCreateValidationException;
import org.ezkey.integration.api.security.AccessControlService;
import org.ezkey.integration.api.security.RateLimitService;
import org.ezkey.integration.domain.entity.Integration;
import org.ezkey.integration.domain.repository.IntegrationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
@DisplayName("IntegrationApiAuthAttemptController")
class IntegrationApiAuthAttemptControllerTest {

  @Mock private AuthAttemptService authAttemptService;
  @Mock private AuthAttemptIntegrationApiMapper authAttemptMapper;
  @Mock private AuditLogService auditLogService;
  @Mock private RateLimitService rateLimitService;
  @Mock private EnrollmentRepository enrollmentRepository;
  @Mock private AccessControlService accessControlService;
  @Mock private IntegrationRepository integrationRepository;

  private IntegrationApiAuthAttemptController controller;

  @BeforeEach
  void setUp() {
    lenient().when(enrollmentRepository.existsById(any())).thenReturn(true);
    lenient().when(integrationRepository.existsById(any())).thenReturn(true);
    AuditEntityFkResolver auditEntityFkResolver =
        new AuditEntityFkResolver(integrationRepository, enrollmentRepository);
    controller =
        new IntegrationApiAuthAttemptController(
            authAttemptService,
            authAttemptMapper,
            auditLogService,
            rateLimitService,
            enrollmentRepository,
            integrationRepository,
            auditEntityFkResolver);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("create() - Should rethrow create validation exception from request resolution")
  void create_ShouldRethrowCreateValidationException_WhenResolutionFails() {
    setupApiKeyAuthentication(77);
    when(rateLimitService.canCreateAuthAttempt(any())).thenReturn(true);

    HttpServletRequest request = new MockHttpServletRequest();
    AuthAttemptCreateRequestDto dto =
        new AuthAttemptCreateRequestDto(null, null, false, null, null);

    AuthAttemptCreateValidationException exception =
        assertThrows(
            AuthAttemptCreateValidationException.class, () -> controller.create(dto, request));

    assertEquals("Either enrollmentId or userIdentifier is required.", exception.getMessage());
  }

  @Test
  @DisplayName("create() - Should rethrow create validation exception from service layer")
  void create_ShouldRethrowCreateValidationException_WhenServiceRejectsRequest() {
    setupApiKeyAuthentication(77);
    when(rateLimitService.canCreateAuthAttempt(any())).thenReturn(true);

    Enrollment enrollment = new Enrollment();
    enrollment.setEnrollmentId(123);
    enrollment.setIntegrationId(77);
    enrollment.setActive(true);
    enrollment.setStatus(EnrollmentStatus.VERIFIED);
    Integration integration = new Integration();
    integration.setId(77);

    when(enrollmentRepository.findById(123)).thenReturn(Optional.of(enrollment));
    when(integrationRepository.findById(77)).thenReturn(Optional.of(integration));
    when(authAttemptService.create(any(AuthAttemptCreateRequest.class)))
        .thenThrow(
            new AuthAttemptCreateValidationException(
                "No verified enrollment found for userIdentifier 'alice'."));

    HttpServletRequest request = new MockHttpServletRequest();
    AuthAttemptCreateRequestDto dto = new AuthAttemptCreateRequestDto(123, null, false, null, null);

    AuthAttemptCreateValidationException exception =
        assertThrows(
            AuthAttemptCreateValidationException.class, () -> controller.create(dto, request));

    assertEquals(
        "No verified enrollment found for userIdentifier 'alice'.", exception.getMessage());
  }

  @Test
  @DisplayName("create() - Should audit enrollment inactive as failure before rethrowing")
  void create_ShouldAuditFailure_WhenEnrollmentIsInactive() {
    setupApiKeyAuthentication(77);
    when(rateLimitService.canCreateAuthAttempt(any())).thenReturn(true);

    Enrollment enrollment = new Enrollment();
    enrollment.setEnrollmentId(123);
    enrollment.setIntegrationId(77);
    enrollment.setActive(false);
    enrollment.setStatus(EnrollmentStatus.REVOKED);
    Integration integration = new Integration();
    integration.setId(77);

    when(enrollmentRepository.findById(123)).thenReturn(Optional.of(enrollment));
    when(integrationRepository.findById(77)).thenReturn(Optional.of(integration));
    HttpServletRequest request = new MockHttpServletRequest();
    AuthAttemptCreateRequestDto dto = new AuthAttemptCreateRequestDto(123, null, false, null, null);

    EnrollmentInactiveException exception =
        assertThrows(EnrollmentInactiveException.class, () -> controller.create(dto, request));

    assertEquals(
        "Enrollment is not active for authentication. Enrollment ID: 123", exception.getMessage());
    verify(auditLogService)
        .log(
            argThat(
                (AuditLog auditLog) ->
                    auditLog.getEventStatus() == EventStatus.FAILURE
                        && "auth_attempt_creation_failed".equals(auditLog.getEventAction())
                        && "Enrollment is not active for authentication. Enrollment ID: 123"
                            .equals(auditLog.getErrorMessage())
                        && Integer.valueOf(123).equals(auditLog.getEnrollmentId())
                        && Integer.valueOf(77).equals(auditLog.getIntegrationId())));
  }

  private void setupApiKeyAuthentication(Integer integrationId) {
    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(
            integrationId, null, List.of(new SimpleGrantedAuthority("ROLE_API_KEY")));
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }
}
