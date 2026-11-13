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
import org.ezkey.exception.RateLimitExceededException;
import org.ezkey.exception.ResourceNotFoundException;
import org.ezkey.exception.auth.AuthAttemptCreateValidationException;
import org.ezkey.exception.auth.AuthAttemptPendingExistsException;
import org.ezkey.exception.auth.AuthAttemptRequestInvalidException;
import org.ezkey.integration.api.config.ApiKeyRateLimitProperties;
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
            auditEntityFkResolver,
            new ApiKeyRateLimitProperties(),
            accessControlService);
    org.mockito.Mockito.lenient()
        .when(accessControlService.canAccessAuthAttempt(any(), any()))
        .thenReturn(true);
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
        new AuthAttemptCreateRequestDto(null, null, false, null, null, null);

    AuthAttemptCreateValidationException exception =
        assertThrows(
            AuthAttemptCreateValidationException.class, () -> controller.create(dto, request));

    assertEquals("Either enrollmentId or userIdentifier is required.", exception.getMessage());
  }

  @Test
  @DisplayName(
      "create() - enrollmentId of another integration answers exactly like an unknown id"
          + " (SQL-ISO-001)")
  void create_foreignEnrollmentAnswersLikeMissingEnrollment() {
    setupApiKeyAuthentication(77);
    when(rateLimitService.canCreateAuthAttempt(any())).thenReturn(true);

    Enrollment foreign = new Enrollment();
    foreign.setEnrollmentId(500);
    foreign.setIntegrationId(88);
    foreign.setActive(true);
    foreign.setStatus(EnrollmentStatus.VERIFIED);
    when(enrollmentRepository.findById(500)).thenReturn(Optional.of(foreign));
    when(enrollmentRepository.findById(999)).thenReturn(Optional.empty());

    HttpServletRequest request = new MockHttpServletRequest();
    ResourceNotFoundException foreignFailure =
        assertThrows(
            ResourceNotFoundException.class,
            () ->
                controller.create(
                    new AuthAttemptCreateRequestDto(500, null, false, null, null, null), request));
    ResourceNotFoundException missingFailure =
        assertThrows(
            ResourceNotFoundException.class,
            () ->
                controller.create(
                    new AuthAttemptCreateRequestDto(999, null, false, null, null, null), request));

    // Same exception type and wording (-> 404 resource/not-found); only the caller's id differs
    assertEquals(
        new ResourceNotFoundException("Enrollment", 500).getMessage(), foreignFailure.getMessage());
    assertEquals(
        new ResourceNotFoundException("Enrollment", 999).getMessage(), missingFailure.getMessage());
    // Both failures are audited the same way
    verify(auditLogService, org.mockito.Mockito.times(2)).log(any());
    verify(authAttemptService, org.mockito.Mockito.never()).create(any());
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
    AuthAttemptCreateRequestDto dto =
        new AuthAttemptCreateRequestDto(123, null, false, null, null, null);

    AuthAttemptCreateValidationException exception =
        assertThrows(
            AuthAttemptCreateValidationException.class, () -> controller.create(dto, request));

    assertEquals(
        "No verified enrollment found for userIdentifier 'alice'.", exception.getMessage());
  }

  @Test
  @DisplayName("create() - ttlSeconds=14 rejects before enrollment lookup (known user)")
  void create_ttlBelowFloor_rejectsBeforeLookup_knownUser() {
    setupApiKeyAuthentication(77);
    when(rateLimitService.canCreateAuthAttempt(any())).thenReturn(true);

    HttpServletRequest request = new MockHttpServletRequest();
    AuthAttemptCreateRequestDto dto =
        new AuthAttemptCreateRequestDto(123, null, false, null, null, 14);

    AuthAttemptRequestInvalidException exception =
        assertThrows(
            AuthAttemptRequestInvalidException.class, () -> controller.create(dto, request));

    assertEquals("ttlSeconds must be at least 15", exception.getMessage());
    verify(enrollmentRepository, org.mockito.Mockito.never()).findById(any());
  }

  @Test
  @DisplayName(
      "create() - ttlSeconds=14 same rejection for unknown userIdentifier (non-enumerating)")
  void create_ttlBelowFloor_rejectsBeforeLookup_unknownUser() {
    setupApiKeyAuthentication(77);
    when(rateLimitService.canCreateAuthAttempt(any())).thenReturn(true);

    HttpServletRequest request = new MockHttpServletRequest();
    AuthAttemptCreateRequestDto dto =
        new AuthAttemptCreateRequestDto(null, "nobody@example.com", false, null, null, 14);

    AuthAttemptRequestInvalidException exception =
        assertThrows(
            AuthAttemptRequestInvalidException.class, () -> controller.create(dto, request));

    assertEquals("ttlSeconds must be at least 15", exception.getMessage());
    verify(enrollmentRepository, org.mockito.Mockito.never())
        .findByIntegrationIdAndUserIdentifierAndStatusAndActive(any(), any(), any(), any());
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
    AuthAttemptCreateRequestDto dto =
        new AuthAttemptCreateRequestDto(123, null, false, null, null, null);

    EnrollmentInactiveException exception =
        assertThrows(EnrollmentInactiveException.class, () -> controller.create(dto, request));

    assertEquals(
        "Enrollment is not active for authentication. Enrollment ID: 123", exception.getMessage());
    verify(auditLogService)
        .log(
            argThat((AuditLog auditLog) ->
                auditLog.getEventStatus() == EventStatus.FAILURE
                    && "auth_attempt_creation_failed".equals(auditLog.getEventAction())
                    && "Enrollment is not active for authentication. Enrollment ID: 123"
                        .equals(auditLog.getErrorMessage())
                    && Integer.valueOf(123).equals(auditLog.getEnrollmentId())
                    && Integer.valueOf(77).equals(auditLog.getIntegrationId())));
  }

  @Test
  @DisplayName("create rethrows a pending-attempt refusal instead of HTTP 500")
  void create_rethrowsPendingExists() {
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
        .thenThrow(new AuthAttemptPendingExistsException(15));
    HttpServletRequest request = new MockHttpServletRequest();
    AuthAttemptCreateRequestDto dto =
        new AuthAttemptCreateRequestDto(123, null, false, null, null, null);

    AuthAttemptPendingExistsException ex =
        assertThrows(
            AuthAttemptPendingExistsException.class, () -> controller.create(dto, request));
    assertEquals(15L, ex.getRetryAfterSeconds());
  }

  @Test
  @DisplayName("429 limit and window come from configuration, including wait and cancel")
  void rateLimit_usesConfiguredLimitAndWindow() {
    ApiKeyRateLimitProperties properties = new ApiKeyRateLimitProperties();
    properties.getCreateAuthAttempt().setRequests(7);
    properties.getCreateAuthAttempt().setWindowMinutes(4);
    properties.getWaitAuthAttempt().setRequests(9);
    properties.getWaitAuthAttempt().setWindowMinutes(6);
    IntegrationApiAuthAttemptController configured =
        new IntegrationApiAuthAttemptController(
            authAttemptService,
            authAttemptMapper,
            auditLogService,
            rateLimitService,
            enrollmentRepository,
            integrationRepository,
            new AuditEntityFkResolver(integrationRepository, enrollmentRepository),
            properties,
            accessControlService);
    setupApiKeyAuthentication(77);
    when(rateLimitService.canCreateAuthAttempt(any())).thenReturn(false);
    when(rateLimitService.canWaitAuthAttempt(any())).thenReturn(false);
    HttpServletRequest request = new MockHttpServletRequest();
    AuthAttemptCreateRequestDto dto =
        new AuthAttemptCreateRequestDto(123, null, false, null, null, null);

    RateLimitExceededException createLimit =
        assertThrows(RateLimitExceededException.class, () -> configured.create(dto, request));
    assertEquals("CREATE_AUTH_ATTEMPT", createLimit.getOperation());
    assertEquals(7, createLimit.getLimit());
    assertEquals(0, createLimit.getCurrent());
    assertEquals(4, createLimit.getWindowSizeMinutes());

    RateLimitExceededException waitLimit =
        assertThrows(
            RateLimitExceededException.class, () -> configured.waitForResponse(1, 30, 2, request));
    assertEquals("WAIT_AUTH_ATTEMPT", waitLimit.getOperation());
    assertEquals(9, waitLimit.getLimit());
    assertEquals(6, waitLimit.getWindowSizeMinutes());

    RateLimitExceededException cancelLimit =
        assertThrows(RateLimitExceededException.class, () -> configured.cancel(1, request));
    assertEquals("CANCEL_AUTH_ATTEMPT", cancelLimit.getOperation());
    assertEquals(9, cancelLimit.getLimit());
    assertEquals(6, cancelLimit.getWindowSizeMinutes());
  }

  @Test
  @DisplayName("wait / cancel on a missing or foreign attempt answer the same 404, before any work")
  void waitAndCancel_objectCheckDenyIsNotFound() {
    setupApiKeyAuthentication(77);
    when(accessControlService.canAccessAuthAttempt(any(), org.mockito.ArgumentMatchers.eq(404)))
        .thenReturn(false);
    HttpServletRequest request = new MockHttpServletRequest();

    assertThrows(
        ResourceNotFoundException.class, () -> controller.waitForResponse(404, 30, 2, request));
    assertThrows(ResourceNotFoundException.class, () -> controller.cancel(404, request));
    verify(authAttemptService, org.mockito.Mockito.never()).cancel(any());
  }

  private void setupApiKeyAuthentication(Integer integrationId) {
    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(
            integrationId, null, List.of(new SimpleGrantedAuthority("ROLE_API_KEY")));
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }
}
