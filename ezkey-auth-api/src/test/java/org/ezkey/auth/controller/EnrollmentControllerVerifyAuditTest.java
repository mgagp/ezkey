/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: EnrollmentControllerVerifyAuditTest
 * Description: Unit tests for enrollment verification audit logging with uniqueness validation.
 */

package org.ezkey.auth.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;
import org.ezkey.audit.domain.ApiName;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.enrollment.domain.EnrollmentVerifyRequest;
import org.ezkey.enrollment.domain.EnrollmentVerifyResponse;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.enrollment.dto.EnrollmentVerifyRequestDto;
import org.ezkey.enrollment.dto.EnrollmentVerifyResponseDto;
import org.ezkey.enrollment.mapper.EnrollmentAuthMapper;
import org.ezkey.enrollment.service.EnrollmentService;
import org.ezkey.exception.auth.EnrollmentVerifyFailedException;
import org.ezkey.exception.auth.EnrollmentVerifyStateConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Unit tests for enrollment verification audit logging with uniqueness validation.
 *
 * <p>Tests validate that audit logs are correctly written with required fields for operator-visible audit
 * when enrollment verification is rejected due to uniqueness constraints.
 *
 * @author Ezkey contributors
 * @since 2025
 */
@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
@DisplayName("Enrollment Verification Audit Logging Tests")
class EnrollmentControllerVerifyAuditTest {

  @Mock private EnrollmentService enrollmentService;

  @Mock private EnrollmentAuthMapper enrollmentMapper;

  @Mock private AuditLogService auditLogService;

  @Mock private EnrollmentRepository enrollmentRepository;

  @Mock private org.ezkey.integration.domain.repository.IntegrationRepository integrationRepository;

  @Mock private org.ezkey.integration.domain.repository.EzkeyAdminRepository adminRepository;

  @Mock private HttpServletRequest httpRequest;

  private EnrollmentController enrollmentController;

  private EnrollmentVerifyRequestDto requestDto;
  private EnrollmentVerifyRequest verifyRequest;
  private Enrollment attemptedEnrollment;

  @BeforeEach
  void setUp() {
    requestDto =
        new EnrollmentVerifyRequestDto(
            200, 123456, "device-public-key", "proof-token-signature", null);

    verifyRequest = new EnrollmentVerifyRequest();
    verifyRequest.setEnrollmentId(200);
    verifyRequest.setChallengeResponse(123456);
    verifyRequest.setDevicePublicKey("device-public-key");
    verifyRequest.setEnrollmentProofTokenSigned("proof-token-signature");

    attemptedEnrollment = new Enrollment();
    attemptedEnrollment.setEnrollmentId(200);
    attemptedEnrollment.setIntegrationId(1);
    attemptedEnrollment.setEnrollmentName("Test Enrollment");
    attemptedEnrollment.setStatus(EnrollmentStatus.BOUND);

    when(enrollmentMapper.toEnrollmentVerifyRequest(requestDto)).thenReturn(verifyRequest);

    when(adminRepository.findTenantIdByEnrollmentId(anyInt())).thenReturn(Optional.empty());

    // Mock HttpServletRequest for AuditHelper (with empty trusted proxies only remoteAddr
    // is used). Lenient for header stubs that may not be called.
    lenient().when(httpRequest.getHeader("CF-Connecting-IP")).thenReturn(null);
    lenient().when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
    lenient().when(httpRequest.getHeader("X-Real-IP")).thenReturn(null);
    when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
    when(httpRequest.getHeader("User-Agent")).thenReturn("test-agent");

    // Create controller manually
    enrollmentController =
        new EnrollmentController(
            enrollmentService,
            mock(org.ezkey.enrollment.service.EnrollmentInstanceInfoService.class),
            enrollmentMapper,
            auditLogService,
            enrollmentRepository,
            integrationRepository,
            adminRepository,
            new org.ezkey.auth.config.TrustedProxyProperties());
  }

  @Test
  @DisplayName("verify() - Should log audit with existing enrollment ID when verification rejected")
  void verify_WhenRejected_ShouldLogAuditWithExistingEnrollmentId() {
    // Arrange
    Enrollment existingVerified = new Enrollment();
    existingVerified.setEnrollmentId(100);
    existingVerified.setIntegrationId(1);
    existingVerified.setEnrollmentName("Test Enrollment");
    existingVerified.setStatus(EnrollmentStatus.VERIFIED);
    existingVerified.setActive(true);

    when(enrollmentService.verify(verifyRequest))
        .thenThrow(
            new EnrollmentVerifyStateConflictException(
                "A verified enrollment with the same name already exists for this integration."));

    when(enrollmentRepository.findById(200)).thenReturn(Optional.of(attemptedEnrollment));
    when(enrollmentRepository.findByIntegrationIdAndEnrollmentNameAndStatusAndEnrollmentIdNot(
            eq(1), eq("Test Enrollment"), eq(EnrollmentStatus.VERIFIED), eq(200)))
        .thenReturn(List.of(existingVerified));

    // Act
    try {
      enrollmentController.verify(requestDto, httpRequest);
    } catch (EnrollmentVerifyStateConflictException e) {
      // Expected
    }

    // Assert: Verify audit log was written with required fields
    ArgumentCaptor<AuditLog> auditLogCaptor = ArgumentCaptor.forClass(AuditLog.class);
    verify(auditLogService, times(1)).log(auditLogCaptor.capture());

    AuditLog auditLog = auditLogCaptor.getValue();
    assertEquals(EventType.ENROLLMENT_VERIFY, auditLog.getEventType());
    assertEquals(EventStatus.FAILURE, auditLog.getEventStatus());
    assertEquals("enrollment_verify_failed", auditLog.getEventAction());
    assertEquals(ApiName.AUTH_API, auditLog.getApiName());
    assertEquals(Integer.valueOf(200), auditLog.getEnrollmentId()); // Attempted enrollment ID
    assertNotNull(auditLog.getErrorMessage());
    assertTrue(auditLog.getErrorMessage().contains("verified enrollment with the same name"));
    assertNotNull(auditLog.getEventDetails());
    assertTrue(auditLog.getEventDetails().contains("Attempted enrollment ID: 200"));
    assertTrue(auditLog.getEventDetails().contains("Existing enrollment ID: 100"));
    assertTrue(auditLog.getEventDetails().contains("Enrollment name: Test Enrollment"));
  }

  @Test
  @DisplayName(
      "verify() - Should log enrollment_verify_failed when service throws"
          + " EnrollmentVerifyFailedException")
  void verify_WhenInvalidChallenge_ShouldLogEnrollmentVerifyFailed() {
    // Arrange: service throws EnrollmentVerifyFailedException (e.g. invalid challenge response)
    when(enrollmentService.verify(verifyRequest))
        .thenThrow(new EnrollmentVerifyFailedException("Invalid challenge response"));
    when(enrollmentRepository.findById(200)).thenReturn(Optional.of(attemptedEnrollment));

    // Act
    try {
      enrollmentController.verify(requestDto, httpRequest);
    } catch (EnrollmentVerifyFailedException e) {
      // Expected
    }

    // Assert: audit log written with enrollment_verify_failed
    ArgumentCaptor<AuditLog> auditLogCaptor = ArgumentCaptor.forClass(AuditLog.class);
    verify(auditLogService, times(1)).log(auditLogCaptor.capture());

    AuditLog auditLog = auditLogCaptor.getValue();
    assertEquals(EventType.ENROLLMENT_VERIFY, auditLog.getEventType());
    assertEquals(EventStatus.FAILURE, auditLog.getEventStatus());
    assertEquals("enrollment_verify_failed", auditLog.getEventAction());
    assertEquals(ApiName.AUTH_API, auditLog.getApiName());
    assertEquals(Integer.valueOf(200), auditLog.getEnrollmentId());
    assertNotNull(auditLog.getErrorMessage());
    assertTrue(auditLog.getErrorMessage().contains("Invalid challenge response"));
  }

  @Test
  @DisplayName("verify() - Should log standard success audit when verification succeeds")
  void verify_WhenSucceeds_ShouldLogStandardSuccessAudit() {
    // Arrange
    EnrollmentVerifyResponse verifyResponse = new EnrollmentVerifyResponse();
    verifyResponse.setActive(true);
    verifyResponse.setEnrollmentVerifyMessage("Enrollment verified successfully");
    verifyResponse.setEnrollmentVerifyPayloadSignedByIntegration("sig");

    EnrollmentVerifyResponseDto responseDto =
        new EnrollmentVerifyResponseDto(true, "Enrollment verified successfully", "sig");

    when(enrollmentService.verify(verifyRequest)).thenReturn(verifyResponse);
    when(enrollmentMapper.toEnrollmentVerifyResponseDto(verifyResponse)).thenReturn(responseDto);

    // Act
    ResponseEntity<EnrollmentVerifyResponseDto> response =
        enrollmentController.verify(requestDto, httpRequest);

    // Assert
    assertEquals(HttpStatus.OK, response.getStatusCode());

    // Verify audit log was written with standard success logging
    ArgumentCaptor<AuditLog> auditLogCaptor = ArgumentCaptor.forClass(AuditLog.class);
    verify(auditLogService, times(1)).log(auditLogCaptor.capture());

    AuditLog auditLog = auditLogCaptor.getValue();
    assertEquals(EventType.ENROLLMENT_VERIFY, auditLog.getEventType());
    assertEquals(EventStatus.SUCCESS, auditLog.getEventStatus());
    assertEquals("enrollment_verify_success", auditLog.getEventAction());
    assertEquals(ApiName.AUTH_API, auditLog.getApiName());
    assertEquals(Integer.valueOf(200), auditLog.getEnrollmentId());
    assertNotNull(auditLog.getEventDetails());
    assertTrue(auditLog.getEventDetails().contains("Enrollment activated"));
  }
}
