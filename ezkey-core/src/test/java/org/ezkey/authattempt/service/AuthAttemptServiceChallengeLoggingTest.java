/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AuthAttemptServiceChallengeLoggingTest
 * Description: Auth-attempt challenge codes must not appear in DEBUG logs (#750).
 */

package org.ezkey.authattempt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.Collections;
import java.util.Optional;
import org.ezkey.authattempt.domain.AuthAttemptCreateRequest;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.ezkey.authattempt.domain.repository.AuthAttemptRepository;
import org.ezkey.config.EzkeyCoreProperties;
import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.signature.SignatureService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

/**
 * Output-capture tests: auth-attempt challenge values stay out of DEBUG logs (issue #750).
 *
 * @since 2026
 */
@ExtendWith(MockitoExtension.class)
class AuthAttemptServiceChallengeLoggingTest {

  private static final int CHALLENGE_CODE = 987654;
  private static final int ENROLLMENT_ID = 42;

  @Mock private AuthAttemptRepository authAttemptRepository;
  @Mock private EnrollmentRepository enrollmentRepository;
  @Mock private SignatureService signatureService;
  @Mock private AuthAttemptPendingService pendingService;
  @Mock private AuthAttemptRespondService respondService;
  @Mock private AuthAttemptWaitService waitService;

  private AuthAttemptService authAttemptService;
  private ListAppender<ILoggingEvent> logAppender;
  private Logger authLogger;
  private Level previousLevel;

  @BeforeEach
  void setUp() {
    EzkeyCoreProperties properties = new EzkeyCoreProperties();
    properties.getAuthAttempt().setChallengeDigits(6);
    authAttemptService =
        new AuthAttemptService(
            authAttemptRepository,
            enrollmentRepository,
            signatureService,
            properties,
            pendingService,
            respondService,
            waitService);

    authLogger = (Logger) LoggerFactory.getLogger(AuthAttemptService.class);
    previousLevel = authLogger.getLevel();
    authLogger.setLevel(Level.DEBUG);
    logAppender = new ListAppender<>();
    logAppender.start();
    authLogger.addAppender(logAppender);
  }

  @AfterEach
  void tearDown() {
    authLogger.detachAppender(logAppender);
    authLogger.setLevel(previousLevel);
  }

  @Test
  @DisplayName("create with challenge logs enrollment id and never the challenge code")
  void createDoesNotLogChallengeCode() {
    Enrollment enrollment = new Enrollment();
    enrollment.setEnrollmentId(ENROLLMENT_ID);
    enrollment.setStatus(EnrollmentStatus.VERIFIED);
    enrollment.setActive(true);
    enrollment.setAuthAttemptChallengeRequired(true);

    when(enrollmentRepository.findById(ENROLLMENT_ID)).thenReturn(Optional.of(enrollment));
    when(authAttemptRepository.findByEnrollmentIdAndStatusIn(anyInt(), anyList()))
        .thenReturn(Collections.emptyList());
    when(signatureService.generateSecureChallenge(6)).thenReturn(CHALLENGE_CODE);
    when(signatureService.generateProofToken()).thenReturn("test-proof-token");

    AuthAttempt saved = new AuthAttempt();
    saved.setAuthAttemptId(9);
    saved.setAuthAttemptChallenge(CHALLENGE_CODE);
    when(authAttemptRepository.save(any(AuthAttempt.class))).thenReturn(saved);

    AuthAttemptCreateRequest request = new AuthAttemptCreateRequest();
    request.setEnrollmentId(ENROLLMENT_ID);
    request.setChallengeRequested(true);
    authAttemptService.create(request);

    String joined =
        logAppender.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .reduce("", (a, b) -> a + "\n" + b);

    assertThat(joined).contains("enrollment " + ENROLLMENT_ID);
    assertThat(joined).doesNotContain(String.valueOf(CHALLENGE_CODE));
  }
}
