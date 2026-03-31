/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: GapDeclarationResult
 * Description: Result of the audit chain gap declaration lifecycle operation.
 */

package org.ezkey.audit.dto;

import java.time.OffsetDateTime;

/**
 * Result of formally declaring a downtime gap in the audit chain.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2026
 */
public record GapDeclarationResult(

    /** Start of the declared gap period (inclusive). */
    OffsetDateTime gapStart,

    /** End of the declared gap period (exclusive). */
    OffsetDateTime gapEnd,

    /**
     * Database ID of the single {@code GAP_DECLARATION} checkpoint created to represent this gap in
     * the chain.
     */
    Long gapCheckpointId,

    /**
     * The {@code chain_hmac} of the gap declaration checkpoint. The next regular checkpoint created
     * by the scheduler will link its {@code prev_chain_hmac} to this value, restoring chain
     * continuity.
     */
    String gapChainHmac,

    /**
     * The ID of the meta-audit entry ({@code AUDIT_CHAIN_GAP_DECLARED}) created to record this
     * operation in the audit trail.
     */
    Long auditLogId,

    /** The justification provided by the admin, as stored in the gap checkpoint. */
    String justification) {}
