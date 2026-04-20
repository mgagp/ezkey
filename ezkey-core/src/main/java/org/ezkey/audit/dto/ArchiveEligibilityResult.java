/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: ArchiveEligibilityResult
 * Description: Summary of the current audit archive eligibility window.
 */

package org.ezkey.audit.dto;

import java.time.OffsetDateTime;

/**
 * Summary of the current audit archive eligibility window.
 *
 * <p>This contract gives future archival clients enough information to know whether the system
 * currently exposes a sealed range awaiting external archival confirmation.
 *
 * @param externalArchivalEnabled whether the policy currently requires an external archival step
 * @param confirmationRequired whether sealed checkpoints currently require confirmation to progress
 * @param sealedCheckpointCount number of sealed checkpoints currently awaiting external archival
 * @param oldestSealedWindowStart oldest sealed checkpoint window start, or {@code null} if none
 * @param newestSealedWindowEnd newest sealed checkpoint window end, or {@code null} if none
 * @param checkpointIdFrom first checkpoint identifier of the currently eligible sealed tranche, or
 *     {@code null} if none
 * @param checkpointIdTo last checkpoint identifier of the currently eligible sealed tranche, or
 *     {@code null} if none
 * @since 2026
 */
public record ArchiveEligibilityResult(
    boolean externalArchivalEnabled,
    boolean confirmationRequired,
    int sealedCheckpointCount,
    OffsetDateTime oldestSealedWindowStart,
    OffsetDateTime newestSealedWindowEnd,
    Long checkpointIdFrom,
    Long checkpointIdTo) {}
