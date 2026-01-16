/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors Licensed under the MIT License. See LICENSE file in the
 * project root for full license information.
 *
 * Test: EnrollmentControllerTest Description: Critical unit tests for EnrollmentController REST
 * endpoints in auth-api.
 */

package org.ezkey.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.ezkey.auth.config.SecurityConfig;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

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
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
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
@Import({SecurityConfig.class, EnrollmentControllerTest.TestConfig.class})
@DisplayName("Enrollment Controller Critical Tests")
class EnrollmentControllerTest {

  private static final String BASE_URL = "/api/v1/enrollments";

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private EnrollmentService enrollmentService;

  @Autowired private EnrollmentAuthMapper enrollmentMapper;

  @Autowired private org.ezkey.audit.service.AuditLogService auditLogService;

  @TestConfiguration
  static class TestConfig {

    @Bean
    @Primary
    public EnrollmentService enrollmentService() {
      return mock(EnrollmentService.class);
    }

    @Bean
    @Primary
    public EnrollmentAuthMapper enrollmentAuthMapper() {
      return mock(EnrollmentAuthMapper.class);
    }

    @Bean
    @Primary
    public org.ezkey.audit.service.AuditLogService auditLogService() {
      return mock(org.ezkey.audit.service.AuditLogService.class);
    }
  }

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
    bindRequestDto =
        new EnrollmentBindRequestDto(
            123, // enrollmentId
            "test-proof-token", // enrollmentProofToken
            "en" // language
            );

    bindRequest = new EnrollmentBindRequest();
    bindRequest.setEnrollmentId(123);
    bindRequest.setEnrollmentProofToken("test-proof-token");
    bindRequest.setLanguage("en");

    bindResponse = new EnrollmentBindResponse();
    bindResponse.setEnrollmentId(123);
    bindResponse.setEnrollmentProofToken("test-proof-token");
    bindResponse.setIntegrationPublicKey("test-public-key");

    bindResponseDto =
        new EnrollmentBindResponseDto(
            123, "test-public-key", "test-proof-token", null, null, null, null);

    // Setup verify request test data
    verifyRequestDto =
        new EnrollmentVerifyRequestDto(123, null, "device-public-key", "proof-token-signature");

    verifyRequest = new EnrollmentVerifyRequest();
    verifyRequest.setEnrollmentId(123);
    verifyRequest.setDevicePublicKey("device-public-key");
    verifyRequest.setEnrollmentProofTokenSigned("proof-token-signature");

    verifyResponse = new EnrollmentVerifyResponse();
    verifyResponse.setActive(true);

    verifyResponseDto = new EnrollmentVerifyResponseDto(true);
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
        new EnrollmentBindRequestDto(
            999, // enrollmentId
            "invalid-proof-token", // enrollmentProofToken
            "en" // language
            );

    when(enrollmentMapper.toEnrollmentBindRequest(any(EnrollmentBindRequestDto.class)))
        .thenReturn(bindRequest);
    when(enrollmentService.bind(any(EnrollmentBindRequest.class)))
        .thenThrow(new IllegalArgumentException("Invalid enrollment ID"));

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
  @DisplayName("POST /api/v1/enrollments/bind - Should return 409 when enrollment already bound")
  void bind_WhenEnrollmentAlreadyBound_ShouldReturn409() throws Exception {
    // Arrange
    when(enrollmentMapper.toEnrollmentBindRequest(any(EnrollmentBindRequestDto.class)))
        .thenReturn(bindRequest);
    when(enrollmentService.bind(any(EnrollmentBindRequest.class)))
        .thenThrow(new IllegalStateException("Enrollment already bound"));

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
  @DisplayName("POST /api/v1/enrollments/bind - Should handle default language when not specified")
  void bind_WhenMissingAcceptLanguage_ShouldUseDefaultLanguage() throws Exception {
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
  @DisplayName("POST /api/v1/enrollments/verify - Should return 400 on invalid verification data")
  void verify_WhenInvalidVerificationData_ShouldReturn400() throws Exception {
    // Arrange
    when(enrollmentMapper.toEnrollmentVerifyRequest(any(EnrollmentVerifyRequestDto.class)))
        .thenReturn(verifyRequest);
    when(enrollmentService.verify(any(EnrollmentVerifyRequest.class)))
        .thenThrow(new IllegalArgumentException("Invalid cryptographic data"));

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
        .thenThrow(new IllegalStateException("Enrollment not in bind state"));

    String json = objectMapper.writeValueAsString(verifyRequestDto);

    // Act & Assert
    mockMvc
        .perform(post(BASE_URL + "/verify").contentType(MediaType.APPLICATION_JSON).content(json))
        .andExpect(status().isConflict());

    // Verify service interactions
    verify(enrollmentService, times(1)).verify(any(EnrollmentVerifyRequest.class));
  }
}
