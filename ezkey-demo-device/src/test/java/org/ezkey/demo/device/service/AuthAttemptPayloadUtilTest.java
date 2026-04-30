/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.demo.device.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AuthAttemptPayloadUtilTest {

  @Test
  void buildRespondResultPayload_matches_backend_canonical_form() {
    assertThat(
            AuthAttemptPayloadUtil.buildRespondResultPayload(
                "pt", 42, "APPROVED", "Auth attempt completed"))
        .isEqualTo("pt|42|APPROVED|Auth attempt completed");
  }
}
