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

  private DemoAuthMessages() {}
}
