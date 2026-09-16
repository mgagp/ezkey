/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: DashboardService
 * Description: Builds aggregated dashboard overview (stats, recent activity, optional alerts).
 */

package org.ezkey.admin.service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.ezkey.admin.dto.response.DashboardAlertItemDto;
import org.ezkey.admin.dto.response.DashboardAuth24hStatsDto;
import org.ezkey.admin.dto.response.DashboardEnrollmentStatsDto;
import org.ezkey.admin.dto.response.DashboardIntegrationStatsDto;
import org.ezkey.admin.dto.response.DashboardIntegrityConfigSummaryDto;
import org.ezkey.admin.dto.response.DashboardOverviewDto;
import org.ezkey.admin.dto.response.DashboardRecentActivityItemDto;
import org.ezkey.admin.dto.response.DashboardScheduledJobRowDto;
import org.ezkey.admin.security.AdminPrincipal;
import org.ezkey.alert.domain.entity.Alert;
import org.ezkey.alert.service.AlertService;
import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.integrity.AuditChainProperties;
import org.ezkey.audit.integrity.NightlyIntegrityProperties;
import org.ezkey.audit.integrity.ScheduledJobKey;
import org.ezkey.audit.integrity.ScheduledJobLastRun;
import org.ezkey.audit.integrity.ScheduledJobLastRunRepository;
import org.ezkey.audit.integrity.ScheduledJobLastRunStatus;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.authattempt.domain.AuthAttemptDashboard24hStats;
import org.ezkey.authattempt.service.AuthAttemptService;
import org.ezkey.enrollment.domain.EnrollmentDashboardStats;
import org.ezkey.enrollment.service.EnrollmentService;
import org.ezkey.integration.domain.IntegrationLifecycleStatus;
import org.ezkey.integration.service.IntegrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the aggregated dashboard overview for the Admin UI.
 *
 * <p>Role-aware: Tenant Admin receives tenant-scoped stats and no alerts. Global Admin receives
 * instance-wide stats, open-alert count, batch job health, and the most recent open alerts.
 *
 * <p>Uses parallel execution (CompletableFuture) to fetch integration, enrollment, auth-24h, and
 * recent-activity data in one overview call.
 */
@Service
public class DashboardService {

  private static final Logger logger = LoggerFactory.getLogger(DashboardService.class);

  private static final int RECENT_ACTIVITY_SIZE = 5;
  private static final int ALERTS_SIZE = 10;

  private static final List<ScheduledJobKey> INTEGRITY_JOB_KEYS =
      List.of(ScheduledJobKey.AUDIT_CHAIN_CHECKPOINT, ScheduledJobKey.NIGHTLY_INTEGRITY_VALIDATION);

  private static final List<ScheduledJobKey> OPERATIONAL_JOB_KEYS =
      List.of(ScheduledJobKey.REENCRYPTION);

  private final IntegrationService integrationService;
  private final EnrollmentService enrollmentService;
  private final AuthAttemptService authAttemptService;
  private final AuditLogService auditLogService;
  private final AlertService alertService;
  private final ScheduledJobLastRunRepository scheduledJobLastRunRepository;
  private final AuditChainProperties auditChainProperties;
  private final NightlyIntegrityProperties nightlyIntegrityProperties;

  /**
   * Constructs the dashboard service.
   *
   * @param integrationService integration aggregate stats
   * @param enrollmentService enrollment aggregate stats
   * @param authAttemptService 24h authentication aggregate stats
   * @param auditLogService recent-activity feed
   * @param alertService open operator-facing alerts (Global Admin)
   * @param scheduledJobLastRunRepository batch job last-run registry
   * @param auditChainProperties rolling checkpoint configuration
   * @param nightlyIntegrityProperties nightly validation configuration
   */
  public DashboardService(
      IntegrationService integrationService,
      EnrollmentService enrollmentService,
      AuthAttemptService authAttemptService,
      AuditLogService auditLogService,
      AlertService alertService,
      ScheduledJobLastRunRepository scheduledJobLastRunRepository,
      AuditChainProperties auditChainProperties,
      NightlyIntegrityProperties nightlyIntegrityProperties) {
    this.integrationService = integrationService;
    this.enrollmentService = enrollmentService;
    this.authAttemptService = authAttemptService;
    this.auditLogService = auditLogService;
    this.alertService = alertService;
    this.scheduledJobLastRunRepository = scheduledJobLastRunRepository;
    this.auditChainProperties = auditChainProperties;
    this.nightlyIntegrityProperties = nightlyIntegrityProperties;
  }

  /**
   * Builds the dashboard overview for the given principal.
   *
   * @param principal the authenticated admin principal
   * @return the dashboard overview DTO
   */
  @Transactional(readOnly = true)
  public DashboardOverviewDto buildOverview(AdminPrincipal principal) {
    Integer tenantId = principal.tenantId();
    OffsetDateTime since24h = OffsetDateTime.now().minusHours(24);
    PageRequest pageOne = PageRequest.of(0, 1);
    Sort createdAtDesc = Sort.by(Sort.Direction.DESC, "createdAt");

    CompletableFuture<DashboardIntegrationStatsDto> integrationsFuture =
        CompletableFuture.supplyAsync(() -> buildIntegrationStats(tenantId, pageOne));

    CompletableFuture<DashboardEnrollmentStatsDto> enrollmentsFuture =
        CompletableFuture.supplyAsync(() -> buildEnrollmentStats(tenantId));

    CompletableFuture<DashboardAuth24hStatsDto> auth24hFuture =
        CompletableFuture.supplyAsync(() -> buildAuth24hStats(tenantId, since24h));

    CompletableFuture<List<DashboardRecentActivityItemDto>> recentActivityFuture =
        CompletableFuture.supplyAsync(
            () ->
                buildRecentActivity(
                    tenantId, PageRequest.of(0, RECENT_ACTIVITY_SIZE, createdAtDesc)));

    CompletableFuture<Long> openAlertCountFuture =
        principal.isGlobalAdmin()
            ? CompletableFuture.supplyAsync(alertService::countOpen)
            : CompletableFuture.completedFuture(null);

    CompletableFuture<List<DashboardAlertItemDto>> alertsFuture =
        principal.isGlobalAdmin()
            ? CompletableFuture.supplyAsync(this::buildAlerts)
            : CompletableFuture.completedFuture(null);

    CompletableFuture<List<DashboardScheduledJobRowDto>> integrityJobsFuture =
        principal.isGlobalAdmin()
            ? CompletableFuture.supplyAsync(() -> buildJobRows(INTEGRITY_JOB_KEYS))
            : CompletableFuture.completedFuture(null);

    CompletableFuture<List<DashboardScheduledJobRowDto>> operationalJobsFuture =
        principal.isGlobalAdmin()
            ? CompletableFuture.supplyAsync(() -> buildJobRows(OPERATIONAL_JOB_KEYS))
            : CompletableFuture.completedFuture(null);

    CompletableFuture<DashboardIntegrityConfigSummaryDto> integrityConfigFuture =
        principal.isGlobalAdmin()
            ? CompletableFuture.completedFuture(buildIntegrityConfigSummary())
            : CompletableFuture.completedFuture(null);

    try {
      return new DashboardOverviewDto(
          integrationsFuture.join(),
          enrollmentsFuture.join(),
          auth24hFuture.join(),
          recentActivityFuture.join(),
          openAlertCountFuture.join(),
          alertsFuture.join(),
          integrityJobsFuture.join(),
          operationalJobsFuture.join(),
          integrityConfigFuture.join());
    } catch (CompletionException | CancellationException e) {
      logger.error("Dashboard overview build failed", e);
      throw new RuntimeException("Failed to build dashboard overview", e);
    }
  }

  private DashboardIntegrityConfigSummaryDto buildIntegrityConfigSummary() {
    return new DashboardIntegrityConfigSummaryDto(
        auditChainProperties.getLookbackMinutes(),
        nightlyIntegrityProperties.getWindowHours(),
        auditChainProperties.isEnabled(),
        nightlyIntegrityProperties.isEnabled());
  }

  private List<DashboardScheduledJobRowDto> buildJobRows(List<ScheduledJobKey> keys) {
    List<DashboardScheduledJobRowDto> rows = new ArrayList<>(keys.size());
    for (ScheduledJobKey key : keys) {
      ScheduledJobLastRun row =
          scheduledJobLastRunRepository
              .findById(key)
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "Missing scheduled job registry row for key: " + key.name()));
      rows.add(toJobRowDto(row));
    }
    return rows;
  }

  private static DashboardScheduledJobRowDto toJobRowDto(ScheduledJobLastRun row) {
    ScheduledJobLastRunStatus status = row.getLastStatus();
    return new DashboardScheduledJobRowDto(
        row.getJobKey().name(),
        row.getLastExecutionAt(),
        status != null ? status.name() : ScheduledJobLastRunStatus.NEVER_RUN.name(),
        row.getLastRunScope(),
        row.getLastErrorSummary());
  }

  private DashboardIntegrationStatsDto buildIntegrationStats(
      Integer tenantId, PageRequest pageOne) {
    long total =
        integrationService
            .findByFilters(null, null, true, null, null, tenantId, pageOne)
            .getTotalElements();
    long active =
        integrationService
            .findByFilters(
                null, IntegrationLifecycleStatus.ACTIVE, false, null, null, tenantId, pageOne)
            .getTotalElements();
    long retired =
        integrationService
            .findByFilters(
                null, IntegrationLifecycleStatus.RETIRED, false, null, null, tenantId, pageOne)
            .getTotalElements();
    return new DashboardIntegrationStatsDto(total, active, retired);
  }

  private DashboardEnrollmentStatsDto buildEnrollmentStats(Integer tenantId) {
    EnrollmentDashboardStats s = enrollmentService.aggregateDashboardEnrollmentStats(tenantId);
    return new DashboardEnrollmentStatsDto(
        s.verified(), s.inProgress(), s.suspended(), s.expired(), s.invalid(), s.revoked());
  }

  private DashboardAuth24hStatsDto buildAuth24hStats(Integer tenantId, OffsetDateTime since24h) {
    AuthAttemptDashboard24hStats s = authAttemptService.aggregateDashboard24h(since24h, tenantId);
    return new DashboardAuth24hStatsDto(
        s.total(),
        s.pending(),
        s.readCount(),
        s.accepted(),
        s.rejected(),
        s.invalid(),
        s.expired(),
        s.terminalTotal(),
        s.successRatePct(),
        s.invalidRatePct(),
        s.expiredRatePct(),
        s.rejectedRatePct());
  }

  private List<DashboardRecentActivityItemDto> buildRecentActivity(
      Integer requesterTenantId, PageRequest pageRequest) {
    var page =
        auditLogService.findByFilters(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            requesterTenantId,
            null,
            null,
            null,
            pageRequest);
    List<DashboardRecentActivityItemDto> list = new ArrayList<>();
    for (AuditLog log : page.getContent()) {
      list.add(
          new DashboardRecentActivityItemDto(
              log.getAuditLogId(),
              log.getEventType() != null ? log.getEventType().name() : null,
              log.getEventStatus() != null ? log.getEventStatus().name() : null,
              log.getEventAction(),
              log.getApiName() != null ? log.getApiName().name() : null,
              log.getAdminId(),
              log.getCreatedAt()));
    }
    return list;
  }

  private List<DashboardAlertItemDto> buildAlerts() {
    List<Alert> openAlerts = alertService.findRecentOpen(ALERTS_SIZE);
    List<DashboardAlertItemDto> list = new ArrayList<>(openAlerts.size());
    for (Alert a : openAlerts) {
      list.add(
          new DashboardAlertItemDto(
              a.getAlertId(),
              a.getAlertType(),
              a.getSeverity(),
              a.getStatus(),
              a.getCreatedAt(),
              a.getPayload()));
    }
    return list;
  }
}
