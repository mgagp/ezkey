/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: EzkeyAppControllerRespondConflictTest
 * Description: Respond 409 mapping for auth-attempt-expired (#895 PR-C), tolerant of older types.
 */

package org.ezkey.demo.device.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.ezkey.demo.device.config.DemoDeviceProperties;
import org.ezkey.demo.device.service.AuthApiService;
import org.ezkey.demo.device.service.DeviceCryptoService;
import org.ezkey.demo.device.service.EnrollmentStoreService;
import org.ezkey.demo.device.service.EnrollmentStoreService.Record;
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
 * Unit tests for Demo Device respond conflict UX (#895 PR-C).
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
  @DisplayName("409 auth-attempt-expired shows clear expired result")
  void respondMapsTypedExpired409() {
    when(storeService.load(ENROLLMENT_ID)).thenReturn(Optional.of(sampleRecord()));
    when(cryptoService.signStringToBase64(anyString(), anyString())).thenReturn(DEVICE_SIG);

    String body =
        "{\"type\":\"https://ezkey.io/problems/auth/auth-attempt-expired\","
            + "\"title\":\"Gone\",\"detail\":\"expired\"}";
    WebClientResponseException error =
        WebClientResponseException.create(
            409,
            "Conflict",
            HttpHeaders.EMPTY,
            body.getBytes(StandardCharsets.UTF_8),
            StandardCharsets.UTF_8);
    when(authApiService.respond(any(), anyString())).thenReturn(Mono.error(error));

    Model model = new ConcurrentModel();
    String view =
        controller.respondToAuth(
            ENROLLMENT_ID,
            AUTH_ATTEMPT_ID,
            true,
            null,
            PROOF_TOKEN,
            model,
            new MockHttpSession());

    assertThat(view).isEqualTo("phone/ezkey/auth_result");
    assertThat(model.getAttribute("expired")).isEqualTo(true);
    assertThat(model.getAttribute("failed")).isEqualTo(false);
    assertThat(model.getAttribute("success")).isEqualTo(false);
    assertThat(model.getAttribute("message").toString()).containsIgnoringCase("expired");
  }

  @Test
  @DisplayName("409 without auth-attempt-expired type stays tolerant (no longer active)")
  void respondMapsGeneric409WithoutTypedExpired() {
    when(storeService.load(ENROLLMENT_ID)).thenReturn(Optional.of(sampleRecord()));
    when(cryptoService.signStringToBase64(anyString(), anyString())).thenReturn(DEVICE_SIG);

    String body =
        "{\"type\":\"https://ezkey.io/problems/auth/auth-attempt-state-conflict\","
            + "\"title\":\"Conflict\",\"detail\":\"inactive\"}";
    WebClientResponseException error =
        WebClientResponseException.create(
            409,
            "Conflict",
            HttpHeaders.EMPTY,
            body.getBytes(StandardCharsets.UTF_8),
            StandardCharsets.UTF_8);
    when(authApiService.respond(any(), anyString())).thenReturn(Mono.error(error));

    Model model = new ConcurrentModel();
    controller.respondToAuth(
        ENROLLMENT_ID, AUTH_ATTEMPT_ID, true, null, PROOF_TOKEN, model, new MockHttpSession());

    assertThat(model.getAttribute("expired")).isEqualTo(false);
    assertThat(model.getAttribute("failed")).isEqualTo(true);
    assertThat(model.getAttribute("message").toString()).contains("no longer active");
  }

  @Test
  @DisplayName("409 with unparseable body is tolerated as no longer active")
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

    assertThat(model.getAttribute("expired")).isEqualTo(false);
    assertThat(model.getAttribute("failed")).isEqualTo(true);
    assertThat(model.getAttribute("message").toString()).contains("no longer active");
  }

  @Test
  @DisplayName("isAuthAttemptExpiredProblem matches type suffix only")
  void isAuthAttemptExpiredProblemHelpers() {
    assertThat(
            EzkeyAppController.isAuthAttemptExpiredProblem(
                "https://ezkey.io/problems/auth/auth-attempt-expired"))
        .isTrue();
    assertThat(
            EzkeyAppController.isAuthAttemptExpiredProblem(
                "https://ezkey.io/problems/auth/auth-attempt-state-conflict"))
        .isFalse();
    assertThat(EzkeyAppController.isAuthAttemptExpiredProblem(null)).isFalse();
    assertThat(EzkeyAppController.isAuthAttemptExpiredProblem("")).isFalse();
  }

  @Test
  @DisplayName("applyRespondConflictToModel sets expired attributes for typed 409")
  void applyRespondConflictToModelExpired() {
    Model model = new ConcurrentModel();
    EzkeyAppController.applyRespondConflictToModel(
        model, 409, "https://ezkey.io/problems/auth/auth-attempt-expired");

    assertThat(model.getAttribute("expired")).isEqualTo(true);
    assertThat(model.getAttribute("failed")).isEqualTo(false);
    assertThat(model.getAttribute("message").toString()).containsIgnoringCase("expired");
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
