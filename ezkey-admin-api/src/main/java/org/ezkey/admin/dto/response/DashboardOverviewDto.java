/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: DashboardOverviewDto
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
 */
@Schema(description = "Aggregated dashboard overview (stats, recent activity, optional alerts)")
public class DashboardOverviewDto {

  private DashboardIntegrationStatsDto integrations;
  private DashboardEnrollmentStatsDto enrollments;
  private DashboardAuth24hStatsDto auth24h;
  private List<DashboardRecentActivityItemDto> recentActivity;
  private Long openAlertCount;
  private List<DashboardAlertItemDto> alerts;
  private List<DashboardScheduledJobRowDto> integrityJobs;
  private List<DashboardScheduledJobRowDto> operationalJobs;
  private DashboardIntegrityConfigSummaryDto integrityConfigSummary;

  public DashboardOverviewDto() {}

  public DashboardIntegrationStatsDto getIntegrations() {
    return integrations;
  }

  public void setIntegrations(DashboardIntegrationStatsDto integrations) {
    this.integrations = integrations;
  }

  public DashboardEnrollmentStatsDto getEnrollments() {
    return enrollments;
  }

  public void setEnrollments(DashboardEnrollmentStatsDto enrollments) {
    this.enrollments = enrollments;
  }

  public DashboardAuth24hStatsDto getAuth24h() {
    return auth24h;
  }

  public void setAuth24h(DashboardAuth24hStatsDto auth24h) {
    this.auth24h = auth24h;
  }

  public List<DashboardRecentActivityItemDto> getRecentActivity() {
    return recentActivity;
  }

  public void setRecentActivity(List<DashboardRecentActivityItemDto> recentActivity) {
    this.recentActivity = recentActivity;
  }

  @Schema(description = "Count of OPEN operator-facing alerts. Populated only for Global Admin.")
  public Long getOpenAlertCount() {
    return openAlertCount;
  }

  public void setOpenAlertCount(Long openAlertCount) {
    this.openAlertCount = openAlertCount;
  }

  @Schema(
      description = "Recent OPEN alerts (newest first, capped). Populated only for Global Admin.")
  public List<DashboardAlertItemDto> getAlerts() {
    return alerts;
  }

  public void setAlerts(List<DashboardAlertItemDto> alerts) {
    this.alerts = alerts;
  }

  @Schema(
      description =
          "System/integrity scheduled jobs (checkpoint, nightly validation). Global Admin only.")
  public List<DashboardScheduledJobRowDto> getIntegrityJobs() {
    return integrityJobs;
  }

  public void setIntegrityJobs(List<DashboardScheduledJobRowDto> integrityJobs) {
    this.integrityJobs = integrityJobs;
  }

  @Schema(description = "Other operational scheduled jobs (e.g. re-encryption). Global Admin only.")
  public List<DashboardScheduledJobRowDto> getOperationalJobs() {
    return operationalJobs;
  }

  public void setOperationalJobs(List<DashboardScheduledJobRowDto> operationalJobs) {
    this.operationalJobs = operationalJobs;
  }

  @Schema(description = "Active integrity configuration summary (non-secret). Global Admin only.")
  public DashboardIntegrityConfigSummaryDto getIntegrityConfigSummary() {
    return integrityConfigSummary;
  }

  public void setIntegrityConfigSummary(DashboardIntegrityConfigSummaryDto integrityConfigSummary) {
    this.integrityConfigSummary = integrityConfigSummary;
  }
}
