/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AuthApiUrlValidatorTest
 * Description: Format rules, host allowlist, and linear-time trailing-slash trim (CodeQL #9/#10).
 */

package org.ezkey.demo.device.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Unit tests for {@link AuthApiUrlValidator} — Java mirror of mobile and JS QR URL rules. */
class AuthApiUrlValidatorTest {

  private static final Set<String> DEFAULT_ALLOW =
      AuthApiUrlValidator.effectiveAllowlist("http://localhost:8080", List.of());

  @Test
  void validate_blankOrNull_returnsNull() {
    assertThat(AuthApiUrlValidator.validate(null, DEFAULT_ALLOW)).isNull();
    assertThat(AuthApiUrlValidator.validate("", DEFAULT_ALLOW)).isNull();
    assertThat(AuthApiUrlValidator.validate("   ", DEFAULT_ALLOW)).isNull();
  }

  @Test
  void validate_httpsOriginOnAllowlist_accepts() {
    Set<String> allow = Set.of("ezkey.acme.com", "localhost", "127.0.0.1");
    assertThat(AuthApiUrlValidator.validate("https://ezkey.acme.com", allow))
        .isEqualTo("https://ezkey.acme.com");
  }

  @Test
  void validate_httpsWithPort_accepts() {
    Set<String> allow = Set.of("ezkey.acme.com");
    assertThat(AuthApiUrlValidator.validate("https://ezkey.acme.com:8443", allow))
        .isEqualTo("https://ezkey.acme.com:8443");
  }

  @Test
  void validate_httpsStripsTrailingSlashOnPath() {
    Set<String> allow = Set.of("ezkey.acme.com");
    assertThat(AuthApiUrlValidator.validate("https://ezkey.acme.com/api/", allow))
        .isEqualTo("https://ezkey.acme.com/api");
  }

  @Test
  void validate_httpsRootStripsTrailingSlash() {
    Set<String> allow = Set.of("ezkey.acme.com");
    assertThat(AuthApiUrlValidator.validate("https://ezkey.acme.com/", allow))
        .isEqualTo("https://ezkey.acme.com");
  }

  @Test
  void validate_httpProductionHost_rejects() {
    Set<String> allow = Set.of("ezkey.acme.com");
    assertThat(AuthApiUrlValidator.validate("http://ezkey.acme.com", allow)).isNull();
  }

  @Test
  void validate_httpLocalhost_accepts() {
    assertThat(AuthApiUrlValidator.validate("http://localhost:8080", DEFAULT_ALLOW))
        .isEqualTo("http://localhost:8080");
  }

  @Test
  void validate_httpLoopback_accepts() {
    Set<String> allow = Set.of("127.0.0.1", "10.0.2.2", "localhost");
    assertThat(AuthApiUrlValidator.validate("http://127.0.0.1:8080", allow))
        .isEqualTo("http://127.0.0.1:8080");
    assertThat(AuthApiUrlValidator.validate("http://10.0.2.2:8080", allow))
        .isEqualTo("http://10.0.2.2:8080");
  }

  @Test
  void validate_invalidScheme_rejects() {
    assertThat(AuthApiUrlValidator.validate("ftp://files.example.com", DEFAULT_ALLOW)).isNull();
    assertThat(AuthApiUrlValidator.validate("not-a-url", DEFAULT_ALLOW)).isNull();
  }

  @Test
  void validate_hostOutsideAllowlist_rejects() {
    assertThat(AuthApiUrlValidator.validate("https://evil.example", DEFAULT_ALLOW)).isNull();
    assertThat(AuthApiUrlValidator.isHostNotAllowed("https://evil.example", DEFAULT_ALLOW))
        .isTrue();
  }

  @Test
  void validate_userinfoEvilAtAllowed_rejectsAsInvalidUrl() {
    Set<String> allow = Set.of("allowed");
    // Classic SSRF: authority looks like evil.com@allowed; Java host is "allowed".
    assertThat(AuthApiUrlValidator.validate("https://evil.com@allowed", allow)).isNull();
    assertThat(AuthApiUrlValidator.isHostNotAllowed("https://evil.com@allowed", allow)).isFalse();
  }

  @Test
  void validate_userinfoAllowedAtEvil_rejectsAsInvalidUrl() {
    Set<String> allow = Set.of("allowed");
    assertThat(AuthApiUrlValidator.validate("https://allowed@evil.com", allow)).isNull();
    assertThat(AuthApiUrlValidator.isHostNotAllowed("https://allowed@evil.com", allow)).isFalse();
  }

  @Test
  void validate_suffixHostAllowedDotEvil_rejects() {
    Set<String> allow = Set.of("allowed");
    assertThat(AuthApiUrlValidator.validate("https://allowed.evil.com", allow)).isNull();
    assertThat(AuthApiUrlValidator.isHostNotAllowed("https://allowed.evil.com", allow)).isTrue();
  }

  @Test
  void validate_allowedHostOnlyInQueryString_rejects() {
    Set<String> allow = Set.of("allowed");
    assertThat(AuthApiUrlValidator.validate("https://evil.com/?h=allowed", allow)).isNull();
    assertThat(AuthApiUrlValidator.isHostNotAllowed("https://evil.com/?h=allowed", allow)).isTrue();
  }

  @Test
  void validate_trailingDotHost_rejects() {
    Set<String> allow = Set.of("allowed");
    assertThat(AuthApiUrlValidator.validate("https://allowed.", allow)).isNull();
    assertThat(AuthApiUrlValidator.isHostNotAllowed("https://allowed.", allow)).isTrue();
  }

  @Test
  void validate_uppercaseHost_acceptsNormalizedLowercase() {
    Set<String> allow = Set.of("allowed");
    assertThat(AuthApiUrlValidator.validate("https://ALLOWED", allow)).isEqualTo("https://allowed");
    assertThat(AuthApiUrlValidator.validate("https://ALLOWED:8443/api/", allow))
        .isEqualTo("https://allowed:8443/api");
  }

  @Test
  void validate_httpDockerServiceHostname_rejectsEvenWhenAllowlisted() {
    // Docker compose uses http://auth-api:8080 as ezkey.auth.api.url (config default path).
    // Override validation still requires HTTPS outside loopback — see CONFIGURATION.md.
    Set<String> allow = AuthApiUrlValidator.effectiveAllowlist("http://auth-api:8080", List.of());
    assertThat(allow).contains("auth-api");
    assertThat(AuthApiUrlValidator.validate("http://auth-api:8080", allow)).isNull();
    assertThat(AuthApiUrlValidator.isHostNotAllowed("http://auth-api:8080", allow)).isFalse();
  }

  @Test
  void validate_allowedHostFromConfiguredAuthApi_accepts() {
    Set<String> allow =
        AuthApiUrlValidator.effectiveAllowlist("https://auth-api.ezkey.online", List.of());
    assertThat(allow)
        .containsExactlyInAnyOrder("localhost", "127.0.0.1", "auth-api.ezkey.online");
    assertThat(AuthApiUrlValidator.validate("https://auth-api.ezkey.online", allow))
        .isEqualTo("https://auth-api.ezkey.online");
  }

  @Test
  void effectiveAllowlist_explicitPropertyOverridesDefault() {
    Set<String> allow =
        AuthApiUrlValidator.effectiveAllowlist(
            "https://auth-api.ezkey.online", List.of("auth.lab.example"));
    assertThat(allow).containsExactly("auth.lab.example");
  }

  @Test
  @Timeout(value = 2, unit = TimeUnit.SECONDS)
  void stripTrailingSlashes_oneHundredThousandSlashes_isLinearTime() {
    String path = "/" + "/".repeat(100_000);
    String stripped = AuthApiUrlValidator.stripTrailingSlashes(path);
    assertThat(stripped).isEqualTo("/");

    String withApi = "/api" + "/".repeat(100_000);
    assertThat(AuthApiUrlValidator.stripTrailingSlashes(withApi)).isEqualTo("/api");

    Set<String> allow = Set.of("ezkey.acme.com");
    String url = "https://ezkey.acme.com/api" + "/".repeat(100_000);
    assertThat(AuthApiUrlValidator.validate(url, allow)).isEqualTo("https://ezkey.acme.com/api");
  }
}
