/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: PublicEvaluatorSignupControllerTest
 * Description: Unit tests for public evaluator signup endpoint.
 */

package org.ezkey.admin.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.OffsetDateTime;
import org.ezkey.admin.config.AdminBrowserSessionCookieProperties;
import org.ezkey.admin.dto.request.EvaluatorOnboardingReissueRequestDto;
import org.ezkey.admin.dto.request.EvaluatorSelfRegistrationRequestDto;
import org.ezkey.admin.dto.response.EvaluatorOnboardingReissueResponseDto;
import org.ezkey.admin.dto.response.EvaluatorSelfRegistrationResponseDto;
import org.ezkey.admin.security.AdminCsrfTokenService;
import org.ezkey.admin.security.AdminSessionCookieService;
import org.ezkey.admin.service.EvaluatorSelfRegistrationService;
import org.ezkey.audit.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
@DisplayName("PublicEvaluatorSignupController")
class PublicEvaluatorSignupControllerTest {

  @Mock private EvaluatorSelfRegistrationService evaluatorSelfRegistrationService;
  @Mock private AuditLogService auditLogService;
  @Mock private AdminBrowserSessionCookieProperties browserSessionCookieProperties;
  @Mock private AdminSessionCookieService sessionCookieService;
  @Mock private AdminCsrfTokenService csrfTokenService;
  @Mock private HttpServletRequest httpRequest;
  @Mock private HttpServletResponse httpResponse;

  private PublicEvaluatorSignupController controller;

  @BeforeEach
  void setUp() {
    controller =
        new PublicEvaluatorSignupController(
            evaluatorSelfRegistrationService,
            auditLogService,
            browserSessionCookieProperties,
            sessionCookieService,
            csrfTokenService);
  }

  @Test
  @DisplayName("returns 404 when feature disabled — no session fields advertised")
  void disabled_returns404() {
    when(evaluatorSelfRegistrationService.isEnabled()).thenReturn(false);

    ResponseEntity<EvaluatorSelfRegistrationResponseDto> response =
        controller.evaluatorSignup(
            new EvaluatorSelfRegistrationRequestDto("Lab"), httpRequest, httpResponse);

    assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    assertNull(response.getBody());
    verify(evaluatorSelfRegistrationService, never()).register(any(), any());
  }

  @Test
  @DisplayName("returns 201 with activation and bootstrap session fields when enabled (Mode A)")
  void enabled_returns201_withBootstrapSession() {
    when(evaluatorSelfRegistrationService.isEnabled()).thenReturn(true);
    when(browserSessionCookieProperties.isBrowserSessionCookieEnabled()).thenReturn(false);
    when(httpRequest.getAttribute(org.ezkey.audit.util.ClientContext.CLIENT_IP_REQUEST_ATTRIBUTE))
        .thenReturn("203.0.113.8");
    OffsetDateTime sessionExpires = OffsetDateTime.now().plusHours(8);
    EvaluatorSelfRegistrationResponseDto body =
        new EvaluatorSelfRegistrationResponseDto(
            "WXYZ-5678",
            OffsetDateTime.now().plusDays(7),
            "https://exp1-admin-ui.ezkey.org",
            "https://ezkey.org/exp1-guided-tour.html",
            "eval-cafebabe",
            "ezkey_bootstrap_abc",
            sessionExpires,
            "eval-admin-cafebabe");
    when(evaluatorSelfRegistrationService.register(eq("Lab"), eq("203.0.113.8"))).thenReturn(body);

    ResponseEntity<EvaluatorSelfRegistrationResponseDto> response =
        controller.evaluatorSignup(
            new EvaluatorSelfRegistrationRequestDto("Lab"), httpRequest, httpResponse);

    assertEquals(HttpStatus.CREATED, response.getStatusCode());
    assertEquals(body, response.getBody());
    assertEquals("ezkey_bootstrap_abc", response.getBody().sessionToken());
    assertEquals("eval-admin-cafebabe", response.getBody().username());
    verify(auditLogService).log(any());
    verify(sessionCookieService, never()).addSessionCookie(any(), any(), any());
  }

  @Test
  @DisplayName("Mode B strips sessionToken from JSON after setting HttpOnly cookie")
  void enabled_cookieMode_omitsSessionTokenFromBody() {
    when(evaluatorSelfRegistrationService.isEnabled()).thenReturn(true);
    when(browserSessionCookieProperties.isBrowserSessionCookieEnabled()).thenReturn(true);
    when(httpRequest.getAttribute(org.ezkey.audit.util.ClientContext.CLIENT_IP_REQUEST_ATTRIBUTE))
        .thenReturn("203.0.113.8");
    OffsetDateTime sessionExpires = OffsetDateTime.now().plusHours(8);
    EvaluatorSelfRegistrationResponseDto body =
        new EvaluatorSelfRegistrationResponseDto(
            "WXYZ-5678",
            OffsetDateTime.now().plusDays(7),
            "https://exp1-admin-ui.ezkey.org",
            "https://ezkey.org/exp1-guided-tour.html",
            "eval-cafebabe",
            "ezkey_bootstrap_abc",
            sessionExpires,
            "eval-admin-cafebabe");
    when(evaluatorSelfRegistrationService.register(eq("Lab"), eq("203.0.113.8"))).thenReturn(body);
    when(csrfTokenService.createToken("ezkey_bootstrap_abc")).thenReturn("csrf-token");

    ResponseEntity<EvaluatorSelfRegistrationResponseDto> response =
        controller.evaluatorSignup(
            new EvaluatorSelfRegistrationRequestDto("Lab"), httpRequest, httpResponse);

    assertEquals(HttpStatus.CREATED, response.getStatusCode());
    assertNull(response.getBody().sessionToken());
    assertEquals(sessionExpires, response.getBody().sessionExpiresAt());
    verify(sessionCookieService)
        .addSessionCookie(httpResponse, "ezkey_bootstrap_abc", sessionExpires);
  }

  @Test
  @DisplayName("reissue returns 404 when feature disabled")
  void reissue_disabled_returns404() {
    when(evaluatorSelfRegistrationService.isEnabled()).thenReturn(false);

    ResponseEntity<EvaluatorOnboardingReissueResponseDto> response =
        controller.evaluatorOnboardingReissue(
            new EvaluatorOnboardingReissueRequestDto("eval-admin-x"), httpRequest, httpResponse);

    assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    assertNull(response.getBody());
    verify(evaluatorSelfRegistrationService, never()).reissueOnboarding(any(), any());
  }

  @Test
  @DisplayName("reissue returns 200 with DEVICE_BIND material when enabled")
  void reissue_enabled_returns200() {
    when(evaluatorSelfRegistrationService.isEnabled()).thenReturn(true);
    when(browserSessionCookieProperties.isBrowserSessionCookieEnabled()).thenReturn(false);
    when(httpRequest.getAttribute(org.ezkey.audit.util.ClientContext.CLIENT_IP_REQUEST_ATTRIBUTE))
        .thenReturn("203.0.113.20");
    OffsetDateTime sessionExpires = OffsetDateTime.now().plusHours(8);
    EvaluatorOnboardingReissueResponseDto body =
        new EvaluatorOnboardingReissueResponseDto(
            "DEVICE_BIND",
            "eval-admin-cafebabe",
            null,
            null,
            42,
            "ezkey_proof_abc",
            123456,
            "ezkey_bootstrap_resume",
            sessionExpires,
            "https://exp1-admin-ui.ezkey.org",
            "https://ezkey.org/exp1-guided-tour.html");
    when(evaluatorSelfRegistrationService.reissueOnboarding(
            eq("eval-admin-cafebabe"), eq("203.0.113.20")))
        .thenReturn(body);

    ResponseEntity<EvaluatorOnboardingReissueResponseDto> response =
        controller.evaluatorOnboardingReissue(
            new EvaluatorOnboardingReissueRequestDto("eval-admin-cafebabe"),
            httpRequest,
            httpResponse);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals("DEVICE_BIND", response.getBody().phase());
    assertEquals("ezkey_bootstrap_resume", response.getBody().sessionToken());
    verify(auditLogService).log(any());
  }
}
