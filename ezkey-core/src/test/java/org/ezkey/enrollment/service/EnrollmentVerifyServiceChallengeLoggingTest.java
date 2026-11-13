/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: EnrollmentVerifyServiceChallengeLoggingTest
 * Description: Verify failure logs must not include expected/received challenge values.
 */

package org.ezkey.enrollment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.enrollment.domain.EnrollmentVerifyRequest;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.exception.auth.EnrollmentVerifyFailedException;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.ezkey.service.EntityEligibilityService;
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
 * Output-capture: challenge mismatch logs enrollment ID only (issue #751 re-review).
 *
 * @since 2026
 */
@ExtendWith(MockitoExtension.class)
class EnrollmentVerifyServiceChallengeLoggingTest {

  private static final int EXPECTED_CHALLENGE = 654321;
  private static final int RECEIVED_CHALLENGE = 111222;

  @Mock private EnrollmentRepository enrollmentRepository;
  @Mock private SignatureService signatureService;
  @Mock private EzkeyAdminRepository ezkeyAdminRepository;
  @Mock private EntityEligibilityService eligibilityService;
  @Mock private EnrollmentTxHelper enrollmentTxHelper;

  private EnrollmentVerifyService verifyService;
  private ListAppender<ILoggingEvent> logAppender;
  private Logger serviceLogger;
  private Level previousLevel;

  @BeforeEach
  void setUp() {
    verifyService =
        new EnrollmentVerifyService(
            enrollmentRepository,
            ezkeyAdminRepository,
            eligibilityService,
            signatureService,
            enrollmentTxHelper);
    serviceLogger = (Logger) LoggerFactory.getLogger(EnrollmentVerifyService.class);
    previousLevel = serviceLogger.getLevel();
    serviceLogger.setLevel(Level.WARN);
    logAppender = new ListAppender<>();
    logAppender.start();
    serviceLogger.addAppender(logAppender);
  }

  @AfterEach
  void tearDown() {
    serviceLogger.detachAppender(logAppender);
    serviceLogger.setLevel(previousLevel);
  }

  @Test
  @DisplayName("challenge mismatch logs enrollmentId and never expected/received challenge values")
  void challengeMismatchDoesNotLogChallengeValues() {
    EnrollmentVerifyRequest request = new EnrollmentVerifyRequest();
    request.setEnrollmentId(42);
    request.setDevicePublicKey("device-public-key");
    request.setEnrollmentProofTokenSigned("signature");
    request.setChallengeResponse(RECEIVED_CHALLENGE);

    Enrollment enrollment = new Enrollment();
    enrollment.setEnrollmentId(42);
    enrollment.setStatus(EnrollmentStatus.BOUND);
    enrollment.setEnrollmentProofToken("token");
    enrollment.setEnrollmentChallenge(EXPECTED_CHALLENGE);
    enrollment.setCreatedAt(OffsetDateTime.now());
    enrollment.setExpiresAt(OffsetDateTime.now().plusHours(1));
    enrollment.setDevicePublicKey("device-public-key");

    when(enrollmentRepository.findById(42)).thenReturn(Optional.of(enrollment));
    when(ezkeyAdminRepository.findByEnrollmentId(42)).thenReturn(Optional.empty());
    when(signatureService.validateSignature(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.eq("signature"),
            org.mockito.ArgumentMatchers.eq("device-public-key")))
        .thenReturn(true);
    when(enrollmentRepository.existsByDevicePublicKeyHash(org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(false);

    assertThatThrownBy(() -> verifyService.verify(request))
        .isInstanceOf(EnrollmentVerifyFailedException.class);

    String joined =
        logAppender.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .reduce("", (a, b) -> a + "\n" + b);

    assertThat(joined).contains("enrollment ID: 42");
    assertThat(joined).doesNotContain(String.valueOf(EXPECTED_CHALLENGE));
    assertThat(joined).doesNotContain(String.valueOf(RECEIVED_CHALLENGE));
    assertThat(joined).doesNotContain("Expected:");
    assertThat(joined).doesNotContain("Received:");
  }
}
