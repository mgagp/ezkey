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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.ezkey.admin.dto.response.DashboardAlertItemDto;
import org.ezkey.admin.dto.response.DashboardAuth24hStatsDto;
import org.ezkey.admin.dto.response.DashboardEnrollmentStatsDto;
import org.ezkey.admin.dto.response.DashboardGapPendingDetailsDto;
import org.ezkey.admin.dto.response.DashboardIntegrationStatsDto;
import org.ezkey.admin.dto.response.DashboardOverviewDto;
import org.ezkey.admin.dto.response.DashboardRecentActivityItemDto;
import org.ezkey.admin.security.AdminPrincipal;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.integrity.AuditChainCheckpointRepository;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.authattempt.domain.AuthAttemptDashboard24hStats;
import org.ezkey.authattempt.service.AuthAttemptService;
import org.ezkey.enrollment.domain.EnrollmentStatus;
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
 * instance-wide stats and instance-level alerts (e.g. {@code AUDIT_CHAIN_GAP_PENDING}).
 *
 * <p>Uses parallel execution (CompletableFuture) to fetch integration, enrollment, auth-24h, and
 * recent-activity data in one overview call.
 */
@Service
public class DashboardService {

  private static final Logger logger = LoggerFactory.getLogger(DashboardService.class);

  private static final int RECENT_ACTIVITY_SIZE = 5;
  private static final int ALERTS_SIZE = 10;

  /** Used only for parsing GAP_PENDING eventDetails JSON; not injected to avoid bean dependency. */
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private static final String CHECKPOINT_TYPE_GAP_DECLARATION = "GAP_DECLARATION";

  private final IntegrationService integrationService;
  private final EnrollmentService enrollmentService;
  private final AuthAttemptService authAttemptService;
  private final AuditLogService auditLogService;
  private final AuditChainCheckpointRepository checkpointRepository;

  public DashboardService(
      IntegrationService integrationService,
      EnrollmentService enrollmentService,
      AuthAttemptService authAttemptService,
      AuditLogService auditLogService,
      AuditChainCheckpointRepository checkpointRepository) {
    this.integrationService = integrationService;
    this.enrollmentService = enrollmentService;
    this.authAttemptService = authAttemptService;
    this.auditLogService = auditLogService;
    this.checkpointRepository = checkpointRepository;
  }

  /**
   * Builds the dashboard overview for the given principal.
   *
   * <p>Tenant Admin: tenant-scoped integrations, enrollments, auth 24h, recent activity; alerts
   * null/empty.
   *
   * <p>Global Admin: instance-wide stats, recent activity across tenants, and alerts (e.g. audit
   * chain gap pending).
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
        CompletableFuture.supplyAsync(() -> buildEnrollmentStats(tenantId, pageOne));

    CompletableFuture<DashboardAuth24hStatsDto> auth24hFuture =
        CompletableFuture.supplyAsync(() -> buildAuth24hStats(tenantId, since24h));

    CompletableFuture<List<DashboardRecentActivityItemDto>> recentActivityFuture =
        CompletableFuture.supplyAsync(
            () ->
                buildRecentActivity(
                    tenantId, PageRequest.of(0, RECENT_ACTIVITY_SIZE, createdAtDesc)));

    CompletableFuture<List<DashboardAlertItemDto>> alertsFuture =
        principal.isGlobalAdmin()
            ? CompletableFuture.supplyAsync(
                () -> buildAlerts(PageRequest.of(0, ALERTS_SIZE, createdAtDesc)))
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
            .findByFilters(null, null, false, null, null, tenantId, pageOne)
            .getTotalElements();
    long active =
        integrationService
            .findByFilters(
                null, IntegrationLifecycleStatus.ACTIVE, false, null, null, tenantId, pageOne)
            .getTotalElements();
    return new DashboardIntegrationStatsDto(total, active, total - active);
  }

  private DashboardEnrollmentStatsDto buildEnrollmentStats(Integer tenantId, PageRequest pageOne) {
    long total =
        enrollmentService
            .findByFilters(null, null, null, true, null, null, tenantId, pageOne)
            .getTotalElements();
    long verified =
        enrollmentService
            .findByFilters(
                EnrollmentStatus.VERIFIED, null, null, true, null, null, tenantId, pageOne)
            .getTotalElements();
    long bound =
        enrollmentService
            .findByFilters(EnrollmentStatus.BOUND, null, null, true, null, null, tenantId, pageOne)
            .getTotalElements();
    long created =
        enrollmentService
            .findByFilters(
                EnrollmentStatus.CREATED, null, null, true, null, null, tenantId, pageOne)
            .getTotalElements();
    return new DashboardEnrollmentStatsDto(total, verified, bound, created);
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

  private List<DashboardAlertItemDto> buildAlerts(PageRequest pageRequest) {
    var page =
        auditLogService.findByFilters(
            EventType.AUDIT_CHAIN_GAP_PENDING,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            pageRequest);
    List<DashboardAlertItemDto> list = new ArrayList<>();
    for (AuditLog log : page.getContent()) {
      DashboardGapPendingDetailsDto details = parseGapPendingDetails(log.getEventDetails());
      if (details != null && isGapDeclared(details)) {
        continue;
      }
      DashboardAlertItemDto item = new DashboardAlertItemDto();
      item.setAuditLogId(log.getAuditLogId());
      item.setEventType(log.getEventType() != null ? log.getEventType().name() : null);
      item.setEventStatus(log.getEventStatus() != null ? log.getEventStatus().name() : null);
      item.setCreatedAt(log.getCreatedAt());
      item.setEventDetails(details);
      list.add(item);
    }
    return list;
  }

  /**
   * Returns true if a GAP_DECLARATION checkpoint exists for the period described in the alert
   * details, meaning the gap has been formally declared and the alert should not be shown.
   */
  private boolean isGapDeclared(DashboardGapPendingDetailsDto details) {
    if (details.getGapStart() == null
        || details.getGapStart().isBlank()
        || details.getEstimatedGapEnd() == null
        || details.getEstimatedGapEnd().isBlank()) {
      return false;
    }
    try {
      OffsetDateTime windowStart = OffsetDateTime.parse(details.getGapStart().trim());
      OffsetDateTime windowEnd = OffsetDateTime.parse(details.getEstimatedGapEnd().trim());
      return checkpointRepository
          .findByWindowStartAndWindowEndAndCheckpointType(
              windowStart, windowEnd, CHECKPOINT_TYPE_GAP_DECLARATION)
          .isPresent();
    } catch (DateTimeParseException e) {
      logger.debug("Could not parse gap window for declared check: {}", e.getMessage());
      return false;
    }
  }

  private DashboardGapPendingDetailsDto parseGapPendingDetails(String eventDetailsJson) {
    if (eventDetailsJson == null || eventDetailsJson.isBlank()) {
      return null;
    }
    try {
      JsonNode root = OBJECT_MAPPER.readTree(eventDetailsJson);
      DashboardGapPendingDetailsDto dto = new DashboardGapPendingDetailsDto();
      if (root.has("gapStart")) {
        dto.setGapStart(root.get("gapStart").asText());
      }
      if (root.has("estimatedGapEnd")) {
        dto.setEstimatedGapEnd(root.get("estimatedGapEnd").asText());
      }
      if (root.has("estimatedGapMinutes")) {
        dto.setEstimatedGapMinutes(root.get("estimatedGapMinutes").asLong());
      }
      if (root.has("anchorCheckpointId")) {
        dto.setAnchorCheckpointId(root.get("anchorCheckpointId").asLong());
      }
      if (root.has("message")) {
        dto.setMessage(root.get("message").asText());
      }
      return dto;
    } catch (Exception e) {
      logger.warn("Failed to parse AUDIT_CHAIN_GAP_PENDING eventDetails: {}", e.getMessage());
      return null;
    }
  }
}
