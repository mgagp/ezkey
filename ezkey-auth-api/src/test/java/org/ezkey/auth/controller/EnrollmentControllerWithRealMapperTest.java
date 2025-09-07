/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: EnrollmentControllerWithRealMapperTest
 * Description: Critical integration tests using real MapStruct mapper for EnrollmentController.
 */

package org.ezkey.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.ezkey.enrollment.domain.EnrollmentVerifyRequest;
import org.ezkey.enrollment.domain.EnrollmentVerifyResponse;
import org.ezkey.enrollment.dto.EnrollmentVerifyRequestDto;
import org.ezkey.enrollment.mapper.EnrollmentAuthMapperImpl;
import org.ezkey.enrollment.service.EnrollmentService;
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
 * Critical integration tests using real MapStruct mapper for EnrollmentController.
 * <p>
 * This test class validates the critical mapping functionality between DTOs and domain objects
 * using the actual MapStruct implementation. It ensures that cryptographic enrollment data
 * and verification parameters are correctly transformed between the API layer and service layer.
 * </p>
 *
 * <p>
 * <b>Critical Mapping Validation:</b>
 * <ul>
 * <li>EnrollmentVerifyRequestDto ↔ EnrollmentVerifyRequest mapping</li>
 * <li>Cryptographic key propagation</li>
 * <li>Proof token signature handling</li>
 * <li>Enrollment ID mapping</li>
 * <li>Device public key handling</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Security Focus:</b> These tests ensure that critical security parameters
 * like cryptographic keys and proof token signatures are correctly mapped and
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
 * @see EnrollmentController
 * @see EnrollmentAuthMapperImpl
 * @see EnrollmentService
 */
@WebMvcTest(EnrollmentController.class)
@Import(EnrollmentAuthMapperImpl.class)
@DisplayName("Enrollment Controller Real Mapper Tests")
class EnrollmentControllerWithRealMapperTest {

    private static final String BASE_URL = "/api/v1/enrollments";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EnrollmentService enrollmentService;

    @Test
    @DisplayName("POST /api/v1/enrollments/verify - Real mapper smoke test")
    void verify_WithRealMapper_SmokeTest() throws Exception {
        // Arrange
        EnrollmentVerifyRequestDto dto = new EnrollmentVerifyRequestDto();
        dto.setEnrollmentId(123);
        dto.setDevicePublicKey("device-public-key-abc123");
        dto.setEnrollmentProofTokenSigned("proof-token-signature-def456");

        EnrollmentVerifyResponse response = new EnrollmentVerifyResponse();
        response.setActive(true);

        org.mockito.Mockito.when(enrollmentService.verify(any(EnrollmentVerifyRequest.class)))
            .thenReturn(response);

        String json = objectMapper.writeValueAsString(dto);

        // Act & Assert
        mockMvc.perform(post(BASE_URL + "/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isOk());

        // Verify mapping correctness
        ArgumentCaptor<EnrollmentVerifyRequest> captor = ArgumentCaptor.forClass(EnrollmentVerifyRequest.class);
        org.mockito.Mockito.verify(enrollmentService).verify(captor.capture());
        
        EnrollmentVerifyRequest mappedRequest = captor.getValue();
        if (mappedRequest.getEnrollmentId() == null || !mappedRequest.getEnrollmentId().equals(123)) {
            throw new AssertionError("EnrollmentId not mapped correctly. Expected 123, got: " + mappedRequest.getEnrollmentId());
        }
        if (!"device-public-key-abc123".equals(mappedRequest.getDevicePublicKey())) {
            throw new AssertionError("DevicePublicKey not mapped correctly. Expected 'device-public-key-abc123', got: " + mappedRequest.getDevicePublicKey());
        }
        if (!"proof-token-signature-def456".equals(mappedRequest.getEnrollmentProofTokenSigned())) {
            throw new AssertionError("EnrollmentProofTokenSigned not mapped correctly. Expected 'proof-token-signature-def456', got: " + mappedRequest.getEnrollmentProofTokenSigned());
        }
    }
}