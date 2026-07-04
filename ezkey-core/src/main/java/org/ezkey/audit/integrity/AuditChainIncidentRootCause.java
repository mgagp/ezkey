/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Enum: AuditChainIncidentRootCause
 * Description: Operator-selected root cause for an audit-chain heartbeat incident.
 */

package org.ezkey.audit.integrity;

/**
 * Operator-selected root cause for a heartbeat incident declaration.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2026
 */
public enum AuditChainIncidentRootCause {
  /** Planned rolling upgrade, version bump, or redeploy that stops checkpoint emission briefly. */
  PLANNED_SYSTEM_UPGRADE,

  ADMIN_API_DOWN,

  SCHEDULER_FAILURE,

  DB_UNAVAILABLE,

  NETWORK_PARTITION,

  MISCONFIGURATION,

  UNKNOWN
}
