/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Scheduler: EnrollmentExpiredCleanupScheduler
 * Description: Scheduled job to mark expired pending enrollments as EXPIRED and emit audit.
 */

package org.ezkey.enrollment.service;

import java.time.OffsetDateTime;
import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.ezkey.audit.domain.ApiName;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.audit.util.AuditDetailsBuilder;
import org.ezkey.config.EnrollmentProperties;
import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scheduled job that marks expired pending enrollments (CREATED + expires_at &lt; now) as EXPIRED
 * and emits ENROLLMENT_EXPIRED audit events.
 *
 * <p>Default schedule: once per day at 1 AM (configurable via
 * ezkey.enrollment.expired-cleanup.cron). Enforcement of expiration is already done at bind/verify
 * (time check); this job provides cleanup and audit trail.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Component
@ConditionalOnProperty(
    name = "ezkey.enrollment.expired-cleanup.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class EnrollmentExpiredCleanupScheduler {

  private static final Logger logger =
      LoggerFactory.getLogger(EnrollmentExpiredCleanupScheduler.class);

  private final EnrollmentRepository enrollmentRepository;
  private final AuditLogService auditLogService;

  public EnrollmentExpiredCleanupScheduler(
      EnrollmentRepository enrollmentRepository,
      AuditLogService auditLogService,
      EnrollmentProperties enrollmentProperties) {
    this.enrollmentRepository = enrollmentRepository;
    this.auditLogService = auditLogService;
  }

  /**
   * Marks CREATED enrollments with expires_at &lt; now as EXPIRED and emits ENROLLMENT_EXPIRED for
   * each.
   */
  @Scheduled(cron = "${ezkey.enrollment.expired-cleanup.cron:0 0 1 * * ?}")
  @SchedulerLock(name = "ENROLLMENT_EXPIRED_CLEANUP", lockAtMostFor = "PT30M")
  @Transactional
  public void markExpiredEnrollments() {
    OffsetDateTime now = OffsetDateTime.now();
    List<Enrollment> expired =
        enrollmentRepository.findCreatedEnrollmentsExpiredBefore(EnrollmentStatus.CREATED, now);
    if (expired.isEmpty()) {
      logger.debug("Enrollment expired cleanup: no expired pending enrollments");
      return;
    }
    logger.info(
        "Enrollment expired cleanup: marking {} pending enrollment(s) as EXPIRED", expired.size());
    for (Enrollment e : expired) {
      e.setStatus(EnrollmentStatus.EXPIRED);
      enrollmentRepository.save(e);
      String details =
          AuditDetailsBuilder.builder()
              .custom("enrollmentId", e.getEnrollmentId())
              .custom("expiresAt", e.getExpiresAt() != null ? e.getExpiresAt().toString() : null)
              .custom("reason", "scheduled_cleanup")
              .toJson();
      auditLogService.log(
          AuditLog.builder()
              .eventType(EventType.ENROLLMENT_EXPIRED)
              .eventAction("enrollment_expired_cleanup")
              .eventStatus(EventStatus.SUCCESS)
              .apiName(ApiName.ADMIN_API)
              .enrollmentId(e.getEnrollmentId())
              .integrationId(e.getIntegrationId())
              .eventDetails(details)
              .build());
    }
  }
}
