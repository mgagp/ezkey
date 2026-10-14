/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: EnrollmentAuthApiUrlResolver
 * Description: Resolves Auth API base URL for bind/verify with host allowlist (CodeQL #9/#10).
 */

package org.ezkey.demo.device.service;

import java.util.Set;
import org.ezkey.demo.device.service.EnrollmentStoreService.Record;

/**
 * Resolves the effective Auth API base URL for demo-device enrollment flows.
 *
 * <p>QR {@code authUrl} wins when present and valid; manual entry uses the configured default only;
 * subsequent operations read the persisted {@link Record#enrollmentUrl()} when set. Submitted QR /
 * override hosts must be on the allowlist so enrollment tokens are not sent off-allowlist.
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
   * @param allowedHosts effective host allowlist (see {@link
   *     AuthApiUrlValidator#effectiveAllowlist})
   * @return bind API base and optional persisted enrollment URL
   * @throws InvalidAuthApiUrlException when a non-blank submitted URL fails validation (G10)
   */
  public static BindResolution resolveForBind(
      String submittedAuthApiBaseUrl, String configuredDefault, Set<String> allowedHosts) {
    if (submittedAuthApiBaseUrl == null || submittedAuthApiBaseUrl.isBlank()) {
      return new BindResolution(configuredDefault, null);
    }
    if (AuthApiUrlValidator.isHostNotAllowed(submittedAuthApiBaseUrl, allowedHosts)) {
      throw new InvalidAuthApiUrlException(AuthApiUrlValidator.HOST_NOT_ALLOWED_MESSAGE);
    }
    String validated = AuthApiUrlValidator.validate(submittedAuthApiBaseUrl, allowedHosts);
    if (validated == null) {
      throw new InvalidAuthApiUrlException(AuthApiUrlValidator.INVALID_URL_MESSAGE);
    }
    return new BindResolution(validated, validated);
  }

  /**
   * Resolves Auth API base for verify, pending, and respond using stored enrollment URL or config.
   *
   * <p>Persisted {@code enrollmentUrl} is revalidated against the same host allowlist used at bind
   * (defense in depth: a tampered or legacy store file must not send tokens off-allowlist).
   *
   * @param record persisted enrollment, may be null during bind-only paths
   * @param configuredDefault {@code ezkey.auth.api.url}
   * @param allowedHosts effective host allowlist (see {@link
   *     AuthApiUrlValidator#effectiveAllowlist})
   * @return effective base URL for Auth API calls
   * @throws InvalidAuthApiUrlException when a stored enrollment URL fails revalidation
   */
  public static String resolveForStoredEnrollment(
      Record record, String configuredDefault, Set<String> allowedHosts) {
    if (record != null && record.enrollmentUrl() != null && !record.enrollmentUrl().isBlank()) {
      String stored = record.enrollmentUrl();
      if (AuthApiUrlValidator.isHostNotAllowed(stored, allowedHosts)) {
        throw new InvalidAuthApiUrlException(AuthApiUrlValidator.HOST_NOT_ALLOWED_MESSAGE);
      }
      String validated = AuthApiUrlValidator.validate(stored, allowedHosts);
      if (validated == null) {
        throw new InvalidAuthApiUrlException(AuthApiUrlValidator.INVALID_URL_MESSAGE);
      }
      return validated;
    }
    return configuredDefault;
  }
}
