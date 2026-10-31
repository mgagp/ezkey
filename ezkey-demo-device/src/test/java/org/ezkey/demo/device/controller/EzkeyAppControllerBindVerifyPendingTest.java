/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: EzkeyAppControllerBindVerifyPendingTest
 * Description: F1 bind ed25519 fail-closed, 429/503 mapping, no leak to screen (#943).
 */

package org.ezkey.demo.device.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.ezkey.demo.device.config.DemoDeviceProperties;
import org.ezkey.demo.device.service.AuthApiService;
import org.ezkey.demo.device.service.DeviceCryptoService;
import org.ezkey.demo.device.service.DeviceCryptoService.ECP256DeviceKeyPair;
import org.ezkey.demo.device.service.EnrollmentStoreService;
import org.ezkey.demo.device.service.EnrollmentStoreService.Record;
import org.ezkey.demodevice.generated.dto.EnrollmentBindResponseDto;
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
 * Unit tests for Demo Device bind / verify / pending F1 UX (#943).
 *
 * @since 2026
 */
@ExtendWith(MockitoExtension.class)
class EzkeyAppControllerBindVerifyPendingTest {

  private static final int ENROLLMENT_ID = 42;
  private static final String ENROLLMENT_PROOF = "enrollment-proof";
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
  @DisplayName("bind refuses non-ed25519 integrationKeyAlgorithm before persist")
  void bindFailsClosedOnWrongAlgorithm() {
    EnrollmentBindResponseDto dto =
        new EnrollmentBindResponseDto()
            .enrollmentId(ENROLLMENT_ID)
            .integrationPublicKey("pk")
            .integrationKeyAlgorithm("rsa")
            .enrollmentProofToken(ENROLLMENT_PROOF)
            .isSystemIntegration(false);
    when(authApiService.bind(any(), anyString(), anyString())).thenReturn(Mono.just(dto));

    Model model = new ConcurrentModel();
    String view = controller.bindEnrollment(ENROLLMENT_ID, ENROLLMENT_PROOF, null, model);

    assertThat(view).isEqualTo("phone/ezkey/bind_enrollment");
    assertThat(model.getAttribute("error"))
        .isEqualTo(EzkeyAppController.MSG_UNSUPPORTED_INTEGRATION_KEY_ALGORITHM);
    assertThat(model.getAttribute("success")).isNull();
    verify(storeService, never()).save(any());
    verify(cryptoService, never()).generateDeviceKeyPair();
  }

  @Test
  @DisplayName("bind accepts ed25519 and persists")
  void bindAcceptsEd25519() {
    EnrollmentBindResponseDto dto =
        new EnrollmentBindResponseDto()
            .enrollmentId(ENROLLMENT_ID)
            .integrationPublicKey("pk")
            .integrationKeyAlgorithm(EzkeyAppController.INTEGRATION_KEY_ALGORITHM_PHASE1)
            .enrollmentProofToken(ENROLLMENT_PROOF)
            .integrationName("Acme")
            .isSystemIntegration(false);
    when(authApiService.bind(any(), anyString(), anyString())).thenReturn(Mono.just(dto));
    when(cryptoService.generateDeviceKeyPair())
        .thenReturn(new ECP256DeviceKeyPair("dev-priv", "dev-pub"));

    Model model = new ConcurrentModel();
    controller.bindEnrollment(ENROLLMENT_ID, ENROLLMENT_PROOF, null, model);

    assertThat(model.getAttribute("error")).isNull();
    assertThat(model.getAttribute("success")).isNotNull();
    verify(storeService).save(any());
  }

  @Test
  @DisplayName("bind 429 → rate-limit copy; no status/body/URL on screen")
  void bind429SafeMessage() {
    when(authApiService.bind(any(), anyString(), anyString()))
        .thenReturn(Mono.error(problem(429, null, "http://evil/ slow POST")));

    Model model = new ConcurrentModel();
    controller.bindEnrollment(ENROLLMENT_ID, ENROLLMENT_PROOF, null, model);

    assertThat(model.getAttribute("error")).isEqualTo(EzkeyAppController.MSG_AUTH_RATE_LIMITED);
    assertNoLeak(model);
  }

  @Test
  @DisplayName("bind 503 → signing-unavailable copy; no detail leak")
  void bind503SafeMessage() {
    when(authApiService.bind(any(), anyString(), anyString()))
        .thenReturn(
            Mono.error(
                problem(503, "integration-signing-unavailable", "detail with localhost:8080")));

    Model model = new ConcurrentModel();
    controller.bindEnrollment(ENROLLMENT_ID, ENROLLMENT_PROOF, null, model);

    assertThat(model.getAttribute("error"))
        .isEqualTo(EzkeyAppController.MSG_AUTH_SIGNING_UNAVAILABLE);
    assertNoLeak(model);
  }

  @Test
  @DisplayName("bind other 5xx → generic; never Q9 unconfirmed or HTTP code")
  void bind5xxGenericNotUnconfirmed() {
    when(authApiService.bind(any(), anyString(), anyString()))
        .thenReturn(Mono.error(problem(500, null, "stacktrace host")));

    Model model = new ConcurrentModel();
    controller.bindEnrollment(ENROLLMENT_ID, ENROLLMENT_PROOF, null, model);

    assertThat(model.getAttribute("error")).isEqualTo(EzkeyAppController.MSG_AUTH_GENERIC);
    assertThat(model.getAttribute("error").toString())
        .doesNotContain("confirmation")
        .doesNotContain("500");
    assertNoLeak(model);
  }

  @Test
  @DisplayName("toBindErrorMessage never embeds status or body")
  void toBindErrorMessageNoLeak() {
    String msg409 = EzkeyAppController.toBindErrorMessage(409, null);
    assertThat(msg409).contains("already bound");
    assertThat(msg409).doesNotMatch(".*\\b409\\b.*");

    String msg429 = EzkeyAppController.toBindErrorMessage(429, null);
    assertThat(msg429).isEqualTo(EzkeyAppController.MSG_AUTH_RATE_LIMITED);

    String msgOther = EzkeyAppController.toBindErrorMessage(502, "https://ezkey.io/problems/x");
    assertThat(msgOther).isEqualTo(EzkeyAppController.MSG_AUTH_GENERIC);
    assertThat(msgOther).doesNotContain("502");
    assertThat(msgOther).doesNotContain("ezkey.io");
  }

  @Test
  @DisplayName("verify 429 → rate-limit copy; deletes local enrollment; no leak")
  void verify429SafeMessage() {
    when(storeService.load(ENROLLMENT_ID)).thenReturn(Optional.of(sampleRecord()));
    when(cryptoService.signStringToBase64(anyString(), anyString())).thenReturn("sig");
    when(authApiService.verify(any(), anyString()))
        .thenReturn(Mono.error(problem(429, null, "rate POST http://x")));

    Model model = new ConcurrentModel();
    String view = controller.verifyEnrollment(ENROLLMENT_ID, "1234", null, model);

    assertThat(view).isEqualTo("phone/ezkey/bind_enrollment");
    assertThat(model.getAttribute("error")).isEqualTo(EzkeyAppController.MSG_AUTH_RATE_LIMITED);
    verify(storeService).delete(ENROLLMENT_ID);
    assertNoLeak(model);
  }

  @Test
  @DisplayName("pending 429 → rate-limit on auth screen; no URL/host/method")
  void pending429SafeMessage() {
    when(storeService.load(ENROLLMENT_ID)).thenReturn(Optional.of(sampleRecord()));
    when(cryptoService.generateProofToken()).thenReturn("device-proof");
    when(cryptoService.signStringToBase64(anyString(), anyString())).thenReturn("sig");
    when(authApiService.pending(any(), anyString()))
        .thenReturn(Mono.error(problem(429, null, "https://api.example/pending GET")));

    Model model = new ConcurrentModel();
    String view = controller.enrollmentAuth(ENROLLMENT_ID, model, new MockHttpSession());

    assertThat(view).isEqualTo("phone/ezkey/auth");
    assertThat(model.getAttribute("error")).isEqualTo(EzkeyAppController.MSG_AUTH_RATE_LIMITED);
    assertThat(model.getAttribute("hasPendingAuth")).isEqualTo(false);
    assertNoLeak(model);
  }

  @Test
  @DisplayName("pending transport exception → generic; never e.getMessage() leak")
  void pendingTransportNoMessageLeak() {
    when(storeService.load(ENROLLMENT_ID)).thenReturn(Optional.of(sampleRecord()));
    when(cryptoService.generateProofToken()).thenReturn("device-proof");
    when(cryptoService.signStringToBase64(anyString(), anyString())).thenReturn("sig");
    when(authApiService.pending(any(), anyString()))
        .thenReturn(
            Mono.error(
                new RuntimeException("Connection refused to http://localhost:8080 via POST")));

    Model model = new ConcurrentModel();
    controller.enrollmentAuth(ENROLLMENT_ID, model, new MockHttpSession());

    assertThat(model.getAttribute("error")).isEqualTo(EzkeyAppController.MSG_AUTH_GENERIC);
    assertNoLeak(model);
    assertThat(model.getAttribute("error").toString())
        .doesNotContain("Connection refused")
        .doesNotContain("localhost");
  }

  @Test
  @DisplayName("safePreRespondHttpMessage maps 429/503 only; else generic")
  void safePreRespondHttpMessage() {
    assertThat(EzkeyAppController.safePreRespondHttpMessage(429, null))
        .isEqualTo(EzkeyAppController.MSG_AUTH_RATE_LIMITED);
    assertThat(EzkeyAppController.safePreRespondHttpMessage(503, null))
        .isEqualTo(EzkeyAppController.MSG_AUTH_SIGNING_UNAVAILABLE);
    assertThat(EzkeyAppController.safePreRespondHttpMessage(500, null))
        .isEqualTo(EzkeyAppController.MSG_AUTH_GENERIC);
    assertThat(EzkeyAppController.safePreRespondHttpMessage(404, null))
        .isEqualTo(EzkeyAppController.MSG_AUTH_GENERIC);
  }

  private static void assertNoLeak(Model model) {
    Object error = model.getAttribute("error");
    Object message = model.getAttribute("message");
    for (Object o : new Object[] {error, message}) {
      if (o == null) {
        continue;
      }
      String s = o.toString();
      assertThat(s).doesNotContain("localhost");
      assertThat(s).doesNotContain("http://");
      assertThat(s).doesNotContain("https://");
      assertThat(s).doesNotContain("POST");
      assertThat(s).doesNotContain("GET");
      assertThat(s).doesNotContain("evil");
      assertThat(s).doesNotContain("stacktrace");
      assertThat(s).doesNotMatch(".*\\b\\d{3}\\b.*");
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
