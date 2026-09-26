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
import org.ezkey.admin.dto.response.AdminSessionResponseDto;
import org.ezkey.admin.security.AdminAuthRequestAttributes;
import org.ezkey.admin.security.AdminCsrfTokenService;
import org.ezkey.admin.security.AdminPrincipal;
import org.ezkey.admin.security.AdminRateLimitFilter;
import org.ezkey.admin.security.AdminSessionCookieService;
import org.ezkey.admin.service.AdminAuthService;
import org.ezkey.admin.service.AdminProvisioningService;
import org.ezkey.admin.service.AdminRecoveryService;
import org.ezkey.admin.service.EvaluatorTempSessionService;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminType;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminAuthControllerBrowserSessionCookieTest {

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
  @Mock private EvaluatorTempSessionService evaluatorTempSessionService;
  @Mock private HttpServletRequest httpRequest;
  @Mock private HttpServletResponse httpResponse;

  private AdminAuthController controller;

  @BeforeEach
  void setUp() {
    SecurityContextHolder.clearContext();
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
            csrfTokenService,
            evaluatorTempSessionService);
    when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
    when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
    when(httpRequest.getHeader("User-Agent")).thenReturn("JUnit");
  }

  @Test
  @DisplayName("me returns non-secret session metadata and refreshes CSRF in cookie mode")
  void me_cookieMode_returnsMetadataAndCsrf() {
    when(browserSessionCookieProperties.isBrowserSessionCookieEnabled()).thenReturn(true);
    OffsetDateTime exp = OffsetDateTime.now().plusHours(2);
    when(httpRequest.getAttribute(AdminAuthRequestAttributes.USERNAME)).thenReturn("u1");
    when(httpRequest.getAttribute(AdminAuthRequestAttributes.EXPIRES_AT)).thenReturn(exp);
    when(httpRequest.getAttribute(AdminAuthRequestAttributes.PLAIN_TOKEN))
        .thenReturn("secret-token");
    when(httpRequest.getAttribute(AdminAuthRequestAttributes.AUTH_SOURCE))
        .thenReturn(AdminAuthRequestAttributes.AuthSource.COOKIE);
    when(httpRequest.getAttribute(AdminAuthRequestAttributes.TENANT_NAME)).thenReturn(null);
    when(csrfTokenService.createToken("secret-token")).thenReturn("csrf-token");
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new AdminPrincipal(9, AdminType.GLOBAL_ADMIN, null, null), null));

    ResponseEntity<AdminSessionResponseDto> response = controller.me(httpRequest, httpResponse);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals("u1", response.getBody().username());
    assertEquals("GLOBAL_ADMIN", response.getBody().adminType());
    assertEquals(exp, response.getBody().expiresAt());
    assertNull(response.getBody().tenantName());
    assertEquals("csrf-token", response.getBody().csrfToken());
    verify(sessionCookieService).addCsrfCookie(httpResponse, "csrf-token", exp);
  }

  @Test
  @DisplayName("me returns tenant display name for tenant-scoped administrators")
  void me_tenantAdmin_includesTenantName() {
    OffsetDateTime exp = OffsetDateTime.now().plusHours(2);
    when(httpRequest.getAttribute(AdminAuthRequestAttributes.USERNAME)).thenReturn("tenant.admin");
    when(httpRequest.getAttribute(AdminAuthRequestAttributes.EXPIRES_AT)).thenReturn(exp);
    when(httpRequest.getAttribute(AdminAuthRequestAttributes.PLAIN_TOKEN)).thenReturn("tok");
    when(httpRequest.getAttribute(AdminAuthRequestAttributes.AUTH_SOURCE))
        .thenReturn(AdminAuthRequestAttributes.AuthSource.BEARER);
    when(httpRequest.getAttribute(AdminAuthRequestAttributes.TENANT_NAME)).thenReturn("Acme Corp");
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new AdminPrincipal(12, AdminType.TENANT_ADMIN, 3, null), null));

    ResponseEntity<AdminSessionResponseDto> response = controller.me(httpRequest, httpResponse);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals("tenant.admin", response.getBody().username());
    assertEquals("TENANT_ADMIN", response.getBody().adminType());
    assertEquals(Integer.valueOf(3), response.getBody().tenantId());
    assertEquals("Acme Corp", response.getBody().tenantName());
    assertNull(response.getBody().csrfToken());
  }

  @Test
  @DisplayName("When browser session cookie enabled, passwordlessWait strips token and sets cookie")
  void passwordlessWait_cookieMode_omitsTokenAndSetsCookie() {
    when(browserSessionCookieProperties.isBrowserSessionCookieEnabled()).thenReturn(true);
    when(authService.findAuditContextForAuthAttempt(1)).thenReturn(Optional.empty());
    OffsetDateTime exp = OffsetDateTime.now().plusHours(2);
    AdminLoginResponseDto ok =
        AdminLoginResponseDto.success("secret-token", "GLOBAL_ADMIN", "u1", exp, 9, null, null);
    when(authService.waitForPasswordlessAuth(1, null, "waiter-secret")).thenReturn(ok);
    when(browserSessionCookieProperties.getBrowserSessionCookieName())
        .thenReturn("EZKEY_ADMIN_SESSION");
    when(csrfTokenService.createToken("secret-token")).thenReturn("csrf-token");

    ResponseEntity<AdminLoginResponseDto> response =
        controller.passwordlessWait(
            new AdminPasswordlessWaitRequestDto(1, null, "waiter-secret"),
            httpRequest,
            httpResponse);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNull(response.getBody().token());
    assertEquals("csrf-token", response.getBody().csrfToken());
    verify(sessionCookieService).addSessionCookie(httpResponse, "secret-token", exp);
    verify(sessionCookieService).addCsrfCookie(httpResponse, "csrf-token", exp);
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
