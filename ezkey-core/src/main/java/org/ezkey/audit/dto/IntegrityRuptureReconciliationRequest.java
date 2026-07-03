/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: IntegrityRuptureReconciliationRequest
 * Description: Request body for reconciling an AUDIT_INTEGRITY_RUPTURE alert.
 */

package org.ezkey.audit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Request body for reconciling an open {@code AUDIT_INTEGRITY_RUPTURE} alert.
 *
 * <p>Creates a signed {@code MANIPULATION_CONCILIATION} checkpoint spanning {@code failBoundary} to
 * {@code resumeBoundary}, re-chains downstream checkpoints, emits a conciliation audit event, and
 * resolves the alert.
 *
 * @since 2026
 */
public record IntegrityRuptureReconciliationRequest(

    /** Open alert id to reconcile. Required when {@code dedupeKey} is not provided. */
    Long alertId,

    /** Alternative to {@code alertId}: dedupe key of the open alert. */
    String dedupeKey,

    /** Cryptographic discontinuity start (inclusive), from alert payload. */
    @NotNull OffsetDateTime failBoundary,

    /** Cryptographic continuity resume point (exclusive), from alert payload. */
    @NotNull OffsetDateTime resumeBoundary,

    /** Operator justification (10–500 characters). */
    @NotBlank(message = "Justification is required")
        @Size(min = 10, max = 500, message = "Justification must be between 10 and 500 characters")
        String justification,

    /** Optional external ticket reference (e.g. ITSM id). */
    @Size(max = 128, message = "External ticket reference must be at most 128 characters")
        String externalTicketReference,

    /** Operator classification of the rupture. */
    @NotNull IntegrityRuptureConciliationCategory category,

    /**
     * Audit log ids of entry HMAC violations acknowledged in this reconcile act. Required to
     * exactly match live unacknowledged entry violations when any remain; absent or empty for
     * chain-only ruptures.
     */
    List<Long> acknowledgedAuditLogIds) {}
