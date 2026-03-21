/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Canonical payload builder for Pending (verify integration signature) and Respond (device sign).
 * Must match backend format: see docs/AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md and
 * ezkey-core AuthAttemptSignaturePayload.
 */

package org.ezkey.demo.device.service;

import java.text.Normalizer;

/**
 * Builds canonical signature payloads for auth attempt Pending and Respond. Uses Unicode NFC for
 * context text and pipe-separated format (UTF-8).
 */
public final class AuthAttemptPayloadUtil {

  private static final String SEP = "|";
  private static final String TRUE = "true";
  private static final String FALSE = "false";

  private AuthAttemptPayloadUtil() {}

  /**
   * Builds the payload that the integration signed for the Pending response. Used to verify the
   * integration signature. Format: proofToken|challengeRequired|contextTitle|contextMessage.
   */
  public static String buildPendingPayload(
      String proofToken, boolean challengeRequired, String contextTitle, String contextMessage) {
    String challengeStr = challengeRequired ? TRUE : FALSE;
    String title = nfcOrEmpty(contextTitle);
    String message = nfcOrEmpty(contextMessage);
    return proofToken + SEP + challengeStr + SEP + title + SEP + message;
  }

  /**
   * Builds the payload the device must sign for the Respond request. Format: proofToken|accepted.
   */
  public static String buildRespondPayload(String proofToken, boolean accepted) {
    String acceptedStr = accepted ? TRUE : FALSE;
    return proofToken + SEP + acceptedStr;
  }

  private static String nfcOrEmpty(String s) {
    if (s == null) {
      return "";
    }
    return Normalizer.normalize(s, Normalizer.Form.NFC);
  }
}
