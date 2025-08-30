package org.ezkey.admin.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.ezkey.authattempt.domain.AuthAttemptCreateRequest;
import org.ezkey.authattempt.domain.AuthAttemptCreateResponse;
import org.ezkey.authattempt.domain.AuthAttemptWaitRequest;
import org.ezkey.authattempt.domain.AuthAttemptWaitResponse;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.ezkey.authattempt.dto.AuthAttemptCreateRequestDto;
import org.ezkey.authattempt.dto.AuthAttemptCreateResponseDto;
import org.ezkey.authattempt.dto.AuthAttemptDto;
import org.ezkey.authattempt.dto.AuthAttemptWaitRequestDto;
import org.ezkey.authattempt.dto.AuthAttemptWaitResponseDto;
import org.ezkey.authattempt.mapper.AuthAttemptMapper;
import org.ezkey.authattempt.service.AuthAttemptService;
import org.ezkey.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

@WebMvcTest(AuthAttemptController.class)
@DisplayName("AuthAttempt Controller Tests")
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

    private AuthAttempt authAttemptEntity;
    private AuthAttemptDto authAttemptDto;
    private AuthAttemptCreateRequestDto createRequestDto;
    private AuthAttemptCreateRequest createRequest;
    private AuthAttemptCreateResponse createResponseDomain;
    private AuthAttemptCreateResponseDto createResponseDto;

    @BeforeEach
    void setUp(){
        authAttemptEntity = new AuthAttempt();
        authAttemptEntity.setAuthAttemptId(100);
        authAttemptEntity.setEnrollmentId(55);
        authAttemptEntity.setAuthAttemptRead(false);
        authAttemptEntity.setAuthAttemptResponded(false);
        authAttemptEntity.setAuthAttemptAccepted(false);
        authAttemptEntity.setCreatedAt(LocalDateTime.of(2025,1,1,12,0,0));

        authAttemptDto = new AuthAttemptDto();
        authAttemptDto.setAuthAttemptId(100);
        authAttemptDto.setEnrollmentId(55);
        authAttemptDto.setAuthAttemptRead(false);
        authAttemptDto.setAuthAttemptResponded(false);
        authAttemptDto.setAuthAttemptAccepted(false);
        authAttemptDto.setCreatedAt(LocalDateTime.of(2025,1,1,12,0,0));

        createRequestDto = new AuthAttemptCreateRequestDto();
        createRequestDto.setEnrollmentId(55);
        createRequestDto.setChallengeRequested(true);

        createRequest = new AuthAttemptCreateRequest();
        createRequest.setEnrollmentId(55);
        createRequest.setChallengeRequested(true);

        createResponseDomain = new AuthAttemptCreateResponse();
        createResponseDomain.setAuthAttemptId(200);

        createResponseDto = new AuthAttemptCreateResponseDto();
        createResponseDto.setAuthAttemptId(200);
    }

    @Test
    @DisplayName("GET /api/v1/auth-attempts - Should return list")
    void getAll_ShouldReturnList() throws Exception {
        when(authAttemptService.getAll()).thenReturn(List.of(authAttemptEntity));
        when(authAttemptMapper.toDtoList(List.of(authAttemptEntity))).thenReturn(List.of(authAttemptDto));

        mockMvc.perform(get(BASE_URL).contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].authAttemptId").value(100))
            .andExpect(jsonPath("$[0].enrollmentId").value(55));

        verify(authAttemptService, times(1)).getAll();
        verify(authAttemptMapper, times(1)).toDtoList(List.of(authAttemptEntity));
    }

    @Test
    @DisplayName("GET /api/v1/auth-attempts - Should return empty array when none")
    void getAll_WhenEmpty_ShouldReturnEmptyArray() throws Exception {
        when(authAttemptService.getAll()).thenReturn(List.of());
        when(authAttemptMapper.toDtoList(List.of())).thenReturn(List.of());

        mockMvc.perform(get(BASE_URL).contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().json("[]"));

        verify(authAttemptService, times(1)).getAll();
        verify(authAttemptMapper, times(1)).toDtoList(List.of());
    }

    @Test
    @DisplayName("GET /api/v1/auth-attempts/{id} - Found")
    void getById_Found() throws Exception {
        when(authAttemptService.getById(100)).thenReturn(authAttemptEntity);
        when(authAttemptMapper.toDto(authAttemptEntity)).thenReturn(authAttemptDto);

        mockMvc.perform(get(BASE_URL + "/{id}",100).contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authAttemptId").value(100))
            .andExpect(jsonPath("$.enrollmentId").value(55));

        verify(authAttemptService, times(1)).getById(100);
        verify(authAttemptMapper, times(1)).toDto(authAttemptEntity);
    }

    @Test
    @DisplayName("GET /api/v1/auth-attempts/{id} - Not Found")
    void getById_NotFound() throws Exception {
        when(authAttemptService.getById(999)).thenThrow(new ResourceNotFoundException("Authorization attempt",999));

        mockMvc.perform(get(BASE_URL + "/{id}",999).contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound());

        verify(authAttemptService, times(1)).getById(999);
        verify(authAttemptMapper, times(0)).toDto(any());
    }

    @Test
    @DisplayName("POST /api/v1/auth-attempts - Create 201")
    void create_ShouldReturn201() throws Exception {
        when(authAttemptMapper.toAuthAttemptCreateRequest(any(AuthAttemptCreateRequestDto.class))).thenReturn(createRequest);
        when(authAttemptService.create(any(AuthAttemptCreateRequest.class))).thenReturn(createResponseDomain);
        when(authAttemptMapper.toAuthAttemptCreateResponseDto(createResponseDomain)).thenReturn(createResponseDto);

        String json = objectMapper.writeValueAsString(createRequestDto);

        mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(json))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.authAttemptId").value(200));

        verify(authAttemptMapper, times(1)).toAuthAttemptCreateRequest(any(AuthAttemptCreateRequestDto.class));
        verify(authAttemptService, times(1)).create(any(AuthAttemptCreateRequest.class));
        verify(authAttemptMapper, times(1)).toAuthAttemptCreateResponseDto(createResponseDomain);
    }

    @Test
    @DisplayName("POST /api/v1/auth-attempts - Propagate fields")
    void create_ShouldPropagateFields() throws Exception {
        when(authAttemptMapper.toAuthAttemptCreateRequest(any(AuthAttemptCreateRequestDto.class))).thenReturn(createRequest);
        when(authAttemptService.create(any(AuthAttemptCreateRequest.class))).thenReturn(createResponseDomain);
        when(authAttemptMapper.toAuthAttemptCreateResponseDto(createResponseDomain)).thenReturn(createResponseDto);

        String json = objectMapper.writeValueAsString(createRequestDto);

        mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(json))
            .andExpect(status().isCreated());

        ArgumentCaptor<AuthAttemptCreateRequest> captor = ArgumentCaptor.forClass(AuthAttemptCreateRequest.class);
        verify(authAttemptService).create(captor.capture());
        AuthAttemptCreateRequest passed = captor.getValue();
        if (!passed.getEnrollmentId().equals(55) || !Boolean.TRUE.equals(passed.getChallengeRequested())) {
            throw new AssertionError("Fields not propagated correctly");
        }
    }

    @Test
    @DisplayName("POST /api/v1/auth-attempts - 400 on validation error")
    void create_ValidationError_ShouldReturn400() throws Exception {
        when(authAttemptMapper.toAuthAttemptCreateRequest(any(AuthAttemptCreateRequestDto.class))).thenReturn(createRequest);
        when(authAttemptService.create(any(AuthAttemptCreateRequest.class))).thenThrow(new IllegalArgumentException("invalid"));

        String json = objectMapper.writeValueAsString(createRequestDto);

        mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(json))
            .andExpect(status().isBadRequest());

        verify(authAttemptService, times(1)).create(any(AuthAttemptCreateRequest.class));
    }

    @Test
    @DisplayName("POST /api/v1/auth-attempts - 500 on unexpected error")
    void create_InternalError_ShouldReturn500() throws Exception {
        when(authAttemptMapper.toAuthAttemptCreateRequest(any(AuthAttemptCreateRequestDto.class))).thenReturn(createRequest);
        when(authAttemptService.create(any(AuthAttemptCreateRequest.class))).thenThrow(new RuntimeException("boom"));

        String json = objectMapper.writeValueAsString(createRequestDto);

        mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(json))
            .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("GET /api/v1/auth-attempts/{id}/wait - Success")
    void waitForResponse_Success() throws Exception {
        AuthAttemptWaitRequestDto waitRequestDto = new AuthAttemptWaitRequestDto();
        waitRequestDto.setPolling(2);
        waitRequestDto.setTimeout(5);

        AuthAttemptWaitRequest waitRequest = new AuthAttemptWaitRequest();
        waitRequest.setPolling(2);
        waitRequest.setTimeout(5);

        AuthAttemptWaitResponse domainWaitResponse = new AuthAttemptWaitResponse(authAttemptEntity, "PENDING", false, false, 1, LocalDateTime.now());
        AuthAttemptWaitResponseDto waitResponseDto = new AuthAttemptWaitResponseDto();
        waitResponseDto.setStatus("PENDING");
        waitResponseDto.setCompleted(false);
        waitResponseDto.setTimeoutReached(false);
        waitResponseDto.setWaitDuration(1);

        // mapper conversions
        when(authAttemptMapper.toAuthAttemptWaitRequest(any(AuthAttemptWaitRequestDto.class))).thenReturn(waitRequest);
        when(authAttemptService.waitForResponse(100, waitRequest)).thenReturn(domainWaitResponse);
        when(authAttemptMapper.toAuthAttemptWaitResponseDto(domainWaitResponse)).thenReturn(waitResponseDto);

        mockMvc.perform(get(BASE_URL + "/{id}/wait?timeout=5&polling=2",100))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.completed").value(false));

        verify(authAttemptService, times(1)).waitForResponse(100, waitRequest);
    }

    @Test
    @DisplayName("GET /api/v1/auth-attempts/{id}/wait - 404 not found")
    void waitForResponse_NotFound() throws Exception {
        AuthAttemptWaitRequest waitRequest = new AuthAttemptWaitRequest();
        waitRequest.setPolling(2);
        waitRequest.setTimeout(5);
        when(authAttemptMapper.toAuthAttemptWaitRequest(any(AuthAttemptWaitRequestDto.class))).thenReturn(waitRequest);
        when(authAttemptService.waitForResponse(999, waitRequest)).thenThrow(new ResourceNotFoundException("Authorization attempt",999));

        mockMvc.perform(get(BASE_URL + "/{id}/wait?timeout=5&polling=2",999))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/v1/auth-attempts/{id}/wait - 400 invalid params")
    void waitForResponse_InvalidParams() throws Exception {
        AuthAttemptWaitRequest waitRequest = new AuthAttemptWaitRequest();
        waitRequest.setPolling(10);
        waitRequest.setTimeout(5); // invalid because polling > timeout
        when(authAttemptMapper.toAuthAttemptWaitRequest(any(AuthAttemptWaitRequestDto.class))).thenReturn(waitRequest);
        when(authAttemptService.waitForResponse(100, waitRequest)).thenThrow(new IllegalArgumentException("invalid"));

        mockMvc.perform(get(BASE_URL + "/{id}/wait?timeout=5&polling=10",100))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/auth-attempts/{id}/wait - 500 internal error")
    void waitForResponse_InternalError() throws Exception {
        AuthAttemptWaitRequest waitRequest = new AuthAttemptWaitRequest();
        waitRequest.setPolling(2);
        waitRequest.setTimeout(5);
        when(authAttemptMapper.toAuthAttemptWaitRequest(any(AuthAttemptWaitRequestDto.class))).thenReturn(waitRequest);
        when(authAttemptService.waitForResponse(100, waitRequest)).thenThrow(new RuntimeException("boom"));

        mockMvc.perform(get(BASE_URL + "/{id}/wait?timeout=5&polling=2",100))
            .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("DELETE /api/v1/auth-attempts/{id} - 204")
    void delete_Success() throws Exception {
        doNothing().when(authAttemptService).delete(100);

        mockMvc.perform(delete(BASE_URL + "/{id}",100))
            .andExpect(status().isNoContent())
            .andExpect(content().string(""));

        verify(authAttemptService, times(1)).delete(100);
    }

    @Test
    @DisplayName("DELETE /api/v1/auth-attempts/{id} - 404")
    void delete_NotFound() throws Exception {
        org.mockito.Mockito.doThrow(new ResourceNotFoundException("Authorization attempt",999)).when(authAttemptService).delete(999);

        mockMvc.perform(delete(BASE_URL + "/{id}",999))
            .andExpect(status().isNotFound());

        verify(authAttemptService, times(1)).delete(999);
    }
}
