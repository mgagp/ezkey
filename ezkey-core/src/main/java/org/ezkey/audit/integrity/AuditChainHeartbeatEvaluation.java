/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Record: AuditChainHeartbeatEvaluation
 * Description: Snapshot result of evaluating checkpoint heartbeat staleness.
 */

package org.ezkey.audit.integrity;

import java.time.OffsetDateTime;

/**
 * Immutable snapshot describing heartbeat supervision relative to the latest checkpoint.
 *
 * @param phase derived supervision phase
 * @param anchorCheckpointId checkpoint id used as heartbeat anchor (latest at evaluation time), or
 *     {@code null} when no checkpoints exist
 * @param latestWindowEnd {@code window_end} of latest checkpoint; {@code null} when none
 * @param stalePhaseStartsAt wall-clock boundary where stale/unbounded-risk supervision begins
 *     relative to {@code latestWindowEnd}, or {@code null} when not applicable
 * @param failClosedNotBefore earliest instant fail-closed may activate (grace minus safety buffer),
 *     or {@code null} when not applicable
 * @param peripheralFailClosed when true, peripheral APIs must reject creation of new MFA work
 * @author Ezkey contributors
 * @since 2026
 */
public record AuditChainHeartbeatEvaluation(
    AuditChainHeartbeatPhase phase,
    Long anchorCheckpointId,
    OffsetDateTime latestWindowEnd,
    OffsetDateTime stalePhaseStartsAt,
    OffsetDateTime failClosedNotBefore,
    boolean peripheralFailClosed) {}
