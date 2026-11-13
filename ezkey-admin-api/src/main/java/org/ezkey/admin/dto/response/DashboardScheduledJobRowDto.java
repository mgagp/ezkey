/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Record: DashboardScheduledJobRowDto
 * Description: Last-run row for a scheduled job on the dashboard overview.
 */

package org.ezkey.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/**
 * Last-run metadata for a logical scheduled job, projected for the dashboard (Global Admin only).
 *
 * @param jobKey stable job identifier
 * @param lastExecutionAt timestamp of the most recent execution (null when never run)
 * @param lastStatus outcome of the most recent execution
 * @param lastRunScope human-readable scope of the last run
 * @param lastErrorSummary safe operator-facing error summary when lastStatus is FAILED
 * @since 2026
 */
@Schema(description = "Scheduled job last-run row for dashboard batch health widgets")
public record DashboardScheduledJobRowDto(
    @Schema(
            description = "Stable job identifier",
            example = "AUDIT_CHAIN_CHECKPOINT",
            allowableValues = {
              "AUDIT_CHAIN_CHECKPOINT",
              "NIGHTLY_INTEGRITY_VALIDATION",
              "REENCRYPTION"
            })
        String jobKey,
    @Schema(description = "Timestamp of the most recent execution (null when never run)")
        OffsetDateTime lastExecutionAt,
    @Schema(
            description = "Outcome of the most recent execution",
            allowableValues = {"SUCCESS", "FAILED", "NEVER_RUN"})
        String lastStatus,
    @Schema(description = "Human-readable scope of the last run (e.g. validated window)")
        String lastRunScope,
    @Schema(description = "Safe operator-facing error summary when lastStatus is FAILED")
        String lastErrorSummary) {}
