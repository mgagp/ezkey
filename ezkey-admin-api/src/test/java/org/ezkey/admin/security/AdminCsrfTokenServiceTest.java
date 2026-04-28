/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 *
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.admin.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Verifies the signed double-submit CSRF token binding. */
class AdminCsrfTokenServiceTest {

  private final AdminCsrfTokenService service = new AdminCsrfTokenService();

  @Test
  void tokenIsValidOnlyForTheSessionTokenItWasIssuedFor() {
    String csrfToken = service.createToken("session-a");

    assertTrue(service.isValid("session-a", csrfToken));
    assertFalse(service.isValid("session-b", csrfToken));
    assertFalse(service.isValid("session-a", "invalid"));
    assertFalse(service.isValid("session-a", null));
  }
}
