/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Enum: AlertResolutionReason
 * Description: Reason an alert transitioned from OPEN to RESOLVED.
 */

package org.ezkey.alert.domain;

/**
 * Reason an alert transitioned from {@code OPEN} to {@code RESOLVED}.
 *
 * <p>Stored on the resolved row for SOC 2-oriented traceability. New reasons are added at the
 * bottom as new producers and resolution paths land.
 *
 * @since 2026
 */
public enum AlertResolutionReason {
  /**
   * Resolved automatically by the system because the operator declared the matching audit chain gap
   * (a {@code GAP_DECLARATION} checkpoint was created).
   */
  GAP_DECLARED,

  /**
   * Resolved automatically because periodic audit-chain checkpoints advanced again after a
   * heartbeat-stale episode (peripheral supervision recovered).
   */
  HEARTBEAT_RESTORED,

  /** Resolved manually by an administrator (reserved; no producer in the current iteration). */
  MANUAL
}
