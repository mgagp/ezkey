/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Canonical payload builder for Pending (integration-signed) and Respond (device-signed) signatures.
 * See docs/AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md for the shared spec (NFC + UTF-8).
 */

package org.ezkey.authattempt.service;

import java.text.Normalizer;
import org.ezkey.authattempt.domain.AuthenticationResult;

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
   * <p>Format: {@code proofToken|challengeRequired|challengeRequiredByPolicy|contextTitle
   * |contextMessage}. Null title/message become empty string. Challenge flags and text are
   * canonical (NFC for text).
   *
   * @param proofToken the auth attempt proof token (must not be null)
   * @param challengeRequired whether a challenge is required
   * @param challengeRequiredByPolicy whether the challenge requirement is enforced by enrollment
   *     policy
   * @param contextTitle optional context title (null → "")
   * @param contextMessage optional context message (null → "")
   * @return the canonical payload string (UTF-8)
   */
  public static String buildPendingPayload(
      String proofToken,
      boolean challengeRequired,
      boolean challengeRequiredByPolicy,
      String contextTitle,
      String contextMessage) {
    String challengeStr = challengeRequired ? TRUE : FALSE;
    String challengeByPolicyStr = challengeRequiredByPolicy ? TRUE : FALSE;
    String title = nfcOrEmpty(contextTitle);
    String message = nfcOrEmpty(contextMessage);
    return proofToken
        + SEP
        + challengeStr
        + SEP
        + challengeByPolicyStr
        + SEP
        + title
        + SEP
        + message;
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
   * Builds the payload signed by the integration when returning the Respond HTTP response.
   *
   * <p>Format: {@code proofToken|authAttemptId|result|message}. Use empty string for a null proof
   * token (e.g. attempt not found). {@code authAttemptId} is the decimal string or empty when null.
   * {@code result} is {@link AuthenticationResult#name()}. {@code message} is NFC-normalized
   * user-facing text (null becomes "").
   *
   * @param proofToken the auth attempt proof token, or null to use empty string
   * @param authAttemptId the attempt id, or null to use empty string
   * @param result the authentication result enum, or null to use empty string for the segment
   * @param message user-facing message (NFC-normalized; null becomes "")
   * @return the canonical payload string (UTF-8)
   */
  public static String buildRespondResultPayload(
      String proofToken, Integer authAttemptId, AuthenticationResult result, String message) {
    String pt = proofToken != null ? proofToken : "";
    String idStr = authAttemptId != null ? String.valueOf(authAttemptId) : "";
    String res = result != null ? result.name() : "";
    String msg = nfcOrEmpty(message);
    return pt + SEP + idStr + SEP + res + SEP + msg;
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
