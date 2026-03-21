/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.authattempt.service;

/**
 * Operator-facing audit text for the simulated MITM demo. Keeps {@code eventDetails} suitable for
 * presentations: business context (what the approver was asked to validate), not cryptographic
 * material.
 */
public final class DemoMitmAuditNarrative {

  private static final int MAX_SNIPPET = 400;

  private DemoMitmAuditNarrative() {}

  /**
   * Narrative when contextual title/message were altered after signing (e.g. approval amount).
   *
   * @param signedTitle context title included in the signed payload (may be null)
   * @param signedMessage context message included in the signed payload (may be null)
   * @param shownTitle title returned in the pending JSON after tamper
   * @param shownMessage message returned in the pending JSON after tamper
   * @return non-null audit text for {@code eventDetails}
   */
  public static String contextAltered(
      String signedTitle, String signedMessage, String shownTitle, String shownMessage) {
    return "Demo MITM (simulated): After signing, the contextual approval shown to the user was"
        + " altered on the wire. What the integration signed — title: "
        + snippet(signedTitle)
        + ", message: "
        + snippet(signedMessage)
        + ". What the device received — title: "
        + snippet(shownTitle)
        + ", message: "
        + snippet(shownMessage)
        + ". The authenticator rejects the request because the approval details no longer match"
        + " what was signed.";
  }

  /**
   * Narrative when there was no contextual text to illustrate; another pending field was altered so
   * the response is still rejected.
   *
   * @return non-null audit text for {@code eventDetails}
   */
  public static String noContextFallback() {
    return "Demo MITM (simulated): This attempt had no contextual approval text. The demo still"
        + " changes the response after signing so the app rejects it. The same class of attack"
        + " would apply if an intermediary changed an approval amount or wording in the body.";
  }

  private static String snippet(String s) {
    if (s == null || s.isBlank()) {
      return "(none)";
    }
    String t = s.trim().replaceAll("\\s+", " ");
    if (t.length() > MAX_SNIPPET) {
      return t.substring(0, MAX_SNIPPET) + "…";
    }
    return t;
  }
}
