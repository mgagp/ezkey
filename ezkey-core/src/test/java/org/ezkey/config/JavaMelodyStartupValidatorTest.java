/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: JavaMelodyStartupValidatorTest
 * Description: Unit tests for JavaMelody / management-port startup fail-closed checks.
 */

package org.ezkey.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JavaMelodyStartupValidatorTest {

  @Test
  @DisplayName("allows JavaMelody off regardless of management-endpoint flag")
  void allowsJavaMelodyDisabled() {
    assertDoesNotThrow(() -> JavaMelodyStartupValidator.enforce(false, false, 8080, 8085));
    assertDoesNotThrow(() -> JavaMelodyStartupValidator.enforce(false, true, 8080, 8085));
  }

  @Test
  @DisplayName("allows JavaMelody on when management-endpoint monitoring is on and ports differ")
  void allowsJavaMelodyWithManagementEndpoint() {
    assertDoesNotThrow(() -> JavaMelodyStartupValidator.enforce(true, true, 8080, 8085));
  }

  @Test
  @DisplayName("fails when JavaMelody is on without management-endpoint monitoring")
  void failsWhenJavaMelodyWithoutManagementEndpoint() {
    IllegalStateException ex =
        assertThrows(
            IllegalStateException.class,
            () -> JavaMelodyStartupValidator.enforce(true, false, 8080, 8085));
    assertTrue(ex.getMessage().contains("management-endpoint-monitoring-enabled"));
  }

  @Test
  @DisplayName("fails when management port equals business port")
  void failsWhenPortsEqual() {
    IllegalStateException ex =
        assertThrows(
            IllegalStateException.class,
            () -> JavaMelodyStartupValidator.enforce(false, false, 8080, 8080));
    assertTrue(ex.getMessage().contains("must differ"));
  }

  @Test
  @DisplayName("allows missing management port when JavaMelody is off")
  void allowsUnsetManagementPortWhenJavaMelodyOff() {
    assertDoesNotThrow(() -> JavaMelodyStartupValidator.enforce(false, false, 8080, null));
  }

  @Test
  @DisplayName("fails when JavaMelody is on without management.server.port")
  void failsWhenJavaMelodyWithoutManagementPort() {
    IllegalStateException ex =
        assertThrows(
            IllegalStateException.class,
            () -> JavaMelodyStartupValidator.enforce(true, true, 8080, null));
    assertTrue(ex.getMessage().contains("management.server.port"));
  }

  @Test
  @DisplayName("allows both ports 0 (Spring Boot random-port placeholder)")
  void allowsRandomPortPlaceholders() {
    assertDoesNotThrow(() -> JavaMelodyStartupValidator.enforce(false, false, 0, 0));
    assertDoesNotThrow(() -> JavaMelodyStartupValidator.enforce(true, true, 0, 0));
  }
}
