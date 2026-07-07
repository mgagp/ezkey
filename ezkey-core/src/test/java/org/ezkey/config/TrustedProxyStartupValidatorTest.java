/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: TrustedProxyStartupValidatorTest
 * Description: Unit tests for trusted proxy startup validation (SEC-011).
 */

package org.ezkey.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TrustedProxyStartupValidatorTest {

  @Test
  @DisplayName("enforceRequired is no-op when required flag is false")
  void skipsValidationWhenNotRequired() {
    assertDoesNotThrow(() -> TrustedProxyStartupValidator.enforceRequired(false, List.of()));
  }

  @Test
  @DisplayName("enforceRequired fails when required and cidrs are empty")
  void failsWhenRequiredAndEmpty() {
    assertThrows(
        IllegalStateException.class,
        () -> TrustedProxyStartupValidator.enforceRequired(true, List.of()));
  }

  @Test
  @DisplayName("enforceRequired accepts valid CIDR list when required")
  void acceptsValidCidrsWhenRequired() {
    assertDoesNotThrow(
        () ->
            TrustedProxyStartupValidator.enforceRequired(
                true, List.of("10.0.0.0/8", "172.16.0.0/12")));
  }

  @Test
  @DisplayName("enforceRequired fails when required and an entry is invalid")
  void failsWhenRequiredAndInvalidEntry() {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                TrustedProxyStartupValidator.enforceRequired(
                    true, List.of("10.0.0.0/8", "not-a-cidr")));

    assertTrue(exception.getMessage().contains("not-a-cidr"));
  }
}
