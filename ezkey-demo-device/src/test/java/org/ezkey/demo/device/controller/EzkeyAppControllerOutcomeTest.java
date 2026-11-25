/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: EzkeyAppControllerOutcomeTest
 * Description: What the evaluator sees on bind, pending, and respond outcomes (#943 visible
 * parity with the mobile app; #895 typed expiry).
 */

package org.ezkey.demo.device.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.ConnectException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import org.ezkey.demo.device.config.DemoDeviceProperties;
import org.ezkey.demo.device.controller.EzkeyAppController.RespondHttpOutcome;
import org.ezkey.demo.device.service.AuthApiService;
import org.ezkey.demo.device.service.ClaimedPendingAuth;
import org.ezkey.demo.device.service.DeviceCryptoService;
import org.ezkey.demo.device.service.DeviceCryptoService.ECP256DeviceKeyPair;
import org.ezkey.demo.device.service.EnrollmentStoreService;
import org.ezkey.demo.device.service.EnrollmentStoreService.Record;
import org.ezkey.demo.device.service.PendingAuthClaimSession;
import org.ezkey.demodevice.generated.dto.AuthAttemptRespondRequestDto;
import org.ezkey.demodevice.generated.dto.AuthAttemptRespondResponseDto;
import org.ezkey.demodevice.generated.dto.EnrollmentBindResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

/**
 * Unit tests for the Demo Device outcomes an evaluator sees (#943).
 *
 * @since 2026
 */
@ExtendWith(MockitoExtension.class)
class EzkeyAppControllerOutcomeTest {

  private static final int ENROLLMENT_ID = 42;
  private static final int AUTH_ATTEMPT_ID = 77;
  private static final String PROOF_TOKEN = "proof-token";
  private static final String DEVICE_SIG = "device-sig";
  private static final String INTEGRATION_SIG = "integration-sig";
  private static final String AUTH_API = "http://localhost:8080";
  private static final String PROBLEMS = "https://ezkey.io/problems/auth/";
  private static final String SERVER_DETAIL = "server-detail-must-not-reach-the-screen";

  @Mock private AuthApiService authApiService;
  @Mock private DeviceCryptoService cryptoService;
  @Mock private EnrollmentStoreService storeService;

  private EzkeyAppController controller;
  private MockHttpSession session;

  @BeforeEach
  void setUp() {
    controller =
        new EzkeyAppController(
            authApiService, cryptoService, storeService, AUTH_API, new DemoDeviceProperties());
    session = new MockHttpSession();
  }

  // --- respond: terminal HTTP errors ---

  @Test
  @DisplayName("409 auth-attempt-expired: expired state, mobile copy, claim cleared")
  void typedExpired409() {
    storeOpenClaim();
    respondFails(409, PROBLEMS + "auth-attempt-expired");

    Model model = new ConcurrentModel();
    String view = approve(model);

    assertThat(view).isEqualTo("phone/ezkey/auth_result");
    assertOnly(model, "expired");
    assertThat(model.getAttribute("message")).isEqualTo(EzkeyAppController.MSG_AUTH_EXPIRED);
    assertThat(openClaim()).isEmpty();
  }

  @Test
  @DisplayName("409 state-conflict (cancel, decided, superseded): neutral no-longer-active state")
  void stateConflict409() {
    respondFails(409, PROBLEMS + "auth-attempt-state-conflict");

    Model model = new ConcurrentModel();
    approve(model);

    assertOnly(model, "inactive");
    assertThat(model.getAttribute("message"))
        .isEqualTo(EzkeyAppController.MSG_AUTH_NO_LONGER_ACTIVE);
  }

  @Test
  @DisplayName("409 with an unparseable body is tolerated as no longer active")
  void untyped409() {
    respondFailsWithBody(409, "not-json");

    Model model = new ConcurrentModel();
    approve(model);

    assertOnly(model, "inactive");
  }

  @Test
  @DisplayName("other 5xx after sending: uncertain, Back only")
  void serverError500IsUncertain() {
    respondFails(500, PROBLEMS + "internal");

    Model model = new ConcurrentModel();
    approve(model);

    assertOnly(model, "uncertain");
    assertThat(model.getAttribute("message")).isEqualTo(EzkeyAppController.MSG_RESULT_UNCERTAIN);
  }

  @Test
  @DisplayName("transport failure after sending: uncertain, not a failure")
  void transportFailureIsUncertain() {
    loadRecordAndSign();
    when(authApiService.respond(any(), anyString()))
        .thenReturn(
            Mono.error(
                new WebClientRequestException(
                    new ConnectException("reset"),
                    HttpMethod.POST,
                    URI.create(AUTH_API + "/api/v1/auth-attempts/respond"),
                    HttpHeaders.EMPTY)));

    Model model = new ConcurrentModel();
    approve(model);

    assertOnly(model, "uncertain");
  }

  @Test
  @DisplayName("400 with a server detail: generic failure, no detail or code on screen")
  void badRequestShowsNoServerDetail() {
    respondFails(400, PROBLEMS + "validation");

    Model model = new ConcurrentModel();
    approve(model);

    assertOnly(model, "failed");
    assertThat(model.getAttribute("message"))
        .isEqualTo(EzkeyAppController.MSG_REQUEST_FAILED)
        .asString()
        .doesNotContain(SERVER_DETAIL)
        .doesNotContain("400")
        .doesNotContain("localhost");
  }

  // --- respond: retryable errors keep the claim ---

  @Test
  @DisplayName("503 signing unavailable: same request again with a banner, claim kept")
  void signingUnavailableReshowsRequest() {
    storeOpenClaim();
    respondFails(503, PROBLEMS + "integration-signing-unavailable");

    Model model = new ConcurrentModel();
    String view = approve(model);

    assertThat(view).isEqualTo("phone/ezkey/auth_pending");
    assertThat(model.getAttribute("retryMessage"))
        .isEqualTo(EzkeyAppController.MSG_SIGNING_UNAVAILABLE);
    assertThat(model.getAttribute("authAttemptId")).isEqualTo(AUTH_ATTEMPT_ID);
    assertThat(model.getAttribute("leaveHint"))
        .isEqualTo(EzkeyAppController.MSG_LEAVE_DECLINES_HINT);
    assertThat(openClaim()).isPresent();
  }

  @Test
  @DisplayName("429 on respond: same request again with a banner, claim kept")
  void rateLimitedReshowsRequest() {
    storeOpenClaim();
    respondFails(429, PROBLEMS + "rate-limited");

    Model model = new ConcurrentModel();
    String view = approve(model);

    assertThat(view).isEqualTo("phone/ezkey/auth_pending");
    assertThat(model.getAttribute("retryMessage")).isEqualTo(EzkeyAppController.MSG_RATE_LIMITED);
    assertThat(openClaim()).isPresent();
  }

  @Test
  @DisplayName("503 when the claim is already gone: terminal message instead of a dead form")
  void signingUnavailableWithoutClaim() {
    respondFails(503, PROBLEMS + "integration-signing-unavailable");

    Model model = new ConcurrentModel();
    String view = approve(model);

    assertThat(view).isEqualTo("phone/ezkey/auth_result");
    assertOnly(model, "failed");
    assertThat(model.getAttribute("message")).isEqualTo(EzkeyAppController.MSG_SIGNING_UNAVAILABLE);
  }

  @Test
  @DisplayName("non-numeric challenge: same request again, nothing sent")
  void nonNumericChallengeReshowsRequest() {
    storeOpenClaim();
    loadRecordAndSign();

    Model model = new ConcurrentModel();
    String view =
        controller.respondToAuth(
            ENROLLMENT_ID, AUTH_ATTEMPT_ID, true, "ab", PROOF_TOKEN, false, model, session);

    assertThat(view).isEqualTo("phone/ezkey/auth_pending");
    assertThat(model.getAttribute("retryMessage"))
        .isEqualTo(EzkeyAppController.MSG_ENTER_CHALLENGE);
    verify(authApiService, never()).respond(any(), anyString());
    assertThat(openClaim()).isPresent();
  }

  // --- respond: HTTP 200 ---

  @Test
  @DisplayName("200 with an invalid result signature: unconfirmed, no tampering wording")
  void invalidResultSignatureIsUnconfirmed() {
    loadRecordAndSign();
    when(authApiService.respond(any(), anyString())).thenReturn(Mono.just(approvedResponse()));
    when(cryptoService.validateSignature(anyString(), anyString(), anyString())).thenReturn(false);

    Model model = new ConcurrentModel();
    approve(model);

    assertOnly(model, "unconfirmed");
    assertThat(model.getAttribute("message"))
        .isEqualTo(EzkeyAppController.MSG_RESULT_NOT_CONFIRMED)
        .asString()
        .doesNotContainIgnoringCase("tampering");
  }

  @Test
  @DisplayName("200 without a result signature: unconfirmed")
  void missingResultSignatureIsUnconfirmed() {
    loadRecordAndSign();
    AuthAttemptRespondResponseDto response = approvedResponse();
    response.authAttemptProofTokenResultSignedByIntegration(null);
    when(authApiService.respond(any(), anyString())).thenReturn(Mono.just(response));

    Model model = new ConcurrentModel();
    approve(model);

    assertOnly(model, "unconfirmed");
  }

  @Test
  @DisplayName("200 approved with a valid signature: success, claim cleared")
  void approvedWithValidSignature() {
    storeOpenClaim();
    loadRecordAndSign();
    when(authApiService.respond(any(), anyString())).thenReturn(Mono.just(approvedResponse()));
    when(cryptoService.validateSignature(anyString(), anyString(), anyString())).thenReturn(true);

    Model model = new ConcurrentModel();
    approve(model);

    assertOnly(model, "success");
    assertThat(openClaim()).isEmpty();
  }

  // --- leave-as-deny ---

  @Test
  @DisplayName("leave sends a Deny and returns to the enrollments list")
  void leaveSendsDeny() {
    storeOpenClaim();
    loadRecordAndSign();
    AuthAttemptRespondResponseDto denied = approvedResponse();
    denied.authAttemptResult(AuthAttemptRespondResponseDto.AuthAttemptResultEnum.DENIED);
    when(authApiService.respond(any(), anyString())).thenReturn(Mono.just(denied));
    when(cryptoService.validateSignature(anyString(), anyString(), anyString())).thenReturn(true);

    String view =
        controller.respondToAuth(
            ENROLLMENT_ID,
            AUTH_ATTEMPT_ID,
            false,
            null,
            PROOF_TOKEN,
            true,
            new ConcurrentModel(),
            session);

    ArgumentCaptor<AuthAttemptRespondRequestDto> sent =
        ArgumentCaptor.forClass(AuthAttemptRespondRequestDto.class);
    verify(authApiService).respond(sent.capture(), anyString());
    assertThat(sent.getValue().getAuthAttemptAccepted()).isFalse();
    assertThat(sent.getValue().getAuthAttemptChallengeResponse()).isNull();
    assertThat(view).isEqualTo("redirect:/phone/ezkey");
    assertThat(openClaim()).isEmpty();
  }

  @Test
  @DisplayName("Deny with an empty challenge field is sent without a challenge (#1466)")
  void denyWithEmptyChallengeIsSent() {
    storeOpenClaim();
    loadRecordAndSign();
    AuthAttemptRespondResponseDto denied = approvedResponse();
    denied.authAttemptResult(AuthAttemptRespondResponseDto.AuthAttemptResultEnum.DENIED);
    when(authApiService.respond(any(), anyString())).thenReturn(Mono.just(denied));
    when(cryptoService.validateSignature(anyString(), anyString(), anyString())).thenReturn(true);

    String view =
        controller.respondToAuth(
            ENROLLMENT_ID,
            AUTH_ATTEMPT_ID,
            false,
            "",
            PROOF_TOKEN,
            false,
            new ConcurrentModel(),
            session);

    ArgumentCaptor<AuthAttemptRespondRequestDto> sent =
        ArgumentCaptor.forClass(AuthAttemptRespondRequestDto.class);
    verify(authApiService).respond(sent.capture(), anyString());
    assertThat(sent.getValue().getAuthAttemptAccepted()).isFalse();
    assertThat(sent.getValue().getAuthAttemptChallengeResponse()).isNull();
    assertThat(view).isEqualTo("phone/ezkey/auth_result");
  }

  @Test
  @DisplayName("leave never approves, even when approved=true is posted")
  void leaveForcesDeny() {
    loadRecordAndSign();
    when(authApiService.respond(any(), anyString())).thenReturn(Mono.just(approvedResponse()));
    when(cryptoService.validateSignature(anyString(), anyString(), anyString())).thenReturn(true);

    controller.respondToAuth(
        ENROLLMENT_ID,
        AUTH_ATTEMPT_ID,
        true,
        "12",
        PROOF_TOKEN,
        true,
        new ConcurrentModel(),
        session);

    ArgumentCaptor<AuthAttemptRespondRequestDto> sent =
        ArgumentCaptor.forClass(AuthAttemptRespondRequestDto.class);
    verify(authApiService).respond(sent.capture(), anyString());
    assertThat(sent.getValue().getAuthAttemptAccepted()).isFalse();
  }

  @Test
  @DisplayName("leave still leaves on a retryable error (mobile #834 Q3: never trap)")
  void leaveDoesNotTrapOnRetryableError() {
    storeOpenClaim();
    respondFails(503, PROBLEMS + "integration-signing-unavailable");

    String view =
        controller.respondToAuth(
            ENROLLMENT_ID,
            AUTH_ATTEMPT_ID,
            false,
            null,
            PROOF_TOKEN,
            true,
            new ConcurrentModel(),
            session);

    assertThat(view).isEqualTo("redirect:/phone/ezkey");
    assertThat(openClaim()).isEmpty();
  }

  // --- classification helpers ---

  @Test
  @DisplayName("classifyRespondHttpError follows the mobile catalog")
  void classification() {
    assertThat(EzkeyAppController.classifyRespondHttpError(409, PROBLEMS + "auth-attempt-expired"))
        .isEqualTo(RespondHttpOutcome.EXPIRED);
    assertThat(EzkeyAppController.classifyRespondHttpError(409, null))
        .isEqualTo(RespondHttpOutcome.NO_LONGER_ACTIVE);
    assertThat(
            EzkeyAppController.classifyRespondHttpError(
                503, PROBLEMS + "integration-signing-unavailable"))
        .isEqualTo(RespondHttpOutcome.SIGNING_UNAVAILABLE);
    assertThat(EzkeyAppController.classifyRespondHttpError(503, null))
        .isEqualTo(RespondHttpOutcome.UNCERTAIN);
    assertThat(EzkeyAppController.classifyRespondHttpError(429, null))
        .isEqualTo(RespondHttpOutcome.RATE_LIMITED);
    assertThat(EzkeyAppController.classifyRespondHttpError(400, null))
        .isEqualTo(RespondHttpOutcome.FAILED);
  }

  @Test
  @DisplayName("isAuthAttemptExpiredProblem matches type suffix only")
  void isAuthAttemptExpiredProblemHelpers() {
    assertThat(EzkeyAppController.isAuthAttemptExpiredProblem(PROBLEMS + "auth-attempt-expired"))
        .isTrue();
    assertThat(
            EzkeyAppController.isAuthAttemptExpiredProblem(
                PROBLEMS + "auth-attempt-state-conflict"))
        .isFalse();
    assertThat(EzkeyAppController.isAuthAttemptExpiredProblem(null)).isFalse();
    assertThat(EzkeyAppController.isAuthAttemptExpiredProblem("")).isFalse();
  }

  // --- bind and pending ---

  @Test
  @DisplayName("bind refuses a non-Ed25519 integration key before generating or saving keys")
  void bindRefusesNonEd25519() {
    EnrollmentBindResponseDto bind =
        new EnrollmentBindResponseDto()
            .enrollmentId(ENROLLMENT_ID)
            .integrationKeyAlgorithm("p256")
            .integrationPublicKey("key")
            .enrollmentProofToken(PROOF_TOKEN);
    when(authApiService.bind(anyString(), anyString())).thenReturn(Mono.just(bind));

    Model model = new ConcurrentModel();
    String view = controller.bindEnrollment(PROOF_TOKEN, null, model);

    assertThat(view).isEqualTo("phone/ezkey/bind_enrollment");
    assertThat(model.getAttribute("error")).isEqualTo(EzkeyAppController.MSG_BIND_UNSUPPORTED_KEY);
    assertThat(model.getAttribute("success")).isNull();
    verify(cryptoService, never()).generateDeviceKeyPair();
    verify(storeService, never()).save(any());
  }

  @Test
  @DisplayName("bind accepts an Ed25519 integration key")
  void bindAcceptsEd25519() {
    EnrollmentBindResponseDto bind =
        new EnrollmentBindResponseDto()
            .enrollmentId(ENROLLMENT_ID)
            .integrationKeyAlgorithm("ed25519")
            .integrationPublicKey("key")
            .enrollmentProofToken(PROOF_TOKEN);
    when(authApiService.bind(anyString(), anyString())).thenReturn(Mono.just(bind));
    when(cryptoService.generateDeviceKeyPair())
        .thenReturn(new ECP256DeviceKeyPair("device-private", "device-public"));

    Model model = new ConcurrentModel();
    controller.bindEnrollment(PROOF_TOKEN, null, model);

    assertThat(model.getAttribute("error")).isNull();
    verify(storeService).save(any());
  }

  @Test
  @DisplayName("bind 5xx never echoes the response body or the status code")
  void bindErrorDoesNotEchoBody() {
    WebClientResponseException error = httpError(500, PROBLEMS + "internal");
    when(authApiService.bind(anyString(), anyString())).thenReturn(Mono.error(error));

    Model model = new ConcurrentModel();
    controller.bindEnrollment(PROOF_TOKEN, null, model);

    assertThat(model.getAttribute("error"))
        .isEqualTo(EzkeyAppController.MSG_BIND_FAILED)
        .asString()
        .doesNotContain(SERVER_DETAIL)
        .doesNotContain("500");
  }

  @Test
  @DisplayName("pending errors map to fixed messages, never the exception text")
  void pendingErrorMessages() {
    assertThat(EzkeyAppController.toPendingErrorMessage(httpError(429, PROBLEMS + "rate-limited")))
        .isEqualTo(EzkeyAppController.MSG_RATE_LIMITED);
    assertThat(
            EzkeyAppController.toPendingErrorMessage(
                httpError(401, PROBLEMS + "auth-attempt-binding-failed")))
        .isEqualTo(EzkeyAppController.MSG_ENROLLMENT_NO_LONGER_ACTIVE);
    assertThat(EzkeyAppController.toPendingErrorMessage(httpError(500, PROBLEMS + "internal")))
        .isEqualTo(EzkeyAppController.MSG_REQUEST_FAILED);
    assertThat(
            EzkeyAppController.toPendingErrorMessage(
                new RuntimeException(
                    new WebClientRequestException(
                        new ConnectException("refused"),
                        HttpMethod.POST,
                        URI.create(AUTH_API),
                        HttpHeaders.EMPTY))))
        .isEqualTo(EzkeyAppController.MSG_AUTH_API_UNREACHABLE);
    assertThat(EzkeyAppController.toPendingErrorMessage(new IllegalStateException("boom")))
        .isEqualTo(EzkeyAppController.MSG_REQUEST_FAILED);
  }

  // --- helpers ---

  private String approve(Model model) {
    return controller.respondToAuth(
        ENROLLMENT_ID, AUTH_ATTEMPT_ID, true, null, PROOF_TOKEN, false, model, session);
  }

  private void loadRecordAndSign() {
    when(storeService.load(ENROLLMENT_ID)).thenReturn(Optional.of(sampleRecord()));
    when(cryptoService.signStringToBase64(anyString(), anyString())).thenReturn(DEVICE_SIG);
  }

  private void respondFails(int status, String problemType) {
    loadRecordAndSign();
    when(authApiService.respond(any(), anyString()))
        .thenReturn(Mono.error(httpError(status, problemType)));
  }

  private void respondFailsWithBody(int status, String body) {
    loadRecordAndSign();
    when(authApiService.respond(any(), anyString()))
        .thenReturn(
            Mono.error(
                WebClientResponseException.create(
                    status,
                    "Error",
                    HttpHeaders.EMPTY,
                    body.getBytes(StandardCharsets.UTF_8),
                    StandardCharsets.UTF_8)));
  }

  private static WebClientResponseException httpError(int status, String problemType) {
    String body =
        "{\"type\":\""
            + problemType
            + "\",\"title\":\"Error\",\"detail\":\""
            + SERVER_DETAIL
            + "\",\"instance\":\"http://localhost:8080/api\"}";
    return WebClientResponseException.create(
        status,
        "Error",
        HttpHeaders.EMPTY,
        body.getBytes(StandardCharsets.UTF_8),
        StandardCharsets.UTF_8);
  }

  private void storeOpenClaim() {
    Instant now = Instant.now();
    PendingAuthClaimSession.store(
        session,
        new ClaimedPendingAuth(
            ENROLLMENT_ID,
            AUTH_ATTEMPT_ID,
            PROOF_TOKEN,
            true,
            null,
            null,
            now,
            now.plusSeconds(60)));
  }

  private Optional<ClaimedPendingAuth> openClaim() {
    return PendingAuthClaimSession.findOpen(
        session, ENROLLMENT_ID, Instant.now(), PendingAuthClaimSession.DEFAULT_TTL);
  }

  private static AuthAttemptRespondResponseDto approvedResponse() {
    AuthAttemptRespondResponseDto response = new AuthAttemptRespondResponseDto();
    response.authAttemptId(AUTH_ATTEMPT_ID);
    response.authAttemptResult(AuthAttemptRespondResponseDto.AuthAttemptResultEnum.APPROVED);
    response.authAttemptMessage("ok");
    response.authAttemptProofTokenResultSignedByIntegration(INTEGRATION_SIG);
    return response;
  }

  /** Asserts exactly one result flag is true. */
  private static void assertOnly(Model model, String flag) {
    for (EzkeyAppController.ResultState state : EzkeyAppController.ResultState.values()) {
      String name = state.name().toLowerCase(java.util.Locale.ROOT);
      assertThat(model.getAttribute(name)).as(name).isEqualTo(name.equals(flag));
    }
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
