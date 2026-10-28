/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: EzkeyAppControllerRespondConflictTest
 * Description: Respond outcomes — 409/503/429/Q9 unconfirmed, claim clear reorder (#943).
 */

package org.ezkey.demo.device.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import org.ezkey.demo.device.config.DemoDeviceProperties;
import org.ezkey.demo.device.service.AuthApiService;
import org.ezkey.demo.device.service.ClaimedPendingAuth;
import org.ezkey.demo.device.service.DeviceCryptoService;
import org.ezkey.demo.device.service.EnrollmentStoreService;
import org.ezkey.demo.device.service.EnrollmentStoreService.Record;
import org.ezkey.demo.device.service.PendingAuthClaimSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

/**
 * Unit tests for Demo Device respond outcome UX (#895 / #943).
 *
 * @since 2026
 */
@ExtendWith(MockitoExtension.class)
class EzkeyAppControllerRespondConflictTest {

  private static final int ENROLLMENT_ID = 42;
  private static final int AUTH_ATTEMPT_ID = 77;
  private static final String PROOF_TOKEN = "proof-token";
  private static final String DEVICE_SIG = "device-sig";
  private static final String AUTH_API = "http://localhost:8080";

  @Mock private AuthApiService authApiService;
  @Mock private DeviceCryptoService cryptoService;
  @Mock private EnrollmentStoreService storeService;

  private EzkeyAppController controller;

  @BeforeEach
  void setUp() {
    controller =
        new EzkeyAppController(
            authApiService, cryptoService, storeService, AUTH_API, new DemoDeviceProperties());
  }

  @Test
  @DisplayName("409 auth-attempt-expired → expired result, no failed/inactive, Julie copy")
  void respondMapsTypedExpired409() {
    when(storeService.load(ENROLLMENT_ID)).thenReturn(Optional.of(sampleRecord()));
    when(cryptoService.signStringToBase64(anyString(), anyString())).thenReturn(DEVICE_SIG);

    when(authApiService.respond(any(), anyString()))
        .thenReturn(Mono.error(problem(409, "auth-attempt-expired", "expired-detail")));

    Model model = new ConcurrentModel();
    MockHttpSession session = sessionWithClaim();
    String view =
        controller.respondToAuth(
            ENROLLMENT_ID, AUTH_ATTEMPT_ID, true, null, PROOF_TOKEN, model, session);

    assertThat(view).isEqualTo("phone/ezkey/auth_result");
    assertThat(model.getAttribute("expired")).isEqualTo(true);
    assertThat(model.getAttribute("failed")).isEqualTo(false);
    assertThat(model.getAttribute("inactive")).isEqualTo(false);
    assertThat(model.getAttribute("message")).isEqualTo(EzkeyAppController.MSG_AUTH_EXPIRED);
    assertNoLeak(model);
    assertThat(
            PendingAuthClaimSession.findOpen(
                session, ENROLLMENT_ID, Instant.now(), PendingAuthClaimSession.DEFAULT_TTL))
        .isEmpty();
  }

  @Test
  @DisplayName("claim still open + initiator-side expired 409 → expired screen (automated)")
  void liveClaimWithInitiatorExpiredShowsExpiredScreen() {
    when(storeService.load(ENROLLMENT_ID)).thenReturn(Optional.of(sampleRecord()));
    when(cryptoService.signStringToBase64(anyString(), anyString())).thenReturn(DEVICE_SIG);
    when(authApiService.respond(any(), anyString()))
        .thenReturn(Mono.error(problem(409, "auth-attempt-expired", "gone")));

    MockHttpSession session = sessionWithClaim();
    assertThat(
            PendingAuthClaimSession.findOpen(
                session, ENROLLMENT_ID, Instant.now(), PendingAuthClaimSession.DEFAULT_TTL))
        .isPresent();

    Model model = new ConcurrentModel();
    controller.respondToAuth(
        ENROLLMENT_ID, AUTH_ATTEMPT_ID, true, null, PROOF_TOKEN, model, session);

    assertThat(model.getAttribute("expired")).isEqualTo(true);
    assertThat(model.getAttribute("message")).isEqualTo(EzkeyAppController.MSG_AUTH_EXPIRED);
    assertThat(model.getAttribute("success")).isNotEqualTo(true);
    assertThat(model.getAttribute("denied")).isNotEqualTo(true);
  }

  @Test
  @DisplayName("409 state-conflict → inactive (no longer active), not orange failed")
  void respondMapsGeneric409AsInactive() {
    when(storeService.load(ENROLLMENT_ID)).thenReturn(Optional.of(sampleRecord()));
    when(cryptoService.signStringToBase64(anyString(), anyString())).thenReturn(DEVICE_SIG);
    when(authApiService.respond(any(), anyString()))
        .thenReturn(Mono.error(problem(409, "auth-attempt-state-conflict", "inactive")));

    Model model = new ConcurrentModel();
    controller.respondToAuth(
        ENROLLMENT_ID, AUTH_ATTEMPT_ID, true, null, PROOF_TOKEN, model, new MockHttpSession());

    assertThat(model.getAttribute("inactive")).isEqualTo(true);
    assertThat(model.getAttribute("failed")).isEqualTo(false);
    assertThat(model.getAttribute("expired")).isEqualTo(false);
    assertThat(model.getAttribute("message"))
        .isEqualTo(EzkeyAppController.MSG_AUTH_NO_LONGER_ACTIVE);
    assertNoLeak(model);
  }

  @Test
  @DisplayName("409 without type → no longer active (tolerant)")
  void respondMaps409WithoutProblemType() {
    when(storeService.load(ENROLLMENT_ID)).thenReturn(Optional.of(sampleRecord()));
    when(cryptoService.signStringToBase64(anyString(), anyString())).thenReturn(DEVICE_SIG);
    WebClientResponseException error =
        WebClientResponseException.create(
            409,
            "Conflict",
            HttpHeaders.EMPTY,
            "not-json".getBytes(StandardCharsets.UTF_8),
            StandardCharsets.UTF_8);
    when(authApiService.respond(any(), anyString())).thenReturn(Mono.error(error));

    Model model = new ConcurrentModel();
    controller.respondToAuth(
        ENROLLMENT_ID, AUTH_ATTEMPT_ID, true, null, PROOF_TOKEN, model, new MockHttpSession());

    assertThat(model.getAttribute("inactive")).isEqualTo(true);
    assertThat(model.getAttribute("message"))
        .isEqualTo(EzkeyAppController.MSG_AUTH_NO_LONGER_ACTIVE);
  }

  @Test
  @DisplayName("503 keeps claim and re-shows pending with neutral banner")
  void respond503KeepsClaimAndBanner() {
    when(storeService.load(ENROLLMENT_ID)).thenReturn(Optional.of(sampleRecord()));
    when(cryptoService.signStringToBase64(anyString(), anyString())).thenReturn(DEVICE_SIG);
    when(authApiService.respond(any(), anyString()))
        .thenReturn(Mono.error(problem(503, "integration-signing-unavailable", "signing")));

    MockHttpSession session = sessionWithClaim();
    Model model = new ConcurrentModel();
    String view =
        controller.respondToAuth(
            ENROLLMENT_ID, AUTH_ATTEMPT_ID, true, null, PROOF_TOKEN, model, session);

    assertThat(view).isEqualTo("phone/ezkey/auth_pending");
    assertThat(model.getAttribute("pendingBanner"))
        .isEqualTo(EzkeyAppController.MSG_AUTH_SIGNING_UNAVAILABLE);
    assertThat(model.getAttribute("hasPendingAuth")).isEqualTo(true);
    assertThat(model.getAttribute("authAttemptId")).isEqualTo(AUTH_ATTEMPT_ID);
    assertThat(
            PendingAuthClaimSession.findOpen(
                session, ENROLLMENT_ID, Instant.now(), PendingAuthClaimSession.DEFAULT_TTL))
        .isPresent();
    assertNoLeak(model);
  }

  @Test
  @DisplayName("429 keeps claim and re-shows pending with rate-limit banner")
  void respond429KeepsClaimAndBanner() {
    when(storeService.load(ENROLLMENT_ID)).thenReturn(Optional.of(sampleRecord()));
    when(cryptoService.signStringToBase64(anyString(), anyString())).thenReturn(DEVICE_SIG);
    when(authApiService.respond(any(), anyString()))
        .thenReturn(Mono.error(problem(429, null, "slow down")));

    MockHttpSession session = sessionWithClaim();
    Model model = new ConcurrentModel();
    String view =
        controller.respondToAuth(
            ENROLLMENT_ID, AUTH_ATTEMPT_ID, true, null, PROOF_TOKEN, model, session);

    assertThat(view).isEqualTo("phone/ezkey/auth_pending");
    assertThat(model.getAttribute("pendingBanner"))
        .isEqualTo(EzkeyAppController.MSG_AUTH_RATE_LIMITED);
    assertThat(
            PendingAuthClaimSession.findOpen(
                session, ENROLLMENT_ID, Instant.now(), PendingAuthClaimSession.DEFAULT_TTL))
        .isPresent();
  }

  @Test
  @DisplayName("result signature fail → unconfirmed, not failed/tampering")
  void respondResultSigFailIsUnconfirmed() {
    when(storeService.load(ENROLLMENT_ID)).thenReturn(Optional.of(sampleRecord()));
    when(cryptoService.signStringToBase64(anyString(), anyString())).thenReturn(DEVICE_SIG);
    when(cryptoService.validateSignature(anyString(), anyString(), anyString())).thenReturn(false);

    org.ezkey.demodevice.generated.dto.AuthAttemptRespondResponseDto dto =
        new org.ezkey.demodevice.generated.dto.AuthAttemptRespondResponseDto()
            .authAttemptId(AUTH_ATTEMPT_ID)
            .authAttemptResult(
                org.ezkey.demodevice.generated.dto.AuthAttemptRespondResponseDto
                    .AuthAttemptResultEnum.APPROVED)
            .authAttemptMessage("ok")
            .authAttemptProofTokenResultSignedByIntegration("bad-sig");
    when(authApiService.respond(any(), anyString())).thenReturn(Mono.just(dto));

    Model model = new ConcurrentModel();
    controller.respondToAuth(
        ENROLLMENT_ID, AUTH_ATTEMPT_ID, true, null, PROOF_TOKEN, model, new MockHttpSession());

    assertThat(model.getAttribute("unconfirmed")).isEqualTo(true);
    assertThat(model.getAttribute("failed")).isEqualTo(false);
    assertThat(model.getAttribute("message")).isEqualTo(EzkeyAppController.MSG_AUTH_UNCONFIRMED);
    assertThat(model.getAttribute("message").toString()).doesNotContain("tampering");
  }

  @Test
  @DisplayName("classify maps status+type; messageFor never leaks codes")
  void classifyAndMessageFor() {
    assertThat(
            EzkeyAppController.classify(409, "https://ezkey.io/problems/auth/auth-attempt-expired"))
        .isEqualTo(AuthApiOutcome.EXPIRED);
    assertThat(EzkeyAppController.classify(409, null)).isEqualTo(AuthApiOutcome.NO_LONGER_ACTIVE);
    assertThat(EzkeyAppController.classify(503, null))
        .isEqualTo(AuthApiOutcome.SIGNING_UNAVAILABLE);
    assertThat(EzkeyAppController.classify(429, null)).isEqualTo(AuthApiOutcome.RATE_LIMITED);
    assertThat(EzkeyAppController.classify(500, null)).isEqualTo(AuthApiOutcome.UNCONFIRMED);

    for (AuthApiOutcome o : AuthApiOutcome.values()) {
      String msg = EzkeyAppController.messageFor(o);
      assertThat(msg).doesNotContain("http");
      assertThat(msg).doesNotContain("HTTP");
      assertThat(msg).doesNotContain("localhost");
      assertThat(msg).doesNotContain("POST");
      assertThat(msg).doesNotMatch(".*\\b\\d{3}\\b.*");
    }
  }

  @Test
  @DisplayName("applyRespondConflictToModel sets expired attributes for typed 409")
  void applyRespondConflictToModelExpired() {
    Model model = new ConcurrentModel();
    EzkeyAppController.applyRespondConflictToModel(
        model, 409, "https://ezkey.io/problems/auth/auth-attempt-expired");

    assertThat(model.getAttribute("expired")).isEqualTo(true);
    assertThat(model.getAttribute("failed")).isEqualTo(false);
    assertThat(model.getAttribute("message")).isEqualTo(EzkeyAppController.MSG_AUTH_EXPIRED);
  }

  private static void assertNoLeak(Model model) {
    Object message = model.getAttribute("message");
    Object banner = model.getAttribute("pendingBanner");
    Object error = model.getAttribute("error");
    for (Object o : new Object[] {message, banner, error}) {
      if (o == null) {
        continue;
      }
      String s = o.toString();
      assertThat(s).doesNotContain("localhost");
      assertThat(s).doesNotContain("http://");
      assertThat(s).doesNotContain("https://");
      assertThat(s).doesNotContain("POST");
      assertThat(s).doesNotContain("detail");
    }
  }

  private static WebClientResponseException problem(int status, String typeSuffix, String detail) {
    String type = typeSuffix == null ? null : "https://ezkey.io/problems/auth/" + typeSuffix;
    String body =
        type == null
            ? "{\"title\":\"err\",\"detail\":\"" + detail + "\"}"
            : "{\"type\":\"" + type + "\",\"title\":\"err\",\"detail\":\"" + detail + "\"}";
    return WebClientResponseException.create(
        status,
        "Err",
        HttpHeaders.EMPTY,
        body.getBytes(StandardCharsets.UTF_8),
        StandardCharsets.UTF_8);
  }

  private static MockHttpSession sessionWithClaim() {
    MockHttpSession session = new MockHttpSession();
    PendingAuthClaimSession.store(
        session,
        ClaimedPendingAuth.of(
            ENROLLMENT_ID,
            AUTH_ATTEMPT_ID,
            PROOF_TOKEN,
            false,
            null,
            null,
            Instant.now().plusSeconds(120)));
    return session;
  }

  private static Record sampleRecord() {
    return new Record(
        ENROLLMENT_ID,
        1,
        "Demo User",
        AUTH_API + "/enroll",
        "integration-public-key",
        "enrollment-proof",
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
