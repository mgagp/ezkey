/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Enum: AuditChainIncidentStatus
 * Description: Lifecycle state for audit-chain heartbeat operational incidents.
 */

package org.ezkey.audit.integrity;

/**
 * Lifecycle state for rows in {@code ezkey_audit_chain_incident}.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2026
 */
public enum AuditChainIncidentStatus {

  /** Heartbeat is stale or peripherals are in fail-closed degraded mode for this outage. */
  IN_PROGRESS,

  /** Heartbeat recovered; operator must record justification and root cause. */
  RECOVERED_PENDING_DECLARATION,

  /** Operator closed the incident with a formal declaration. */
  CLOSED
}
