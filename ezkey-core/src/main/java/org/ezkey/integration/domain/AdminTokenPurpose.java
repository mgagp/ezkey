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
 * (SEC-021). {@link #EVALUATOR_TEMP} tokens grant a one-shot navigable tenant-admin foothold on
 * community/lab when evaluator self-registration is enabled (Mode C); they must never be promoted
 * into {@link #SESSION}.
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
   * One-shot temporary evaluator console session (absolute TTL, navigable TENANT_ADMIN deny-list).
   * Distinct from {@link #SESSION}; revoked on MFA VERIFIED bind or expiry.
   */
  EVALUATOR_TEMP
}
