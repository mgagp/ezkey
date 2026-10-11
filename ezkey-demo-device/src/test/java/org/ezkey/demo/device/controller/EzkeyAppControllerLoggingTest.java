/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: EzkeyAppControllerLoggingTest
 * Description: Output-capture — demo-device auth logs must not leak tokens/signatures/challenges (#753).
 */

package org.ezkey.demo.device.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.ezkey.demo.device.service.AuthApiService;
import org.ezkey.demo.device.service.DeviceCryptoService;
import org.ezkey.demo.device.service.EnrollmentStoreService;
import org.ezkey.demo.device.service.EnrollmentStoreService.Record;
import org.ezkey.demodevice.generated.dto.AuthAttemptPendingResponseDto;
import org.ezkey.demodevice.generated.dto.AuthAttemptRespondResponseDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

/**
 * Output-capture for {@link EzkeyAppController}: INFO logs may include enrollmentId and
 * authAttemptId only — never proof tokens, signatures, or challenge values (issue #753).
 *
 * @since 2026
 */
@ExtendWith(MockitoExtension.class)
class EzkeyAppControllerLoggingTest {

  private static final int ENROLLMENT_ID = 42;
  private static final int AUTH_ATTEMPT_ID = 77;
  private static final String PROOF_TOKEN = "proof-token-SECRET-VALUE-do-not-log";
  private static final String INTEGRATION_SIG = "integration-sig-SECRET-VALUE-do-not-log";
  private static final String DEVICE_SIG = "device-sig-SECRET-VALUE-do-not-log";
  private static final String ENROLLMENT_PROOF = "enrollment-proof-SECRET-VALUE-do-not-log";
  private static final String DEVICE_PROOF = "device-proof-SECRET-VALUE-do-not-log";
  private static final String CHALLENGE = "654321";
  private static final String AUTH_API = "http://localhost:8080";

  @Mock private AuthApiService authApiService;
  @Mock private DeviceCryptoService cryptoService;
  @Mock private EnrollmentStoreService storeService;

  private EzkeyAppController controller;
  private ListAppender<ILoggingEvent> logAppender;
  private Logger controllerLogger;
  private Level previousLevel;

  @BeforeEach
  void setUp() {
    controller = new EzkeyAppController(authApiService, cryptoService, storeService, AUTH_API);
    controllerLogger = (Logger) LoggerFactory.getLogger(EzkeyAppController.class);
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
  @DisplayName("pending auth check logs ids only — never DTO secrets")
  void enrollmentAuthDoesNotLogTokensOrSignatures() {
    when(storeService.load(ENROLLMENT_ID)).thenReturn(Optional.of(sampleRecord()));
    when(cryptoService.generateProofToken()).thenReturn(DEVICE_PROOF);
    when(cryptoService.signStringToBase64(anyString(), anyString())).thenReturn(DEVICE_SIG);

    AuthAttemptPendingResponseDto pending = new AuthAttemptPendingResponseDto();
    pending.authAttemptId(AUTH_ATTEMPT_ID);
    pending.authAttemptProofToken(PROOF_TOKEN);
    pending.authAttemptProofTokenSignedByIntegration(INTEGRATION_SIG);
    pending.authAttemptChallengeRequired(true);
    pending.contextTitle("Pay");
    pending.contextMessage("Approve $10");

    when(authApiService.pending(any(), anyString())).thenReturn(Mono.just(pending));
    when(cryptoService.validateSignature(anyString(), eq(INTEGRATION_SIG), anyString()))
        .thenReturn(true);

    Model model = new ConcurrentModel();
    controller.enrollmentAuth(ENROLLMENT_ID, model, new MockHttpSession());

    String joined = joinedMessages();
    assertThat(joined).contains(String.valueOf(ENROLLMENT_ID));
    assertThat(joined).contains(String.valueOf(AUTH_ATTEMPT_ID));
    assertNoSecrets(joined);
  }

  @Test
  @DisplayName("respond logs ids only — never challenge, tokens, or signatures")
  void respondToAuthDoesNotLogChallengeOrTokens() {
    when(storeService.load(ENROLLMENT_ID)).thenReturn(Optional.of(sampleRecord()));
    when(cryptoService.signStringToBase64(anyString(), anyString())).thenReturn(DEVICE_SIG);

    AuthAttemptRespondResponseDto respond = new AuthAttemptRespondResponseDto();
    respond.authAttemptId(AUTH_ATTEMPT_ID);
    respond.authAttemptResult(AuthAttemptRespondResponseDto.AuthAttemptResultEnum.APPROVED);
    respond.authAttemptMessage("ok");
    respond.authAttemptProofTokenResultSignedByIntegration(INTEGRATION_SIG);

    when(authApiService.respond(any(), anyString())).thenReturn(Mono.just(respond));
    when(cryptoService.validateSignature(anyString(), eq(INTEGRATION_SIG), anyString()))
        .thenReturn(true);

    Model model = new ConcurrentModel();
    controller.respondToAuth(
        ENROLLMENT_ID, AUTH_ATTEMPT_ID, true, CHALLENGE, PROOF_TOKEN, model, new MockHttpSession());

    String joined = joinedMessages();
    assertThat(joined).contains(String.valueOf(ENROLLMENT_ID));
    assertThat(joined).contains(String.valueOf(AUTH_ATTEMPT_ID));
    assertThat(joined).doesNotContain(CHALLENGE);
    assertNoSecrets(joined);
  }

  @Test
  @DisplayName("bind error logs status and problem type only — never response body")
  void bindEnrollmentDoesNotLogErrorBody() {
    String leakBody =
        "{\"type\":\"https://ezkey.org/problems/bind-failed\",\"title\":\"x\","
            + "\"detail\":\"body-SECRET-VALUE-do-not-log\"}";
    WebClientResponseException error =
        WebClientResponseException.create(
            400,
            "Bad Request",
            HttpHeaders.EMPTY,
            leakBody.getBytes(StandardCharsets.UTF_8),
            StandardCharsets.UTF_8);
    when(authApiService.bind(eq(ENROLLMENT_ID), anyString(), anyString()))
        .thenReturn(Mono.error(error));

    Model model = new ConcurrentModel();
    controller.bindEnrollment(ENROLLMENT_ID, ENROLLMENT_PROOF, null, model);

    String joined = joinedMessages();
    assertThat(joined).contains(String.valueOf(ENROLLMENT_ID));
    assertThat(joined).contains("status=400");
    assertThat(joined).contains("https://ezkey.org/problems/bind-failed");
    assertThat(joined).doesNotContain("body-SECRET-VALUE-do-not-log");
    assertThat(joined).doesNotContain(ENROLLMENT_PROOF);
    assertThat(joined).doesNotContain(leakBody);
  }

  @Test
  @DisplayName("invalid challenge format warn does not echo the entered value")
  void invalidChallengeFormatDoesNotLogEnteredValue() {
    when(storeService.load(ENROLLMENT_ID)).thenReturn(Optional.of(sampleRecord()));
    when(cryptoService.signStringToBase64(anyString(), anyString())).thenReturn(DEVICE_SIG);

    String badChallenge = "not-a-number-SECRET";
    Model model = new ConcurrentModel();
    controller.respondToAuth(
        ENROLLMENT_ID,
        AUTH_ATTEMPT_ID,
        true,
        badChallenge,
        PROOF_TOKEN,
        model,
        new MockHttpSession());

    String joined = joinedMessages();
    assertThat(joined).contains(String.valueOf(AUTH_ATTEMPT_ID));
    assertThat(joined).doesNotContain(badChallenge);
    assertThat(joined).doesNotContain(PROOF_TOKEN);
  }

  private String joinedMessages() {
    return logAppender.list.stream()
        .map(ILoggingEvent::getFormattedMessage)
        .reduce("", (a, b) -> a + "\n" + b);
  }

  private static void assertNoSecrets(String joined) {
    assertThat(joined).doesNotContain(PROOF_TOKEN);
    assertThat(joined).doesNotContain(INTEGRATION_SIG);
    assertThat(joined).doesNotContain(DEVICE_SIG);
    assertThat(joined).doesNotContain(ENROLLMENT_PROOF);
    assertThat(joined).doesNotContain(DEVICE_PROOF);
    assertThat(joined).doesNotContain("SECRET-VALUE");
  }

  private static Record sampleRecord() {
    return new Record(
        ENROLLMENT_ID,
        1,
        "Demo User",
        AUTH_API + "/enroll",
        "integration-public-key",
        ENROLLMENT_PROOF,
        "device-public-key",
        "device-private-key",
        true,
        "demo",
        null,
        "Acme",
        "Acme desc",
        null,
        2,
        "Tenant",
        "Tenant desc",
        "NONE");
  }
}
