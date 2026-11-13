/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: EzkeyConfigToStringMaskingTest
 * Description: EzkeyConfig.toString() must mask secretKey (#755).
 */

package org.ezkey.sdk;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guards against accidental leakage of integration secrets via {@link EzkeyConfig#toString()}.
 *
 * @since 2026
 */
class EzkeyConfigToStringMaskingTest {

  private static final String SECRET = "ezkey_skey_must-not-appear-in-logs";

  @Test
  @DisplayName("toString masks secretKey and never prints the raw secret (#755)")
  void toString_masksSecretKey() {
    EzkeyConfig config =
        new EzkeyConfig(
            "http://localhost:7080",
            "ezkey_ikey_demo",
            SECRET,
            Duration.ofSeconds(10),
            Duration.ofSeconds(30));

    String rendered = config.toString();

    assertFalse(rendered.contains(SECRET));
    assertTrue(rendered.contains("secretKey=[PROTECTED]"));
    assertTrue(rendered.contains("ezkey_ikey_demo"));
    assertTrue(rendered.contains("http://localhost:7080"));
  }
}
