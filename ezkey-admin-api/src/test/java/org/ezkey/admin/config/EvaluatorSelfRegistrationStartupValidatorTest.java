/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: EvaluatorSelfRegistrationStartupValidatorTest
 * Description: Unit tests for evaluator self-registration startup URL validation.
 */

package org.ezkey.admin.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("EvaluatorSelfRegistrationStartupValidator")
class EvaluatorSelfRegistrationStartupValidatorTest {

  @Test
  @DisplayName("enforceUrlsWhenEnabled is no-op when disabled")
  void skipsValidationWhenDisabled() {
    assertDoesNotThrow(
        () -> EvaluatorSelfRegistrationStartupValidator.enforceUrlsWhenEnabled(false, "", ""));
  }

  @Test
  @DisplayName("enforceUrlsWhenEnabled fails when enabled and admin-ui-url is blank")
  void failsWhenEnabledAndAdminUiUrlBlank() {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                EvaluatorSelfRegistrationStartupValidator.enforceUrlsWhenEnabled(
                    true, "  ", "https://ezkey.org/community-guided-tour.html"));

    assertTrue(exception.getMessage().contains("admin-ui-url"));
  }

  @Test
  @DisplayName("enforceUrlsWhenEnabled fails when enabled and guided-tour-url is blank")
  void failsWhenEnabledAndGuidedTourUrlBlank() {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                EvaluatorSelfRegistrationStartupValidator.enforceUrlsWhenEnabled(
                    true, "https://admin-ui.example.local", null));

    assertTrue(exception.getMessage().contains("guided-tour-url"));
  }

  @Test
  @DisplayName("enforceUrlsWhenEnabled accepts non-blank URLs when enabled")
  void acceptsUrlsWhenEnabled() {
    assertDoesNotThrow(
        () ->
            EvaluatorSelfRegistrationStartupValidator.enforceUrlsWhenEnabled(
                true,
                "https://admin-ui.example.local",
                "https://ezkey.org/community-guided-tour.html"));
  }
}
