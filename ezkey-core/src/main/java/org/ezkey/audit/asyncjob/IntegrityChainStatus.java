/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Enum: IntegrityChainStatus
 * Description: Persisted chain verification status on Integrity async job rows.
 */

package org.ezkey.audit.asyncjob;

/**
 * Persisted {@code chainStatus} on Integrity job rows (Integrity cut 3 D3 / P1).
 *
 * <p>{@link #NOT_VERIFIED} covers HMAC inactive, no checkpoints in range, and skipped validation —
 * fail closed (never green).
 *
 * @since 2026
 */
public enum IntegrityChainStatus {

  /** Cryptographically intact with continuous coverage. */
  OK,

  /** Chain crypto OK but undeclared temporal gaps exist. */
  UNDECLARED_GAP_DETECTED,

  /** Digest / chain HMAC / link violation. */
  CHAIN_INTEGRITY_VIOLATION_DETECTED,

  /** Verification did not produce a usable verdict (inactive HMAC, empty range, skipped). */
  NOT_VERIFIED
}
