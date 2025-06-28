/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: IntegrationControllerTest
 * Description: Minimal unit test for IntegrationController GET by ID endpoint.
 */

package org.ezkey.integration.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import org.ezkey.integration.domain.entity.EzkeyIntegration;
import org.ezkey.integration.dto.response.IntegrationResponse;
import org.ezkey.integration.mapper.IntegrationMapper;
import org.ezkey.integration.service.EzkeyIntegrationService;

/**
 * Minimal unit test for IntegrationController.
 * <p>
 * Tests only the GET by ID endpoint in happy path scenario.
 * </p>
 *
 * @author Ezkey contributors
 * @since 2025
 */
@WebMvcTest(IntegrationController.class)
@DisplayName("Integration Controller Test")
class IntegrationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EzkeyIntegrationService service;

    @MockBean
    private IntegrationMapper mapper;

    private EzkeyIntegration testIntegration;
    private IntegrationResponse testResponse;

    @BeforeEach
    void setUp() {
        // Setup test integration entity
        testIntegration = new EzkeyIntegration();
        testIntegration.setId(1);
        testIntegration.setCode("TEST_INTEGRATION");
        testIntegration.setLogo("https://example.com/logo.png");
        testIntegration.setActive(true);
        testIntegration.setCreatedAt(LocalDateTime.now());

        // Setup test response DTO
        testResponse = new IntegrationResponse();
        testResponse.setId(1);
        testResponse.setCode("TEST_INTEGRATION");
        testResponse.setLogo("https://example.com/logo.png");
        testResponse.setActive(true);
        testResponse.setCreatedAt(testIntegration.getCreatedAt());
    }

    @Test
    @DisplayName("GET /api/v1/integrations/{id} - Should return integration when found")
    void getIntegrationById_ShouldReturnIntegration_WhenFound() throws Exception {
        // Arrange
        when(service.getById(1)).thenReturn(Optional.of(testIntegration));
        when(mapper.toResponse(testIntegration)).thenReturn(testResponse);

        // Act & Assert
        mockMvc.perform(get("/api/v1/integrations/1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.code").value("TEST_INTEGRATION"))
                .andExpect(jsonPath("$.logo").value("https://example.com/logo.png"))
                .andExpect(jsonPath("$.active").value(true));
    }
} 