/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AuthAttemptControllerLabelsTest
 * Description: Single-attempt responses (wait, cancel) carry the display labels (#1477)
 */

package org.ezkey.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.ezkey.admin.security.AccessControlService;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.audit.support.AuditEntityFkResolver;
import org.ezkey.authattempt.domain.AuthAttemptStatus;
import org.ezkey.authattempt.domain.AuthAttemptWaitRequest;
import org.ezkey.authattempt.domain.AuthAttemptWaitResponse;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.ezkey.authattempt.dto.AuthAttemptDto;
import org.ezkey.authattempt.dto.AuthAttemptWaitResponseDto;
import org.ezkey.authattempt.mapper.AuthAttemptAdminApiMapper;
import org.ezkey.authattempt.service.AuthAttemptService;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.integration.domain.entity.Integration;
import org.ezkey.integration.domain.entity.Tenant;
import org.ezkey.integration.domain.repository.IntegrationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Verifies that {@code GET /{id}/wait} and {@code POST /{id}/cancel} return the enrollment,
 * integration and tenant labels that the OpenAPI schema announces, like {@code GET /{id}} (#1477).
 *
 * <p>Uses the real MapStruct mapper so the test fails if the controller goes back to the label-less
 * {@code toDto}.
 *
 * @since 2026
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthAttemptController labels on wait and cancel (#1477)")
class AuthAttemptControllerLabelsTest {

  private static final int ATTEMPT_ID = 55;
  private static final int ENROLLMENT_ID = 100;
  private static final int INTEGRATION_ID = 7;

  @Mock private AuthAttemptService authAttemptService;
  @Mock private AuditLogService auditLogService;
  @Mock private EnrollmentRepository enrollmentRepository;
  @Mock private AccessControlService accessControlService;
  @Mock private IntegrationRepository integrationRepository;

  private AuthAttemptController controller;
  private AuthAttempt attempt;

  @BeforeEach
  void setUp() {
    SecurityContextHolder.clearContext();

    Tenant tenant = new Tenant("Acme Corp", "Tenant for label tests");
    tenant.setTenantId(42);
    tenant.setActive(true);

    Integration integration = new Integration();
    integration.setId(INTEGRATION_ID);
    integration.setCode("web-portal");
    integration.setName("Web Portal");
    integration.setTenant(tenant);

    Enrollment enrollment = new Enrollment(INTEGRATION_ID, "Alice Phone", "proof-token");
    enrollment.setEnrollmentId(ENROLLMENT_ID);

    attempt = new AuthAttempt(ENROLLMENT_ID, "attempt-proof");
    attempt.setAuthAttemptId(ATTEMPT_ID);

    lenient()
        .when(enrollmentRepository.findById(ENROLLMENT_ID))
        .thenReturn(Optional.of(enrollment));
    lenient()
        .when(integrationRepository.findAllByIdWithTenant(any()))
        .thenReturn(List.of(integration));
    lenient().when(integrationRepository.existsById(any())).thenReturn(true);
    lenient().when(enrollmentRepository.existsById(any())).thenReturn(true);

    controller =
        new AuthAttemptController(
            authAttemptService,
            Mappers.getMapper(AuthAttemptAdminApiMapper.class),
            auditLogService,
            enrollmentRepository,
            accessControlService,
            integrationRepository,
            new AuditEntityFkResolver(integrationRepository, enrollmentRepository));
  }

  @Test
  @DisplayName("wait() returns integration, enrollment and tenant labels")
  void waitForResponse_returnsLabels() {
    attempt.setAuthAttemptStatus(AuthAttemptStatus.ACCEPTED);
    when(authAttemptService.waitForResponse(eq(ATTEMPT_ID), any(AuthAttemptWaitRequest.class)))
        .thenReturn(
            new AuthAttemptWaitResponse(attempt, "ACCEPTED", true, false, 3, OffsetDateTime.now()));

    ResponseEntity<AuthAttemptWaitResponseDto> response =
        controller.waitForResponse(ATTEMPT_ID, 30, 2, new MockHttpServletRequest());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    AuthAttemptWaitResponseDto body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.status()).isEqualTo("ACCEPTED");
    assertThat(body.completed()).isTrue();
    assertThat(body.timeoutReached()).isFalse();
    assertThat(body.waitDuration()).isEqualTo(3);
    assertLabels(body.authAttempt());
  }

  @Test
  @DisplayName("cancel() returns integration, enrollment and tenant labels")
  void cancel_returnsLabels() {
    attempt.setAuthAttemptStatus(AuthAttemptStatus.EXPIRED);
    when(authAttemptService.getById(ATTEMPT_ID)).thenReturn(attempt);
    when(authAttemptService.cancel(ATTEMPT_ID)).thenReturn(attempt);

    ResponseEntity<AuthAttemptDto> response =
        controller.cancel(ATTEMPT_ID, new MockHttpServletRequest());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertLabels(response.getBody());
  }

  @Test
  @DisplayName("wait() keeps the outcome without labels when the label lookup fails")
  void waitForResponse_labelLookupFails_returnsOutcomeWithoutLabels() {
    attempt.setAuthAttemptStatus(AuthAttemptStatus.ACCEPTED);
    when(authAttemptService.waitForResponse(eq(ATTEMPT_ID), any(AuthAttemptWaitRequest.class)))
        .thenReturn(
            new AuthAttemptWaitResponse(attempt, "ACCEPTED", true, false, 3, OffsetDateTime.now()));
    when(enrollmentRepository.findById(ENROLLMENT_ID))
        .thenThrow(new QueryTimeoutException("simulated"));

    ResponseEntity<AuthAttemptWaitResponseDto> response =
        controller.waitForResponse(ATTEMPT_ID, 30, 2, new MockHttpServletRequest());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    AuthAttemptWaitResponseDto body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.status()).isEqualTo("ACCEPTED");
    assertThat(body.authAttempt().authAttemptId()).isEqualTo(ATTEMPT_ID);
    assertThat(body.authAttempt().integrationName()).isNull();
  }

  private static void assertLabels(AuthAttemptDto dto) {
    assertThat(dto).isNotNull();
    assertThat(dto.authAttemptId()).isEqualTo(ATTEMPT_ID);
    assertThat(dto.enrollmentName()).isEqualTo("Alice Phone");
    assertThat(dto.integrationId()).isEqualTo(INTEGRATION_ID);
    assertThat(dto.integrationName()).isEqualTo("Web Portal");
    assertThat(dto.tenantId()).isEqualTo(42);
    assertThat(dto.tenantName()).isEqualTo("Acme Corp");
  }
}
