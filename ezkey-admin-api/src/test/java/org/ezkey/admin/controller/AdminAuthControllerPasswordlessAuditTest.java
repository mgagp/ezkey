/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.admin.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.ezkey.admin.config.AdminRecoveryProperties;
import org.ezkey.admin.constants.AdminAuditConstants;
import org.ezkey.admin.dto.AdminAuthAuditContext;
import org.ezkey.admin.dto.request.AdminPasswordlessWaitRequestDto;
import org.ezkey.admin.dto.response.AdminLoginResponseDto;
import org.ezkey.admin.exception.AdminAuthenticationExpiredException;
import org.ezkey.admin.exception.AdminAuthenticationRejectedException;
import org.ezkey.admin.security.AdminRateLimitFilter;
import org.ezkey.admin.service.AdminAuthService;
import org.ezkey.admin.service.AdminRecoveryService;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Verifies audit logging for {@link AdminAuthController#passwordlessWait} outcomes. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminAuthControllerPasswordlessAuditTest {

  @Mock private AdminAuthService authService;
  @Mock private AdminRecoveryService recoveryService;
  @Mock private AuditLogService auditLogService;
  @Mock private AdminRateLimitFilter rateLimitFilter;
  @Mock private AdminRecoveryProperties recoveryProperties;
  @Mock private EzkeyAdminRepository adminRepository;
  @Mock private HttpServletRequest httpRequest;

  @Captor private ArgumentCaptor<AuditLog> auditCaptor;

  private AdminAuthController controller;

  private final AdminAuthAuditContext auditCtx = new AdminAuthAuditContext("admin1", 42, 1);

  @BeforeEach
  void setUp() {
    controller =
        new AdminAuthController(
            authService,
            recoveryService,
            auditLogService,
            rateLimitFilter,
            recoveryProperties,
            adminRepository);
    when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
    when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
    when(httpRequest.getHeader("User-Agent")).thenReturn("JUnit");
  }

  @Test
  @DisplayName("passwordlessWait success emits login_mfa_session_issued SUCCESS audit")
  void success_emitsSessionIssuedAudit() {
    when(authService.findAuditContextForAuthAttempt(10)).thenReturn(Optional.of(auditCtx));
    AdminLoginResponseDto ok =
        new AdminLoginResponseDto(
            "token", "GLOBAL_ADMIN", "admin1", java.time.OffsetDateTime.now().plusHours(1));
    when(authService.waitForPasswordlessAuth(10, 7)).thenReturn(ok);

    ResponseEntity<AdminLoginResponseDto> response =
        controller.passwordlessWait(new AdminPasswordlessWaitRequestDto(10, 7), httpRequest);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    verify(auditLogService).log(auditCaptor.capture());
    AuditLog log = auditCaptor.getValue();
    assertEquals(EventType.ADMIN_LOGIN, log.getEventType());
    assertEquals(AdminAuditConstants.LOGIN_MFA_SESSION_ISSUED, log.getEventAction());
    assertEquals(EventStatus.SUCCESS, log.getEventStatus());
    assertEquals(Integer.valueOf(42), log.getAdminId());
  }

  @Test
  @DisplayName("passwordlessWait expired emits login_mfa_expired FAILURE audit")
  void expired_emitsExpiredAudit() {
    when(authService.findAuditContextForAuthAttempt(10)).thenReturn(Optional.of(auditCtx));
    when(authService.waitForPasswordlessAuth(10, null))
        .thenThrow(new AdminAuthenticationExpiredException("expired"));

    try {
      controller.passwordlessWait(new AdminPasswordlessWaitRequestDto(10, null), httpRequest);
    } catch (AdminAuthenticationExpiredException e) {
      // expected
    }

    verify(auditLogService).log(auditCaptor.capture());
    AuditLog log = auditCaptor.getValue();
    assertEquals(AdminAuditConstants.LOGIN_MFA_EXPIRED, log.getEventAction());
    assertEquals(EventStatus.FAILURE, log.getEventStatus());
  }

  @Test
  @DisplayName("passwordlessWait rejected emits login_mfa_rejected FAILURE audit")
  void rejected_emitsRejectedAudit() {
    when(authService.findAuditContextForAuthAttempt(10)).thenReturn(Optional.of(auditCtx));
    when(authService.waitForPasswordlessAuth(10, null))
        .thenThrow(new AdminAuthenticationRejectedException("no"));

    try {
      controller.passwordlessWait(new AdminPasswordlessWaitRequestDto(10, null), httpRequest);
    } catch (AdminAuthenticationRejectedException e) {
      // expected
    }

    verify(auditLogService).log(auditCaptor.capture());
    assertEquals(AdminAuditConstants.LOGIN_MFA_REJECTED, auditCaptor.getValue().getEventAction());
  }

  @Test
  @DisplayName("passwordlessWait does not emit session audit when service throws before success")
  void failure_doesNotEmitSuccessAudit() {
    when(authService.findAuditContextForAuthAttempt(10)).thenReturn(Optional.of(auditCtx));
    when(authService.waitForPasswordlessAuth(10, null))
        .thenThrow(new AdminAuthenticationExpiredException("expired"));

    try {
      controller.passwordlessWait(new AdminPasswordlessWaitRequestDto(10, null), httpRequest);
    } catch (AdminAuthenticationExpiredException e) {
      // expected
    }

    verify(auditLogService, never())
        .log(
            org.mockito.ArgumentMatchers.argThat(
                l ->
                    AdminAuditConstants.LOGIN_MFA_SESSION_ISSUED.equals(
                        ((AuditLog) l).getEventAction())));
  }

  @Test
  @DisplayName("findAuditContext empty still logs failure with null tenant")
  void emptyContext_stillLogsFailure() {
    when(authService.findAuditContextForAuthAttempt(99)).thenReturn(Optional.empty());
    when(authService.waitForPasswordlessAuth(99, null))
        .thenThrow(new AdminAuthenticationExpiredException("expired"));

    try {
      controller.passwordlessWait(new AdminPasswordlessWaitRequestDto(99, null), httpRequest);
    } catch (AdminAuthenticationExpiredException e) {
      // expected
    }

    verify(auditLogService).log(auditCaptor.capture());
    AuditLog log = auditCaptor.getValue();
    assertEquals(AdminAuditConstants.LOGIN_MFA_EXPIRED, log.getEventAction());
    assertEquals(EventStatus.FAILURE, log.getEventStatus());
  }
}
