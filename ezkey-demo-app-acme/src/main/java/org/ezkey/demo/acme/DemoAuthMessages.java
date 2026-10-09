/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.demo.acme;

/**
 * User-facing auth copy shared across ACME demo controllers.
 *
 * @author Ezkey contributors
 * @since 2026
 */
public final class DemoAuthMessages {

  /**
   * Generic failure for unknown access codes, unknown users, duplicates, and raw SDK errors. Never
   * echo backend exception messages to the browser.
   */
  public static final String GENERIC_SIGN_IN_FAILED =
      "Sign-in failed. Check your access link and username.";

  /** Shown when the shared login / access-link rate-limit bucket is exhausted. */
  public static final String RATE_LIMIT_LOGIN =
      "Too many login attempts from your network. Please wait a moment and try again.";

  /** Shown when the Apply API Key rate-limit bucket is exhausted. */
  public static final String RATE_LIMIT_APPLY_API_KEY =
      "Too many API key apply attempts from your network. Please wait and try again.";

  /**
   * Session or access-code slot lost (expired cookie, concurrent rotation race, unknown session).
   * Never includes the access code. EN/FR on one line for the thin demo surface.
   */
  public static final String SESSION_OR_SLOT_LOST =
      "Your session ended. Reopen your personal access link to continue. / Votre session a pris"
          + " fin. Rouvrez votre lien d'accès personnel pour continuer.";

  /**
   * Recovery line for link-lost login layout (state B) when the primary message is not already
   * {@link #SESSION_OR_SLOT_LOST}.
   */
  public static final String LINK_RECOVERY_REOPEN =
      "To sign in again, reopen the access link you were given. / Pour vous reconnecter, rouvrez"
          + " le lien d'accès qu'on vous a transmis.";

  /** Hint after an unknown access code on {@code /t/{code}} (state B). */
  public static final String LINK_HINT_CHECK_OR_ASK =
      "Check the access link you were given, or ask for a new one. / Vérifiez le lien d'accès"
          + " qu'on vous a transmis, ou demandez-en un nouveau.";

  /** Hint after access-link rate limiting on {@code /t/{code}} (state B). */
  public static final String LINK_HINT_WAIT_AND_REOPEN =
      "Wait a few minutes, then reopen your access link. / Attendez quelques minutes, puis"
          + " rouvrez votre lien d'accès.";

  private DemoAuthMessages() {}
}
