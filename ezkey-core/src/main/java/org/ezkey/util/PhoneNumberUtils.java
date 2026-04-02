/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.util;

import java.util.regex.Pattern;

/** Utility methods for canonical phone number normalization and masking. */
public final class PhoneNumberUtils {

  private static final Pattern E164_PATTERN = Pattern.compile("^\\+[1-9][0-9]{7,14}$");

  private PhoneNumberUtils() {
    // Utility class
  }

  /**
   * Normalizes a user-provided phone number to canonical E.164.
   *
   * <p>Accepted inputs may contain spaces and common separators, but they must already include the
   * international prefix ({@code +}) and country code. Blank inputs are treated as absent.
   *
   * @param rawValue raw user input
   * @return normalized E.164 value, or {@code null} when blank
   * @throws IllegalArgumentException if the value cannot be normalized safely
   */
  public static String normalizeToE164OrNull(String rawValue) {
    if (rawValue == null) {
      return null;
    }

    String trimmed = rawValue.trim();
    if (trimmed.isEmpty()) {
      return null;
    }

    StringBuilder normalized = new StringBuilder();
    for (int i = 0; i < trimmed.length(); i++) {
      char ch = trimmed.charAt(i);
      if (Character.isDigit(ch)) {
        normalized.append(ch);
        continue;
      }
      if (ch == '+' && normalized.isEmpty()) {
        normalized.append(ch);
        continue;
      }
      if (Character.isWhitespace(ch) || ch == '-' || ch == '(' || ch == ')' || ch == '.') {
        continue;
      }
      throw new IllegalArgumentException("Phone number must be a valid international number");
    }

    String result = normalized.toString();
    if (!E164_PATTERN.matcher(result).matches()) {
      throw new IllegalArgumentException(
          "Phone number must include country code and use a valid international format");
    }
    return result;
  }

  /**
   * Masks an E.164 phone number for audit logs.
   *
   * @param e164Phone canonical E.164 phone number, or any nullable value
   * @return masked value suitable for audit/event details
   */
  public static String maskForAudit(String e164Phone) {
    if (e164Phone == null || e164Phone.isBlank()) {
      return null;
    }
    if (e164Phone.length() <= 5) {
      return "+***";
    }
    String prefix = e164Phone.substring(0, Math.min(2, e164Phone.length()));
    String suffix = e164Phone.substring(Math.max(e164Phone.length() - 4, prefix.length()));
    return prefix
        + "*".repeat(Math.max(0, e164Phone.length() - prefix.length() - suffix.length()))
        + suffix;
  }
}
