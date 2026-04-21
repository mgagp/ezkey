/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 *
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.admin.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.ezkey.admin.config.AdminBrowserSessionCookieProperties;
import org.ezkey.admin.config.AdminRecoveryProperties;
import org.ezkey.admin.dto.request.AdminPasswordlessWaitRequestDto;
import org.ezkey.admin.dto.response.AdminLoginResponseDto;
import org.ezkey.admin.security.AdminRateLimitFilter;
import org.ezkey.admin.security.AdminSessionCookieService;
import org.ezkey.admin.service.AdminAuthService;
import org.ezkey.admin.service.AdminRecoveryService;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminAuthControllerBrowserSessionCookieTest {

  @Mock private AdminAuthService authService;
  @Mock private AdminRecoveryService recoveryService;
  @Mock private AuditLogService auditLogService;
  @Mock private AdminRateLimitFilter rateLimitFilter;
  @Mock private AdminRecoveryProperties recoveryProperties;
  @Mock private EzkeyAdminRepository adminRepository;
  @Mock private AdminBrowserSessionCookieProperties browserSessionCookieProperties;
  @Mock private AdminSessionCookieService sessionCookieService;
  @Mock private HttpServletRequest httpRequest;
  @Mock private HttpServletResponse httpResponse;

  private AdminAuthController controller;

  @BeforeEach
  void setUp() {
    controller =
        new AdminAuthController(
            authService,
            recoveryService,
            auditLogService,
            rateLimitFilter,
            recoveryProperties,
            adminRepository,
            browserSessionCookieProperties,
            sessionCookieService);
    when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
    when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
    when(httpRequest.getHeader("User-Agent")).thenReturn("JUnit");
  }

  @Test
  @DisplayName("When browser session cookie enabled, passwordlessWait strips token and sets cookie")
  void passwordlessWait_cookieMode_omitsTokenAndSetsCookie() {
    when(browserSessionCookieProperties.isBrowserSessionCookieEnabled()).thenReturn(true);
    when(authService.findAuditContextForAuthAttempt(1)).thenReturn(Optional.empty());
    OffsetDateTime exp = OffsetDateTime.now().plusHours(2);
    AdminLoginResponseDto ok =
        AdminLoginResponseDto.success("secret-token", "GLOBAL_ADMIN", "u1", exp, 9, null);
    when(authService.waitForPasswordlessAuth(1, null)).thenReturn(ok);
    when(browserSessionCookieProperties.getBrowserSessionCookieName())
        .thenReturn("EZKEY_ADMIN_SESSION");

    ResponseEntity<AdminLoginResponseDto> response =
        controller.passwordlessWait(
            new AdminPasswordlessWaitRequestDto(1, null), httpRequest, httpResponse);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNull(response.getBody().token());
    verify(sessionCookieService).addSessionCookie(httpResponse, "secret-token", exp);
  }

  @Test
  @DisplayName("logout without Authorization uses session cookie when cookie mode enabled")
  void logout_cookieOnly_invalidatesAndClearsCookie() {
    when(browserSessionCookieProperties.isBrowserSessionCookieEnabled()).thenReturn(true);
    when(browserSessionCookieProperties.getBrowserSessionCookieName())
        .thenReturn("EZKEY_ADMIN_SESSION");
    when(httpRequest.getCookies())
        .thenReturn(new Cookie[] {new Cookie("EZKEY_ADMIN_SESSION", "tok")});
    when(authService.getAdminIdForToken("tok")).thenReturn(5);
    when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);

    ResponseEntity<Void> response = controller.logout(null, httpRequest, httpResponse);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    verify(authService).logout("tok");
    verify(sessionCookieService).clearSessionCookie(httpResponse);
  }

  @Test
  @DisplayName("logout with missing token returns 401")
  void logout_noCredentials_returns401() {
    when(browserSessionCookieProperties.isBrowserSessionCookieEnabled()).thenReturn(true);
    when(httpRequest.getCookies()).thenReturn(null);

    ResponseEntity<Void> response = controller.logout(null, httpRequest, httpResponse);

    assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
  }
}
