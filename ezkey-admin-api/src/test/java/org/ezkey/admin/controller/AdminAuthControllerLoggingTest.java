/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AdminAuthControllerLoggingTest
 * Description: Username/clientIp CR/LF must be neutralized in auth logs (#801 Lot D).
 */

package org.ezkey.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.ezkey.admin.config.AdminBrowserSessionCookieProperties;
import org.ezkey.admin.config.AdminRecoveryProperties;
import org.ezkey.admin.dto.request.AdminLoginRequestDto;
import org.ezkey.admin.dto.request.AdminRecoveryRequestDto;
import org.ezkey.admin.dto.response.AdminLoginResponseDto;
import org.ezkey.admin.security.AdminCsrfTokenService;
import org.ezkey.admin.security.AdminRateLimitFilter;
import org.ezkey.admin.security.AdminSessionCookieService;
import org.ezkey.admin.service.AdminAuthService;
import org.ezkey.admin.service.AdminProvisioningService;
import org.ezkey.admin.service.AdminRecoveryService;
import org.ezkey.admin.service.EvaluatorTempSessionService;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Output-capture tests: caller-controlled username and client IP in AdminAuthController logs go
 * through {@code LogSanitizer} while remaining readable (CodeQL #62, #70–#72 / issue #801).
 *
 * @since 2026
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminAuthControllerLoggingTest {

  private static final String INJECTED_USERNAME = "admin\r\ninjected";
  private static final String INJECTED_IP = "203.0.113.9\r\ninjected";
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
  @Mock private EvaluatorTempSessionService evaluatorTempSessionService;
  @Mock private HttpServletRequest httpRequest;

  private AdminAuthController controller;
  private ListAppender<ILoggingEvent> logAppender;
  private Logger controllerLogger;
  private Level previousLevel;

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
            csrfTokenService,
            evaluatorTempSessionService);

    when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
    when(httpRequest.getRemoteAddr()).thenReturn(INJECTED_IP);
    when(httpRequest.getHeader("User-Agent")).thenReturn("JUnit");
    when(browserSessionCookieProperties.isBrowserSessionCookieEnabled()).thenReturn(false);
    when(adminRepository.findByUsername(any())).thenReturn(Optional.empty());

    controllerLogger = (Logger) LoggerFactory.getLogger(AdminAuthController.class);
    previousLevel = controllerLogger.getLevel();
    controllerLogger.setLevel(Level.INFO);
    logAppender = new ListAppender<>();
    logAppender.start();
    controllerLogger.addAppender(logAppender);
  }

  @AfterEach
  void tearDown() {
    controllerLogger.detachAppender(logAppender);
    controllerLogger.setLevel(previousLevel);
  }

  @Test
  @DisplayName("login success INFO does not echo CR/LF from username or client IP (LogSanitizer)")
  void loginSuccessSanitizesUsernameAndClientIp() {
    when(authService.authenticate(any()))
        .thenReturn(
            AdminLoginResponseDto.success(
                "tok",
                "GLOBAL_ADMIN",
                INJECTED_USERNAME,
                OffsetDateTime.now().plusHours(1),
                1,
                null,
                null));

    HttpServletResponse httpResponse = new MockHttpServletResponse();
    controller.login(
        new AdminLoginRequestDto(INJECTED_USERNAME, false, false), httpRequest, httpResponse);

    String joined = joinedMessages();
    assertThat(joined).contains("Login successful");
    assertThat(joined).doesNotContain("\r");
    assertThat(joined).doesNotContain("\ninjected");
    assertThat(joined).contains("admin__injected");
    assertThat(joined).contains("203.0.113.9__injected");
  }

  @Test
  @DisplayName("recovery attempt WARN does not echo CR/LF from username or client IP (LogSanitizer)")
  void recoveryAttemptSanitizesUsernameAndClientIp() {
    when(recoveryService.validateRecoveryCode(INJECTED_USERNAME, SAMPLE_CODE))
        .thenReturn("temp-recovery-token");
    EzkeyAdmin admin = new EzkeyAdmin();
    admin.setAdminId(7);
    when(recoveryService.validateRecoveryToken("temp-recovery-token")).thenReturn(admin);
    when(recoveryProperties.getTempTokenDurationMinutes()).thenReturn(15);

    controllerLogger.setLevel(Level.WARN);
    controller.recover(new AdminRecoveryRequestDto(INJECTED_USERNAME, SAMPLE_CODE), httpRequest);

    String joined = joinedMessages();
    assertThat(joined).contains("Recovery attempt");
    assertThat(joined).doesNotContain("\r");
    assertThat(joined).doesNotContain("\ninjected");
    assertThat(joined).contains("admin__injected");
    assertThat(joined).contains("203.0.113.9__injected");
  }

  private String joinedMessages() {
    return logAppender.list.stream()
        .map(ILoggingEvent::getFormattedMessage)
        .reduce("", (a, b) -> a + "\n" + b);
  }
}
