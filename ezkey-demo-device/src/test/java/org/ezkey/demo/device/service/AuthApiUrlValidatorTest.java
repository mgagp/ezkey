package org.ezkey.demo.device.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AuthApiUrlValidator} — Java mirror of mobile and JS QR URL rules.
 */
class AuthApiUrlValidatorTest {

  @Test
  void validate_blankOrNull_returnsNull() {
    assertThat(AuthApiUrlValidator.validate(null)).isNull();
    assertThat(AuthApiUrlValidator.validate("")).isNull();
    assertThat(AuthApiUrlValidator.validate("   ")).isNull();
  }

  @Test
  void validate_httpsOrigin_accepts() {
    assertThat(AuthApiUrlValidator.validate("https://ezkey.acme.com"))
        .isEqualTo("https://ezkey.acme.com");
  }

  @Test
  void validate_httpsWithPort_accepts() {
    assertThat(AuthApiUrlValidator.validate("https://ezkey.acme.com:8443"))
        .isEqualTo("https://ezkey.acme.com:8443");
  }

  @Test
  void validate_httpsStripsTrailingSlashOnPath() {
    assertThat(AuthApiUrlValidator.validate("https://ezkey.acme.com/api/"))
        .isEqualTo("https://ezkey.acme.com/api");
  }

  @Test
  void validate_httpsRootStripsTrailingSlash() {
    assertThat(AuthApiUrlValidator.validate("https://ezkey.acme.com/"))
        .isEqualTo("https://ezkey.acme.com");
  }

  @Test
  void validate_httpProductionHost_rejects() {
    assertThat(AuthApiUrlValidator.validate("http://ezkey.acme.com")).isNull();
  }

  @Test
  void validate_httpLocalhost_accepts() {
    assertThat(AuthApiUrlValidator.validate("http://localhost:8080"))
        .isEqualTo("http://localhost:8080");
  }

  @Test
  void validate_httpLoopback_accepts() {
    assertThat(AuthApiUrlValidator.validate("http://127.0.0.1:8080"))
        .isEqualTo("http://127.0.0.1:8080");
    assertThat(AuthApiUrlValidator.validate("http://10.0.2.2:8080"))
        .isEqualTo("http://10.0.2.2:8080");
  }

  @Test
  void validate_invalidScheme_rejects() {
    assertThat(AuthApiUrlValidator.validate("ftp://files.example.com")).isNull();
    assertThat(AuthApiUrlValidator.validate("not-a-url")).isNull();
  }
}
