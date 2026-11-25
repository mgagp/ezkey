/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Helper: LogSanitizer
 * Description: Neutralizes control characters in values written to demo-device application logs.
 */

package org.ezkey.demo.device.util;

/**
 * Log-injection hardening helpers for the demo device (mirrors {@code
 * org.ezkey.util.LogSanitizer}).
 *
 * @author Ezkey contributors
 * @since 2026
 */
public final class LogSanitizer {

  private LogSanitizer() {}

  /**
   * Neutralizes control characters in values written to logs (log-injection hardening).
   *
   * <p>Every character for which {@link Character#isISOControl(char)} is true (C0 controls such as
   * CR, LF, TAB and ESC, DEL, and C1 controls such as NEL U+0085) is replaced by {@code '_'}, as
   * are the Unicode LINE SEPARATOR (U+2028) and PARAGRAPH SEPARATOR (U+2029). This blocks forged
   * log lines and ANSI escape sequences in terminals. Other characters are kept as is.
   *
   * @param value raw value (may be null)
   * @return sanitized value, or null
   */
  public static String sanitizeForLog(String value) {
    if (value == null) {
      return null;
    }
    StringBuilder sanitized = null;
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (isUnsafe(c)) {
        if (sanitized == null) {
          sanitized = new StringBuilder(value);
        }
        sanitized.setCharAt(i, '_');
      }
    }
    return sanitized == null ? value : sanitized.toString();
  }

  private static boolean isUnsafe(char c) {
    return Character.isISOControl(c) || c == '\u2028' || c == '\u2029';
  }
}
