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
 * <p><b>Alerts:</b> When the principal is a Global Admin, {@code alerts} is populated with recent
 * {@code AUDIT_CHAIN_GAP_PENDING} entries. For Tenant Admin, {@code alerts} is null or empty.
 */
@Schema(description = "Aggregated dashboard overview (stats, recent activity, optional alerts)")
public class DashboardOverviewDto {

  private DashboardIntegrationStatsDto integrations;
  private DashboardEnrollmentStatsDto enrollments;
  private DashboardAuth24hStatsDto auth24h;
  private List<DashboardRecentActivityItemDto> recentActivity;
  private List<DashboardAlertItemDto> alerts;

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

  @Schema(
      description =
          "Instance-level alerts (e.g. audit chain gap pending). Populated only for Global Admin.")
  public List<DashboardAlertItemDto> getAlerts() {
    return alerts;
  }

  public void setAlerts(List<DashboardAlertItemDto> alerts) {
    this.alerts = alerts;
  }
}
