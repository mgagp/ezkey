/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: EnrollmentExpiredCleanupSchedulerTest
 * Description: Unit tests for enrollment expired cleanup job.
 */

package org.ezkey.enrollment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.config.EnrollmentProperties;
import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link EnrollmentExpiredCleanupScheduler#markExpiredEnrollments()}.
 *
 * <p>Verifies that only CREATED enrollments with expires_at &lt; now are marked EXPIRED and that
 * ENROLLMENT_EXPIRED audit is emitted for each.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Enrollment expired cleanup scheduler")
class EnrollmentExpiredCleanupSchedulerTest {

  @Mock private EnrollmentRepository enrollmentRepository;
  @Mock private AuditLogService auditLogService;
  @Mock private EnrollmentProperties enrollmentProperties;

  @InjectMocks private EnrollmentExpiredCleanupScheduler scheduler;

  @Test
  @DisplayName("markExpiredEnrollments - marks only expired CREATED enrollments and emits audit")
  void markExpiredEnrollments_WhenExpiredExist_MarksAndEmitsAudit() {
    OffsetDateTime now = OffsetDateTime.now();
    Enrollment expired1 = new Enrollment();
    expired1.setEnrollmentId(1);
    expired1.setIntegrationId(10);
    expired1.setStatus(EnrollmentStatus.CREATED);
    expired1.setExpiresAt(now.minusHours(1));

    when(enrollmentRepository.findCreatedEnrollmentsExpiredBefore(
            eq(EnrollmentStatus.CREATED), any(OffsetDateTime.class)))
        .thenReturn(List.of(expired1));

    scheduler.markExpiredEnrollments();

    verify(enrollmentRepository).save(expired1);
    assertEquals(EnrollmentStatus.EXPIRED, expired1.getStatus());

    ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
    verify(auditLogService, times(1)).log(auditCaptor.capture());
    AuditLog log = auditCaptor.getValue();
    assertEquals(EventType.ENROLLMENT_EXPIRED, log.getEventType());
    assertEquals(1, log.getEnrollmentId());
    assertEquals(10, log.getIntegrationId());
    assertNotNull(log.getEventDetails());
    assertTrue(log.getEventDetails().contains("enrollmentId"));
  }

  @Test
  @DisplayName("markExpiredEnrollments - does nothing when no expired enrollments")
  void markExpiredEnrollments_WhenNoneExpired_DoesNothing() {
    when(enrollmentRepository.findCreatedEnrollmentsExpiredBefore(
            eq(EnrollmentStatus.CREATED), any(OffsetDateTime.class)))
        .thenReturn(List.of());

    scheduler.markExpiredEnrollments();

    verify(enrollmentRepository, never()).save(any());
    verify(auditLogService, never()).log(any());
  }
}
