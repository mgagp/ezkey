/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: EntryIntegrityConciliationSummary
 * Description: ACTIVE conciliation metadata for an entry integrity violation.
 */

package org.ezkey.audit.dto;

import java.time.OffsetDateTime;

/**
 * Summary of the ACTIVE conciliation row for an audit log entry, when present.
 *
 * @param conciliationId conciliation registry primary key
 * @param conciliatedAt UTC timestamp of the operator act
 * @param category reconciliation category copied at conciliation time
 * @param conciliatedByAdminId Global Admin who recorded the conciliation
 * @param sourceAlertId alert that triggered the bundled reconcile act
 * @since 2026
 */
public record EntryIntegrityConciliationSummary(
    Long conciliationId,
    OffsetDateTime conciliatedAt,
    IntegrityRuptureConciliationCategory category,
    Integer conciliatedByAdminId,
    Long sourceAlertId) {}
