/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Record: DashboardAuth24hStatsDto
 * Description: Auth attempt health stats in the last 24 hours for dashboard overview.
 */

package org.ezkey.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Authentication attempt statistics in the last rolling 24 hours for the dashboard overview.
 *
 * <p>Counts include every attempt created in the window. Percentage fields are computed over
 * <strong>terminal</strong> outcomes only ({@code ACCEPTED}, {@code REJECTED}, {@code INVALID},
 * {@code EXPIRED}) and are {@code null} when there are no terminal outcomes in the window.
 *
 * @param total all attempts created in the rolling 24h window
 * @param pending attempts still pending on the device
 * @param readCount attempts claimed by the device but not yet completed
 * @param accepted user approved
 * @param rejected user explicitly denied
 * @param invalid cryptographic validation failed
 * @param expired timed out or superseded
 * @param terminalTotal accepted + rejected + invalid + expired (denominator for rate fields)
 * @param successRatePct accepted as % of terminal outcomes; null if terminalTotal is 0
 * @param invalidRatePct invalid as % of terminal outcomes; null if terminalTotal is 0
 * @param expiredRatePct expired as % of terminal outcomes; null if terminalTotal is 0
 * @param rejectedRatePct rejected as % of terminal outcomes; null if terminalTotal is 0
 */
@Schema(description = "Auth attempt counts and terminal-outcome rates in the last 24h (dashboard)")
public record DashboardAuth24hStatsDto(
    @Schema(description = "All attempts created in the rolling 24h window") long total,
    @Schema(description = "Attempts still pending on the device") long pending,
    @Schema(description = "Attempts claimed by the device (read) but not yet completed")
        long readCount,
    @Schema(description = "User approved") long accepted,
    @Schema(description = "User explicitly denied") long rejected,
    @Schema(description = "Cryptographic validation failed") long invalid,
    @Schema(description = "Timed out or superseded") long expired,
    @Schema(
            description =
                "Terminal outcomes: accepted + rejected + invalid + expired (denominator for rate"
                    + " fields)")
        long terminalTotal,
    @Schema(
            description = "Accepted as % of terminal outcomes; null if terminalTotal is 0",
            nullable = true)
        Integer successRatePct,
    @Schema(
            description = "Invalid as % of terminal outcomes; null if terminalTotal is 0",
            nullable = true)
        Integer invalidRatePct,
    @Schema(
            description = "Expired as % of terminal outcomes; null if terminalTotal is 0",
            nullable = true)
        Integer expiredRatePct,
    @Schema(
            description = "Rejected as % of terminal outcomes; null if terminalTotal is 0",
            nullable = true)
        Integer rejectedRatePct) {}
