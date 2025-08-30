package org.ezkey.admin.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.ezkey.authattempt.domain.AuthAttemptCreateRequest;
import org.ezkey.authattempt.domain.AuthAttemptCreateResponse;
import org.ezkey.authattempt.dto.AuthAttemptCreateRequestDto;
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
 * Smoke test using real MapStruct mapper implementation for AuthAttemptController.
 * Verifies request->domain mapping and response mapping on create endpoint.
 */
@WebMvcTest(AuthAttemptController.class)
@Import(AuthAttemptMapperImpl.class)
class AuthAttemptControllerWithRealMapperTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthAttemptService authAttemptService;

    @Test
    @DisplayName("POST /api/v1/auth-attempts (real mapper) - smoke mapping test")
    void createAuthAttempt_WithRealMapper_Smoke() throws Exception {
        AuthAttemptCreateRequestDto dto = new AuthAttemptCreateRequestDto();
        dto.setEnrollmentId(55);
        dto.setChallengeRequested(true);

        AuthAttemptCreateResponse resp = new AuthAttemptCreateResponse();
        resp.setAuthAttemptId(888);
        org.mockito.Mockito.when(authAttemptService.create(any(AuthAttemptCreateRequest.class))).thenReturn(resp);

        String json = objectMapper.writeValueAsString(dto);

        mockMvc.perform(post("/api/v1/auth-attempts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.authAttemptId").value(888));

        ArgumentCaptor<AuthAttemptCreateRequest> captor = ArgumentCaptor.forClass(AuthAttemptCreateRequest.class);
        org.mockito.Mockito.verify(authAttemptService).create(captor.capture());
        AuthAttemptCreateRequest passed = captor.getValue();
        if (passed.getEnrollmentId() == null || passed.getEnrollmentId() != 55) {
            throw new AssertionError("enrollmentId not mapped correctly");
        }
        if (!Boolean.TRUE.equals(passed.getChallengeRequested())) {
            throw new AssertionError("challengeRequested not mapped correctly");
        }
    }
}
