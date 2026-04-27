/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: DashboardServiceTest
 * Description: Unit tests for dashboard integration lifecycle statistics.
 */

package org.ezkey.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;
import org.ezkey.admin.security.AdminPrincipal;
import org.ezkey.authattempt.domain.AuthAttemptDashboard24hStats;
import org.ezkey.authattempt.service.AuthAttemptService;
import org.ezkey.enrollment.domain.EnrollmentDashboardStats;
import org.ezkey.enrollment.service.EnrollmentService;
import org.ezkey.integration.domain.IntegrationLifecycleStatus;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminType;
import org.ezkey.integration.service.IntegrationService;
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
 * <p>These tests focus on the integration lifecycle badges shown on the dashboard. The dashboard
 * must count retired integrations explicitly instead of deriving a synthetic "inactive" count from
 * a total that excludes retired rows.
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

  @Test
  @DisplayName("Should include retired integrations in total and expose retired count separately")
  void buildOverview_shouldExposeRetiredIntegrationCount() {
    DashboardService service =
        new DashboardService(
            integrationService,
            enrollmentService,
            authAttemptService,
            auditLogService,
            alertService);
    AdminPrincipal principal = new AdminPrincipal(7, AdminType.TENANT_ADMIN, 42, null);

    when(integrationService.findByFilters(
            eq(null), eq(null), eq(true), eq(null), eq(null), eq(42), any(PageRequest.class)))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 1), 5));
    when(integrationService.findByFilters(
            eq(null),
            eq(IntegrationLifecycleStatus.ACTIVE),
            eq(false),
            eq(null),
            eq(null),
            eq(42),
            any(PageRequest.class)))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 1), 3));
    when(integrationService.findByFilters(
            eq(null),
            eq(IntegrationLifecycleStatus.RETIRED),
            eq(false),
            eq(null),
            eq(null),
            eq(42),
            any(PageRequest.class)))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 1), 2));
    when(enrollmentService.aggregateDashboardEnrollmentStats(42))
        .thenReturn(new EnrollmentDashboardStats(10, 2, 1, 0, 0));
    when(authAttemptService.aggregateDashboard24h(any(), eq(42)))
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
            eq(42),
            eq(null),
            eq(null),
            eq(null),
            any(PageRequest.class)))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 5), 0));

    var overview = service.buildOverview(principal);

    assertThat(overview.getIntegrations()).isNotNull();
    assertThat(overview.getIntegrations().getTotal()).isEqualTo(5);
    assertThat(overview.getIntegrations().getActive()).isEqualTo(3);
    assertThat(overview.getIntegrations().getRetired()).isEqualTo(2);
  }
}
