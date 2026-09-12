/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors Licensed under the MIT License. See LICENSE file in the
 * project root for full license information.
 *
 * Class: AuthAttemptControllerOwnershipTest Description: Unit tests for admin create access on
 * AuthAttemptController.
 */

package org.ezkey.admin.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import org.ezkey.admin.security.AccessControlService;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.audit.support.AuditEntityFkResolver;
import org.ezkey.authattempt.domain.AuthAttemptCreateRequest;
import org.ezkey.authattempt.domain.AuthAttemptCreateResponse;
import org.ezkey.authattempt.dto.AuthAttemptCreateRequestDto;
import org.ezkey.authattempt.dto.AuthAttemptCreateResponseDto;
import org.ezkey.authattempt.mapper.AuthAttemptAdminApiMapper;
import org.ezkey.authattempt.service.AuthAttemptService;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Unit tests for admin create access on {@link AuthAttemptController}.
 *
 * <p>API-key ownership checks live on Integration API, not Admin API.
 *
 * @since 2025
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthAttemptController Ownership Tests")
class AuthAttemptControllerOwnershipTest {

  @Mock private AuthAttemptService authAttemptService;

  @Mock private AuthAttemptAdminApiMapper authAttemptMapper;

  @Mock private AuditLogService auditLogService;

  @Mock private EnrollmentRepository enrollmentRepository;

  @Mock private AccessControlService accessControlService;

  @Mock private org.ezkey.integration.domain.repository.IntegrationRepository integrationRepository;

  private AuthAttemptController controller;

  @BeforeEach
  void setUp() {
    lenient().when(integrationRepository.existsById(any())).thenReturn(true);
    lenient().when(enrollmentRepository.existsById(any())).thenReturn(true);
    AuditEntityFkResolver auditEntityFkResolver =
        new AuditEntityFkResolver(integrationRepository, enrollmentRepository);
    controller =
        new AuthAttemptController(
            authAttemptService,
            authAttemptMapper,
            auditLogService,
            enrollmentRepository,
            accessControlService,
            integrationRepository,
            auditEntityFkResolver);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("Admin can create auth attempt when enrollment access is granted")
  void adminCanCreateAuthAttemptForAnyEnrollment() {
    Integer enrollmentId = 100;

    setupAdminAuthentication();

    when(accessControlService.canAccessEnrollment(any(), eq(enrollmentId))).thenReturn(true);

    AuthAttemptCreateResponse mockResponse = createMockResponse();
    when(authAttemptService.create(any(AuthAttemptCreateRequest.class))).thenReturn(mockResponse);
    when(authAttemptMapper.toAuthAttemptCreateResponseDto(mockResponse))
        .thenReturn(
            new AuthAttemptCreateResponseDto(
                1, null, 120, OffsetDateTime.now().plusSeconds(120), null, null));

    AuthAttemptCreateRequestDto request =
        new AuthAttemptCreateRequestDto(enrollmentId, null, null, false, null, null);

    ResponseEntity<?> response = controller.create(request, null);

    assert response.getStatusCode() == HttpStatus.CREATED;
  }

  private void setupAdminAuthentication() {
    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(
            "admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  private AuthAttemptCreateResponse createMockResponse() {
    AuthAttemptCreateResponse response = new AuthAttemptCreateResponse();
    response.setAuthAttemptId(1);
    response.setAuthAttemptChallenge(null);
    response.setTimeoutSeconds(120);
    response.setExpiresAt(OffsetDateTime.now().plusSeconds(120));
    return response;
  }
}
