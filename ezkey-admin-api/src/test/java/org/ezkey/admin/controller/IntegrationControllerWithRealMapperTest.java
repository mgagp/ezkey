package org.ezkey.admin.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.ezkey.integration.domain.IntegrationCreateRequest;
import org.ezkey.integration.domain.IntegrationCreateResponse;
import org.ezkey.integration.dto.IntegrationCreateRequestDto;
import org.ezkey.integration.mapper.IntegrationControllerMapperImpl;
import org.ezkey.integration.service.IntegrationService;
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
 * Focused controller test using the real MapStruct mapper implementation to detect mapping drift.
 */
@WebMvcTest(IntegrationController.class)
@Import(IntegrationControllerMapperImpl.class)
class IntegrationControllerWithRealMapperTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // Real mapper provided by @Import, only mock the service
    @MockBean
    private IntegrationService integrationService;

    @Test
    @DisplayName("POST /api/v1/integrations (real mapper) - mapping smoke test")
    void createIntegration_WithRealMapper_SmokeTest() throws Exception {
        // Arrange request DTO
        IntegrationCreateRequestDto dto = new IntegrationCreateRequestDto();
        dto.setLogo("https://example.com/real-mapper-logo.png");

        // Service returns domain response
        IntegrationCreateResponse response = new IntegrationCreateResponse();
        response.setId(101);
        org.mockito.Mockito.when(integrationService.createIntegration(any(IntegrationCreateRequest.class)))
            .thenReturn(response);

        String json = objectMapper.writeValueAsString(dto);

        // Act & Assert
        mockMvc.perform(post("/api/v1/integrations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(101))
            .andExpect(header().string("Location", "/api/v1/integrations/101"));

        // Capture argument passed to service to ensure real mapper populated it
        ArgumentCaptor<IntegrationCreateRequest> captor = ArgumentCaptor.forClass(IntegrationCreateRequest.class);
        org.mockito.Mockito.verify(integrationService).createIntegration(captor.capture());
        IntegrationCreateRequest passed = captor.getValue();
        if (!"https://example.com/real-mapper-logo.png".equals(passed.getLogo())) {
            throw new AssertionError("Real mapper did not map logo correctly: " + passed.getLogo());
        }
    }
}
