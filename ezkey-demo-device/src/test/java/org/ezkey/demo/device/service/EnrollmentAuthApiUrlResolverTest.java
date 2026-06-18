package org.ezkey.demo.device.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.ezkey.demo.device.service.EnrollmentAuthApiUrlResolver.BindResolution;
import org.ezkey.demo.device.service.EnrollmentStoreService.Record;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link EnrollmentAuthApiUrlResolver} precedence (QR URL vs config default).
 */
class EnrollmentAuthApiUrlResolverTest {

  private static final String CONFIG = "https://exp1-auth-api.ezkey.org";
  private static final String LOCAL = "http://localhost:8080";

  @Test
  void resolveForBind_manualEntry_usesConfigOnly() {
    BindResolution resolution = EnrollmentAuthApiUrlResolver.resolveForBind(null, CONFIG);
    assertThat(resolution.apiBaseUrl()).isEqualTo(CONFIG);
    assertThat(resolution.enrollmentUrlToPersist()).isNull();

    resolution = EnrollmentAuthApiUrlResolver.resolveForBind("  ", CONFIG);
    assertThat(resolution.apiBaseUrl()).isEqualTo(CONFIG);
    assertThat(resolution.enrollmentUrlToPersist()).isNull();
  }

  @Test
  void resolveForBind_qrUrl_winsAndPersists() {
    BindResolution resolution =
        EnrollmentAuthApiUrlResolver.resolveForBind(LOCAL, CONFIG);
    assertThat(resolution.apiBaseUrl()).isEqualTo(LOCAL);
    assertThat(resolution.enrollmentUrlToPersist()).isEqualTo(LOCAL);
  }

  @Test
  void resolveForBind_invalidQrUrl_throws() {
    assertThatThrownBy(
            () -> EnrollmentAuthApiUrlResolver.resolveForBind("http://evil.example.com", CONFIG))
        .isInstanceOf(InvalidAuthApiUrlException.class)
        .hasMessageContaining("Invalid Auth API URL");
  }

  @Test
  void resolveForStoredEnrollment_prefersStoredUrl() {
    Record record =
        new Record(
            1,
            null,
            "Name",
            LOCAL,
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
    assertThat(EnrollmentAuthApiUrlResolver.resolveForStoredEnrollment(record, CONFIG))
        .isEqualTo(LOCAL);
  }

  @Test
  void resolveForStoredEnrollment_fallsBackToConfig() {
    Record record =
        new Record(
            1,
            null,
            "Name",
            null,
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
    assertThat(EnrollmentAuthApiUrlResolver.resolveForStoredEnrollment(record, CONFIG))
        .isEqualTo(CONFIG);
  }
}
