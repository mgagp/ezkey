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
import java.util.concurrent.CompletableFuture;
import org.ezkey.admin.dto.response.DashboardAlertItemDto;
import org.ezkey.admin.dto.response.DashboardAuth24hStatsDto;
import org.ezkey.admin.dto.response.DashboardEnrollmentStatsDto;
import org.ezkey.admin.dto.response.DashboardIntegrationStatsDto;
import org.ezkey.admin.dto.response.DashboardOverviewDto;
import org.ezkey.admin.dto.response.DashboardRecentActivityItemDto;
import org.ezkey.admin.security.AdminPrincipal;
import org.ezkey.alert.domain.entity.Alert;
import org.ezkey.alert.service.AlertService;
import org.ezkey.audit.domain.entity.AuditLog;
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
 * instance-wide stats and the most recent open operator-facing alerts.
 *
 * <p>Uses parallel execution (CompletableFuture) to fetch integration, enrollment, auth-24h, and
 * recent-activity data in one overview call.
 */
@Service
public class DashboardService {

  private static final Logger logger = LoggerFactory.getLogger(DashboardService.class);

  private static final int RECENT_ACTIVITY_SIZE = 5;
  private static final int ALERTS_SIZE = 10;

  private final IntegrationService integrationService;
  private final EnrollmentService enrollmentService;
  private final AuthAttemptService authAttemptService;
  private final AuditLogService auditLogService;
  private final AlertService alertService;

  /**
   * Constructs the dashboard service.
   *
   * @param integrationService integration aggregate stats
   * @param enrollmentService enrollment aggregate stats
   * @param authAttemptService 24h authentication aggregate stats
   * @param auditLogService recent-activity feed
   * @param alertService open operator-facing alerts (Global Admin)
   */
  public DashboardService(
      IntegrationService integrationService,
      EnrollmentService enrollmentService,
      AuthAttemptService authAttemptService,
      AuditLogService auditLogService,
      AlertService alertService) {
    this.integrationService = integrationService;
    this.enrollmentService = enrollmentService;
    this.authAttemptService = authAttemptService;
    this.auditLogService = auditLogService;
    this.alertService = alertService;
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

    CompletableFuture<List<DashboardAlertItemDto>> alertsFuture =
        principal.isGlobalAdmin()
            ? CompletableFuture.supplyAsync(this::buildAlerts)
            : CompletableFuture.completedFuture(null);

    DashboardOverviewDto dto = new DashboardOverviewDto();
    try {
      dto.setIntegrations(integrationsFuture.join());
      dto.setEnrollments(enrollmentsFuture.join());
      dto.setAuth24h(auth24hFuture.join());
      dto.setRecentActivity(recentActivityFuture.join());
      dto.setAlerts(alertsFuture.join());
    } catch (Exception e) {
      logger.error("Dashboard overview build failed", e);
      throw new RuntimeException("Failed to build dashboard overview", e);
    }
    return dto;
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
        s.verified(), s.inProgress(), s.suspended(), s.expired(), s.incidents());
  }

  private DashboardAuth24hStatsDto buildAuth24hStats(Integer tenantId, OffsetDateTime since24h) {
    AuthAttemptDashboard24hStats s = authAttemptService.aggregateDashboard24h(since24h, tenantId);
    DashboardAuth24hStatsDto dto = new DashboardAuth24hStatsDto();
    dto.setTotal(s.total());
    dto.setPending(s.pending());
    dto.setReadCount(s.readCount());
    dto.setAccepted(s.accepted());
    dto.setRejected(s.rejected());
    dto.setInvalid(s.invalid());
    dto.setExpired(s.expired());
    dto.setTerminalTotal(s.terminalTotal());
    dto.setSuccessRatePct(s.successRatePct());
    dto.setInvalidRatePct(s.invalidRatePct());
    dto.setExpiredRatePct(s.expiredRatePct());
    dto.setRejectedRatePct(s.rejectedRatePct());
    return dto;
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
      DashboardRecentActivityItemDto item = new DashboardRecentActivityItemDto();
      item.setAuditLogId(log.getAuditLogId());
      item.setEventType(log.getEventType() != null ? log.getEventType().name() : null);
      item.setEventStatus(log.getEventStatus() != null ? log.getEventStatus().name() : null);
      item.setEventAction(log.getEventAction());
      item.setApiName(log.getApiName() != null ? log.getApiName().name() : null);
      item.setAdminId(log.getAdminId());
      item.setCreatedAt(log.getCreatedAt());
      list.add(item);
    }
    return list;
  }

  private List<DashboardAlertItemDto> buildAlerts() {
    List<Alert> openAlerts = alertService.findRecentOpen(ALERTS_SIZE);
    List<DashboardAlertItemDto> list = new ArrayList<>(openAlerts.size());
    for (Alert a : openAlerts) {
      DashboardAlertItemDto item = new DashboardAlertItemDto();
      item.setAlertId(a.getAlertId());
      item.setAlertType(a.getAlertType());
      item.setSeverity(a.getSeverity());
      item.setStatus(a.getStatus());
      item.setCreatedAt(a.getCreatedAt());
      item.setPayload(a.getPayload());
      list.add(item);
    }
    return list;
  }
}
