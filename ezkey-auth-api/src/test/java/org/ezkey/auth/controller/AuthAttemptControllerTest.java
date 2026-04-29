/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors Licensed under the MIT License. See LICENSE file in the
 * project root for full license information.
 *
 * Test: AuthAttemptControllerTest Description: Critical unit tests for AuthAttemptController REST
 * endpoints in auth-api.
 */

package org.ezkey.auth.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.auth.config.SecurityConfig;
import org.ezkey.auth.config.TrustedProxyConfig;
import org.ezkey.authattempt.domain.AuthAttemptPendingRequest;
import org.ezkey.authattempt.domain.AuthAttemptPendingResponse;
import org.ezkey.authattempt.domain.AuthAttemptRespondRequest;
import org.ezkey.authattempt.domain.AuthAttemptRespondResponse;
import org.ezkey.authattempt.domain.AuthenticationResult;
import org.ezkey.authattempt.dto.AuthAttemptPendingRequestDto;
import org.ezkey.authattempt.dto.AuthAttemptPendingResponseDto;
import org.ezkey.authattempt.dto.AuthAttemptRespondRequestDto;
import org.ezkey.authattempt.dto.AuthAttemptRespondResponseDto;
import org.ezkey.authattempt.mapper.AuthAttemptAuthApiMapper;
import org.ezkey.authattempt.service.AuthAttemptService;
import org.ezkey.exception.AuthApiProblemCatalog;
import org.ezkey.exception.GlobalExceptionHandler;
import org.ezkey.exception.NoPendingAuthAttemptException;
import org.ezkey.exception.auth.AuthAttemptRequestFailedException;
import org.ezkey.exception.auth.AuthAttemptStateConflictException;
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
 * Critical unit tests for {@link AuthAttemptController} in auth-api.
 *
 * <p>This test class provides comprehensive coverage of the AuthAttemptController REST endpoints
 * focusing on security-critical authentication operations. Tests cover cryptographic signature
 * validation, state management, and proper HTTP status code handling for mobile device
 * authentication.
 *
 * <p><b>Critical Test Coverage:</b>
 *
 * <ul>
 *   <li>POST /api/v1/auth-attempts/pending - Mobile polling for pending requests
 *   <li>POST /api/v1/auth-attempts/respond - Mobile response submission
 *   <li>Security validation - Cryptographic signature handling
 *   <li>State management - Proper status transitions
 *   <li>Error handling - Appropriate HTTP status codes
 * </ul>
 *
 * <p><b>Security Focus:</b> These tests validate the critical security aspects of mobile
 * authentication including signature validation, state consistency, and proper error handling to
 * prevent security vulnerabilities.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see AuthAttemptController
 * @see AuthAttemptService
 * @see AuthAttemptAuthApiMapper
 */
@WebMvcTest(controllers = AuthAttemptController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({SecurityConfig.class, TrustedProxyConfig.class, GlobalExceptionHandler.class})
@DisplayName("AuthAttempt Controller Critical Tests")
class AuthAttemptControllerTest {

  private static final String BASE_URL = "/api/v1/auth-attempts";

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private AuthAttemptService authAttemptService;

  @MockitoBean private AuthAttemptAuthApiMapper authAttemptMapper;

  @MockitoBean private org.ezkey.audit.service.AuditLogService auditLogService;

  @MockitoBean
  private org.ezkey.authattempt.domain.repository.AuthAttemptRepository authAttemptRepository;

  @MockitoBean
  private org.ezkey.enrollment.domain.repository.EnrollmentRepository enrollmentRepository;

  @MockitoBean
  private org.ezkey.integration.domain.repository.IntegrationRepository integrationRepository;

  @MockitoBean private org.ezkey.integration.domain.repository.EzkeyAdminRepository adminRepository;

  private AuthAttemptPendingRequestDto pendingRequestDto;

  private AuthAttemptPendingRequest pendingRequest;

  private AuthAttemptPendingResponse pendingResponse;

  private AuthAttemptPendingResponseDto pendingResponseDto;

  private AuthAttemptRespondRequestDto respondRequestDto;

  private AuthAttemptRespondRequest respondRequest;

  private AuthAttemptRespondResponse respondResponse;

  private AuthAttemptRespondResponseDto respondResponseDto;

  @BeforeEach
  void setUp() {
    // Setup pending request test data - Using record constructor
    pendingRequestDto =
        new AuthAttemptPendingRequestDto(
            123, // enrollmentId
            "EZK-ABC123-DEF456", // enrollmentProofToken
            "test-proof-token", // deviceProofToken
            "test-signature" // deviceProofTokenSigned
            );

    pendingRequest = new AuthAttemptPendingRequest();
    pendingRequest.setEnrollmentId(123);
    pendingRequest.setDeviceProofToken("test-proof-token");
    pendingRequest.setDeviceProofTokenSigned("test-signature");

    pendingResponse = new AuthAttemptPendingResponse();
    pendingResponse.setAuthAttemptId(456);

    // Using record constructor for AuthAttemptPendingResponseDto
    pendingResponseDto =
        new AuthAttemptPendingResponseDto(
            456, // authAttemptId
            "eyJhbGciOiJSUzI1NiJ9...", // authAttemptProofToken
            "eyJhbGciOiJSUzI1NiJ9...", // authAttemptProofTokenSignedByIntegration
            true, // authAttemptChallengeRequired
            true, // authAttemptChallengeRequiredByPolicy
            null, // contextTitle
            null); // contextMessage

    // Setup respond request test data - Using record constructor
    respondRequestDto =
        new AuthAttemptRespondRequestDto(
            456, // authAttemptId
            "test-proof-token", // authAttemptProofTokenSignedByDevice
            123456, // authAttemptChallengeResponse
            true // authAttemptAccepted
            );

    respondRequest = new AuthAttemptRespondRequest();
    respondRequest.setAuthAttemptId(456);
    respondRequest.setAuthAttemptProofTokenSignedByDevice("test-proof-token");
    respondRequest.setAuthAttemptAccepted(true);

    respondResponse = new AuthAttemptRespondResponse();
    respondResponse.setResult(AuthenticationResult.APPROVED);
    respondResponse.setMessage("Authentication approved");
    respondResponse.setAuthAttemptId(456);
    respondResponse.setAuthAttemptProofTokenResultSignedByIntegration(
        "dGVzdC1zaWduYXR1cmU="); // arbitrary base64 for tests

    // Using record constructor for AuthAttemptRespondResponseDto
    respondResponseDto =
        new AuthAttemptRespondResponseDto(
            456, // authAttemptId
            "APPROVED", // authAttemptResult
            "Authentication approved", // authAttemptMessage
            "dGVzdC1zaWduYXR1cmU=" // authAttemptProofTokenResultSignedByIntegration
            );
  }

  // ===== PENDING ENDPOINT TESTS =====

  @Test
  @DisplayName("POST /api/v1/auth-attempts/pending - Should return 200 when pending request found")
  void pending_WhenPendingRequestFound_ShouldReturn200() throws Exception {
    // Arrange
    when(authAttemptMapper.toAuthAttemptPendingRequest(any(AuthAttemptPendingRequestDto.class)))
        .thenReturn(pendingRequest);
    when(authAttemptService.pending(any(AuthAttemptPendingRequest.class)))
        .thenReturn(pendingResponse);
    when(authAttemptMapper.toAuthAttemptPendingResponseDto(pendingResponse))
        .thenReturn(pendingResponseDto);

    String json = objectMapper.writeValueAsString(pendingRequestDto);

    // Act & Assert
    mockMvc
        .perform(post(BASE_URL + "/pending").contentType(MediaType.APPLICATION_JSON).content(json))
        .andExpect(status().isOk());

    // Verify service interactions
    verify(authAttemptMapper, times(1))
        .toAuthAttemptPendingRequest(any(AuthAttemptPendingRequestDto.class));
    verify(authAttemptService, times(1)).pending(any(AuthAttemptPendingRequest.class));
    verify(authAttemptMapper, times(1)).toAuthAttemptPendingResponseDto(pendingResponse);
  }

  @Test
  @DisplayName(
      "POST /api/v1/auth-attempts/pending — audit includes demo MITM business narrative in"
          + " eventDetails")
  void pending_WhenDemoMitmTamperApplied_ShouldLogAuditWithNarrativeDetails() throws Exception {
    pendingResponse.setDemoMitmTamperApplied(true);
    pendingResponse.setDemoMitmPendingAuditNarrative(
        "Demo MITM (simulated): After signing, the contextual approval shown to the user was"
            + " altered on the wire. What the integration signed — title: (none), message: (none)."
            + " What the device received — title: (none), message: (none)."
            + " The authenticator rejects the request because the approval details no longer match"
            + " what was signed.");
    when(authAttemptMapper.toAuthAttemptPendingRequest(any(AuthAttemptPendingRequestDto.class)))
        .thenReturn(pendingRequest);
    when(authAttemptService.pending(any(AuthAttemptPendingRequest.class)))
        .thenReturn(pendingResponse);
    when(authAttemptMapper.toAuthAttemptPendingResponseDto(pendingResponse))
        .thenReturn(pendingResponseDto);

    String json = objectMapper.writeValueAsString(pendingRequestDto);

    mockMvc
        .perform(post(BASE_URL + "/pending").contentType(MediaType.APPLICATION_JSON).content(json))
        .andExpect(status().isOk());

    ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
    verify(auditLogService).log(captor.capture());
    AuditLog audit = captor.getValue();
    assertEquals("auth_attempt_pending_demo_mitm", audit.getEventAction());
    assertNotNull(audit.getEventDetails());
    assertTrue(audit.getEventDetails().startsWith("Demo MITM (simulated):"));
    assertTrue(audit.getEventDetails().contains("What the integration signed"));
    assertTrue(audit.getEventDetails().contains("contextual approval"));
  }

  @Test
  @DisplayName("POST /api/v1/auth-attempts/pending - Should return 204 when no pending requests")
  void pending_WhenNoPendingRequests_ShouldReturn204() throws Exception {
    // Arrange
    when(authAttemptMapper.toAuthAttemptPendingRequest(any(AuthAttemptPendingRequestDto.class)))
        .thenReturn(pendingRequest);
    when(authAttemptService.pending(any(AuthAttemptPendingRequest.class)))
        .thenThrow(new NoPendingAuthAttemptException("No pending requests"));

    String json = objectMapper.writeValueAsString(pendingRequestDto);

    // Act & Assert
    mockMvc
        .perform(post(BASE_URL + "/pending").contentType(MediaType.APPLICATION_JSON).content(json))
        .andExpect(status().isNoContent());

    // Verify service interactions
    verify(authAttemptService, times(1)).pending(any(AuthAttemptPendingRequest.class));
  }

  @Test
  @DisplayName("POST /api/v1/auth-attempts/pending - Should return 400 on invalid request")
  void pending_WhenInvalidRequest_ShouldReturn400() throws Exception {
    // Arrange
    when(authAttemptMapper.toAuthAttemptPendingRequest(any(AuthAttemptPendingRequestDto.class)))
        .thenReturn(pendingRequest);
    when(authAttemptService.pending(any(AuthAttemptPendingRequest.class)))
        .thenThrow(new AuthAttemptRequestFailedException("Invalid enrollment or signature"));

    String json = objectMapper.writeValueAsString(pendingRequestDto);

    // Act & Assert
    mockMvc
        .perform(post(BASE_URL + "/pending").contentType(MediaType.APPLICATION_JSON).content(json))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value(AuthApiProblemCatalog.TYPE_AUTH_ATTEMPT_BINDING_FAILED))
        .andExpect(jsonPath("$.title").value(AuthApiProblemCatalog.TITLE_BAD_REQUEST))
        .andExpect(jsonPath("$.detail").value(AuthApiProblemCatalog.DETAIL_AUTH_ATTEMPT_FAILED));

    // Verify service interactions
    verify(authAttemptService, times(1)).pending(any(AuthAttemptPendingRequest.class));
  }

  @Test
  @DisplayName("POST /api/v1/auth-attempts/pending - Should return 409 on state conflict")
  void pending_WhenStateConflict_ShouldReturn409() throws Exception {
    // Arrange
    when(authAttemptMapper.toAuthAttemptPendingRequest(any(AuthAttemptPendingRequestDto.class)))
        .thenReturn(pendingRequest);
    when(authAttemptService.pending(any(AuthAttemptPendingRequest.class)))
        .thenThrow(new AuthAttemptStateConflictException("Auth attempt already read"));

    String json = objectMapper.writeValueAsString(pendingRequestDto);

    // Act & Assert
    mockMvc
        .perform(post(BASE_URL + "/pending").contentType(MediaType.APPLICATION_JSON).content(json))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value(AuthApiProblemCatalog.TYPE_AUTH_ATTEMPT_STATE_CONFLICT))
        .andExpect(jsonPath("$.title").value(AuthApiProblemCatalog.TITLE_CONFLICT))
        .andExpect(
            jsonPath("$.detail").value(AuthApiProblemCatalog.DETAIL_AUTH_ATTEMPT_STATE_CONFLICT));

    // Verify service interactions
    verify(authAttemptService, times(1)).pending(any(AuthAttemptPendingRequest.class));
  }

  // ===== RESPOND ENDPOINT TESTS =====

  @Test
  @DisplayName(
      "POST /api/v1/auth-attempts/respond - Should return 200 when response submitted successfully")
  void respond_WhenResponseSubmitted_ShouldReturn200() throws Exception {
    // Arrange
    when(authAttemptMapper.toAuthAttemptRespondRequest(any(AuthAttemptRespondRequestDto.class)))
        .thenReturn(respondRequest);
    when(authAttemptService.respond(any(AuthAttemptRespondRequest.class)))
        .thenReturn(respondResponse);
    when(authAttemptMapper.toAuthAttemptRespondResponseDto(respondResponse))
        .thenReturn(respondResponseDto);

    String json = objectMapper.writeValueAsString(respondRequestDto);

    // Act & Assert
    mockMvc
        .perform(post(BASE_URL + "/respond").contentType(MediaType.APPLICATION_JSON).content(json))
        .andExpect(status().isOk());

    // Verify service interactions
    verify(authAttemptMapper, times(1))
        .toAuthAttemptRespondRequest(any(AuthAttemptRespondRequestDto.class));
    verify(authAttemptService, times(1)).respond(any(AuthAttemptRespondRequest.class));
    verify(authAttemptMapper, times(1)).toAuthAttemptRespondResponseDto(respondResponse);
  }

  @Test
  @DisplayName(
      "POST /api/v1/auth-attempts/respond - Should return 400 when service throws legacy "
          + "IllegalArgumentException")
  void respond_WhenServiceThrowsLegacyIllegalArgument_ShouldReturn400() throws Exception {
    when(authAttemptMapper.toAuthAttemptRespondRequest(any(AuthAttemptRespondRequestDto.class)))
        .thenReturn(respondRequest);
    when(authAttemptService.respond(any(AuthAttemptRespondRequest.class)))
        .thenThrow(new IllegalArgumentException("legacy validation"));

    String json = objectMapper.writeValueAsString(respondRequestDto);

    mockMvc
        .perform(post(BASE_URL + "/respond").contentType(MediaType.APPLICATION_JSON).content(json))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value(AuthApiProblemCatalog.TYPE_VALIDATION_FAILED))
        .andExpect(jsonPath("$.title").value(AuthApiProblemCatalog.TITLE_BAD_REQUEST));

    verify(authAttemptService, times(1)).respond(any(AuthAttemptRespondRequest.class));
  }

  @Test
  @DisplayName(
      "POST /api/v1/auth-attempts/respond - Should return 400 when service throws "
          + "AuthAttemptRequestFailedException")
  void respond_WhenServiceThrowsAuthAttemptRequestFailed_ShouldReturn400() throws Exception {
    // Arrange
    when(authAttemptMapper.toAuthAttemptRespondRequest(any(AuthAttemptRespondRequestDto.class)))
        .thenReturn(respondRequest);
    when(authAttemptService.respond(any(AuthAttemptRespondRequest.class)))
        .thenThrow(new AuthAttemptRequestFailedException("Invalid signature or response data"));

    String json = objectMapper.writeValueAsString(respondRequestDto);

    // Act & Assert
    mockMvc
        .perform(post(BASE_URL + "/respond").contentType(MediaType.APPLICATION_JSON).content(json))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value(AuthApiProblemCatalog.TYPE_AUTH_ATTEMPT_BINDING_FAILED))
        .andExpect(jsonPath("$.title").value(AuthApiProblemCatalog.TITLE_BAD_REQUEST))
        .andExpect(jsonPath("$.detail").value(AuthApiProblemCatalog.DETAIL_AUTH_ATTEMPT_FAILED));

    // Verify service interactions
    verify(authAttemptService, times(1)).respond(any(AuthAttemptRespondRequest.class));
  }

  @Test
  @DisplayName("POST /api/v1/auth-attempts/respond - Should return 409 on state conflict")
  void respond_WhenStateConflict_ShouldReturn409() throws Exception {
    // Arrange
    when(authAttemptMapper.toAuthAttemptRespondRequest(any(AuthAttemptRespondRequestDto.class)))
        .thenReturn(respondRequest);
    when(authAttemptService.respond(any(AuthAttemptRespondRequest.class)))
        .thenThrow(new AuthAttemptStateConflictException("Auth attempt already responded"));

    String json = objectMapper.writeValueAsString(respondRequestDto);

    // Act & Assert
    mockMvc
        .perform(post(BASE_URL + "/respond").contentType(MediaType.APPLICATION_JSON).content(json))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value(AuthApiProblemCatalog.TYPE_AUTH_ATTEMPT_STATE_CONFLICT))
        .andExpect(jsonPath("$.title").value(AuthApiProblemCatalog.TITLE_CONFLICT))
        .andExpect(
            jsonPath("$.detail").value(AuthApiProblemCatalog.DETAIL_AUTH_ATTEMPT_STATE_CONFLICT));

    // Verify service interactions
    verify(authAttemptService, times(1)).respond(any(AuthAttemptRespondRequest.class));
  }
}
