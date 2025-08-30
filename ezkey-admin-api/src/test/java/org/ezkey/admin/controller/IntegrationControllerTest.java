/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: IntegrationControllerTest
 * Description: Unit tests for IntegrationController REST endpoints.
 */

package org.ezkey.admin.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.ezkey.exception.ResourceNotFoundException;
import org.ezkey.integration.domain.IntegrationCreateRequest;
import org.ezkey.integration.domain.IntegrationCreateResponse;
import org.ezkey.integration.domain.entity.Integration;
import org.ezkey.integration.dto.IntegrationCreateRequestDto;
import org.ezkey.integration.dto.IntegrationCreateResponseDto;
import org.ezkey.integration.dto.IntegrationResponseDto;
import org.ezkey.integration.mapper.IntegrationControllerMapper;
import org.ezkey.integration.service.IntegrationService;
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
 * Unit tests for {@link IntegrationController}.
 * <p>
 * This test class provides comprehensive coverage of the IntegrationController
 * REST endpoints using Spring Boot's MockMvc framework. It follows Spring Boot
 * testing best practices with proper mocking of dependencies and validation
 * of HTTP responses, status codes, and JSON content.
 * </p>
 *
 * <p>
 * <b>Test Coverage:</b>
 * <ul>
 * <li>GET /api/v1/integrations - List all integrations</li>
 * <li>GET /api/v1/integrations/{id} - Get integration by ID (success and not found)</li>
 * <li>POST /api/v1/integrations - Create new integration</li>
 * <li>DELETE /api/v1/integrations/{id} - Delete integration (success and not found)</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Testing Approach:</b>
 * Uses @WebMvcTest for focused controller testing with mocked dependencies.
 * Validates HTTP status codes, response headers, JSON structure, and proper
 * service layer interactions.
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
 * @see IntegrationController
 * @see IntegrationService
 * @see IntegrationControllerMapper
 */
@WebMvcTest(IntegrationController.class)
@DisplayName("Integration Controller Tests")
class IntegrationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private IntegrationService integrationService;

    @MockBean
    private IntegrationControllerMapper integrationMapper;

    private Integration testIntegration;
    private IntegrationResponseDto testResponseDto;
    private IntegrationCreateRequestDto testCreateRequestDto;
    private IntegrationCreateRequest testCreateRequest;
    private IntegrationCreateResponse testCreateResponse;
    private IntegrationCreateResponseDto testCreateResponseDto;

    /**
     * Sets up test data before each test method.
     * Creates sample Integration entities and DTOs for consistent testing.
     */
    @BeforeEach
    void setUp() {
        // Create test Integration entity
        testIntegration = new Integration();
        testIntegration.setId(1);
        testIntegration.setLogo("https://example.com/logo.png");
        testIntegration.setActive(true);
        testIntegration.setCreatedAt(LocalDateTime.of(2025, 1, 15, 10, 30, 0));

        // Create test response DTO
        testResponseDto = new IntegrationResponseDto();
        testResponseDto.setId(1);
        testResponseDto.setLogo("https://example.com/logo.png");
        testResponseDto.setActive(true);
        testResponseDto.setCreatedAt(LocalDateTime.of(2025, 1, 15, 10, 30, 0));

        // Create test create request DTO
        testCreateRequestDto = new IntegrationCreateRequestDto();
        testCreateRequestDto.setLogo("https://example.com/new-logo.png");

        // Create test create request domain object
        testCreateRequest = new IntegrationCreateRequest();
        testCreateRequest.setLogo("https://example.com/new-logo.png");

        // Create test create response
        testCreateResponse = new IntegrationCreateResponse();
        testCreateResponse.setId(2);

        // Create test create response DTO
        testCreateResponseDto = new IntegrationCreateResponseDto();
        testCreateResponseDto.setId(2);
    }

    /**
     * Tests the GET /api/v1/integrations endpoint for retrieving all integrations.
     * Verifies that the endpoint returns HTTP 200 with a properly formatted JSON array.
     */
    @Test
    @DisplayName("GET /api/v1/integrations - Should return all integrations")
    void getAllIntegrations_ShouldReturnAllIntegrations() throws Exception {
        // Arrange
        List<Integration> integrations = Arrays.asList(testIntegration);
        List<IntegrationResponseDto> responseDtos = Arrays.asList(testResponseDto);

        when(integrationService.getAll()).thenReturn(integrations);
        when(integrationMapper.toResponseList(integrations)).thenReturn(responseDtos);

        // Act & Assert
        mockMvc.perform(get("/api/v1/integrations")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].logo").value("https://example.com/logo.png"))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[0].createdAt").value("2025-01-15T10:30:00"));

        // Verify service interactions
        verify(integrationService, times(1)).getAll();
        verify(integrationMapper, times(1)).toResponseList(integrations);
    }

    /**
     * Tests the GET /api/v1/integrations/{id} endpoint for successful retrieval.
     * Verifies that the endpoint returns HTTP 200 with the correct integration data.
     */
    @Test
    @DisplayName("GET /api/v1/integrations/{id} - Should return integration when found")
    void getIntegrationById_WhenFound_ShouldReturnIntegration() throws Exception {
        // Arrange
        Integer integrationId = 1;
        when(integrationService.getById(integrationId)).thenReturn(Optional.of(testIntegration));
        when(integrationMapper.toResponse(testIntegration)).thenReturn(testResponseDto);

        // Act & Assert
        mockMvc.perform(get("/api/v1/integrations/{id}", integrationId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.logo").value("https://example.com/logo.png"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.createdAt").value("2025-01-15T10:30:00"));

        // Verify service interactions
        verify(integrationService, times(1)).getById(integrationId);
        verify(integrationMapper, times(1)).toResponse(testIntegration);
    }

    /**
     * Tests the GET /api/v1/integrations/{id} endpoint when integration is not found.
     * Verifies that the endpoint returns HTTP 404 when the integration doesn't exist.
     */
    @Test
    @DisplayName("GET /api/v1/integrations/{id} - Should return 404 when not found")
    void getIntegrationById_WhenNotFound_ShouldReturn404() throws Exception {
        // Arrange
        Integer integrationId = 999;
        when(integrationService.getById(integrationId)).thenReturn(Optional.empty());

        // Act & Assert
        mockMvc.perform(get("/api/v1/integrations/{id}", integrationId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        // Verify service interactions
        verify(integrationService, times(1)).getById(integrationId);
        verify(integrationMapper, times(0)).toResponse(any());
    }

    /**
     * Tests the POST /api/v1/integrations endpoint for creating new integrations.
     * Verifies that the endpoint returns HTTP 201 with Location header and created integration data.
     */
    @Test
    @DisplayName("POST /api/v1/integrations - Should create integration and return 201")
    void createIntegration_ShouldCreateAndReturn201() throws Exception {
        // Arrange
        when(integrationMapper.toCreateRequest(testCreateRequestDto)).thenReturn(testCreateRequest);
        when(integrationService.createIntegration(testCreateRequest)).thenReturn(testCreateResponse);
        when(integrationMapper.toCreateResponseDto(testCreateResponse)).thenReturn(testCreateResponseDto);

        // Act & Assert
        mockMvc.perform(post("/api/v1/integrations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testCreateRequestDto)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/integrations/2"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(2));

        // Verify service interactions
        verify(integrationMapper, times(1)).toCreateRequest(testCreateRequestDto);
        verify(integrationService, times(1)).createIntegration(testCreateRequest);
        verify(integrationMapper, times(1)).toCreateResponseDto(testCreateResponse);
    }

    /**
     * Tests the DELETE /api/v1/integrations/{id} endpoint for successful deletion.
     * Verifies that the endpoint returns HTTP 204 when integration is successfully deleted.
     */
    @Test
    @DisplayName("DELETE /api/v1/integrations/{id} - Should delete integration and return 204")
    void deleteIntegration_WhenFound_ShouldReturn204() throws Exception {
        // Arrange
        Integer integrationId = 1;
        when(integrationService.getById(integrationId)).thenReturn(Optional.of(testIntegration));
        doNothing().when(integrationService).delete(integrationId);

        // Act & Assert
        mockMvc.perform(delete("/api/v1/integrations/{id}", integrationId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        // Verify service interactions
        verify(integrationService, times(1)).getById(integrationId);
        verify(integrationService, times(1)).delete(integrationId);
    }

    /**
     * Tests the DELETE /api/v1/integrations/{id} endpoint when integration is not found.
     * Verifies that the endpoint returns HTTP 404 when the integration doesn't exist.
     */
    @Test
    @DisplayName("DELETE /api/v1/integrations/{id} - Should return 404 when not found")
    void deleteIntegration_WhenNotFound_ShouldReturn404() throws Exception {
        // Arrange
        Integer integrationId = 999;
        when(integrationService.getById(integrationId)).thenReturn(Optional.empty());

        // Act & Assert
        mockMvc.perform(delete("/api/v1/integrations/{id}", integrationId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        // Verify service interactions
        verify(integrationService, times(1)).getById(integrationId);
        verify(integrationService, times(0)).delete(anyInt());
    }
}