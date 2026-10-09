/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: IntegrityVerifyReportPropertiesTest
 * Description: Window validation for synchronous Integrity report GETs.
 */

package org.ezkey.audit.integrity;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link IntegrityVerifyReportProperties}.
 *
 * @since 2026
 */
@DisplayName("IntegrityVerifyReportProperties")
class IntegrityVerifyReportPropertiesTest {

  private final IntegrityVerifyReportProperties properties = new IntegrityVerifyReportProperties();

  private static final OffsetDateTime FROM =
      OffsetDateTime.of(2026, 10, 1, 0, 0, 0, 0, ZoneOffset.UTC);

  @Test
  @DisplayName("null from or to is rejected")
  void validateWindow_requiresBothEnds() {
    assertThrows(IllegalArgumentException.class, () -> properties.validateWindow(null, FROM));
    assertThrows(IllegalArgumentException.class, () -> properties.validateWindow(FROM, null));
  }

  @Test
  @DisplayName("inverted range is rejected")
  void validateWindow_rejectsInverted() {
    assertThrows(
        IllegalArgumentException.class, () -> properties.validateWindow(FROM, FROM.minusHours(1)));
  }

  @Test
  @DisplayName("exactly 192 hours is accepted; 193 hours is rejected")
  void validateWindow_enforcesDefaultCap() {
    assertDoesNotThrow(() -> properties.validateWindow(FROM, FROM.plusHours(192)));
    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class,
            () -> properties.validateWindow(FROM, FROM.plusHours(193)));
    assertTrue(ex.getMessage().contains("192"));
  }
}
