/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: LogSanitizerTest
 * Description: Control-character neutralization for log-injection hardening.
 */

package org.ezkey.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LogSanitizerTest {

  @Test
  void sanitizeForLog_null_returnsNull() {
    assertNull(LogSanitizer.sanitizeForLog(null));
  }

  @Test
  void sanitizeForLog_cleanValue_returnsSameInstance() {
    String clean = "192.168.1.1";
    assertSame(clean, LogSanitizer.sanitizeForLog(clean));
    assertEquals("", LogSanitizer.sanitizeForLog(""));
  }

  @Test
  void sanitizeForLog_replacesCrLf() {
    assertEquals("evil__ip", LogSanitizer.sanitizeForLog("evil\r\nip"));
  }

  @Test
  void sanitizeForLog_replacesAnsiEscapeSequence() {
    assertEquals("_[31mred_[0m", LogSanitizer.sanitizeForLog("\u001B[31mred\u001B[0m"));
  }

  @ParameterizedTest(name = "C0 control U+{0}")
  @ValueSource(ints = {0x00, 0x07, 0x08, 0x09, 0x0B, 0x0C, 0x1B, 0x1F})
  void sanitizeForLog_replacesC0Controls(int codePoint) {
    assertEquals("a_b", LogSanitizer.sanitizeForLog("a" + (char) codePoint + "b"));
  }

  @ParameterizedTest(name = "DEL and C1 control U+{0}")
  @ValueSource(ints = {0x7F, 0x80, 0x85, 0x9B, 0x9F})
  void sanitizeForLog_replacesDelAndC1Controls(int codePoint) {
    assertEquals("a_b", LogSanitizer.sanitizeForLog("a" + (char) codePoint + "b"));
  }

  @ParameterizedTest(name = "Unicode separator U+{0}")
  @ValueSource(ints = {0x2028, 0x2029})
  void sanitizeForLog_replacesUnicodeLineAndParagraphSeparators(int codePoint) {
    assertEquals("a_b", LogSanitizer.sanitizeForLog("a" + (char) codePoint + "b"));
  }

  @Test
  void sanitizeForLog_keepsPrintableUnicodeAndSurrogatePairs() {
    String value = "élève café 日本 🔑 user@example.org ~";
    assertSame(value, LogSanitizer.sanitizeForLog(value));
  }
}
