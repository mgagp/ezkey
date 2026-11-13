/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Projection: AuditLogEntryHmacView
 * Description: entry_hmac (+ id) only — chain digest recompute without loading full audit rows.
 */

package org.ezkey.audit.domain.repository;

/**
 * Closed projection for chain {@code entries_digest} recomputation.
 *
 * <p>Loads only {@code audit_log_id} (sort key) and {@code entry_hmac} so verify paths avoid
 * materializing JSONB {@code event_details} and other audit columns (Integrity cut 3 D5).
 *
 * @since 2026
 */
public interface AuditLogEntryHmacView {

  /**
   * Audit log primary key (ordering within a window).
   *
   * @return audit log id
   */
  Long getAuditLogId();

  /**
   * Per-entry HMAC (may be null for legacy / unsigned rows — digest treats null as empty string).
   *
   * @return entry HMAC Base64, or null
   */
  String getEntryHmac();
}
