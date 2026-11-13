/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Enum: PendingAttemptPolicy
 * Description: Per-integration choice when a live authentication attempt already exists.
 */

package org.ezkey.integration.domain;

/**
 * What an integration does when a live authentication attempt already exists for the enrollment.
 *
 * <p>Stored on {@code ezkey_integration.pending_attempt_policy}. There is no instance-wide
 * override. {@link #REPLACE} is the default and keeps the historical supersession behavior.
 *
 * @since 2026
 */
public enum PendingAttemptPolicy {
  /** Expire the live attempt and create the new one. */
  REPLACE,

  /** Reject the new create while a live attempt remains. */
  REFUSE
}
