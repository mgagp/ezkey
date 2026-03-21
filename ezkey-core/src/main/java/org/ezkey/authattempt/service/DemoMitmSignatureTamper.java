/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.authattempt.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simulated MITM alterations on the pending JSON after signing. Prefer changing contextual approval
 * text (e.g. CAD amount) for demo narrative; fall back to the proof token when no context is
 * present. Used only when demo gates are enabled.
 */
final class DemoMitmSignatureTamper {

  private static final Pattern CAD_AMOUNT =
      Pattern.compile("(\\d[\\d\\s\\u00A0]*)\\s*\\$\\s*CAD", Pattern.CASE_INSENSITIVE);

  private DemoMitmSignatureTamper() {}

  /**
   * If the message is non-blank, returns a distinct string so the signed canonical payload no
   * longer matches the JSON (e.g. bumps a {@code $ CAD} amount). Returns {@code null} if the
   * message is blank (caller may try title tamper or proof-token tamper).
   *
   * @param message contextual message that was included in the signed payload
   * @return altered message, or {@code null} if nothing to alter
   */
  static String tamperContextMessageForDemoNarrative(String message) {
    if (message == null || message.isBlank()) {
      return null;
    }
    Matcher m = CAD_AMOUNT.matcher(message);
    if (m.find()) {
      String replacement = tamperedCadAmountReplacement(m.group(1));
      return CAD_AMOUNT.matcher(message).replaceFirst(Matcher.quoteReplacement(replacement));
    }
    return message + "\n— Demo: line added after signing.";
  }

  /**
   * If the title is non-blank, returns a distinct title so the signature no longer matches. Returns
   * {@code null} if the title is blank.
   *
   * @param title contextual title that was included in the signed payload
   * @return altered title, or {@code null}
   */
  static String tamperContextTitleForDemoNarrative(String title) {
    if (title == null || title.isBlank()) {
      return null;
    }
    return title.trim() + " — modified (demo)";
  }

  /**
   * Returns a value that differs from the original token so the integration signature over the
   * canonical payload no longer verifies against the JSON field {@code authAttemptProofToken}.
   *
   * @param token the proof token that was included in the signed payload
   * @return a distinct string safe to embed in JSON
   */
  static String tamperProofToken(String token) {
    if (token == null || token.isEmpty()) {
      return "DEMO_MITM_TAMPER";
    }
    return token + "DEMO_MITM_TAMPER";
  }

  private static String tamperedCadAmountReplacement(String amountGroup) {
    String digits = amountGroup.replaceAll("\\s+", "").replace("\u00A0", "");
    try {
      int n = Integer.parseInt(digits);
      int bumped = n + 100_000;
      return formatThousandsSpaces(bumped) + " $ CAD";
    } catch (NumberFormatException e) {
      return "9 999 999 $ CAD";
    }
  }

  /**
   * Thousands grouped with spaces (e.g. 1234567 → "1 234 567", 102340 → "102 340") — suitable for
   * French-language demo copy.
   */
  private static String formatThousandsSpaces(int n) {
    String s = Integer.toString(Math.abs(n));
    int firstGroupLen = s.length() % 3;
    if (firstGroupLen == 0) {
      firstGroupLen = 3;
    }
    StringBuilder sb = new StringBuilder();
    int pos = 0;
    sb.append(s, pos, firstGroupLen);
    pos = firstGroupLen;
    while (pos < s.length()) {
      sb.append(' ');
      sb.append(s, pos, pos + 3);
      pos += 3;
    }
    return sb.toString();
  }
}
