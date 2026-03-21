/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Canonical payload builder for Pending (integration-signed) and Respond (device-signed) signatures.
 * See docs/AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md for the shared spec (NFC + UTF-8).
 */

package org.ezkey.authattempt.service;

import java.text.Normalizer;

/**
 * Builds canonical signature payloads for auth attempt Pending and Respond flows.
 *
 * <p>Payloads use a pipe-separated format. Text fields ({@code contextTitle}, {@code
 * contextMessage}) are normalized to Unicode NFC so that backend and clients produce identical
 * bytes (UTF-8) for signature verification. See Unicode Standard Annex #15 (UAX #15).
 */
public final class AuthAttemptSignaturePayload {

  private static final String SEP = "|";
  private static final String TRUE = "true";
  private static final String FALSE = "false";

  private AuthAttemptSignaturePayload() {}

  /**
   * Builds the payload signed by the integration when returning the Pending response.
   *
   * <p>Format: {@code proofToken|challengeRequired|contextTitle|contextMessage}. Null title/message
   * become empty string. Challenge and text are canonical (NFC for text).
   *
   * @param proofToken the auth attempt proof token (must not be null)
   * @param challengeRequired whether a challenge is required
   * @param contextTitle optional context title (null → "")
   * @param contextMessage optional context message (null → "")
   * @return the canonical payload string (UTF-8)
   */
  public static String buildPendingPayload(
      String proofToken, boolean challengeRequired, String contextTitle, String contextMessage) {
    String challengeStr = challengeRequired ? TRUE : FALSE;
    String title = nfcOrEmpty(contextTitle);
    String message = nfcOrEmpty(contextMessage);
    return proofToken + SEP + challengeStr + SEP + title + SEP + message;
  }

  /**
   * Builds the payload signed by the device when sending the Respond request.
   *
   * <p>Format: {@code proofToken|accepted}. Accepted is literal "true" or "false".
   *
   * @param proofToken the auth attempt proof token (must not be null)
   * @param accepted whether the user accepted the authentication
   * @return the canonical payload string (UTF-8)
   */
  public static String buildRespondPayload(String proofToken, boolean accepted) {
    String acceptedStr = accepted ? TRUE : FALSE;
    return proofToken + SEP + acceptedStr;
  }

  /**
   * Normalizes to NFC or returns empty string for null. Used for context fields only.
   *
   * @param s input string (may be null)
   * @return NFC-normalized string, or "" if null
   */
  public static String nfcOrEmpty(String s) {
    if (s == null) {
      return "";
    }
    return Normalizer.normalize(s, Normalizer.Form.NFC);
  }
}
