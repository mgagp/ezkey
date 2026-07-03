/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Enum: AuditEntryIntegrityConciliationStatus
 * Description: Lifecycle status for per-entry integrity conciliation rows.
 */

package org.ezkey.audit.integrity;

/**
 * Status of a per-entry integrity conciliation registry row.
 *
 * @since 2026
 */
public enum AuditEntryIntegrityConciliationStatus {
  /** Current acknowledged conciliation for the audit log entry. */
  ACTIVE,

  /** Replaced by a newer conciliation after re-tamper and re-reconcile. */
  SUPERSEDED
}
