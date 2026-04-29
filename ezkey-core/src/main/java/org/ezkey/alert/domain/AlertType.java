/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Enum: AlertType
 * Description: Catalog of operator-facing alert kinds raised by internal Ezkey producers.
 */

package org.ezkey.alert.domain;

/**
 * Catalog of operator-facing alert kinds raised by internal Ezkey producers.
 *
 * <p>Stored in {@code ezkey_alert.alert_type} as the enum name (VARCHAR). New values are added at
 * the bottom; do not reorder or rename existing values, since persisted rows reference them by
 * name.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @since 2026
 */
public enum AlertType {
  /**
   * Audit chain scheduler detected an undeclared gap before its lookback window. Resolved when a
   * matching {@code GAP_DECLARATION} checkpoint is created.
   */
  AUDIT_CHAIN_GAP_PENDING,

  /**
   * Peripheral APIs detected stalled periodic audit-chain checkpoints relative to configured grace
   * windows (Admin API scheduler appears stalled). Resolved automatically when checkpoints advance
   * again.
   */
  AUDIT_CHAIN_HEARTBEAT_STALE
}
