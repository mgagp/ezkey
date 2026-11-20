/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Enum: AdminTokenPurpose
 * Description: Distinguishes passwordless session tokens from recovery reset-only tokens.
 */

package org.ezkey.integration.domain;

/**
 * Purpose of an administrator bearer token.
 *
 * <p>{@link #SESSION} tokens authenticate ordinary Admin API calls. {@link #RECOVERY} tokens are
 * issued after recovery-code validation and may only be used on the enrollment-reset funnel
 * (SEC-021). {@link #ACTIVATION} rows hold deferred-onboarding invite codes; they are only consumed
 * by the unauthenticated activation endpoint and never authenticate a request.
 *
 * @author Ezkey contributors
 * @since 2026
 */
public enum AdminTokenPurpose {

  /** Passwordless login session for the full Admin API surface. */
  SESSION,

  /** Temporary break-glass token scoped to enrollment reset. */
  RECOVERY,

  /**
   * One-time deferred-onboarding activation code ({@code ezkey_activation_…}). Absolute TTL; never
   * accepted as a bearer session (#1122).
   */
  ACTIVATION
}
