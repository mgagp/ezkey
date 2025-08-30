package org.ezkey.admin.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.ezkey.enrollment.domain.EnrollmentCreateRequest;
import org.ezkey.enrollment.domain.EnrollmentCreateResponse;
import org.ezkey.enrollment.dto.EnrollmentCreateRequestDto;
import org.ezkey.enrollment.mapper.EnrollmentAdminMapperImpl;
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
 * Smoke test with the real MapStruct mapper implementation for EnrollmentController.
 * Ensures mapping from request DTO to domain request and domain response to response DTO
 * works as expected (guards against silent mapping drift).
 */
@WebMvcTest(EnrollmentController.class)
@Import(EnrollmentAdminMapperImpl.class)
class EnrollmentControllerWithRealMapperTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // Real mapper is imported; mock only the service
    @MockBean
    private EnrollmentService enrollmentService;

    @Test
    @DisplayName("POST /api/v1/enrollments (real mapper) - mapping smoke test")
    void createEnrollment_WithRealMapper_SmokeTest() throws Exception {
        // Arrange request DTO
        EnrollmentCreateRequestDto dto = new EnrollmentCreateRequestDto();
        dto.setIntegrationId(7);
        dto.setName("Device X");
        dto.setAuthAttemptChallengeRequired(true);

        // Service domain response
        EnrollmentCreateResponse response = new EnrollmentCreateResponse();
        response.setEnrollmentId(55);
        response.setEnrollmentChallenge(654321);
        org.mockito.Mockito.when(enrollmentService.create(any(EnrollmentCreateRequest.class))).thenReturn(response);

        String json = objectMapper.writeValueAsString(dto);

        // Act & Assert
        mockMvc.perform(post("/api/v1/enrollments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.enrollmentId").value(55))
            .andExpect(jsonPath("$.enrollmentChallenge").value(654321));

        // Capture argument passed to service to verify mapping
        ArgumentCaptor<EnrollmentCreateRequest> captor = ArgumentCaptor.forClass(EnrollmentCreateRequest.class);
        org.mockito.Mockito.verify(enrollmentService).create(captor.capture());
        EnrollmentCreateRequest passed = captor.getValue();
        if (passed.getIntegrationId() == null || passed.getIntegrationId() != 7) {
            throw new AssertionError("integrationId not mapped correctly");
        }
        if (!"Device X".equals(passed.getName())) {
            throw new AssertionError("name not mapped correctly");
        }
        if (!Boolean.TRUE.equals(passed.getAuthAttemptChallengeRequired())) {
            throw new AssertionError("authAttemptChallengeRequired not mapped correctly");
        }
    }
}
