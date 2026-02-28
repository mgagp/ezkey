/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: GapDeclarationRequest
 * Description: Request body for the audit chain gap declaration lifecycle operation.
 */

package org.ezkey.audit.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;

/**
 * Request body for formally declaring a downtime gap in the audit chain.
 *
 * <p>When the system is offline for longer than the scheduler lookback window (default: 60
 * minutes), the uncovered time windows have no chain checkpoints. This endpoint creates a single
 * {@code GAP_DECLARATION} checkpoint spanning the full gap period, signed into the chain with the
 * admin's justification.
 *
 * <p><b>Gap start — two alternative modes:</b>
 *
 * <ul>
 *   <li><b>Timestamp mode</b> (default): provide {@code gapStart} explicitly as an ISO-8601
 *       timestamp. Typically the exact moment the system went offline.
 *   <li><b>Anchor checkpoint mode</b>: provide {@code anchorCheckpointId}, the {@code
 *       checkpoint_id} of the last checkpoint recorded <em>before</em> the downtime. The service
 *       automatically derives {@code gapStart = anchorCheckpoint.window_end}. This is the ergonomic
 *       option when working directly with the database — look up the last checkpoint ID visible
 *       before the gap and pass it directly, without needing to extract or format the {@code
 *       window_end} timestamp by hand.
 * </ul>
 *
 * <p>Exactly one of {@code gapStart} or {@code anchorCheckpointId} must be provided. Providing both
 * is an error.
 *
 * <p>{@code gapEnd} is always required as a timestamp. It represents the moment the system came
 * back online. Since there are no checkpoints after the gap at declaration time, an ID reference is
 * not applicable for the end boundary.
 *
 * <p><b>Operational constraint:</b> This operation must be called <em>before</em> the scheduler
 * creates regular checkpoints for the gap period. Specifically, it must be called within {@code
 * ezkey.audit.chain.lookback-minutes} minutes of system restart. If regular checkpoints already
 * exist in the gap period, the request is rejected with a descriptive error.
 *
 * <p><b>Recommended workflow for extended downtime:</b>
 *
 * <ol>
 *   <li>Restart the EZ Key stack
 *   <li>Immediately call this endpoint before the first scheduler tick (within 5 minutes)
 *   <li>The scheduler will then chain its post-restart checkpoints from the gap declaration
 * </ol>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2026
 */
public record GapDeclarationRequest(

    // -----------------------------------------------------------------------------------------
    // Gap start — timestamp mode (mutually exclusive with anchorCheckpointId)
    // -----------------------------------------------------------------------------------------

    /**
     * Start of the gap period (inclusive). ISO-8601 timestamp with timezone offset. Typically the
     * moment the system went offline.
     *
     * <p>Required when using timestamp mode; {@code null} when using anchor checkpoint mode.
     * Exactly one of {@code gapStart} or {@code anchorCheckpointId} must be provided.
     */
    OffsetDateTime gapStart,

    // -----------------------------------------------------------------------------------------
    // Gap start — anchor checkpoint mode (mutually exclusive with gapStart)
    // -----------------------------------------------------------------------------------------

    /**
     * The {@code checkpoint_id} of the last checkpoint recorded before the downtime. The service
     * derives {@code gapStart = anchorCheckpoint.window_end} automatically.
     *
     * <p>This is the ergonomic option when working directly with the database: look up the last
     * checkpoint ID visible before the gap and pass it here without needing to read or format the
     * {@code window_end} timestamp manually.
     *
     * <p>Required when using anchor checkpoint mode; {@code null} when using timestamp mode.
     * Exactly one of {@code gapStart} or {@code anchorCheckpointId} must be provided.
     */
    Long anchorCheckpointId,

    // -----------------------------------------------------------------------------------------
    // Gap end — always required as a timestamp
    // -----------------------------------------------------------------------------------------

    /**
     * End of the gap period (exclusive). ISO-8601 timestamp with timezone offset. Typically the
     * moment the system came back online.
     *
     * <p>Required in timestamp mode ({@code gapStart} provided). In anchor checkpoint mode ({@code
     * anchorCheckpointId} provided), {@code gapEnd} is <em>optional</em>: if omitted, the service
     * auto-derives it as the {@code window_start} of the first checkpoint that exists after the
     * anchor (i.e., the boundary between the undeclared gap and the scheduler's catch-up
     * checkpoints). If no such checkpoint exists yet, falls back to the start of the current
     * 5-minute window.
     *
     * <p>Since there are no checkpoints after the gap at declaration time, an ID-based reference is
     * not applicable for the end boundary — a timestamp or auto-derivation is always used.
     */
    OffsetDateTime gapEnd,

    // -----------------------------------------------------------------------------------------
    // Required in all modes
    // -----------------------------------------------------------------------------------------

    /**
     * Human-readable justification for the gap. Should clearly describe the reason for the downtime
     * (e.g., "Datacenter migration", "Planned maintenance window", "Unplanned outage — hardware
     * failure"). This text is stored in the {@code notes} field of the gap checkpoint and in the
     * audit entry.
     */
    @NotBlank String justification) {}
