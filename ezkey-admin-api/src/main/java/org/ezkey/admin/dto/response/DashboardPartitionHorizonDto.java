/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Record: DashboardPartitionHorizonDto
 * Description: Structured month horizon for the partition-creation dashboard row.
 */

package org.ezkey.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Months covered by the last partition-creation run, split out of the stored scope string so the
 * dashboard does not show that string or the exception class name.
 *
 * @param from first month in the horizon ({@code yyyy-MM})
 * @param to last month in the horizon ({@code yyyy-MM})
 * @param monthsAhead future months beyond the current one
 * @param failedTargets partitions that were not created; null when the run succeeded
 * @param targets partitions the run attempted; null when the run succeeded
 * @since 2026
 */
@Schema(description = "Structured horizon for the PARTITION_CREATION dashboard row")
public record DashboardPartitionHorizonDto(
    @Schema(description = "First month in the horizon (yyyy-MM)", example = "2026-11") String from,
    @Schema(description = "Last month in the horizon (yyyy-MM)", example = "2027-02") String to,
    @Schema(description = "Future months beyond the current one", example = "3") int monthsAhead,
    @Schema(description = "Partitions not created; null when the run succeeded", example = "8")
        Integer failedTargets,
    @Schema(
            description = "Partitions the run attempted; null when the run succeeded",
            example = "8")
        Integer targets) {}
