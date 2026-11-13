/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Utility: EvaluatorSelfRegistrationStartupValidator
 * Description: Fail-closed validation for evaluator self-registration URL config.
 */

package org.ezkey.admin.config;

/**
 * Validates {@code ezkey.evaluator.self-registration} URL settings at application startup.
 *
 * <p>When {@code enabled=true}, both {@code admin-ui-url} and {@code guided-tour-url} must be
 * non-blank. Product defaults intentionally carry no public hostname; live surfaces set these via
 * env or overlay only.
 *
 * @since 2026
 */
public final class EvaluatorSelfRegistrationStartupValidator {

  private EvaluatorSelfRegistrationStartupValidator() {}

  /**
   * Enforces required navigation URLs when anonymous evaluator signup is enabled.
   *
   * @param enabled whether evaluator self-registration is enabled
   * @param adminUiUrl Admin UI entry URL returned after signup
   * @param guidedTourUrl guided tour URL returned after signup
   * @throws IllegalStateException when enabled and either URL is blank
   */
  public static void enforceUrlsWhenEnabled(
      boolean enabled, String adminUiUrl, String guidedTourUrl) {
    if (!enabled) {
      return;
    }
    if (isBlank(adminUiUrl) || isBlank(guidedTourUrl)) {
      throw new IllegalStateException(
          "Evaluator self-registration is enabled"
              + " (ezkey.evaluator.self-registration.enabled=true) but required URLs are blank."
              + " Set ezkey.evaluator.self-registration.admin-ui-url and"
              + " ezkey.evaluator.self-registration.guided-tour-url via environment or overlay"
              + " (no product default hostname). Community example:"
              + " experimental-hybrid/lightsail/.env.ezkey-online.example");
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
