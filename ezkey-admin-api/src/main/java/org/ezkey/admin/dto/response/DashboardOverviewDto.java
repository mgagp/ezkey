/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Record: DashboardOverviewDto
 * Description: Aggregated dashboard overview response (stats + recent activity + optional alerts).
 */

package org.ezkey.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Aggregated dashboard overview response.
 *
 * <p>Consolidates integration, enrollment, and auth-24h stats, recent activity, and optionally
 * instance-level alerts (for Global Admin only). Serves both Tenant Admin (tenant-scoped stats) and
 * Global Admin (instance-wide stats plus alerts).
 *
 * <p><b>Alerts:</b> When the principal is a Global Admin, {@code openAlertCount} and {@code alerts}
 * reflect operator-facing open alerts. For Tenant Admin, alert and batch-health fields are null.
 *
 * <p><b>Batch health:</b> Global Admin only — {@code integrityJobs}, {@code operationalJobs}, and
 * {@code integrityConfigSummary} expose scheduled job last-run metadata from the registry.
 *
 * <p><b>Runtime profile:</b> Global Admin only — {@code runtimeProfile} is the product label {@code
 * base} or {@code integrity} (not a job matrix). Used by Admin UI Integrity honesty chrome.
 *
 * @param integrations integration counts for the current scope
 * @param enrollments operational enrollment counts
 * @param auth24h auth attempt stats for the last 24 hours
 * @param recentActivity recent audit log entries
 * @param openAlertCount count of OPEN operator-facing alerts (Global Admin only)
 * @param alerts recent OPEN alerts (Global Admin only)
 * @param integrityJobs system/integrity scheduled jobs (Global Admin only)
 * @param operationalJobs other operational scheduled jobs (Global Admin only)
 * @param integrityConfigSummary active integrity configuration summary (Global Admin only)
 * @param runtimeProfile product runtime profile {@code base} or {@code integrity} (Global Admin
 *     only; null for Tenant Admin)
 */
@Schema(description = "Aggregated dashboard overview (stats, recent activity, optional alerts)")
public record DashboardOverviewDto(
    @Schema(description = "Integration counts for the current scope")
        DashboardIntegrationStatsDto integrations,
    @Schema(description = "Operational enrollment counts") DashboardEnrollmentStatsDto enrollments,
    @Schema(description = "Auth attempt stats for the last 24 hours")
        DashboardAuth24hStatsDto auth24h,
    @Schema(description = "Recent audit log entries")
        List<DashboardRecentActivityItemDto> recentActivity,
    @Schema(description = "Count of OPEN operator-facing alerts. Populated only for Global Admin.")
        Long openAlertCount,
    @Schema(
            description =
                "Recent OPEN alerts (newest first, capped). Populated only for Global Admin.")
        List<DashboardAlertItemDto> alerts,
    @Schema(
            description =
                "System/integrity scheduled jobs (checkpoint, nightly validation). Global Admin"
                    + " only.")
        List<DashboardScheduledJobRowDto> integrityJobs,
    @Schema(
            description =
                "Other operational scheduled jobs (e.g. re-encryption). Global Admin only.")
        List<DashboardScheduledJobRowDto> operationalJobs,
    @Schema(description = "Active integrity configuration summary (non-secret). Global Admin only.")
        DashboardIntegrityConfigSummaryDto integrityConfigSummary,
    @Schema(
            description =
                "Product runtime profile: base (opt-in; integrity monitoring off) or integrity"
                    + " (default). Non-secret. Global Admin only.",
            allowableValues = {"base", "integrity"},
            example = "integrity")
        String runtimeProfile) {}
