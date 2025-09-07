/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AuthAttemptControllerWithRealMapperTest
 * Description: Critical integration tests using real MapStruct mapper for AuthAttemptController.
 */

package org.ezkey.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.ezkey.authattempt.domain.AuthAttemptPendingRequest;
import org.ezkey.authattempt.domain.AuthAttemptPendingResponse;
import org.ezkey.authattempt.domain.AuthAttemptRespondRequest;
import org.ezkey.authattempt.domain.AuthAttemptRespondResponse;
import org.ezkey.authattempt.domain.AuthenticationResult;
import org.ezkey.authattempt.dto.AuthAttemptPendingRequestDto;
import org.ezkey.authattempt.dto.AuthAttemptRespondRequestDto;
import org.ezkey.authattempt.mapper.AuthAttemptMapperImpl;
import org.ezkey.authattempt.service.AuthAttemptService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Critical integration tests using real MapStruct mapper for AuthAttemptController.
 * <p>
 * This test class validates the critical mapping functionality between DTOs and domain objects
 * using the actual MapStruct implementation. It ensures that cryptographic data and
 * authentication parameters are correctly transformed between the API layer and service layer.
 * </p>
 *
 * <p>
 * <b>Critical Mapping Validation:</b>
 * <ul>
 * <li>AuthAttemptPendingRequestDto ↔ AuthAttemptPendingRequest mapping</li>
 * <li>AuthAttemptRespondRequestDto ↔ AuthAttemptRespondRequest mapping</li>
 * <li>Cryptographic signature propagation</li>
 * <li>Device proof token handling</li>
 * <li>Enrollment ID and AuthAttempt ID mapping</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Security Focus:</b> These tests ensure that critical security parameters
 * like cryptographic signatures and proof tokens are correctly mapped and
 * not lost or corrupted during the transformation process.
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
 * @see AuthAttemptMapperImpl
 * @see AuthAttemptService
 */
@WebMvcTest(AuthAttemptController.class)
@Import(AuthAttemptMapperImpl.class)
@DisplayName("AuthAttempt Controller Real Mapper Tests")
class AuthAttemptControllerWithRealMapperTest {

    private static final String BASE_URL = "/api/v1/auth-attempts";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthAttemptService authAttemptService;

    @Test
    @DisplayName("POST /api/v1/auth-attempts/pending/{enrollmentId} - Real mapper smoke test")
    void pending_WithRealMapper_SmokeTest() throws Exception {
        // Arrange
        AuthAttemptPendingRequestDto dto = new AuthAttemptPendingRequestDto();
        dto.setDeviceProofToken("test-proof-token-123");
        dto.setDeviceProofTokenSigned("test-signature-456");

        AuthAttemptPendingResponse response = new AuthAttemptPendingResponse();
        response.setAuthAttemptId(789);

        org.mockito.Mockito.when(authAttemptService.pending(any(AuthAttemptPendingRequest.class)))
            .thenReturn(response);

        String json = objectMapper.writeValueAsString(dto);

        // Act & Assert
        mockMvc.perform(post(BASE_URL + "/pending/{enrollmentId}", 123)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isOk());

        // Verify mapping correctness
        ArgumentCaptor<AuthAttemptPendingRequest> captor = ArgumentCaptor.forClass(AuthAttemptPendingRequest.class);
        org.mockito.Mockito.verify(authAttemptService).pending(captor.capture());
        
        AuthAttemptPendingRequest mappedRequest = captor.getValue();
        if (mappedRequest.getEnrollmentId() == null || !mappedRequest.getEnrollmentId().equals(123)) {
            throw new AssertionError("EnrollmentId not mapped correctly. Expected 123, got: " + mappedRequest.getEnrollmentId());
        }
        if (!"test-proof-token-123".equals(mappedRequest.getDeviceProofToken())) {
            throw new AssertionError("DeviceProofToken not mapped correctly. Expected 'test-proof-token-123', got: " + mappedRequest.getDeviceProofToken());
        }
        if (!"test-signature-456".equals(mappedRequest.getDeviceProofTokenSigned())) {
            throw new AssertionError("DeviceProofTokenSigned not mapped correctly. Expected 'test-signature-456', got: " + mappedRequest.getDeviceProofTokenSigned());
        }
    }

    @Test
    @DisplayName("POST /api/v1/auth-attempts/respond/{authAttemptId} - Real mapper smoke test")
    void respond_WithRealMapper_SmokeTest() throws Exception {
        // Arrange
        AuthAttemptRespondRequestDto dto = new AuthAttemptRespondRequestDto();
        dto.setAuthAttemptProofTokenSignedByDevice("test-proof-token-respond");
        dto.setAuthAttemptAccepted(true);

        AuthAttemptRespondResponse response = new AuthAttemptRespondResponse();
        response.setResult(AuthenticationResult.APPROVED);
        response.setMessage("Authentication approved");

        org.mockito.Mockito.when(authAttemptService.respond(any(AuthAttemptRespondRequest.class)))
            .thenReturn(response);

        String json = objectMapper.writeValueAsString(dto);

        // Act & Assert
        mockMvc.perform(post(BASE_URL + "/respond/{authAttemptId}", 456)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isOk());

        // Verify mapping correctness
        ArgumentCaptor<AuthAttemptRespondRequest> captor = ArgumentCaptor.forClass(AuthAttemptRespondRequest.class);
        org.mockito.Mockito.verify(authAttemptService).respond(captor.capture());
        
        AuthAttemptRespondRequest mappedRequest = captor.getValue();
        if (mappedRequest.getAuthAttemptId() == null || !mappedRequest.getAuthAttemptId().equals(456)) {
            throw new AssertionError("AuthAttemptId not mapped correctly. Expected 456, got: " + mappedRequest.getAuthAttemptId());
        }
        if (!"test-proof-token-respond".equals(mappedRequest.getAuthAttemptProofTokenSignedByDevice())) {
            throw new AssertionError("AuthAttemptProofTokenSignedByDevice not mapped correctly. Expected 'test-proof-token-respond', got: " + mappedRequest.getAuthAttemptProofTokenSignedByDevice());
        }
        if (!Boolean.TRUE.equals(mappedRequest.getAuthAttemptAccepted())) {
            throw new AssertionError("AuthAttemptAccepted not mapped correctly. Expected true, got: " + mappedRequest.getAuthAttemptAccepted());
        }
    }
}