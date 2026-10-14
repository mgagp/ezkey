/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: EnrollmentAuthApiUrlResolverTest
 * Description: QR vs config precedence and allowlist rejection without echoing the URL.
 */

package org.ezkey.demo.device.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import org.ezkey.demo.device.service.EnrollmentAuthApiUrlResolver.BindResolution;
import org.ezkey.demo.device.service.EnrollmentStoreService.Record;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link EnrollmentAuthApiUrlResolver} precedence (QR URL vs config default). */
class EnrollmentAuthApiUrlResolverTest {

  private static final String CONFIG = "https://exp1-auth-api.ezkey.org";
  private static final String LOCAL = "http://localhost:8080";
  private static final Set<String> ALLOW =
      AuthApiUrlValidator.effectiveAllowlist(CONFIG, List.of());

  @Test
  void resolveForBind_manualEntry_usesConfigOnly() {
    BindResolution resolution = EnrollmentAuthApiUrlResolver.resolveForBind(null, CONFIG, ALLOW);
    assertThat(resolution.apiBaseUrl()).isEqualTo(CONFIG);
    assertThat(resolution.enrollmentUrlToPersist()).isNull();

    resolution = EnrollmentAuthApiUrlResolver.resolveForBind("  ", CONFIG, ALLOW);
    assertThat(resolution.apiBaseUrl()).isEqualTo(CONFIG);
    assertThat(resolution.enrollmentUrlToPersist()).isNull();
  }

  @Test
  void resolveForBind_qrUrl_winsAndPersists() {
    BindResolution resolution = EnrollmentAuthApiUrlResolver.resolveForBind(LOCAL, CONFIG, ALLOW);
    assertThat(resolution.apiBaseUrl()).isEqualTo(LOCAL);
    assertThat(resolution.enrollmentUrlToPersist()).isEqualTo(LOCAL);
  }

  @Test
  void resolveForBind_invalidQrUrl_throwsConstantMessage() {
    assertThatThrownBy(
            () ->
                EnrollmentAuthApiUrlResolver.resolveForBind(
                    "http://evil.example.com", CONFIG, ALLOW))
        .isInstanceOf(InvalidAuthApiUrlException.class)
        .hasMessage(AuthApiUrlValidator.INVALID_URL_MESSAGE)
        .hasMessageNotContaining("evil.example.com");
  }

  @Test
  void resolveForBind_hostOutsideAllowlist_throwsConstantMessageWithoutEcho() {
    String evil = "https://evil.example/steal-tokens";
    assertThatThrownBy(() -> EnrollmentAuthApiUrlResolver.resolveForBind(evil, CONFIG, ALLOW))
        .isInstanceOf(InvalidAuthApiUrlException.class)
        .hasMessage(AuthApiUrlValidator.HOST_NOT_ALLOWED_MESSAGE)
        .hasMessageNotContaining("evil.example")
        .hasMessageNotContaining("steal-tokens");
  }

  @Test
  void resolveForBind_overrideToAllowedConfiguredHost_accepts() {
    BindResolution resolution = EnrollmentAuthApiUrlResolver.resolveForBind(CONFIG, LOCAL, ALLOW);
    assertThat(resolution.apiBaseUrl()).isEqualTo(CONFIG);
    assertThat(resolution.enrollmentUrlToPersist()).isEqualTo(CONFIG);
  }

  @Test
  void resolveForStoredEnrollment_prefersStoredUrl() {
    Record record =
        new Record(
            1, null, "Name", LOCAL, "pk", "token", "pub", "priv", null, "Device", null, null, null,
            null, null, null, null, "NONE");
    assertThat(
            EnrollmentAuthApiUrlResolver.resolveForStoredEnrollment(record, CONFIG, ALLOW))
        .isEqualTo(LOCAL);
  }

  @Test
  void resolveForStoredEnrollment_fallsBackToConfig() {
    Record record =
        new Record(
            1, null, "Name", null, "pk", "token", "pub", "priv", null, "Device", null, null, null,
            null, null, null, null, "NONE");
    assertThat(
            EnrollmentAuthApiUrlResolver.resolveForStoredEnrollment(record, CONFIG, ALLOW))
        .isEqualTo(CONFIG);
  }

  @Test
  void resolveForStoredEnrollment_disallowedHost_throwsConstantMessageWithoutEcho() {
    String evil = "https://evil.example/steal-tokens";
    Record record =
        new Record(
            1, null, "Name", evil, "pk", "token", "pub", "priv", null, "Device", null, null, null,
            null, null, null, null, "NONE");
    assertThatThrownBy(
            () ->
                EnrollmentAuthApiUrlResolver.resolveForStoredEnrollment(record, CONFIG, ALLOW))
        .isInstanceOf(InvalidAuthApiUrlException.class)
        .hasMessage(AuthApiUrlValidator.HOST_NOT_ALLOWED_MESSAGE)
        .hasMessageNotContaining("evil.example")
        .hasMessageNotContaining("steal-tokens");
  }

  @Test
  void resolveForStoredEnrollment_userinfo_throwsConstantMessageWithoutEcho() {
    String withUserinfo = "https://attacker:secret@exp1-auth-api.ezkey.org/path";
    Record record =
        new Record(
            1,
            null,
            "Name",
            withUserinfo,
            "pk",
            "token",
            "pub",
            "priv",
            null,
            "Device",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "NONE");
    assertThatThrownBy(
            () ->
                EnrollmentAuthApiUrlResolver.resolveForStoredEnrollment(record, CONFIG, ALLOW))
        .isInstanceOf(InvalidAuthApiUrlException.class)
        .hasMessage(AuthApiUrlValidator.INVALID_URL_MESSAGE)
        .hasMessageNotContaining("attacker")
        .hasMessageNotContaining("secret")
        .hasMessageNotContaining("exp1-auth-api.ezkey.org");
  }
}
