/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: DashboardServiceTest
 * Description: Unit tests for dashboard overview assembly.
 */

package org.ezkey.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.ezkey.admin.security.AdminPrincipal;
import org.ezkey.alert.domain.AlertStatus;
import org.ezkey.alert.domain.AlertType;
import org.ezkey.alert.domain.entity.Alert;
import org.ezkey.audit.integrity.AuditChainProperties;
import org.ezkey.audit.integrity.NightlyIntegrityProperties;
import org.ezkey.audit.integrity.ScheduledJobKey;
import org.ezkey.audit.integrity.ScheduledJobLastRun;
import org.ezkey.audit.integrity.ScheduledJobLastRunRepository;
import org.ezkey.audit.integrity.ScheduledJobLastRunStatus;
import org.ezkey.authattempt.domain.AuthAttemptDashboard24hStats;
import org.ezkey.authattempt.service.AuthAttemptService;
import org.ezkey.enrollment.domain.EnrollmentDashboardStats;
import org.ezkey.enrollment.service.EnrollmentService;
import org.ezkey.integration.domain.IntegrationLifecycleStatus;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminType;
import org.ezkey.integration.service.IntegrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

/**
 * Unit tests for {@link DashboardService}.
 *
 * @since 2025
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DashboardService Tests")
class DashboardServiceTest {

  @Mock private IntegrationService integrationService;

  @Mock private EnrollmentService enrollmentService;

  @Mock private AuthAttemptService authAttemptService;

  @Mock private org.ezkey.audit.service.AuditLogService auditLogService;

  @Mock private org.ezkey.alert.service.AlertService alertService;

  @Mock private ScheduledJobLastRunRepository scheduledJobLastRunRepository;

  private AuditChainProperties auditChainProperties;
  private NightlyIntegrityProperties nightlyIntegrityProperties;

  private DashboardService service;

  @BeforeEach
  void setUp() {
    auditChainProperties = new AuditChainProperties();
    auditChainProperties.setLookbackMinutes(60);
    auditChainProperties.setEnabled(true);
    nightlyIntegrityProperties = new NightlyIntegrityProperties();
    nightlyIntegrityProperties.setWindowHours(24);
    nightlyIntegrityProperties.setEnabled(true);

    service =
        new DashboardService(
            integrationService,
            enrollmentService,
            authAttemptService,
            auditLogService,
            alertService,
            scheduledJobLastRunRepository,
            auditChainProperties,
            nightlyIntegrityProperties);
  }

  @Test
  @DisplayName("Should include retired integrations in total and expose retired count separately")
  void buildOverview_shouldExposeRetiredIntegrationCount() {
    AdminPrincipal principal = new AdminPrincipal(7, AdminType.TENANT_ADMIN, 42, null);
    stubCommonTenantStats(42);

    var overview = service.buildOverview(principal);

    assertThat(overview.getIntegrations()).isNotNull();
    assertThat(overview.getIntegrations().getTotal()).isEqualTo(5);
    assertThat(overview.getIntegrations().getActive()).isEqualTo(3);
    assertThat(overview.getIntegrations().getRetired()).isEqualTo(2);
    assertThat(overview.getOpenAlertCount()).isNull();
    assertThat(overview.getIntegrityJobs()).isNull();
    assertThat(overview.getOperationalJobs()).isNull();
    assertThat(overview.getIntegrityConfigSummary()).isNull();
  }

  @Test
  @DisplayName(
      "Global Admin should receive batch health rows, config summary, and open alert count")
  void buildOverview_globalAdmin_shouldExposeBatchHealthAndOpenAlertCount() {
    AdminPrincipal principal = new AdminPrincipal(1, AdminType.GLOBAL_ADMIN, null, null);
    stubCommonTenantStats(null);

    when(alertService.countOpen()).thenReturn(3L);
    when(alertService.findRecentOpen(10)).thenReturn(List.of(sampleAlert()));

    when(scheduledJobLastRunRepository.findById(ScheduledJobKey.AUDIT_CHAIN_CHECKPOINT))
        .thenReturn(Optional.of(jobRow(ScheduledJobKey.AUDIT_CHAIN_CHECKPOINT, "Lookback 60 min")));
    when(scheduledJobLastRunRepository.findById(ScheduledJobKey.NIGHTLY_INTEGRITY_VALIDATION))
        .thenReturn(
            Optional.of(
                jobRow(
                    ScheduledJobKey.NIGHTLY_INTEGRITY_VALIDATION,
                    "Validated 24 h ending 2026-07-03T02:00Z")));
    when(scheduledJobLastRunRepository.findById(ScheduledJobKey.REENCRYPTION))
        .thenReturn(Optional.of(neverRunRow(ScheduledJobKey.REENCRYPTION)));

    var overview = service.buildOverview(principal);

    assertThat(overview.getOpenAlertCount()).isEqualTo(3L);
    assertThat(overview.getAlerts()).hasSize(1);
    assertThat(overview.getIntegrityJobs()).hasSize(2);
    assertThat(overview.getIntegrityJobs().get(0).getJobKey()).isEqualTo("AUDIT_CHAIN_CHECKPOINT");
    assertThat(overview.getIntegrityJobs().get(0).getLastStatus()).isEqualTo("SUCCESS");
    assertThat(overview.getOperationalJobs()).hasSize(1);
    assertThat(overview.getOperationalJobs().get(0).getLastStatus()).isEqualTo("NEVER_RUN");
    assertThat(overview.getIntegrityConfigSummary()).isNotNull();
    assertThat(overview.getIntegrityConfigSummary().getChainLookbackMinutes()).isEqualTo(60);
    assertThat(overview.getIntegrityConfigSummary().getNightlyWindowHours()).isEqualTo(24);
    assertThat(overview.getIntegrityConfigSummary().isChainCheckpointsEnabled()).isTrue();
    assertThat(overview.getIntegrityConfigSummary().isNightlyValidationEnabled()).isTrue();
  }

  private void stubCommonTenantStats(Integer tenantId) {
    when(integrationService.findByFilters(
            eq(null), eq(null), eq(true), eq(null), eq(null), eq(tenantId), any(PageRequest.class)))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 1), 5));
    when(integrationService.findByFilters(
            eq(null),
            eq(IntegrationLifecycleStatus.ACTIVE),
            eq(false),
            eq(null),
            eq(null),
            eq(tenantId),
            any(PageRequest.class)))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 1), 3));
    when(integrationService.findByFilters(
            eq(null),
            eq(IntegrationLifecycleStatus.RETIRED),
            eq(false),
            eq(null),
            eq(null),
            eq(tenantId),
            any(PageRequest.class)))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 1), 2));
    when(enrollmentService.aggregateDashboardEnrollmentStats(tenantId))
        .thenReturn(new EnrollmentDashboardStats(10, 2, 1, 0, 0));
    when(authAttemptService.aggregateDashboard24h(any(), eq(tenantId)))
        .thenReturn(new AuthAttemptDashboard24hStats(4, 0, 0, 3, 1, 0, 0, 4, 75, 0, 0, 25));
    when(auditLogService.findByFilters(
            eq(null),
            eq(null),
            eq(null),
            eq(null),
            eq(null),
            eq(null),
            eq(null),
            eq(null),
            eq(null),
            eq(tenantId),
            eq(null),
            eq(null),
            eq(null),
            any(PageRequest.class)))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 5), 0));
  }

  private static ScheduledJobLastRun jobRow(ScheduledJobKey key, String scope) {
    ScheduledJobLastRun row = new ScheduledJobLastRun();
    row.setJobKey(key);
    row.setLastExecutionAt(OffsetDateTime.parse("2026-07-03T02:00:00Z"));
    row.setLastStatus(ScheduledJobLastRunStatus.SUCCESS);
    row.setLastRunScope(scope);
    row.setUpdatedAt(OffsetDateTime.parse("2026-07-03T02:00:00Z"));
    return row;
  }

  private static ScheduledJobLastRun neverRunRow(ScheduledJobKey key) {
    ScheduledJobLastRun row = new ScheduledJobLastRun();
    row.setJobKey(key);
    row.setLastStatus(ScheduledJobLastRunStatus.NEVER_RUN);
    row.setUpdatedAt(OffsetDateTime.parse("2026-07-03T00:00:00Z"));
    return row;
  }

  private static Alert sampleAlert() {
    Alert alert = new Alert();
    alert.setAlertId(99L);
    alert.setAlertType(AlertType.AUDIT_CHAIN_GAP_PENDING);
    alert.setStatus(AlertStatus.OPEN);
    alert.setCreatedAt(OffsetDateTime.parse("2026-07-03T01:00:00Z"));
    return alert;
  }
}
