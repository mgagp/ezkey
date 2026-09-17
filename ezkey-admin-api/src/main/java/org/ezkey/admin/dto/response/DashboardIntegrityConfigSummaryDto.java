/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Record: DashboardIntegrityConfigSummaryDto
 * Description: Active integrity configuration summary for dashboard widgets.
 */

package org.ezkey.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Active integrity-related configuration surfaced on the dashboard (non-secret values only).
 *
 * @param chainLookbackMinutes rolling checkpoint lookback window in minutes
 * @param nightlyWindowHours nightly retroactive validation window in hours
 * @param chainCheckpointsEnabled whether rolling audit chain checkpoints are enabled
 * @param nightlyValidationEnabled whether nightly retroactive integrity validation is enabled
 * @since 2026
 */
@Schema(description = "Active integrity configuration summary for dashboard widgets")
public record DashboardIntegrityConfigSummaryDto(
    @Schema(description = "Rolling checkpoint lookback window in minutes") int chainLookbackMinutes,
    @Schema(description = "Nightly retroactive validation window in hours") int nightlyWindowHours,
    @Schema(description = "Whether rolling audit chain checkpoints are enabled")
        boolean chainCheckpointsEnabled,
    @Schema(description = "Whether nightly retroactive integrity validation is enabled")
        boolean nightlyValidationEnabled) {}
