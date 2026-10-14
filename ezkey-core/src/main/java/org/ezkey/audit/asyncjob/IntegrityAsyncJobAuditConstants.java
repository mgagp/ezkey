/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Constants: IntegrityAsyncJobAuditConstants
 * Description: Shared audit action string for Integrity async job lifecycle events.
 */

package org.ezkey.audit.asyncjob;

/**
 * Audit action constants for Integrity async job STARTED / COMPLETED / ABANDONED events.
 *
 * <p>Matches the nearby integrity family convention of one kebab-case action per feature family
 * (e.g. {@code audit-chain-lifecycle}, {@code retroactive-integrity-validation}).
 *
 * @since 2026
 */
public final class IntegrityAsyncJobAuditConstants {

  private IntegrityAsyncJobAuditConstants() {}

  /**
   * Persisted {@code event_action} for Integrity async job lifecycle audits ({@code STARTED},
   * {@code COMPLETED}, {@code ABANDONED}).
   */
  public static final String EVENT_ACTION = "integrity-async-job";
}
