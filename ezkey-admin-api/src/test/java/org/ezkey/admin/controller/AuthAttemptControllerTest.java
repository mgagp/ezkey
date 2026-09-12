/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AuthAttemptControllerTest
 * Description: Unit tests for AuthAttemptController (excluding ownership tests)
 */

package org.ezkey.admin.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.util.List;
import org.ezkey.admin.security.AccessControlService;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.audit.support.AuditEntityFkResolver;
import org.ezkey.authattempt.domain.AuthAttemptCreateRequest;
import org.ezkey.authattempt.domain.AuthAttemptStatus;
import org.ezkey.authattempt.domain.AuthAttemptWaitRequest;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.ezkey.authattempt.dto.AuthAttemptCreateRequestDto;
import org.ezkey.authattempt.dto.AuthAttemptDto;
import org.ezkey.authattempt.mapper.AuthAttemptAdminApiMapper;
import org.ezkey.authattempt.service.AuthAttemptService;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.exception.EnrollmentInactiveException;
import org.ezkey.exception.auth.AuthAttemptCreateValidationException;
import org.ezkey.exception.auth.AuthAttemptStateConflictException;
import org.ezkey.exception.auth.AuthAttemptWaitValidationException;
import org.ezkey.integration.domain.entity.Integration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Unit tests for AuthAttemptController.
 *
 * <p>Focuses on search and retrieval operations. Ownership tests are in
 * AuthAttemptControllerOwnershipTest.
 *
 * @since 2025
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthAttemptController Tests")
class AuthAttemptControllerTest {

  @Mock private AuthAttemptService authAttemptService;
  @Mock private AuthAttemptAdminApiMapper authAttemptMapper;
  @Mock private AuditLogService auditLogService;
  @Mock private EnrollmentRepository enrollmentRepository;
  @Mock private AccessControlService accessControlService;

  @Mock private org.ezkey.integration.domain.repository.IntegrationRepository integrationRepository;

  private AuthAttemptController controller;

  @BeforeEach
  void setUp() {
    SecurityContextHolder.clearContext();
    lenient().when(integrationRepository.existsById(any())).thenReturn(true);
    lenient().when(enrollmentRepository.existsById(any())).thenReturn(true);
    AuditEntityFkResolver auditEntityFkResolver =
        new AuditEntityFkResolver(integrationRepository, enrollmentRepository);
    controller =
        new AuthAttemptController(
            authAttemptService,
            authAttemptMapper,
            auditLogService,
            enrollmentRepository,
            accessControlService,
            integrationRepository,
            auditEntityFkResolver);
  }

  @Test
  @DisplayName("search() - Should return paginated results with filters")
  void search_ShouldReturnPaginatedResults_WhenFiltersProvided() {
    // Arrange
    AuthAttemptStatus status = AuthAttemptStatus.PENDING;
    Integer enrollmentId = 123;
    Integer integrationId = 456;
    OffsetDateTime now = OffsetDateTime.now();
    Pageable pageable = PageRequest.of(0, 10);

    AuthAttempt authAttempt = new AuthAttempt();
    authAttempt.setAuthAttemptId(1);
    authAttempt.setEnrollmentId(enrollmentId);
    List<AuthAttempt> attempts = List.of(authAttempt);
    Page<AuthAttempt> attemptPage = new PageImpl<>(attempts, pageable, 1);

    // Create DTO using record constructor
    AuthAttemptDto dto =
        new AuthAttemptDto(
            1, // authAttemptId
            enrollmentId, // enrollmentId
            status, // status
            123456, // challenge
            "token", // proof token
            now, // createdAt
            now.plusMinutes(5), // expiresAt
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    when(authAttemptService.findByFilters(
            eq(status),
            eq(enrollmentId),
            eq(integrationId),
            eq(now),
            eq(now),
            any(), // tenantId (null for tests)
            any(Pageable.class)))
        .thenReturn(attemptPage);
    when(enrollmentRepository.findAllById(any())).thenReturn(List.of());
    lenient().when(integrationRepository.findAllByIdWithTenant(any())).thenReturn(List.of());
    when(authAttemptMapper.toDtoWithLabels(eq(authAttempt), any(), any())).thenReturn(dto);

    // Act
    ResponseEntity<Page<AuthAttemptDto>> response =
        controller.search(status, enrollmentId, integrationId, now, now, pageable);

    // Assert
    assertNotNull(response);
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals(1, response.getBody().getTotalElements());
    assertEquals(dto, response.getBody().getContent().get(0));

    verify(authAttemptService)
        .findByFilters(
            eq(status),
            eq(enrollmentId),
            eq(integrationId),
            eq(now),
            eq(now),
            any(), // tenantId (null for tests)
            any(Pageable.class));
  }

  @Test
  @DisplayName("search() - Should return all results when no filters provided")
  void search_ShouldReturnAllResults_WhenNoFiltersProvided() {
    // Arrange
    Pageable pageable = PageRequest.of(0, 20);

    AuthAttempt authAttempt = new AuthAttempt();
    authAttempt.setAuthAttemptId(1);
    authAttempt.setEnrollmentId(100);
    List<AuthAttempt> attempts = List.of(authAttempt);
    Page<AuthAttempt> attemptPage = new PageImpl<>(attempts, pageable, 1);

    AuthAttemptDto dto =
        new AuthAttemptDto(
            1,
            100,
            AuthAttemptStatus.PENDING,
            123456,
            "token",
            OffsetDateTime.now(),
            OffsetDateTime.now().plusMinutes(5),
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    when(authAttemptService.findByFilters(
            eq(null),
            eq(null),
            eq(null),
            eq(null),
            eq(null),
            any(), // tenantId (null for tests)
            any(Pageable.class)))
        .thenReturn(attemptPage);
    when(enrollmentRepository.findAllById(any())).thenReturn(List.of());
    lenient().when(integrationRepository.findAllByIdWithTenant(any())).thenReturn(List.of());
    when(authAttemptMapper.toDtoWithLabels(eq(authAttempt), any(), any())).thenReturn(dto);

    // Act
    ResponseEntity<Page<AuthAttemptDto>> response =
        controller.search(null, null, null, null, null, pageable);

    // Assert
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(1, response.getBody().getTotalElements());
    verify(authAttemptService)
        .findByFilters(
            eq(null),
            eq(null),
            eq(null),
            eq(null),
            eq(null),
            any(), // tenantId (null for tests)
            any(Pageable.class));
  }

  @Test
  @DisplayName("search() - Should respect pagination parameters")
  void search_ShouldRespectPaginationParameters() {
    // Arrange
    Pageable pageable = PageRequest.of(2, 50);

    AuthAttempt authAttempt = new AuthAttempt();
    authAttempt.setAuthAttemptId(1);
    authAttempt.setEnrollmentId(100);
    List<AuthAttempt> attempts = List.of(authAttempt);
    Page<AuthAttempt> attemptPage = new PageImpl<>(attempts, pageable, 1);

    AuthAttemptDto dto =
        new AuthAttemptDto(
            1, // authAttemptId
            100, // enrollmentId
            AuthAttemptStatus.PENDING, // status
            123456, // challenge
            "token", // proof token
            OffsetDateTime.now(), // createdAt
            OffsetDateTime.now().plusMinutes(5), // expiresAt
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    when(authAttemptService.findByFilters(
            eq(null),
            eq(null),
            eq(null),
            eq(null),
            eq(null),
            any(), // tenantId (null for tests)
            any(Pageable.class)))
        .thenReturn(attemptPage);
    when(enrollmentRepository.findAllById(any())).thenReturn(List.of());
    lenient().when(integrationRepository.findAllByIdWithTenant(any())).thenReturn(List.of());
    when(authAttemptMapper.toDtoWithLabels(eq(authAttempt), any(), any())).thenReturn(dto);

    // Act
    controller.search(null, null, null, null, null, pageable);

    // Assert
    verify(authAttemptService)
        .findByFilters(
            eq(null),
            eq(null),
            eq(null),
            eq(null),
            eq(null),
            any(), // tenantId (null for tests)
            eq(pageable));
  }

  @Test
  @DisplayName("search() - Should support dynamic sorting")
  void search_ShouldSupportDynamicSorting() {
    // Arrange
    Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "authAttemptId"));

    AuthAttempt authAttempt = new AuthAttempt();
    authAttempt.setAuthAttemptId(1);
    authAttempt.setEnrollmentId(100);
    List<AuthAttempt> attempts = List.of(authAttempt);
    Page<AuthAttempt> attemptPage = new PageImpl<>(attempts, pageable, 1);

    AuthAttemptDto dto =
        new AuthAttemptDto(
            1, // authAttemptId
            100, // enrollmentId
            AuthAttemptStatus.PENDING, // status
            123456, // challenge
            "token", // proof token
            OffsetDateTime.now(), // createdAt
            OffsetDateTime.now().plusMinutes(5), // expiresAt
            null,
            null,
            null,
            null,
            null,
            null,
            null);

    when(authAttemptService.findByFilters(
            eq(null),
            eq(null),
            eq(null),
            eq(null),
            eq(null),
            any(), // tenantId (null for tests)
            any(Pageable.class)))
        .thenReturn(attemptPage);
    when(enrollmentRepository.findAllById(any())).thenReturn(List.of());
    lenient().when(integrationRepository.findAllByIdWithTenant(any())).thenReturn(List.of());
    when(authAttemptMapper.toDtoWithLabels(eq(authAttempt), any(), any())).thenReturn(dto);

    // Act
    ResponseEntity<Page<AuthAttemptDto>> response =
        controller.search(null, null, null, null, null, pageable);

    // Assert
    assertEquals(HttpStatus.OK, response.getStatusCode());
    verify(authAttemptService)
        .findByFilters(
            eq(null),
            eq(null),
            eq(null),
            eq(null),
            eq(null),
            any(), // tenantId (null for tests)
            eq(pageable));
  }

  @Test
  @DisplayName("cancel() - Should rethrow auth attempt state conflict for global handler mapping")
  void cancel_ShouldRethrowAuthAttemptStateConflictException() {
    Integer authAttemptId = 123;
    HttpServletRequest request = new MockHttpServletRequest();

    when(authAttemptService.cancel(authAttemptId))
        .thenThrow(
            new AuthAttemptStateConflictException(
                "Cannot cancel authentication attempt with status: EXPIRED"));

    AuthAttemptStateConflictException exception =
        assertThrows(
            AuthAttemptStateConflictException.class,
            () -> controller.cancel(authAttemptId, request));

    assertEquals(
        "Cannot cancel authentication attempt with status: EXPIRED", exception.getMessage());
  }

  @Test
  @DisplayName(
      "waitForResponse() - Should rethrow wait validation exception for global handler mapping")
  void waitForResponse_ShouldRethrowAuthAttemptWaitValidationException() {
    Integer authAttemptId = 123;
    HttpServletRequest request = new MockHttpServletRequest();

    when(authAttemptMapper.toAuthAttemptWaitRequest(any()))
        .thenReturn(new AuthAttemptWaitRequest(30, 2));
    when(authAttemptService.waitForResponse(eq(authAttemptId), any(AuthAttemptWaitRequest.class)))
        .thenThrow(
            new AuthAttemptWaitValidationException("Polling must be between 1 and 60 seconds"));

    AuthAttemptWaitValidationException exception =
        assertThrows(
            AuthAttemptWaitValidationException.class,
            () -> controller.waitForResponse(authAttemptId, 30, 2, request));

    assertEquals("Polling must be between 1 and 60 seconds", exception.getMessage());
  }

  @Test
  @DisplayName("create() - Should rethrow create validation exception from request resolution")
  void create_ShouldRethrowCreateValidationException_WhenResolutionFails() {
    HttpServletRequest request = new MockHttpServletRequest();
    AuthAttemptCreateRequestDto dto =
        new AuthAttemptCreateRequestDto(null, null, null, false, null, null);

    AuthAttemptCreateValidationException exception =
        assertThrows(
            AuthAttemptCreateValidationException.class, () -> controller.create(dto, request));

    assertEquals(
        "Either enrollmentId or userIdentifier is required. Provide one or both for consistency"
            + " check.",
        exception.getMessage());
  }

  @Test
  @DisplayName("create() - Should rethrow create validation exception from service layer")
  void create_ShouldRethrowCreateValidationException_WhenServiceRejectsRequest() {
    HttpServletRequest request = new MockHttpServletRequest();
    AuthAttemptCreateRequestDto dto =
        new AuthAttemptCreateRequestDto(123, null, null, false, null, null);

    Enrollment enrollment = new Enrollment();
    enrollment.setEnrollmentId(123);
    enrollment.setIntegrationId(456);
    Integration integration = new Integration();
    integration.setId(456);

    when(enrollmentRepository.findById(123)).thenReturn(java.util.Optional.of(enrollment));
    when(integrationRepository.findById(456)).thenReturn(java.util.Optional.of(integration));
    when(authAttemptService.create(any(AuthAttemptCreateRequest.class)))
        .thenThrow(
            new AuthAttemptCreateValidationException(
                "No verified enrollment found for userIdentifier 'alice'."));

    AuthAttemptCreateValidationException exception =
        assertThrows(
            AuthAttemptCreateValidationException.class, () -> controller.create(dto, request));

    assertEquals(
        "No verified enrollment found for userIdentifier 'alice'.", exception.getMessage());
  }

  @Test
  @DisplayName("create() - Should audit enrollment inactive as failure before rethrowing")
  void create_ShouldAuditFailure_WhenEnrollmentIsInactive() {
    HttpServletRequest request = new MockHttpServletRequest();
    AuthAttemptCreateRequestDto dto =
        new AuthAttemptCreateRequestDto(123, null, null, false, null, null);

    Enrollment enrollment = new Enrollment();
    enrollment.setEnrollmentId(123);
    enrollment.setIntegrationId(456);
    Integration integration = new Integration();
    integration.setId(456);

    when(enrollmentRepository.findById(123)).thenReturn(java.util.Optional.of(enrollment));
    when(integrationRepository.findById(456)).thenReturn(java.util.Optional.of(integration));
    when(authAttemptService.create(any(AuthAttemptCreateRequest.class)))
        .thenThrow(new EnrollmentInactiveException("Enrollment is not active for authentication."));

    EnrollmentInactiveException exception =
        assertThrows(EnrollmentInactiveException.class, () -> controller.create(dto, request));

    assertEquals("Enrollment is not active for authentication.", exception.getMessage());
    verify(auditLogService)
        .log(
            argThat(
                (AuditLog auditLog) ->
                    auditLog.getEventStatus() == EventStatus.FAILURE
                        && "auth_attempt_creation_failed".equals(auditLog.getEventAction())
                        && "Enrollment is not active for authentication."
                            .equals(auditLog.getErrorMessage())
                        && Integer.valueOf(123).equals(auditLog.getEnrollmentId())
                        && Integer.valueOf(456).equals(auditLog.getIntegrationId())));
  }
}
