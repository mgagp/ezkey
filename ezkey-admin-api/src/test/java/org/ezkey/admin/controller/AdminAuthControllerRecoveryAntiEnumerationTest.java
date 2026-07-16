/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AdminAuthControllerRecoveryAntiEnumerationTest
 * Description: Verifies POST /recover failure responses are equivalent across rejection
 * reasons (SEC-024).
 */

package org.ezkey.admin.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import java.util.stream.Stream;
import org.ezkey.admin.audit.RecoveryAuditDetails;
import org.ezkey.admin.config.AdminBrowserSessionCookieProperties;
import org.ezkey.admin.config.AdminRecoveryProperties;
import org.ezkey.admin.constants.AdminAuditConstants;
import org.ezkey.admin.dto.request.AdminRecoveryRequestDto;
import org.ezkey.admin.dto.response.AdminRecoveryResponseDto;
import org.ezkey.admin.exception.AuthenticationException;
import org.ezkey.admin.security.AdminCsrfTokenService;
import org.ezkey.admin.security.AdminRateLimitFilter;
import org.ezkey.admin.security.AdminSessionCookieService;
import org.ezkey.admin.service.AdminAuthService;
import org.ezkey.admin.service.AdminProvisioningService;
import org.ezkey.admin.service.AdminRecoveryService;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * SEC-024: unauthenticated recovery failures must be response-equivalent to the client.
 *
 * @since 2026
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminAuthControllerRecoveryAntiEnumerationTest {

  private static final String GENERIC = AdminRecoveryService.GENERIC_RECOVERY_FAILURE_MESSAGE;
  private static final String SAMPLE_CODE = "1234-5678-9012-3456-7890-1234-5678-9012";

  @Mock private AdminAuthService authService;
  @Mock private AdminProvisioningService provisioningService;
  @Mock private AdminRecoveryService recoveryService;
  @Mock private AuditLogService auditLogService;
  @Mock private AdminRateLimitFilter rateLimitFilter;
  @Mock private AdminRecoveryProperties recoveryProperties;
  @Mock private EzkeyAdminRepository adminRepository;
  @Mock private AdminBrowserSessionCookieProperties browserSessionCookieProperties;
  @Mock private AdminSessionCookieService sessionCookieService;
  @Mock private AdminCsrfTokenService csrfTokenService;
  @Mock private HttpServletRequest httpRequest;

  @Captor private ArgumentCaptor<AuditLog> auditCaptor;

  private AdminAuthController controller;

  @BeforeEach
  void setUp() {
    controller =
        new AdminAuthController(
            authService,
            provisioningService,
            recoveryService,
            auditLogService,
            rateLimitFilter,
            recoveryProperties,
            adminRepository,
            browserSessionCookieProperties,
            sessionCookieService,
            csrfTokenService);
    when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
    when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
    when(httpRequest.getHeader("User-Agent")).thenReturn("JUnit");
  }

  static Stream<Arguments> indistinguishableFailures() {
    return Stream.of(
        Arguments.of("ghost", "Invalid credentials", "unknown_user"),
        Arguments.of("inactive.admin", "Account is inactive", "account_inactive"),
        Arguments.of(
            "no.codes", "No recovery codes available for this account", "no_codes_remaining"),
        Arguments.of("wrong.code", "Invalid recovery code", "invalid_code"));
  }

  @ParameterizedTest(name = "{0} → same 403 + generic message (internal: {1})")
  @MethodSource("indistinguishableFailures")
  @DisplayName("recover returns equivalent 403 response for all pre-auth failures")
  void recoverReturnsEquivalentClientResponse(
      String username, String internalReason, String expectedReasonCode) {
    when(recoveryService.validateRecoveryCode(username, SAMPLE_CODE))
        .thenThrow(
            new AuthenticationException(
                AdminRecoveryService.GENERIC_RECOVERY_FAILURE_MESSAGE, internalReason));

    ResponseEntity<AdminRecoveryResponseDto> response =
        controller.recover(new AdminRecoveryRequestDto(username, SAMPLE_CODE), httpRequest);

    assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    AdminRecoveryResponseDto body = response.getBody();
    assertNotNull(body);
    assertFalse(Boolean.TRUE.equals(body.success()));
    assertEquals(GENERIC, body.message());
    assertNull(body.recoveryToken());
    assertNull(body.expiresAt());
    assertNull(body.codesRemaining());
    assertNull(body.enrollmentId());

    verify(auditLogService).log(auditCaptor.capture());
    AuditLog audit = auditCaptor.getValue();
    assertEquals(EventType.ADMIN_RECOVERY_USE, audit.getEventType());
    assertEquals(AdminAuditConstants.RECOVERY_CODE_FAILED, audit.getEventAction());
    assertEquals(EventStatus.FAILURE, audit.getEventStatus());
    assertEquals(internalReason, audit.getErrorMessage());
    assertEquals(
        expectedReasonCode, RecoveryAuditDetails.recoveryRejectionReasonCode(internalReason));
    assertNotNull(audit.getEventDetails());
    assertEquals(true, audit.getEventDetails().contains("\"reason_code\":\"" + expectedReasonCode));

    verify(rateLimitFilter).recordFailedAttempt(any());
  }
}
