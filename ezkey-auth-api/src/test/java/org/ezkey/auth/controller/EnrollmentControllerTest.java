/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors Licensed under the MIT License. See LICENSE file in the
 * project root for full license information.
 *
 * Test: EnrollmentControllerTest Description: Critical unit tests for EnrollmentController REST
 * endpoints in auth-api.
 */

package org.ezkey.auth.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.integrity.AuditChainHeartbeatGuardService;
import org.ezkey.auth.config.SecurityConfig;
import org.ezkey.auth.config.TrustedProxyConfig;
import org.ezkey.enrollment.domain.EnrollmentBindRequest;
import org.ezkey.enrollment.domain.EnrollmentBindResponse;
import org.ezkey.enrollment.domain.EnrollmentVerifyRequest;
import org.ezkey.enrollment.domain.EnrollmentVerifyResponse;
import org.ezkey.enrollment.dto.EnrollmentBindRequestDto;
import org.ezkey.enrollment.dto.EnrollmentBindResponseDto;
import org.ezkey.enrollment.dto.EnrollmentVerifyRequestDto;
import org.ezkey.enrollment.dto.EnrollmentVerifyResponseDto;
import org.ezkey.enrollment.mapper.EnrollmentAuthMapper;
import org.ezkey.enrollment.service.EnrollmentService;
import org.ezkey.exception.AuthApiProblemCatalog;
import org.ezkey.exception.GlobalExceptionHandler;
import org.ezkey.exception.auth.EnrollmentAlreadyBoundException;
import org.ezkey.exception.auth.EnrollmentBindingFailedException;
import org.ezkey.exception.auth.EnrollmentVerifyFailedException;
import org.ezkey.exception.auth.EnrollmentVerifyStateConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/**
 * Critical unit tests for {@link EnrollmentController} in auth-api.
 *
 * <p>This test class provides comprehensive coverage of the EnrollmentController REST endpoints
 * focusing on security-critical enrollment operations. Tests cover device binding, cryptographic
 * verification, and proper HTTP status code handling for mobile device enrollment.
 *
 * <p><b>Critical Test Coverage:</b>
 *
 * <ul>
 *   <li>POST /api/v1/enrollments/bind - Device binding initiation
 *   <li>POST /api/v1/enrollments/verify - Enrollment verification completion
 *   <li>Security validation - Cryptographic key exchange
 *   <li>State management - Proper enrollment state transitions
 *   <li>Error handling - Appropriate HTTP status codes
 * </ul>
 *
 * <p><b>Security Focus:</b> These tests validate the critical security aspects of mobile enrollment
 * including cryptographic key validation, state consistency, and proper error handling to prevent
 * enrollment vulnerabilities.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see EnrollmentController
 * @see EnrollmentService
 * @see EnrollmentAuthMapper
 */
@WebMvcTest(controllers = EnrollmentController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({SecurityConfig.class, TrustedProxyConfig.class, GlobalExceptionHandler.class})
@DisplayName("Enrollment Controller Critical Tests")
class EnrollmentControllerTest {

  private static final String BASE_URL = "/api/v1/enrollments";

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private EnrollmentService enrollmentService;

  @MockitoBean
  private org.ezkey.enrollment.service.EnrollmentInstanceInfoService enrollmentInstanceInfoService;

  @MockitoBean private EnrollmentAuthMapper enrollmentMapper;

  @MockitoBean private org.ezkey.audit.service.AuditLogService auditLogService;

  @MockitoBean private AuditChainHeartbeatGuardService auditChainHeartbeatGuardService;

  @MockitoBean private org.ezkey.enrollment.service.EnrollmentTxHelper enrollmentTxHelper;

  @MockitoBean
  private org.ezkey.enrollment.domain.repository.EnrollmentRepository enrollmentRepository;

  @MockitoBean
  private org.ezkey.integration.domain.repository.IntegrationRepository integrationRepository;

  @MockitoBean private org.ezkey.integration.domain.repository.EzkeyAdminRepository adminRepository;

  private EnrollmentBindRequestDto bindRequestDto;

  private EnrollmentBindRequest bindRequest;

  private EnrollmentBindResponse bindResponse;

  private EnrollmentBindResponseDto bindResponseDto;

  private EnrollmentVerifyRequestDto verifyRequestDto;

  private EnrollmentVerifyRequest verifyRequest;

  private EnrollmentVerifyResponse verifyResponse;

  private EnrollmentVerifyResponseDto verifyResponseDto;

  @BeforeEach
  void setUp() {
    // Setup bind request test data - Using record constructor
    bindRequestDto = new EnrollmentBindRequestDto(123, "test-proof-token");

    bindRequest = new EnrollmentBindRequest();
    bindRequest.setEnrollmentId(123);
    bindRequest.setEnrollmentProofToken("test-proof-token");

    bindResponse = new EnrollmentBindResponse();
    bindResponse.setEnrollmentId(123);
    bindResponse.setEnrollmentProofToken("test-proof-token");
    bindResponse.setIntegrationPublicKey("test-public-key");
    bindResponse.setIntegrationKeyAlgorithm("ed25519");
    bindResponse.setEnrollmentBindPayloadSignedByIntegration("bind-signature");

    bindResponseDto =
        new EnrollmentBindResponseDto(
            123,
            "test-public-key",
            "ed25519",
            "test-proof-token",
            null,
            null,
            null,
            null,
            null,
            null,
            false,
            null,
            "bind-signature");

    // Setup verify request test data
    verifyRequestDto =
        new EnrollmentVerifyRequestDto(
            123, null, "device-public-key", "proof-token-signature", null);

    verifyRequest = new EnrollmentVerifyRequest();
    verifyRequest.setEnrollmentId(123);
    verifyRequest.setDevicePublicKey("device-public-key");
    verifyRequest.setEnrollmentProofTokenSigned("proof-token-signature");

    verifyResponse = new EnrollmentVerifyResponse();
    verifyResponse.setActive(true);
    verifyResponse.setEnrollmentVerifyMessage("Enrollment verified successfully");
    verifyResponse.setEnrollmentVerifyPayloadSignedByIntegration("verify-signature");

    verifyResponseDto =
        new EnrollmentVerifyResponseDto(
            true, "Enrollment verified successfully", "verify-signature");
  }

  // ===== BIND ENDPOINT TESTS =====

  @Test
  @DisplayName("POST /api/v1/enrollments/bind - Should return 200 when binding successful")
  void bind_WhenBindingSuccessful_ShouldReturn200() throws Exception {
    // Arrange
    when(enrollmentMapper.toEnrollmentBindRequest(any(EnrollmentBindRequestDto.class)))
        .thenReturn(bindRequest);
    when(enrollmentService.bind(any(EnrollmentBindRequest.class))).thenReturn(bindResponse);
    when(enrollmentMapper.toEnrollmentBindResponseDto(bindResponse)).thenReturn(bindResponseDto);

    String json = objectMapper.writeValueAsString(bindRequestDto);

    // Act & Assert
    mockMvc
        .perform(post(BASE_URL + "/bind").contentType(MediaType.APPLICATION_JSON).content(json))
        .andExpect(status().isOk());

    // Verify service interactions
    verify(enrollmentMapper, times(1)).toEnrollmentBindRequest(any(EnrollmentBindRequestDto.class));
    verify(enrollmentService, times(1)).bind(any(EnrollmentBindRequest.class));
    verify(enrollmentMapper, times(1)).toEnrollmentBindResponseDto(bindResponse);
  }

  @Test
  @DisplayName("POST /api/v1/enrollments/bind - Should return 400 on invalid enrollment ID")
  void bind_WhenInvalidEnrollmentId_ShouldReturn400() throws Exception {
    // Arrange - Using record constructor
    EnrollmentBindRequestDto invalidRequestDto =
        new EnrollmentBindRequestDto(999, "invalid-proof-token");

    when(enrollmentMapper.toEnrollmentBindRequest(any(EnrollmentBindRequestDto.class)))
        .thenReturn(bindRequest);
    when(enrollmentService.bind(any(EnrollmentBindRequest.class)))
        .thenThrow(new EnrollmentBindingFailedException("Invalid enrollment ID"));

    String json = objectMapper.writeValueAsString(invalidRequestDto);

    // Act & Assert
    mockMvc
        .perform(post(BASE_URL + "/bind").contentType(MediaType.APPLICATION_JSON).content(json))
        .andExpect(status().isBadRequest());

    // Verify service interactions
    verify(enrollmentMapper, times(1)).toEnrollmentBindRequest(any(EnrollmentBindRequestDto.class));
    verify(enrollmentService, times(1)).bind(any(EnrollmentBindRequest.class));
  }

  @Test
  @DisplayName(
      "POST /api/v1/enrollments/bind - Should audit expected business rejection as failure")
  void bind_WhenBusinessRuleRejects_ShouldAuditFailure() throws Exception {
    when(enrollmentMapper.toEnrollmentBindRequest(any(EnrollmentBindRequestDto.class)))
        .thenReturn(bindRequest);
    when(enrollmentService.bind(any(EnrollmentBindRequest.class)))
        .thenThrow(
            new EnrollmentBindingFailedException(
                "Enrollment binding failed: linked administrator lifecycle status is"
                    + " PENDING_ACTIVATION"));

    String json = objectMapper.writeValueAsString(bindRequestDto);

    mockMvc
        .perform(post(BASE_URL + "/bind").contentType(MediaType.APPLICATION_JSON).content(json))
        .andExpect(status().isBadRequest());

    ArgumentCaptor<AuditLog> auditLogCaptor = ArgumentCaptor.forClass(AuditLog.class);
    verify(auditLogService).log(auditLogCaptor.capture());

    AuditLog auditLog = auditLogCaptor.getValue();
    assertEquals("enrollment_bind_failed", auditLog.getEventAction());
    assertEquals(EventStatus.FAILURE, auditLog.getEventStatus());
    assertEquals(
        "Enrollment binding failed: linked administrator lifecycle status is PENDING_ACTIVATION",
        auditLog.getErrorMessage());
  }

  @Test
  @DisplayName("POST /api/v1/enrollments/bind - Should return 409 when enrollment already bound")
  void bind_WhenEnrollmentAlreadyBound_ShouldReturn409() throws Exception {
    // Arrange
    when(enrollmentMapper.toEnrollmentBindRequest(any(EnrollmentBindRequestDto.class)))
        .thenReturn(bindRequest);
    when(enrollmentService.bind(any(EnrollmentBindRequest.class)))
        .thenThrow(new EnrollmentAlreadyBoundException("Enrollment already bound"));

    String json = objectMapper.writeValueAsString(bindRequestDto);

    // Act & Assert
    mockMvc
        .perform(post(BASE_URL + "/bind").contentType(MediaType.APPLICATION_JSON).content(json))
        .andExpect(status().isConflict());

    // Verify service interactions
    verify(enrollmentMapper, times(1)).toEnrollmentBindRequest(any(EnrollmentBindRequestDto.class));
    verify(enrollmentService, times(1)).bind(any(EnrollmentBindRequest.class));
  }

  @Test
  @DisplayName("POST /api/v1/enrollments/bind - Should return 200 with valid bind request")
  void bind_WhenValidRequest_ShouldReturn200() throws Exception {
    // Arrange
    when(enrollmentMapper.toEnrollmentBindRequest(any(EnrollmentBindRequestDto.class)))
        .thenReturn(bindRequest);
    when(enrollmentService.bind(any(EnrollmentBindRequest.class))).thenReturn(bindResponse);
    when(enrollmentMapper.toEnrollmentBindResponseDto(bindResponse)).thenReturn(bindResponseDto);

    String json = objectMapper.writeValueAsString(bindRequestDto);

    // Act & Assert
    mockMvc
        .perform(post(BASE_URL + "/bind").contentType(MediaType.APPLICATION_JSON).content(json))
        .andExpect(status().isOk());

    // Verify service interactions
    verify(enrollmentMapper, times(1)).toEnrollmentBindRequest(any(EnrollmentBindRequestDto.class));
    verify(enrollmentService, times(1)).bind(any(EnrollmentBindRequest.class));
    verify(enrollmentMapper, times(1)).toEnrollmentBindResponseDto(bindResponse);
  }

  // ===== VERIFY ENDPOINT TESTS =====

  @Test
  @DisplayName("POST /api/v1/enrollments/verify - Should return 200 when verification successful")
  void verify_WhenVerificationSuccessful_ShouldReturn200() throws Exception {
    // Arrange
    when(enrollmentMapper.toEnrollmentVerifyRequest(any(EnrollmentVerifyRequestDto.class)))
        .thenReturn(verifyRequest);
    when(enrollmentService.verify(any(EnrollmentVerifyRequest.class))).thenReturn(verifyResponse);
    when(enrollmentMapper.toEnrollmentVerifyResponseDto(verifyResponse))
        .thenReturn(verifyResponseDto);

    String json = objectMapper.writeValueAsString(verifyRequestDto);

    // Act & Assert
    mockMvc
        .perform(post(BASE_URL + "/verify").contentType(MediaType.APPLICATION_JSON).content(json))
        .andExpect(status().isOk());

    // Verify service interactions
    verify(enrollmentMapper, times(1))
        .toEnrollmentVerifyRequest(any(EnrollmentVerifyRequestDto.class));
    verify(enrollmentService, times(1)).verify(any(EnrollmentVerifyRequest.class));
    verify(enrollmentMapper, times(1)).toEnrollmentVerifyResponseDto(verifyResponse);
  }

  @Test
  @DisplayName("POST /api/v1/enrollments/verify - Should return 400 when enrollmentId is omitted")
  void verify_WhenEnrollmentIdMissing_ShouldReturn400() throws Exception {
    String json =
        """
        {"challengeResponse":123456,"devicePublicKey":"x","enrollmentProofTokenSigned":"y",\
        "devicePrivateKeyStorageTier":"STANDARD"}
        """;

    mockMvc
        .perform(post(BASE_URL + "/verify").contentType(MediaType.APPLICATION_JSON).content(json))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value(AuthApiProblemCatalog.TYPE_VALIDATION_FAILED));

    verify(enrollmentService, never()).verify(any(EnrollmentVerifyRequest.class));
  }

  @Test
  @DisplayName("POST /api/v1/enrollments/verify - Should return 400 on invalid verification data")
  void verify_WhenInvalidVerificationData_ShouldReturn400() throws Exception {
    // Arrange
    when(enrollmentMapper.toEnrollmentVerifyRequest(any(EnrollmentVerifyRequestDto.class)))
        .thenReturn(verifyRequest);
    when(enrollmentService.verify(any(EnrollmentVerifyRequest.class)))
        .thenThrow(new EnrollmentVerifyFailedException("Invalid cryptographic data"));

    String json = objectMapper.writeValueAsString(verifyRequestDto);

    // Act & Assert
    mockMvc
        .perform(post(BASE_URL + "/verify").contentType(MediaType.APPLICATION_JSON).content(json))
        .andExpect(status().isBadRequest());

    // Verify service interactions
    verify(enrollmentService, times(1)).verify(any(EnrollmentVerifyRequest.class));
  }

  @Test
  @DisplayName("POST /api/v1/enrollments/verify - Should return 409 on enrollment state conflict")
  void verify_WhenEnrollmentStateConflict_ShouldReturn409() throws Exception {
    // Arrange
    when(enrollmentMapper.toEnrollmentVerifyRequest(any(EnrollmentVerifyRequestDto.class)))
        .thenReturn(verifyRequest);
    when(enrollmentService.verify(any(EnrollmentVerifyRequest.class)))
        .thenThrow(new EnrollmentVerifyStateConflictException("Enrollment not in bind state"));

    String json = objectMapper.writeValueAsString(verifyRequestDto);

    // Act & Assert
    mockMvc
        .perform(post(BASE_URL + "/verify").contentType(MediaType.APPLICATION_JSON).content(json))
        .andExpect(status().isConflict());

    // Verify service interactions
    verify(enrollmentService, times(1)).verify(any(EnrollmentVerifyRequest.class));
  }

  @Test
  @DisplayName("POST /api/v1/enrollments/verify - Should audit unexpected errors as error")
  void verify_WhenUnexpectedError_ShouldAuditError() throws Exception {
    when(enrollmentMapper.toEnrollmentVerifyRequest(any(EnrollmentVerifyRequestDto.class)))
        .thenReturn(verifyRequest);
    when(enrollmentService.verify(any(EnrollmentVerifyRequest.class)))
        .thenThrow(new RuntimeException("Database unavailable"));

    String json = objectMapper.writeValueAsString(verifyRequestDto);

    mockMvc
        .perform(post(BASE_URL + "/verify").contentType(MediaType.APPLICATION_JSON).content(json))
        .andExpect(status().isInternalServerError());

    ArgumentCaptor<AuditLog> auditLogCaptor = ArgumentCaptor.forClass(AuditLog.class);
    verify(auditLogService).log(auditLogCaptor.capture());

    AuditLog auditLog = auditLogCaptor.getValue();
    assertEquals("enrollment_verify_error", auditLog.getEventAction());
    assertEquals(EventStatus.ERROR, auditLog.getEventStatus());
    assertEquals("Database unavailable", auditLog.getErrorMessage());
  }
}
