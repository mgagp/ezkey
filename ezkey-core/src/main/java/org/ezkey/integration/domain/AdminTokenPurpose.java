/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Enum: AdminTokenPurpose
 * Description: Distinguishes passwordless session tokens from recovery, bootstrap, and resume tokens.
 */
package org.ezkey.integration.domain;

/**
 * Purpose of an administrator bearer token.
 *
 * <p>{@link #SESSION} tokens authenticate ordinary Admin API calls. {@link #RECOVERY} tokens are
 * issued after recovery-code validation and may only be used on the enrollment-reset funnel
 * (SEC-021). {@link #BOOTSTRAP} tokens are issued with anonymous evaluator self-registration and
 * authenticate a narrow pending-activation allowlist only (absolute TTL, no sliding). {@link
 * #ONBOARDING_RESUME} tokens are capability secrets redeemed via {@code POST
 * /api/v1/admin/auth/onboarding-resume} to remint a BOOTSTRAP session — they must not authenticate
 * as Admin API bearers.
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
   * Temporary evaluator console foothold minted with public self-registration when that feature is
   * enabled. Narrow path allowlist only; not a full operator session.
   */
  BOOTSTRAP,

  /**
   * Opaque onboarding-resume capability minted at activation. Redeemed to remint BOOTSTRAP; not a
   * session bearer. Hashed at rest; limited uses; absolute TTL.
   */
  ONBOARDING_RESUME
}
