/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: ArchiveSealResult
 * Description: Result of the audit chain archive seal lifecycle operation.
 */

package org.ezkey.audit.dto;

import java.time.OffsetDateTime;

/**
 * Result of sealing an audit chain period prior to archiving and dropping the partition.
 *
 * <p>The {@code sealChainHmac} is the {@code chain_hmac} of the last checkpoint in the sealed
 * period. It acts as the cryptographic anchor for the archived period and <b>must be included</b>
 * in the Git archive manifest so that the archived period can be independently verified and linked
 * back to the live chain.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2026
 */
public record ArchiveSealResult(

    /** Start of the sealed period (inclusive). */
    OffsetDateTime periodStart,

    /** End of the sealed period (exclusive). */
    OffsetDateTime periodEnd,

    /**
     * Number of checkpoints marked as {@code ARCHIVE_SEAL}. Zero indicates no checkpoints existed
     * in the period (which is valid — the period may have had no activity).
     */
    int checkpointsSealed,

    /**
     * The {@code chain_hmac} of the last checkpoint in the sealed period. This is the cryptographic
     * anchor for the archived period. Include this value in the Git archive manifest for long-term
     * provenance and offline verification.
     *
     * <p>{@code null} if no checkpoints existed in the period.
     */
    String sealChainHmac,

    /**
     * The ID of the meta-audit entry ({@code AUDIT_CHAIN_ARCHIVE_SEALED}) created to record this
     * operation in the audit trail.
     */
    Long auditLogId,

    /** The justification provided by the admin, as stored in the sealed checkpoints. */
    String justification) {}
