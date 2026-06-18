package org.ezkey.demo.device.service;

import org.ezkey.demo.device.service.EnrollmentStoreService.Record;

/**
 * Resolves the effective Auth API base URL for demo-device enrollment flows.
 *
 * <p>QR {@code authUrl} wins when present and valid; manual entry uses the configured default only;
 * subsequent operations read the persisted {@link Record#enrollmentUrl()} when set.
 *
 * @since 2025
 */
public final class EnrollmentAuthApiUrlResolver {

  private EnrollmentAuthApiUrlResolver() {}

  /**
   * Resolution for bind: which URL to call and what to persist in {@code enrollmentUrl}.
   *
   * @param apiBaseUrl URL to see Auth API bind request
   * @param enrollmentUrlToPersist value for {@link Record#enrollmentUrl()}, or {@code null} when
   *     manual entry should fall back to configured default for later ops
   */
  public record BindResolution(String apiBaseUrl, String enrollmentUrlToPersist) {}

  /**
   * Resolves bind routing from optional QR-submitted URL and configured default.
   *
   * @param submittedAuthApiBaseUrl optional hidden form field from QR import
   * @param configuredDefault {@code ezkey.auth.api.url}
   * @return bind API base and optional persisted enrollment URL
   * @throws InvalidAuthApiUrlException when a non-blank submitted URL fails validation (G10)
   */
  public static BindResolution resolveForBind(
      String submittedAuthApiBaseUrl, String configuredDefault) {
    if (submittedAuthApiBaseUrl == null || submittedAuthApiBaseUrl.isBlank()) {
      return new BindResolution(configuredDefault, null);
    }
    String validated = AuthApiUrlValidator.validate(submittedAuthApiBaseUrl);
    if (validated == null) {
      throw new InvalidAuthApiUrlException(
          "Invalid Auth API URL in enrollment QR. Use a valid HTTPS URL or HTTP on localhost.");
    }
    return new BindResolution(validated, validated);
  }

  /**
   * Resolves Auth API base for verify, pending, and respond using stored enrollment URL or config.
   *
   * @param record persisted enrollment, may be null during bind-only paths
   * @param configuredDefault {@code ezkey.auth.api.url}
   * @return effective base URL for Auth API calls
   */
  public static String resolveForStoredEnrollment(Record record, String configuredDefault) {
    if (record != null
        && record.enrollmentUrl() != null
        && !record.enrollmentUrl().isBlank()) {
      return record.enrollmentUrl();
    }
    return configuredDefault;
  }
}
