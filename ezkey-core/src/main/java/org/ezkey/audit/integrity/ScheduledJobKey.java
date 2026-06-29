/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Enum: ScheduledJobKey
 * Description: Stable identifiers for rows in ezkey_scheduled_job_last_run.
 */

package org.ezkey.audit.integrity;

/**
 * Stable identifiers for scheduled job last-run registry rows.
 *
 * @since 2026
 */
public enum ScheduledJobKey {
  AUDIT_CHAIN_CHECKPOINT,
  NIGHTLY_INTEGRITY_VALIDATION,
  REENCRYPTION
}
