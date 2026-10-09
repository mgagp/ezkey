/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: RetroactiveIntegrityPropertiesTest
 * Description: Default and override for operator-max-window-hours (primitive).
 */

package org.ezkey.audit.integrity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link RetroactiveIntegrityProperties}.
 *
 * @since 2026
 */
@DisplayName("RetroactiveIntegrityProperties")
class RetroactiveIntegrityPropertiesTest {

  @Test
  @DisplayName("default operator max is the shared 193h heavy-crypto limit (primitive, non-null)")
  void defaultOperatorMaxMatchesSharedLimit() {
    RetroactiveIntegrityProperties properties = new RetroactiveIntegrityProperties();
    assertEquals(
        IntegrityHeavyCryptoWindowLimits.DEFAULT_MAX_WINDOW_HOURS,
        properties.getOperatorMaxWindowHours());
  }

  @Test
  @DisplayName("setter keeps an explicit positive override")
  void setterAcceptsExplicitOverride() {
    RetroactiveIntegrityProperties properties = new RetroactiveIntegrityProperties();
    properties.setOperatorMaxWindowHours(48);
    assertEquals(48, properties.getOperatorMaxWindowHours());
  }
}
