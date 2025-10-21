/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: EnrollmentControllerTest
 * Description: Unit tests for EnrollmentController REST endpoints with API key restriction tests.
 */

package org.ezkey.admin.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.List;
import org.ezkey.admin.config.SecurityConfig;
import org.ezkey.admin.security.IntegrationAccessControl;
import org.ezkey.enrollment.domain.EnrollmentCreateResponse;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.dto.EnrollmentCreateRequestDto;
import org.ezkey.enrollment.dto.EnrollmentCreateResponseDto;
import org.ezkey.enrollment.dto.EnrollmentResponseDto;
import org.ezkey.enrollment.mapper.EnrollmentAdminMapper;
import org.ezkey.enrollment.service.EnrollmentService;
import org.ezkey.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Unit tests for EnrollmentController in admin-api.
 *
 * <p>This test class validates enrollment management REST endpoints and ensures API keys are
 * properly restricted from accessing enrollment operations while bearer tokens (admin users)
 * continue to have full access.
 *
 * <p><b>Critical Test Coverage:</b>
 *
 * <ul>
 *   <li>API key restriction - all enrollment endpoints return 403 Forbidden
 *   <li>Bearer token access - all enrollment endpoints work normally
 *   <li>GET /api/v1/enrollments - List all enrollments
 *   <li>GET /api/v1/enrollments/{id} - Get enrollment by ID
 *   <li>POST /api/v1/enrollments - Create new enrollment
 *   <li>DELETE /api/v1/enrollments/{id} - Delete enrollment
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see EnrollmentController
 * @see IntegrationAccessControl
 */
@WebMvcTest(controllers = EnrollmentController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(SecurityConfig.class)
@DisplayName("EnrollmentController Tests")
class EnrollmentControllerTest {

    private static final String BASE_URL = "/api/v1/enrollments";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EnrollmentService enrollmentService;

    @MockBean
    private EnrollmentAdminMapper enrollmentMapper;

    @MockBean
    private org.ezkey.audit.service.AuditLogService auditLogService;

    @MockBean
    private IntegrationAccessControl accessControl;

    // Mock security filters required by SecurityConfig
    @MockBean
    private org.ezkey.admin.security.AdminTokenAuthenticationFilter
            adminTokenAuthenticationFilter;

    @MockBean
    private org.ezkey.admin.security.ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;

    private Enrollment testEnrollment;
    private EnrollmentResponseDto testEnrollmentDto;
    private EnrollmentCreateRequestDto createRequestDto;
    private EnrollmentCreateResponse createResponse;
    private EnrollmentCreateResponseDto createResponseDto;

    @BeforeEach
    void setUp() {
        // Clear security context before each test
        SecurityContextHolder.clearContext();

        // Setup test enrollment
        testEnrollment = new Enrollment();
        testEnrollment.setEnrollmentId(123);
        testEnrollment.setIntegrationId(1);
        testEnrollment.setEnrollmentName("Test Enrollment");
        testEnrollment.setActive(true);

        testEnrollmentDto = new EnrollmentResponseDto();
        testEnrollmentDto.setEnrollmentId(123);
        testEnrollmentDto.setIntegrationId(1);
        testEnrollmentDto.setEnrollmentName("Test Enrollment");
        testEnrollmentDto.setDevicePublicKey("device-public-key");
        testEnrollmentDto.setEnrollmentActive(true);

        createRequestDto = new EnrollmentCreateRequestDto();
        createRequestDto.setIntegrationId(1);
        createRequestDto.setName("New Enrollment");
        createRequestDto.setAuthAttemptChallengeRequired(false);

        createResponse = new EnrollmentCreateResponse();
        createResponse.setEnrollmentId(123);
        createResponse.setEnrollmentChallenge(154982);

        createResponseDto = new EnrollmentCreateResponseDto();
        createResponseDto.setEnrollmentId(123);
        createResponseDto.setEnrollmentChallenge(154982);
    }

    /**
     * Helper method to setup bearer token (admin) authentication.
     */
    private void setupBearerTokenAuth() {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                        "admin@example.com",
                        null,
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        // Mock accessControl to return false for API key check
        when(accessControl.isApiKey(any())).thenReturn(false);
    }

    /**
     * Helper method to setup API key authentication.
     */
    private void setupApiKeyAuth() {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                        "integration_123",
                        null,
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        // Mock accessControl to return true for API key check
        when(accessControl.isApiKey(any())).thenReturn(true);
    }

    @Nested
    @DisplayName("API Key Restriction Tests")
    class ApiKeyRestrictionTests {

        @Test
        @DisplayName("GET /api/v1/enrollments - Should return 403 when using API key")
        void getAll_WhenApiKey_ShouldReturn403() throws Exception {
            // Arrange
            setupApiKeyAuth();

            // Act & Assert
            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isForbidden());

            // Verify service was not called
            verify(enrollmentService, times(0)).getAll();
        }

        @Test
        @DisplayName("GET /api/v1/enrollments/{id} - Should return 403 when using API key")
        void getById_WhenApiKey_ShouldReturn403() throws Exception {
            // Arrange
            setupApiKeyAuth();

            // Act & Assert
            mockMvc.perform(get(BASE_URL + "/123"))
                    .andExpect(status().isForbidden());

            // Verify service was not called
            verify(enrollmentService, times(0)).getById(any());
        }

        @Test
        @DisplayName("POST /api/v1/enrollments - Should return 403 when using API key")
        void create_WhenApiKey_ShouldReturn403() throws Exception {
            // Arrange
            setupApiKeyAuth();
            String json = objectMapper.writeValueAsString(createRequestDto);

            // Act & Assert
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isForbidden());

            // Verify service was not called
            verify(enrollmentService, times(0)).create(any());
        }

        @Test
        @DisplayName("DELETE /api/v1/enrollments/{id} - Should return 403 when using API key")
        void delete_WhenApiKey_ShouldReturn403() throws Exception {
            // Arrange
            setupApiKeyAuth();

            // Act & Assert
            mockMvc.perform(delete(BASE_URL + "/123"))
                    .andExpect(status().isForbidden());

            // Verify service was not called
            verify(enrollmentService, times(0)).delete(any());
        }
    }

    @Nested
    @DisplayName("Bearer Token (Admin) Tests")
    class BearerTokenTests {

        @Test
        @DisplayName("GET /api/v1/enrollments - Should return 200 when using bearer token")
        void getAll_WhenBearerToken_ShouldReturn200() throws Exception {
            // Arrange
            setupBearerTokenAuth();
            when(enrollmentService.getAll()).thenReturn(List.of(testEnrollment));
            when(enrollmentMapper.toResponseList(any())).thenReturn(List.of(testEnrollmentDto));

            // Act & Assert
            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].enrollmentId").value(123));

            // Verify service was called
            verify(enrollmentService, times(1)).getAll();
        }

        @Test
        @DisplayName("GET /api/v1/enrollments/{id} - Should return 200 when using bearer token")
        void getById_WhenBearerToken_ShouldReturn200() throws Exception {
            // Arrange
            setupBearerTokenAuth();
            when(enrollmentService.getById(123)).thenReturn(testEnrollment);
            when(enrollmentMapper.toResponse(testEnrollment)).thenReturn(testEnrollmentDto);

            // Act & Assert
            mockMvc.perform(get(BASE_URL + "/123"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.enrollmentId").value(123));

            // Verify service was called
            verify(enrollmentService, times(1)).getById(123);
        }

        @Test
        @DisplayName("GET /api/v1/enrollments/{id} - Should return 404 when enrollment not found")
        void getById_WhenNotFound_ShouldReturn404() throws Exception {
            // Arrange
            setupBearerTokenAuth();
            when(enrollmentService.getById(999))
                    .thenThrow(new ResourceNotFoundException("Enrollment", 999));

            // Act & Assert
            mockMvc.perform(get(BASE_URL + "/999"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("POST /api/v1/enrollments - Should return 201 when using bearer token")
        void create_WhenBearerToken_ShouldReturn201() throws Exception {
            // Arrange
            setupBearerTokenAuth();
            when(enrollmentMapper.toCreateRequest(any())).thenReturn(
                    new org.ezkey.enrollment.domain.EnrollmentCreateRequest());
            when(enrollmentService.create(any())).thenReturn(createResponse);
            when(enrollmentMapper.toCreateResponseDto(any(EnrollmentCreateResponse.class)))
                    .thenReturn(createResponseDto);

            String json = objectMapper.writeValueAsString(createRequestDto);

            // Act & Assert
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.enrollmentId").value(123));

            // Verify service was called
            verify(enrollmentService, times(1)).create(any());
        }

        @Test
        @DisplayName("DELETE /api/v1/enrollments/{id} - Should return 204 when using bearer token")
        void delete_WhenBearerToken_ShouldReturn204() throws Exception {
            // Arrange
            setupBearerTokenAuth();
            when(enrollmentService.getById(123)).thenReturn(testEnrollment);

            // Act & Assert
            mockMvc.perform(delete(BASE_URL + "/123"))
                    .andExpect(status().isNoContent());

            // Verify service was called
            verify(enrollmentService, times(1)).delete(123);
        }

        @Test
        @DisplayName("DELETE /api/v1/enrollments/{id} - Should return 404 when not found")
        void delete_WhenNotFound_ShouldReturn404() throws Exception {
            // Arrange
            setupBearerTokenAuth();
            when(enrollmentService.getById(999))
                    .thenThrow(new ResourceNotFoundException("Enrollment", 999));

            // Act & Assert
            mockMvc.perform(delete(BASE_URL + "/999"))
                    .andExpect(status().isNotFound());
        }
    }
}
