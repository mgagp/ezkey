/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Enum: IntegrityAsyncJobTrigger
 * Description: How an Integrity async / verdict job was initiated.
 */

package org.ezkey.audit.asyncjob;

/**
 * How an Integrity job row was initiated (Integrity cut 3 OQ4 A′).
 *
 * <p>{@link #NIGHTLY} is the persisted form of {@code
 * RetroactiveIntegrityValidationTriggerSource.SCHEDULED}. Operator POST may declare {@link #MANUAL}
 * or {@link #AUTO} only; {@link #NIGHTLY} is rejected on POST.
 *
 * @since 2026
 */
public enum IntegrityAsyncJobTrigger {

  /** Explicit operator action (Revérifier / start). Default for legacy rows. */
  MANUAL,

  /** Client-declared automatic start (e.g. page-open auto-run metadata). */
  AUTO,

  /**
   * Scheduled nightly batch terminal row. Never RUNNING. Excluded from the operator slot readers.
   */
  NIGHTLY
}
