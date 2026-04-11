/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AuthAttemptExpirySchedulerTest
 * Description: Unit tests for auth attempt expiry scheduler.
 */
package org.ezkey.authattempt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.ezkey.audit.constants.AuthAttemptAuditConstants;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.authattempt.domain.AuthAttemptStatus;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.ezkey.authattempt.domain.repository.AuthAttemptRepository;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.integration.domain.repository.IntegrationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for {@link AuthAttemptExpiryScheduler#expireStalePendingAndReadAttempts()}. */
@ExtendWith(MockitoExtension.class)
@DisplayName("Auth attempt expiry scheduler")
class AuthAttemptExpirySchedulerTest {

  @Mock private AuthAttemptRepository authAttemptRepository;
  @Mock private AuditLogService auditLogService;
  @Mock private EnrollmentRepository enrollmentRepository;
  @Mock private IntegrationRepository integrationRepository;

  @InjectMocks private AuthAttemptExpiryScheduler scheduler;

  @Test
  @DisplayName("when no candidates, does not audit or update")
  void expireStalePendingAndReadAttempts_NoCandidates_NoAudit() {
    when(authAttemptRepository.findByAuthAttemptStatusInAndExpiresAtBeforeOrderByAuthAttemptIdAsc(
            any(), any(OffsetDateTime.class)))
        .thenReturn(List.of());

    scheduler.expireStalePendingAndReadAttempts();

    verify(authAttemptRepository, never()).expireIfStale(any(), any(), any(), any());
    verify(auditLogService, never()).log(any(AuditLog.class));
  }

  @Test
  @DisplayName("when expireIfStale returns 0, skips audit")
  void expireStalePendingAndReadAttempts_RaceLost_NoAudit() {
    OffsetDateTime now = OffsetDateTime.parse("2026-04-11T12:00:00Z");
    AuthAttempt a = new AuthAttempt();
    a.setAuthAttemptId(1);
    a.setEnrollmentId(10);
    a.setAuthAttemptStatus(AuthAttemptStatus.READ);
    a.setExpiresAt(now.minusSeconds(30));
    a.setCreatedAt(now.minusMinutes(2));

    when(authAttemptRepository.findByAuthAttemptStatusInAndExpiresAtBeforeOrderByAuthAttemptIdAsc(
            any(), any(OffsetDateTime.class)))
        .thenReturn(List.of(a));
    when(authAttemptRepository.expireIfStale(
            eq(1), any(), any(OffsetDateTime.class), eq(AuthAttemptStatus.EXPIRED)))
        .thenReturn(0);

    scheduler.expireStalePendingAndReadAttempts();

    verify(auditLogService, never()).log(any(AuditLog.class));
  }

  @Test
  @DisplayName("when row expired, logs AUTH_ATTEMPT_EXPIRED with scheduler action")
  void expireStalePendingAndReadAttempts_ExpiresAndAudits() {
    OffsetDateTime now = OffsetDateTime.parse("2026-04-11T12:00:00Z");
    AuthAttempt a = new AuthAttempt();
    a.setAuthAttemptId(42);
    a.setEnrollmentId(10);
    a.setAuthAttemptStatus(AuthAttemptStatus.READ);
    a.setExpiresAt(now.minusSeconds(30));
    a.setCreatedAt(now.minusMinutes(2));

    Enrollment enrollment = new Enrollment(7, "device", "token");
    enrollment.setEnrollmentId(10);

    when(authAttemptRepository.findByAuthAttemptStatusInAndExpiresAtBeforeOrderByAuthAttemptIdAsc(
            any(), any(OffsetDateTime.class)))
        .thenReturn(List.of(a));
    when(authAttemptRepository.expireIfStale(
            eq(42), any(), any(OffsetDateTime.class), eq(AuthAttemptStatus.EXPIRED)))
        .thenReturn(1);
    when(enrollmentRepository.findById(10)).thenReturn(Optional.of(enrollment));
    when(integrationRepository.findTenantIdByIntegrationId(7)).thenReturn(Optional.of(99));

    scheduler.expireStalePendingAndReadAttempts();

    ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
    verify(auditLogService).log(captor.capture());
    AuditLog log = captor.getValue();
    assertThat(log.getEventType()).isEqualTo(EventType.AUTH_ATTEMPT_EXPIRED);
    assertThat(log.getEventAction())
        .isEqualTo(AuthAttemptAuditConstants.AUTH_ATTEMPT_EXPIRED_SCHEDULER);
    assertThat(log.getAuthAttemptId()).isEqualTo(42);
    assertThat(log.getEnrollmentId()).isEqualTo(10);
    assertThat(log.getIntegrationId()).isEqualTo(7);
    assertThat(log.getTenantId()).isEqualTo(99);
  }
}
