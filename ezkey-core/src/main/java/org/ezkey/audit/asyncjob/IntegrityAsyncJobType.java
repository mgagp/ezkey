/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Enum: IntegrityAsyncJobType
 * Description: Long-running Integrity operator job kinds sharing the global async slot.
 */

package org.ezkey.audit.asyncjob;

/**
 * Long-running Integrity operator job kinds that share the single global async slot.
 *
 * @since 2026
 */
public enum IntegrityAsyncJobType {

  /** Read-only chain checkpoint verification over a date range. */
  VERIFY_CHAIN_RANGE,

  /** Read-only per-entry HMAC verification over a date range. */
  VERIFY_ENTRY_HMAC_RANGE,

  /** Detective validation (may raise alert) over a date range. */
  RUN_VALIDATION
}
