/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AdminAuthServiceChallengeLoggingTest
 * Description: Login challenge codes must not appear in AdminAuthService logs (#750).
 */

package org.ezkey.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.ezkey.admin.config.AdminTokenRotationProperties;
import org.ezkey.admin.dto.request.AdminLoginRequestDto;
import org.ezkey.authattempt.domain.AuthAttemptCreateResponse;
import org.ezkey.authattempt.domain.repository.AuthAttemptRepository;
import org.ezkey.authattempt.service.AuthAttemptService;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminType;
import org.ezkey.integration.domain.repository.AdminTokenRepository;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

/**
 * Output-capture tests: admin login challenge codes stay out of logs (issue #750).
 *
 * @since 2026
 */
@ExtendWith(MockitoExtension.class)
class AdminAuthServiceChallengeLoggingTest {

  private static final int CHALLENGE_CODE = 424242;
  private static final int AUTH_ATTEMPT_ID = 77;

  @Mock private EzkeyAdminRepository adminRepository;
  @Mock private AdminTokenRepository tokenRepository;
  @Mock private AdminTokenRotationProperties rotationProperties;
  @Mock private AuthAttemptService authAttemptService;
  @Mock private AuthAttemptRepository authAttemptRepository;
  @Mock private AdminAuthAttemptTxHelper authAttemptTxHelper;

  @InjectMocks private AdminAuthService adminAuthService;

  private ListAppender<ILoggingEvent> logAppender;
  private Logger authLogger;

  @BeforeEach
  void setUpAppender() {
    authLogger = (Logger) LoggerFactory.getLogger(AdminAuthService.class);
    logAppender = new ListAppender<>();
    logAppender.start();
    authLogger.addAppender(logAppender);
  }

  @AfterEach
  void tearDownAppender() {
    authLogger.detachAppender(logAppender);
  }

  @Test
  @DisplayName("authenticate with challenge logs authAttemptId and never the challenge code")
  void authenticateDoesNotLogChallengeCode() {
    EzkeyAdmin admin = boundAdmin("admin.docker");
    when(adminRepository.findByUsernameWithEnrollment("admin.docker"))
        .thenReturn(Optional.of(admin));

    AuthAttemptCreateResponse attemptResponse = new AuthAttemptCreateResponse();
    attemptResponse.setAuthAttemptId(AUTH_ATTEMPT_ID);
    attemptResponse.setAuthAttemptChallenge(CHALLENGE_CODE);
    attemptResponse.setExpiresAt(OffsetDateTime.now().plusMinutes(2));
    when(authAttemptTxHelper.createAuthAttempt(any())).thenReturn(attemptResponse);

    adminAuthService.authenticate(new AdminLoginRequestDto("admin.docker", true, false));

    String joined =
        logAppender.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .reduce("", (a, b) -> a + "\n" + b);

    assertThat(joined).contains("ID: " + AUTH_ATTEMPT_ID);
    assertThat(joined).doesNotContain(String.valueOf(CHALLENGE_CODE));
    assertThat(joined).doesNotContain("challenge: " + CHALLENGE_CODE);
  }

  private static EzkeyAdmin boundAdmin(String username) {
    EzkeyAdmin admin = new EzkeyAdmin(username, AdminType.GLOBAL_ADMIN);
    admin.setActive(true);
    admin.setChallengeRequired(false);
    Enrollment enrollment = new Enrollment();
    enrollment.setEnrollmentId(5);
    enrollment.setDevicePublicKey("device-public-key");
    admin.setEnrollment(enrollment);
    return admin;
  }
}
