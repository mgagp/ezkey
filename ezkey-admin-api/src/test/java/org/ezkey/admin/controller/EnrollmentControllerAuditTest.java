/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: EnrollmentControllerAuditTest
 * Description: Unit tests for enrollment creation audit logging with uniqueness validation.
 */

package org.ezkey.admin.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.ezkey.admin.constants.AdminAuditConstants;
import org.ezkey.admin.security.AccessControlService;
import org.ezkey.admin.security.AdminPrincipal;
import org.ezkey.admin.service.EnrollmentRevocationService;
import org.ezkey.admin.service.EnrollmentUpdateService;
import org.ezkey.admin.service.QrCodeGeneratorService;
import org.ezkey.admin.service.QrCodePayloadService;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.audit.support.AuditEntityFkResolver;
import org.ezkey.authattempt.domain.repository.AuthAttemptRepository;
import org.ezkey.enrollment.domain.EnrollmentCreateRequest;
import org.ezkey.enrollment.domain.EnrollmentCreateResponse;
import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.enrollment.dto.EnrollmentCreateRequestDto;
import org.ezkey.enrollment.mapper.EnrollmentAdminMapper;
import org.ezkey.enrollment.service.EnrollmentService;
import org.ezkey.exception.ActiveVerifiedEnrollmentExistsException;
import org.ezkey.exception.EnrollmentCreateValidationException;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Unit tests for enrollment creation audit logging with uniqueness validation.
 *
 * <p>Tests validate that audit logs are correctly written with required fields for SOC2 compliance
 * when enrollment creation is rejected or allowed due to uniqueness constraints.
 *
 * @author Ezkey contributors
 * @since 2025
 */
@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
@DisplayName("Enrollment Controller Audit Logging Tests")
class EnrollmentControllerAuditTest {

  @Mock private EnrollmentService enrollmentService;

  @Mock private EnrollmentAdminMapper enrollmentMapper;

  @Mock private AuditLogService auditLogService;

  @Mock private QrCodeGeneratorService qrCodeGeneratorService;

  @Mock private QrCodePayloadService qrCodePayloadService;

  @Mock private AccessControlService accessControlService;

  @Mock private EnrollmentRepository enrollmentRepository;

  @Mock private org.ezkey.integration.domain.repository.IntegrationRepository integrationRepository;

  @Mock private EnrollmentRevocationService enrollmentRevocationService;

  @Mock private EnrollmentUpdateService enrollmentUpdateService;

  @Mock private AuthAttemptRepository authAttemptRepository;

  @Mock private HttpServletRequest httpRequest;

  private EnrollmentController enrollmentController;

  private AuditEntityFkResolver auditEntityFkResolver;

  private EnrollmentCreateRequestDto requestDto;
  private EnrollmentCreateRequest createRequest;

  @BeforeEach
  void setUp() {
    requestDto =
        new EnrollmentCreateRequestDto(1, "Test Enrollment", false, null, null, null, null);
    createRequest = new EnrollmentCreateRequest();
    createRequest.setIntegrationId(1);
    createRequest.setName("Test Enrollment");

    // Mock SecurityContext with AdminPrincipal so audit logs get adminId
    Authentication auth = org.mockito.Mockito.mock(Authentication.class);
    when(auth.getPrincipal())
        .thenReturn(new AdminPrincipal(42, EzkeyAdmin.AdminType.TENANT_ADMIN, 1, null));
    SecurityContextHolder.getContext().setAuthentication(auth);

    // Mock mapper
    lenient().when(enrollmentMapper.toCreateRequest(requestDto)).thenReturn(createRequest);

    // Mock access control - allow access
    lenient()
        .when(accessControlService.canAccessIntegration(any(Authentication.class), eq(1)))
        .thenReturn(true);

    // Mock HttpServletRequest for ClientContext (ClientIpResolver with no trusted
    // proxies uses only remoteAddr). Lenient for header stubs that may not be called.
    lenient().when(httpRequest.getHeader("CF-Connecting-IP")).thenReturn(null);
    lenient().when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
    lenient().when(httpRequest.getHeader("X-Real-IP")).thenReturn(null);
    when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
    when(httpRequest.getHeader("User-Agent")).thenReturn("test-agent");

    auditEntityFkResolver = new AuditEntityFkResolver(integrationRepository, enrollmentRepository);
    lenient().when(integrationRepository.existsById(1)).thenReturn(true);

    // Create controller manually (like AuthAttemptControllerTest)
    enrollmentController =
        new EnrollmentController(
            enrollmentService,
            enrollmentMapper,
            auditLogService,
            qrCodeGeneratorService,
            qrCodePayloadService,
            accessControlService,
            enrollmentRepository,
            integrationRepository,
            enrollmentRevocationService,
            enrollmentUpdateService,
            auditEntityFkResolver);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName(
      "create() - Should log audit with existing enrollment ID when creation rejected (active"
          + " VERIFIED)")
  void create_WhenRejected_ShouldLogAuditWithExistingEnrollmentId() {
    // Arrange
    Enrollment existingVerified = new Enrollment();
    existingVerified.setEnrollmentId(100);
    existingVerified.setIntegrationId(1);
    existingVerified.setEnrollmentName("Test Enrollment");
    existingVerified.setStatus(EnrollmentStatus.VERIFIED);
    existingVerified.setActive(true);

    when(enrollmentService.create(createRequest))
        .thenThrow(
            new ActiveVerifiedEnrollmentExistsException(
                "An active verified enrollment with the same name already exists for this"
                    + " integration."));

    when(enrollmentRepository.findByIntegrationIdAndEnrollmentNameAndStatus(
            eq(1), eq("Test Enrollment"), eq(EnrollmentStatus.VERIFIED)))
        .thenReturn(List.of(existingVerified));

    // Act
    assertThrows(
        ActiveVerifiedEnrollmentExistsException.class,
        () -> enrollmentController.create(requestDto, httpRequest));

    // Assert: Verify audit log was written with required fields
    ArgumentCaptor<AuditLog> auditLogCaptor = ArgumentCaptor.forClass(AuditLog.class);
    verify(auditLogService, times(1)).log(auditLogCaptor.capture());

    AuditLog auditLog = auditLogCaptor.getValue();
    assertEquals(EventType.ENROLLMENT_CREATED, auditLog.getEventType());
    assertEquals(EventStatus.FAILURE, auditLog.getEventStatus());
    assertEquals(AdminAuditConstants.ENROLLMENT_CREATION_FAILED, auditLog.getEventAction());
    assertEquals(Integer.valueOf(1), auditLog.getIntegrationId());
    assertEquals(Integer.valueOf(100), auditLog.getEnrollmentId()); // Existing enrollment ID
    assertNotNull(auditLog.getErrorMessage());
    assertTrue(auditLog.getErrorMessage().contains("active verified enrollment"));
    assertNotNull(auditLog.getEventDetails());
    assertTrue(auditLog.getEventDetails().contains("Existing enrollment ID: 100"));
    assertTrue(auditLog.getEventDetails().contains("Requested name: Test Enrollment"));
  }

  @Test
  @DisplayName(
      "create() - Should log audit with inactive enrollment ID when replacing inactive VERIFIED")
  void create_WhenReplacingInactive_ShouldLogAuditWithInactiveEnrollmentId() {
    // Arrange
    Enrollment existingInactiveVerified = new Enrollment();
    existingInactiveVerified.setEnrollmentId(100);
    existingInactiveVerified.setIntegrationId(1);
    existingInactiveVerified.setEnrollmentName("Test Enrollment");
    existingInactiveVerified.setStatus(EnrollmentStatus.VERIFIED);
    existingInactiveVerified.setActive(false);

    EnrollmentCreateResponse createResponse = new EnrollmentCreateResponse();
    createResponse.setEnrollmentId(200);
    createResponse.setEnrollmentChallenge(123456);

    when(enrollmentService.create(createRequest)).thenReturn(createResponse);
    when(enrollmentMapper.toCreateResponseDto(any(EnrollmentCreateResponse.class)))
        .thenReturn(new org.ezkey.enrollment.dto.EnrollmentCreateResponseDto(200, 123456, null));

    when(enrollmentRepository.findByIntegrationIdAndEnrollmentNameAndStatus(
            eq(1), eq("Test Enrollment"), eq(EnrollmentStatus.VERIFIED)))
        .thenReturn(List.of(existingInactiveVerified));

    // Act
    ResponseEntity<org.ezkey.enrollment.dto.EnrollmentCreateResponseDto> response =
        enrollmentController.create(requestDto, httpRequest);

    // Assert
    assertEquals(HttpStatus.CREATED, response.getStatusCode());

    // Verify audit log was written with inactive enrollment context
    ArgumentCaptor<AuditLog> auditLogCaptor = ArgumentCaptor.forClass(AuditLog.class);
    verify(auditLogService, times(1)).log(auditLogCaptor.capture());

    AuditLog auditLog = auditLogCaptor.getValue();
    assertEquals(EventType.ENROLLMENT_CREATED, auditLog.getEventType());
    assertEquals(EventStatus.SUCCESS, auditLog.getEventStatus());
    assertEquals(AdminAuditConstants.ENROLLMENT_CREATED, auditLog.getEventAction());
    assertEquals(Integer.valueOf(200), auditLog.getEnrollmentId()); // New enrollment ID
    assertEquals(Integer.valueOf(1), auditLog.getIntegrationId());
    assertEquals(Integer.valueOf(42), auditLog.getAdminId()); // From mocked AdminPrincipal
    assertNotNull(auditLog.getEventDetails());
    assertTrue(auditLog.getEventDetails().contains("Replacing inactive VERIFIED enrollment"));
    assertTrue(auditLog.getEventDetails().contains("ID: 100")); // Inactive enrollment ID
  }

  @Test
  @DisplayName("create() - Should log standard audit when no VERIFIED enrollment exists")
  void create_WhenNoVerifiedExists_ShouldLogStandardAudit() {
    // Arrange
    EnrollmentCreateResponse createResponse = new EnrollmentCreateResponse();
    createResponse.setEnrollmentId(200);
    createResponse.setEnrollmentChallenge(123456);

    when(enrollmentService.create(createRequest)).thenReturn(createResponse);
    when(enrollmentMapper.toCreateResponseDto(any(EnrollmentCreateResponse.class)))
        .thenReturn(new org.ezkey.enrollment.dto.EnrollmentCreateResponseDto(200, 123456, null));

    when(enrollmentRepository.findByIntegrationIdAndEnrollmentNameAndStatus(
            eq(1), eq("Test Enrollment"), eq(EnrollmentStatus.VERIFIED)))
        .thenReturn(List.of()); // No VERIFIED enrollments

    // Act
    ResponseEntity<org.ezkey.enrollment.dto.EnrollmentCreateResponseDto> response =
        enrollmentController.create(requestDto, httpRequest);

    // Assert
    assertEquals(HttpStatus.CREATED, response.getStatusCode());

    // Verify audit log was written with standard success logging
    ArgumentCaptor<AuditLog> auditLogCaptor = ArgumentCaptor.forClass(AuditLog.class);
    verify(auditLogService, times(1)).log(auditLogCaptor.capture());

    AuditLog auditLog = auditLogCaptor.getValue();
    assertEquals(EventType.ENROLLMENT_CREATED, auditLog.getEventType());
    assertEquals(EventStatus.SUCCESS, auditLog.getEventStatus());
    assertEquals(AdminAuditConstants.ENROLLMENT_CREATED, auditLog.getEventAction());
    assertEquals(Integer.valueOf(200), auditLog.getEnrollmentId());
    assertEquals(Integer.valueOf(1), auditLog.getIntegrationId());
    assertEquals(Integer.valueOf(42), auditLog.getAdminId()); // From mocked AdminPrincipal
    assertNotNull(auditLog.getEventDetails());
    assertTrue(auditLog.getEventDetails().contains("Enrollment name: Test Enrollment"));
    // Should not contain replacement context
    assertFalse(auditLog.getEventDetails().contains("Replacing inactive VERIFIED"));
  }

  @Test
  @DisplayName(
      "create() - Non-existent integrationId: audit omits integration FK, id in error message")
  void create_WhenIntegrationMissing_auditOmitsIntegrationFkButRetainsRequestedIdInMessage() {
    int missingIntegrationId = 99999;
    EnrollmentCreateRequestDto badDto =
        new EnrollmentCreateRequestDto(
            missingIntegrationId, "Test Enrollment", false, null, null, null, null);
    EnrollmentCreateRequest badCreate = new EnrollmentCreateRequest();
    badCreate.setIntegrationId(missingIntegrationId);
    badCreate.setName("Test Enrollment");

    when(enrollmentMapper.toCreateRequest(badDto)).thenReturn(badCreate);
    when(accessControlService.canAccessIntegration(
            any(Authentication.class), eq(missingIntegrationId)))
        .thenReturn(true);
    when(integrationRepository.existsById(missingIntegrationId)).thenReturn(false);
    when(enrollmentService.create(badCreate))
        .thenThrow(
            new EnrollmentCreateValidationException(
                "Integration not found or not available for enrollment creation."));

    assertThrows(
        EnrollmentCreateValidationException.class,
        () -> enrollmentController.create(badDto, httpRequest));

    ArgumentCaptor<AuditLog> auditLogCaptor = ArgumentCaptor.forClass(AuditLog.class);
    verify(auditLogService, times(1)).log(auditLogCaptor.capture());
    AuditLog auditLog = auditLogCaptor.getValue();
    assertEquals(EventType.ENROLLMENT_CREATED, auditLog.getEventType());
    assertEquals(EventStatus.FAILURE, auditLog.getEventStatus());
    assertNull(auditLog.getIntegrationId());
    assertNotNull(auditLog.getErrorMessage());
    assertTrue(auditLog.getErrorMessage().contains("integrationId: " + missingIntegrationId));
  }
}
