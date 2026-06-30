/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: IntegrityRuptureReconciliationResult
 * Description: Result of an integrity rupture reconciliation lifecycle operation.
 */

package org.ezkey.audit.dto;

import java.time.OffsetDateTime;

/**
 * Result of reconciling an {@code AUDIT_INTEGRITY_RUPTURE} alert.
 *
 * @since 2026
 */
public record IntegrityRuptureReconciliationResult(
    OffsetDateTime failBoundary,
    OffsetDateTime resumeBoundary,
    Long conciliationCheckpointId,
    String conciliationChainHmac,
    Long auditLogId,
    Long resolvedAlertId,
    String justification,
    IntegrityRuptureConciliationCategory category) {}
