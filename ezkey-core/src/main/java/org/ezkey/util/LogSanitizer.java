/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Helper: LogSanitizer
 * Description: Neutralizes CR/LF in values written to application logs.
 */

package org.ezkey.util;

/**
 * Shared log-injection hardening helpers.
 *
 * @author Ezkey contributors
 * @since 2026
 */
public final class LogSanitizer {

  private LogSanitizer() {}

  /**
   * Neutralizes CR/LF in values written to logs (log-injection hardening).
   *
   * @param value raw value (may be null)
   * @return sanitized value, or null
   */
  public static String sanitizeForLog(String value) {
    if (value == null) {
      return null;
    }
    return value.replace('\r', '_').replace('\n', '_');
  }
}
