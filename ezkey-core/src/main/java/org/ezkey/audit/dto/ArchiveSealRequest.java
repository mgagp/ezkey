/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: ArchiveSealRequest
 * Description: Request body for the audit chain archive seal lifecycle operation.
 */

package org.ezkey.audit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

/**
 * Request body for sealing an audit chain period prior to archiving and dropping the partition.
 *
 * <p>The seal operation marks all checkpoints in the specified period as {@code ARCHIVE_SEAL} so
 * that future chain verification skips the entries_digest comparison (entries will no longer be in
 * the database) while still verifying the {@code chain_hmac} linkage.
 *
 * <p><b>Period identification — two alternative modes:</b>
 *
 * <ul>
 *   <li><b>Timestamp mode</b> (default): provide {@code periodStart} (inclusive) and {@code
 *       periodEnd} (exclusive). Standard ISO-8601 half-open interval.
 *   <li><b>Checkpoint ID mode</b>: provide {@code checkpointIdFrom} and {@code checkpointIdTo}
 *       (both <em>inclusive</em>). Look up the checkpoint IDs directly in the database — no
 *       timestamp conversion needed. The service derives the effective time range from the
 *       corresponding checkpoints' {@code window_start} / {@code window_end} fields and runs
 *       verification on that range.
 * </ul>
 *
 * <p>Exactly one mode must be specified. Providing both is an error. The checkpoint ID mode is the
 * ergonomic option when working directly with the database (IDs are short integers and immediately
 * visible in any query result).
 *
 * <p><b>Pre-condition:</b> All checkpoints in the period must pass integrity verification before
 * the seal is accepted. The operation is rejected if any checkpoint shows a violation.
 *
 * <p><b>Post-condition:</b> The DBA may safely {@code DROP} the audit log partition for the period.
 * The seal chain HMAC returned in the response must be included in the Git archive manifest for
 * long-term provenance.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2026
 */
public record ArchiveSealRequest(

    // -----------------------------------------------------------------------------------------
    // Timestamp mode (mutually exclusive with checkpoint ID mode)
    // -----------------------------------------------------------------------------------------

    /**
     * Start of the period to seal (inclusive). ISO-8601 timestamp with timezone offset. Typically
     * the first instant of the oldest partition month being archived.
     *
     * <p>Required when using timestamp mode; {@code null} when using checkpoint ID mode.
     */
    OffsetDateTime periodStart,

    /**
     * End of the period to seal (exclusive). ISO-8601 timestamp with timezone offset. Typically the
     * first instant of the month following the last archived partition month.
     *
     * <p>Required when using timestamp mode; {@code null} when using checkpoint ID mode.
     */
    OffsetDateTime periodEnd,

    // -----------------------------------------------------------------------------------------
    // Checkpoint ID mode (mutually exclusive with timestamp mode)
    // -----------------------------------------------------------------------------------------

    /**
     * ID of the first checkpoint to seal (inclusive). Look up the lowest checkpoint ID for the
     * partition period in the database and pass it here.
     *
     * <p>Required when using checkpoint ID mode; {@code null} when using timestamp mode. Both
     * {@code checkpointIdFrom} and {@code checkpointIdTo} must be provided together.
     */
    Long checkpointIdFrom,

    /**
     * ID of the last checkpoint to seal (inclusive). Look up the highest checkpoint ID for the
     * partition period in the database and pass it here.
     *
     * <p>Required when using checkpoint ID mode; {@code null} when using timestamp mode. Both
     * {@code checkpointIdFrom} and {@code checkpointIdTo} must be provided together.
     */
    Long checkpointIdTo,

    // -----------------------------------------------------------------------------------------
    // Required in all modes
    // -----------------------------------------------------------------------------------------

    /**
     * Human-readable justification for the archive seal. Must clearly identify where the archived
     * data is stored (e.g., Git repository URL, export filename, retention policy reference). This
     * text is stored in the {@code notes} field of all sealed checkpoints and in the audit entry.
     * Aligned with other audit reason fields: 10–500 characters (SOC 2 traceability).
     */
    @NotBlank(message = "Justification is required")
    @Size(min = 10, max = 500, message = "Justification must be between 10 and 500 characters")
    String justification) {}
