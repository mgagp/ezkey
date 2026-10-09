/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: LogSanitizerTest
 * Description: CR/LF neutralization for log-injection hardening.
 */

package org.ezkey.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class LogSanitizerTest {

  @Test
  void sanitizeForLog_replacesCrLf() {
    assertNull(LogSanitizer.sanitizeForLog(null));
    assertEquals("192.168.1.1", LogSanitizer.sanitizeForLog("192.168.1.1"));
    assertEquals("evil__ip", LogSanitizer.sanitizeForLog("evil\r\nip"));
  }
}
