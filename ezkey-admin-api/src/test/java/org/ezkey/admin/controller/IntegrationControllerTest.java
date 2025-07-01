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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
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
 * Unit tests for IntegrationController.
 * <p>
 * Tests the REST endpoints for integration management operations.
 * Uses MockMvc for testing HTTP requests and Mockito for mocking dependencies.
 * </p>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative</p>
 * <p><b>License:</b> MIT</p>
 * <p><b>Usage:</b> Integration controller unit tests</p>
 *
 * @author Ezkey contributors
 * @since 2025
 */
@WebMvcTest(IntegrationController.class)
class IntegrationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EzkeyIntegrationService service;

    @MockBean
    private IntegrationMapper mapper;

    private EzkeyIntegration testIntegration1;
    private EzkeyIntegration testIntegration2;
    private IntegrationResponse testResponse1;
    private IntegrationResponse testResponse2;

    @BeforeEach
    void setUp() {
        // Create test integration entities
        testIntegration1 = new EzkeyIntegration();
        testIntegration1.setId(1);
        testIntegration1.setCode("TEST_INTEGRATION_1");
        testIntegration1.setLogo("https://example.com/logo1.png");
        testIntegration1.setActive(true);
        testIntegration1.setCreatedAt(LocalDateTime.now());

        testIntegration2 = new EzkeyIntegration();
        testIntegration2.setId(2);
        testIntegration2.setCode("TEST_INTEGRATION_2");
        testIntegration2.setLogo("https://example.com/logo2.png");
        testIntegration2.setActive(false);
        testIntegration2.setCreatedAt(LocalDateTime.now());

        // Create test response DTOs
        testResponse1 = new IntegrationResponse();
        testResponse1.setId(1);
        testResponse1.setCode("TEST_INTEGRATION_1");
        testResponse1.setLogo("https://example.com/logo1.png");
        testResponse1.setActive(true);
        testResponse1.setCreatedAt(testIntegration1.getCreatedAt());

        testResponse2 = new IntegrationResponse();
        testResponse2.setId(2);
        testResponse2.setCode("TEST_INTEGRATION_2");
        testResponse2.setLogo("https://example.com/logo2.png");
        testResponse2.setActive(false);
        testResponse2.setCreatedAt(testIntegration2.getCreatedAt());
    }

    @Test
    void getAll_ShouldReturnAllIntegrations_WhenIntegrationsExist() throws Exception {
        // Arrange
        List<EzkeyIntegration> integrations = Arrays.asList(testIntegration1, testIntegration2);
        List<IntegrationResponse> expectedResponses = Arrays.asList(testResponse1, testResponse2);

        when(service.getAll()).thenReturn(integrations);
        when(mapper.toResponseList(integrations)).thenReturn(expectedResponses);

        // Act & Assert
        mockMvc.perform(get("/api/v1/integrations")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].code").value("TEST_INTEGRATION_1"))
                .andExpect(jsonPath("$[0].logo").value("https://example.com/logo1.png"))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].code").value("TEST_INTEGRATION_2"))
                .andExpect(jsonPath("$[1].logo").value("https://example.com/logo2.png"))
                .andExpect(jsonPath("$[1].active").value(false));
    }

    @Test
    void getAll_ShouldReturnEmptyArray_WhenNoIntegrationsExist() throws Exception {
        // Arrange
        List<EzkeyIntegration> emptyList = Arrays.asList();
        List<IntegrationResponse> emptyResponses = Arrays.asList();

        when(service.getAll()).thenReturn(emptyList);
        when(mapper.toResponseList(emptyList)).thenReturn(emptyResponses);

        // Act & Assert
        mockMvc.perform(get("/api/v1/integrations")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }
} 