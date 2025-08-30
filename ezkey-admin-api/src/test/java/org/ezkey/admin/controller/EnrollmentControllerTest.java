package org.ezkey.admin.controller;

import static org.mockito.ArgumentMatchers.any;
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

import java.util.List;

import org.ezkey.enrollment.domain.EnrollmentCreateRequest;
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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

@WebMvcTest(EnrollmentController.class)
@DisplayName("Enrollment Controller Tests")
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

    private Enrollment enrollmentEntity;
    private EnrollmentResponseDto enrollmentResponseDto;
    private EnrollmentCreateRequestDto createRequestDto;
    private EnrollmentCreateRequest createRequest;
    private EnrollmentCreateResponse createResponseDomain;
    private EnrollmentCreateResponseDto createResponseDto;

    @BeforeEach
    void setUp(){
        enrollmentEntity = new Enrollment();
        enrollmentEntity.setEnrollmentId(10);
        enrollmentEntity.setIntegrationId(5);
        enrollmentEntity.setEnrollmentName("Device A");

        enrollmentResponseDto = new EnrollmentResponseDto();
        enrollmentResponseDto.setEnrollmentId(10);
        enrollmentResponseDto.setIntegrationId(5);
        enrollmentResponseDto.setEnrollmentName("Device A");

        createRequestDto = new EnrollmentCreateRequestDto();
        createRequestDto.setIntegrationId(5);
        createRequestDto.setName("Device B");
        createRequestDto.setAuthAttemptChallengeRequired(true);

        createRequest = new EnrollmentCreateRequest();
        createRequest.setIntegrationId(5);
        createRequest.setName("Device B");
        createRequest.setAuthAttemptChallengeRequired(true);

        createResponseDomain = new EnrollmentCreateResponse();
        createResponseDomain.setEnrollmentId(22);
        createResponseDomain.setEnrollmentChallenge(123456);

        createResponseDto = new EnrollmentCreateResponseDto();
        createResponseDto.setEnrollmentId(22);
        createResponseDto.setEnrollmentChallenge(123456);
    }

    @Test
    @DisplayName("GET /api/v1/enrollments - Should return all enrollments")
    void getAll_ShouldReturnList() throws Exception {
        when(enrollmentService.getAll()).thenReturn(List.of(enrollmentEntity));
        when(enrollmentMapper.toResponseList(List.of(enrollmentEntity))).thenReturn(List.of(enrollmentResponseDto));

        mockMvc.perform(get(BASE_URL).contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].enrollmentId").value(10))
            .andExpect(jsonPath("$[0].integrationId").value(5))
            .andExpect(jsonPath("$[0].enrollmentName").value("Device A"));

        verify(enrollmentService, times(1)).getAll();
        verify(enrollmentMapper, times(1)).toResponseList(List.of(enrollmentEntity));
    }

    @Test
    @DisplayName("GET /api/v1/enrollments - Should return empty array when none")
    void getAll_WhenEmpty_ShouldReturnEmptyArray() throws Exception {
        when(enrollmentService.getAll()).thenReturn(List.of());
        when(enrollmentMapper.toResponseList(List.of())).thenReturn(List.of());

        mockMvc.perform(get(BASE_URL).contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().json("[]"));

        verify(enrollmentService, times(1)).getAll();
        verify(enrollmentMapper, times(1)).toResponseList(List.of());
    }

    @Test
    @DisplayName("GET /api/v1/enrollments/{id} - Should return enrollment when found")
    void getById_Found() throws Exception {
        when(enrollmentService.getById(10)).thenReturn(enrollmentEntity);
        when(enrollmentMapper.toResponse(enrollmentEntity)).thenReturn(enrollmentResponseDto);

        mockMvc.perform(get(BASE_URL + "/{id}", 10).contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.enrollmentId").value(10))
            .andExpect(jsonPath("$.integrationId").value(5))
            .andExpect(jsonPath("$.enrollmentName").value("Device A"));

        verify(enrollmentService, times(1)).getById(10);
        verify(enrollmentMapper, times(1)).toResponse(enrollmentEntity);
    }

    @Test
    @DisplayName("GET /api/v1/enrollments/{id} - Should return 404 when not found")
    void getById_NotFound() throws Exception {
        when(enrollmentService.getById(999)).thenThrow(new ResourceNotFoundException("Enrollment", 999));

        mockMvc.perform(get(BASE_URL + "/{id}", 999).contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound());

        verify(enrollmentService, times(1)).getById(999);
        verify(enrollmentMapper, times(0)).toResponse(any());
    }

    @Test
    @DisplayName("POST /api/v1/enrollments - Should create enrollment and return 201")
    void create_ShouldReturn201() throws Exception {
        when(enrollmentMapper.toCreateRequest(any(EnrollmentCreateRequestDto.class))).thenReturn(createRequest);
        when(enrollmentService.create(any(EnrollmentCreateRequest.class))).thenReturn(createResponseDomain);
        when(enrollmentMapper.toCreateResponseDto(createResponseDomain)).thenReturn(createResponseDto);

        String json = objectMapper.writeValueAsString(createRequestDto);

        mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(json))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.enrollmentId").value(22))
            .andExpect(jsonPath("$.enrollmentChallenge").value(123456));

        verify(enrollmentMapper, times(1)).toCreateRequest(any(EnrollmentCreateRequestDto.class));
        verify(enrollmentService, times(1)).create(any(EnrollmentCreateRequest.class));
        verify(enrollmentMapper, times(1)).toCreateResponseDto(createResponseDomain);
    }

    @Test
    @DisplayName("POST /api/v1/enrollments - Should propagate fields to service")
    void create_ShouldPropagateFields() throws Exception {
        when(enrollmentMapper.toCreateRequest(any(EnrollmentCreateRequestDto.class))).thenReturn(createRequest);
        when(enrollmentService.create(any(EnrollmentCreateRequest.class))).thenReturn(createResponseDomain);
        when(enrollmentMapper.toCreateResponseDto(createResponseDomain)).thenReturn(createResponseDto);

        String json = objectMapper.writeValueAsString(createRequestDto);

        mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(json))
            .andExpect(status().isCreated());

        ArgumentCaptor<EnrollmentCreateRequest> captor = ArgumentCaptor.forClass(EnrollmentCreateRequest.class);
        verify(enrollmentService).create(captor.capture());
        EnrollmentCreateRequest passed = captor.getValue();
        if(!passed.getIntegrationId().equals(5) || !"Device B".equals(passed.getName()) || !Boolean.TRUE.equals(passed.getAuthAttemptChallengeRequired())){
            throw new AssertionError("Fields not propagated correctly to service");
        }
    }

    @Test
    @DisplayName("POST /api/v1/enrollments - Should return 400 on validation error (IllegalArgumentException)")
    void create_ValidationError_ShouldReturn400() throws Exception {
        when(enrollmentMapper.toCreateRequest(any(EnrollmentCreateRequestDto.class))).thenReturn(createRequest);
        when(enrollmentService.create(any(EnrollmentCreateRequest.class))).thenThrow(new IllegalArgumentException("invalid"));

        String json = objectMapper.writeValueAsString(createRequestDto);

        mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(json))
            .andExpect(status().isBadRequest())
            .andExpect(content().string(""));

        verify(enrollmentMapper, times(1)).toCreateRequest(any(EnrollmentCreateRequestDto.class));
        verify(enrollmentService, times(1)).create(any(EnrollmentCreateRequest.class));
    }

    @Test
    @DisplayName("POST /api/v1/enrollments - Should return 500 on unexpected exception")
    void create_InternalError_ShouldReturn500() throws Exception {
        when(enrollmentMapper.toCreateRequest(any(EnrollmentCreateRequestDto.class))).thenReturn(createRequest);
        when(enrollmentService.create(any(EnrollmentCreateRequest.class))).thenThrow(new RuntimeException("boom"));

        String json = objectMapper.writeValueAsString(createRequestDto);

        mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(json))
            .andExpect(status().isInternalServerError())
            .andExpect(content().string(""));
    }

    @Test
    @DisplayName("DELETE /api/v1/enrollments/{id} - Should delete and return 204")
    void delete_Success() throws Exception {
        doNothing().when(enrollmentService).delete(10);

        mockMvc.perform(delete(BASE_URL + "/{id}", 10).contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent())
            .andExpect(content().string(""));

        verify(enrollmentService, times(1)).delete(10);
    }

    @Test
    @DisplayName("DELETE /api/v1/enrollments/{id} - Should return 404 when not found")
    void delete_NotFound() throws Exception {
        org.mockito.Mockito.doThrow(new ResourceNotFoundException("Enrollment", 999)).when(enrollmentService).delete(999);

        mockMvc.perform(delete(BASE_URL + "/{id}", 999).contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound());

        verify(enrollmentService, times(1)).delete(999);
    }
}
