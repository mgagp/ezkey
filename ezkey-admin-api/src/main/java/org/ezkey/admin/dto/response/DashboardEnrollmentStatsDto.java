/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Record: DashboardEnrollmentStatsDto
 * Description: Operational enrollment counts (by status and active flag) for dashboard overview.
 */

package org.ezkey.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Operational enrollment counts for the dashboard overview.
 *
 * <p>Each field represents a distinct operational state. Counts are not constrained to sum to a
 * single total; they reflect independent operational questions across all enrollment states.
 *
 * <ul>
 *   <li>{@code verified} — devices ready for MFA (VERIFIED + active=true)
 *   <li>{@code inProgress} — onboarding awaiting completion (CREATED or BOUND, any active)
 *   <li>{@code suspended} — admin-disabled devices (VERIFIED + active=false)
 *   <li>{@code expired} — timed out before verification (EXPIRED, any active)
 *   <li>{@code invalid} — failed validation (INVALID, any active)
 *   <li>{@code revoked} — administrator revocations (REVOKED, any active)
 * </ul>
 *
 * @param verified devices ready for MFA: VERIFIED + active=true
 * @param inProgress onboarding in progress: CREATED or BOUND (any active state)
 * @param suspended admin-disabled devices: VERIFIED + active=false
 * @param expired timed out before verification: EXPIRED (any active state)
 * @param invalid failed validation: INVALID (any active state)
 * @param revoked administrator revocations: REVOKED (any active state)
 * @since 2025
 */
@Schema(
    description =
        "Operational enrollment counts (by status and active flag) for dashboard overview")
public record DashboardEnrollmentStatsDto(
    @Schema(description = "Devices ready for MFA: VERIFIED + active=true") long verified,
    @Schema(description = "Onboarding in progress: CREATED or BOUND (any active state)")
        long inProgress,
    @Schema(description = "Admin-disabled devices: VERIFIED + active=false") long suspended,
    @Schema(description = "Timed out before verification: EXPIRED (any active state)") long expired,
    @Schema(description = "Failed validation: INVALID (any active state)") long invalid,
    @Schema(description = "Administrator revocations: REVOKED (any active state)") long revoked) {}
