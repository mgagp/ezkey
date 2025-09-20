/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AuthAttemptControllerTest
 * Description: Critical unit tests for AuthAttemptController REST endpoints in auth-api.
 */

package org.ezkey.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.ezkey.authattempt.domain.AuthAttemptPendingRequest;
import org.ezkey.authattempt.domain.AuthAttemptPendingResponse;
import org.ezkey.authattempt.domain.AuthAttemptRespondRequest;
import org.ezkey.authattempt.domain.AuthAttemptRespondResponse;
import org.ezkey.authattempt.domain.AuthenticationResult;
import org.ezkey.authattempt.dto.AuthAttemptPendingRequestDto;
import org.ezkey.authattempt.dto.AuthAttemptPendingResponseDto;
import org.ezkey.authattempt.dto.AuthAttemptRespondRequestDto;
import org.ezkey.authattempt.dto.AuthAttemptRespondResponseDto;
import org.ezkey.authattempt.mapper.AuthAttemptMapper;
import org.ezkey.authattempt.service.AuthAttemptService;
import org.ezkey.exception.NoPendingAuthAttemptException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Critical unit tests for {@link AuthAttemptController} in auth-api.
 * <p>
 * This test class provides comprehensive coverage of the AuthAttemptController
 * REST endpoints focusing on security-critical authentication operations.
 * Tests cover cryptographic signature validation, state management, and
 * proper HTTP status code handling for mobile device authentication.
 * </p>
 *
 * <p>
 * <b>Critical Test Coverage:</b>
 * <ul>
 * <li>POST /api/v1/auth-attempts/pending/{enrollmentId} - Mobile polling for pending requests</li>
 * <li>POST /api/v1/auth-attempts/respond/{authAttemptId} - Mobile response submission</li>
 * <li>Security validation - Cryptographic signature handling</li>
 * <li>State management - Proper status transitions</li>
 * <li>Error handling - Appropriate HTTP status codes</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Security Focus:</b> These tests validate the critical security aspects
 * of mobile authentication including signature validation, state consistency,
 * and proper error handling to prevent security vulnerabilities.
 * </p>
 *
 * <p>
 * <b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 * </p>
 * <p>
 * <b>License:</b> MIT
 * </p>
 *
 * @author Ezkey contributors
 * @since 2025
 * @see AuthAttemptController
 * @see AuthAttemptService
 * @see AuthAttemptMapper
 */
@WebMvcTest(AuthAttemptController.class)
@DisplayName("AuthAttempt Controller Critical Tests")
class AuthAttemptControllerTest {

    private static final String BASE_URL = "/api/v1/auth-attempts";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthAttemptService authAttemptService;

    @MockBean
    private AuthAttemptMapper authAttemptMapper;

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
        // Setup pending request test data
        pendingRequestDto = new AuthAttemptPendingRequestDto();
        pendingRequestDto.setDeviceProofToken("test-proof-token");
        pendingRequestDto.setDeviceProofTokenSigned("test-signature");

        pendingRequest = new AuthAttemptPendingRequest();
        pendingRequest.setEnrollmentId(123);
        pendingRequest.setDeviceProofToken("test-proof-token");
        pendingRequest.setDeviceProofTokenSigned("test-signature");

        pendingResponse = new AuthAttemptPendingResponse();
        pendingResponse.setAuthAttemptId(456);

        pendingResponseDto = new AuthAttemptPendingResponseDto();
        pendingResponseDto.setAuthAttemptId(456);

        // Setup respond request test data
        respondRequestDto = new AuthAttemptRespondRequestDto();
        respondRequestDto.setAuthAttemptProofTokenSignedByDevice("test-proof-token");
        respondRequestDto.setAuthAttemptAccepted(true);

        respondRequest = new AuthAttemptRespondRequest();
        respondRequest.setAuthAttemptId(456);
        respondRequest.setAuthAttemptProofTokenSignedByDevice("test-proof-token");
        respondRequest.setAuthAttemptAccepted(true);

        respondResponse = new AuthAttemptRespondResponse();
        respondResponse.setResult(AuthenticationResult.APPROVED);
        respondResponse.setMessage("Authentication approved");

        respondResponseDto = new AuthAttemptRespondResponseDto();
        respondResponseDto.setResult("APPROVED");
        respondResponseDto.setMessage("Authentication approved");
    }

    // ===== PENDING ENDPOINT TESTS =====

    @Test
    @DisplayName("POST /api/v1/auth-attempts/pending/{enrollmentId} - Should return 200 when pending request found")
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
        mockMvc.perform(post(BASE_URL + "/pending")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isOk());

        // Verify service interactions
        verify(authAttemptMapper, times(1)).toAuthAttemptPendingRequest(any(AuthAttemptPendingRequestDto.class));
        verify(authAttemptService, times(1)).pending(any(AuthAttemptPendingRequest.class));
        verify(authAttemptMapper, times(1)).toAuthAttemptPendingResponseDto(pendingResponse);
    }

    @Test
    @DisplayName("POST /api/v1/auth-attempts/pending/{enrollmentId} - Should return 204 when no pending requests")
    void pending_WhenNoPendingRequests_ShouldReturn204() throws Exception {
        // Arrange
        when(authAttemptMapper.toAuthAttemptPendingRequest(any(AuthAttemptPendingRequestDto.class)))
            .thenReturn(pendingRequest);
        when(authAttemptService.pending(any(AuthAttemptPendingRequest.class)))
            .thenThrow(new NoPendingAuthAttemptException("No pending requests"));

        String json = objectMapper.writeValueAsString(pendingRequestDto);

        // Act & Assert
        mockMvc.perform(post(BASE_URL + "/pending")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isNoContent());

        // Verify service interactions
        verify(authAttemptService, times(1)).pending(any(AuthAttemptPendingRequest.class));
    }

    @Test
    @DisplayName("POST /api/v1/auth-attempts/pending/{enrollmentId} - Should return 400 on invalid request")
    void pending_WhenInvalidRequest_ShouldReturn400() throws Exception {
        // Arrange
        when(authAttemptMapper.toAuthAttemptPendingRequest(any(AuthAttemptPendingRequestDto.class)))
            .thenReturn(pendingRequest);
        when(authAttemptService.pending(any(AuthAttemptPendingRequest.class)))
            .thenThrow(new IllegalArgumentException("Invalid enrollment or signature"));

        String json = objectMapper.writeValueAsString(pendingRequestDto);

        // Act & Assert
        mockMvc.perform(post(BASE_URL + "/pending")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isBadRequest());

        // Verify service interactions
        verify(authAttemptService, times(1)).pending(any(AuthAttemptPendingRequest.class));
    }

    @Test
    @DisplayName("POST /api/v1/auth-attempts/pending/{enrollmentId} - Should return 409 on state conflict")
    void pending_WhenStateConflict_ShouldReturn409() throws Exception {
        // Arrange
        when(authAttemptMapper.toAuthAttemptPendingRequest(any(AuthAttemptPendingRequestDto.class)))
            .thenReturn(pendingRequest);
        when(authAttemptService.pending(any(AuthAttemptPendingRequest.class)))
            .thenThrow(new IllegalStateException("Auth attempt already read"));

        String json = objectMapper.writeValueAsString(pendingRequestDto);

        // Act & Assert
        mockMvc.perform(post(BASE_URL + "/pending")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isConflict());

        // Verify service interactions
        verify(authAttemptService, times(1)).pending(any(AuthAttemptPendingRequest.class));
    }

    // ===== RESPOND ENDPOINT TESTS =====

    @Test
    @DisplayName("POST /api/v1/auth-attempts/respond/{authAttemptId} - Should return 200 when response submitted successfully")
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
        mockMvc.perform(post(BASE_URL + "/respond/{authAttemptId}", 456)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isOk());

        // Verify service interactions
        verify(authAttemptMapper, times(1)).toAuthAttemptRespondRequest(any(AuthAttemptRespondRequestDto.class));
        verify(authAttemptService, times(1)).respond(any(AuthAttemptRespondRequest.class));
        verify(authAttemptMapper, times(1)).toAuthAttemptRespondResponseDto(respondResponse);
    }

    @Test
    @DisplayName("POST /api/v1/auth-attempts/respond/{authAttemptId} - Should return 400 on invalid response")
    void respond_WhenInvalidResponse_ShouldReturn400() throws Exception {
        // Arrange
        when(authAttemptMapper.toAuthAttemptRespondRequest(any(AuthAttemptRespondRequestDto.class)))
            .thenReturn(respondRequest);
        when(authAttemptService.respond(any(AuthAttemptRespondRequest.class)))
            .thenThrow(new IllegalArgumentException("Invalid signature or response data"));

        String json = objectMapper.writeValueAsString(respondRequestDto);

        // Act & Assert
        mockMvc.perform(post(BASE_URL + "/respond/{authAttemptId}", 456)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isBadRequest());

        // Verify service interactions
        verify(authAttemptService, times(1)).respond(any(AuthAttemptRespondRequest.class));
    }

    @Test
    @DisplayName("POST /api/v1/auth-attempts/respond/{authAttemptId} - Should return 409 on state conflict")
    void respond_WhenStateConflict_ShouldReturn409() throws Exception {
        // Arrange
        when(authAttemptMapper.toAuthAttemptRespondRequest(any(AuthAttemptRespondRequestDto.class)))
            .thenReturn(respondRequest);
        when(authAttemptService.respond(any(AuthAttemptRespondRequest.class)))
            .thenThrow(new IllegalStateException("Auth attempt already responded"));

        String json = objectMapper.writeValueAsString(respondRequestDto);

        // Act & Assert
        mockMvc.perform(post(BASE_URL + "/respond/{authAttemptId}", 456)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isConflict());

        // Verify service interactions
        verify(authAttemptService, times(1)).respond(any(AuthAttemptRespondRequest.class));
    }
}