/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: AuthAttemptExpiryScheduler
 * Description: Scheduled job to persist EXPIRED status for auth attempts past their TTL.
 */
package org.ezkey.authattempt.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.ezkey.audit.constants.AuthAttemptAuditConstants;
import org.ezkey.audit.domain.ApiName;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.audit.util.AuditDetailsBuilder;
import org.ezkey.authattempt.domain.AuthAttemptStatus;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.ezkey.authattempt.domain.repository.AuthAttemptRepository;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.integration.domain.repository.IntegrationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Marks {@link AuthAttemptStatus#PENDING} and {@link AuthAttemptStatus#READ} rows as {@link
 * AuthAttemptStatus#EXPIRED} when {@code expires_at} is in the past.
 *
 * <p>Without this job, rows could remain {@code PENDING} or {@code READ} indefinitely after TTL
 * while {@link org.ezkey.authattempt.service.AuthAttemptWaitService} only computes {@code EXPIRED}
 * at read time. This scheduler aligns persisted state with real-world expiry.
 *
 * <p>Emits {@link EventType#AUTH_ATTEMPT_EXPIRED} for each successful persisted transition (same
 * idea as {@link org.ezkey.enrollment.service.EnrollmentExpiredCleanupScheduler} for enrollments).
 *
 * <p>Runs in processes that enable scheduling and ShedLock (e.g. Admin API). Default: every 60
 * seconds, configurable via {@code ezkey.auth-attempt.expiry-scheduler.fixed-delay-ms}.
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
    name = "ezkey.auth-attempt.expiry-scheduler.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class AuthAttemptExpiryScheduler {

  private static final Logger logger = LoggerFactory.getLogger(AuthAttemptExpiryScheduler.class);

  private static final List<AuthAttemptStatus> EXPIRABLE_STATUSES =
      List.of(AuthAttemptStatus.PENDING, AuthAttemptStatus.READ);

  private final AuthAttemptRepository authAttemptRepository;
  private final AuditLogService auditLogService;
  private final EnrollmentRepository enrollmentRepository;
  private final IntegrationRepository integrationRepository;

  /**
   * Creates the scheduler with auth attempt persistence, audit logging, and tenant resolution
   * dependencies.
   *
   * @param authAttemptRepository persistence for auth attempts
   * @param auditLogService audit trail for expired attempts
   * @param enrollmentRepository resolves integration for tenant-scoped audit rows
   * @param integrationRepository resolves tenant id for audit rows
   */
  public AuthAttemptExpiryScheduler(
      AuthAttemptRepository authAttemptRepository,
      AuditLogService auditLogService,
      EnrollmentRepository enrollmentRepository,
      IntegrationRepository integrationRepository) {
    this.authAttemptRepository = authAttemptRepository;
    this.auditLogService = auditLogService;
    this.enrollmentRepository = enrollmentRepository;
    this.integrationRepository = integrationRepository;
  }

  /**
   * Updates auth attempts that are past {@code expires_at} to {@link AuthAttemptStatus#EXPIRED} and
   * records {@link EventType#AUTH_ATTEMPT_EXPIRED} for each successful update.
   */
  @Scheduled(
      fixedDelayString = "${ezkey.auth-attempt.expiry-scheduler.fixed-delay-ms:60000}",
      initialDelayString = "${ezkey.auth-attempt.expiry-scheduler.initial-delay-ms:60000}")
  @SchedulerLock(name = "AUTH_ATTEMPT_EXPIRY", lockAtMostFor = "PT5M", lockAtLeastFor = "PT5S")
  @Transactional
  public void expireStalePendingAndReadAttempts() {
    OffsetDateTime now = OffsetDateTime.now();
    List<AuthAttempt> candidates =
        authAttemptRepository.findByAuthAttemptStatusInAndExpiresAtBeforeOrderByAuthAttemptIdAsc(
            EXPIRABLE_STATUSES, now);
    if (candidates.isEmpty()) {
      logger.debug("Auth attempt expiry: no attempts past expires_at to update");
      return;
    }

    int expiredCount = 0;
    for (AuthAttempt snapshot : candidates) {
      AuthAttemptStatus previousStatus = snapshot.getAuthAttemptStatus();
      int updated =
          authAttemptRepository.expireIfStale(
              snapshot.getAuthAttemptId(), EXPIRABLE_STATUSES, now, AuthAttemptStatus.EXPIRED);
      if (updated == 0) {
        continue;
      }
      expiredCount++;
      emitExpiryAudit(snapshot, previousStatus, now);
    }

    if (expiredCount > 0) {
      logger.info(
          "Auth attempt expiry: marked {} attempt(s) as EXPIRED (past expires_at)", expiredCount);
    }
  }

  private void emitExpiryAudit(
      AuthAttempt snapshot, AuthAttemptStatus previousStatus, OffsetDateTime now) {
    Integer enrollmentId = snapshot.getEnrollmentId();
    Optional<Enrollment> enrollment = enrollmentRepository.findById(enrollmentId);
    Integer integrationId = enrollment.map(Enrollment::getIntegrationId).orElse(null);
    Integer tenantId =
        integrationId != null
            ? integrationRepository.findTenantIdByIntegrationId(integrationId).orElse(null)
            : null;

    if (enrollment.isEmpty()) {
      logger.warn(
          "Auth attempt expiry audit: enrollment {} not found for authAttemptId {}",
          enrollmentId,
          snapshot.getAuthAttemptId());
    }

    String details =
        AuditDetailsBuilder.builder()
            .custom("authAttemptId", snapshot.getAuthAttemptId())
            .custom("enrollmentId", enrollmentId)
            .custom("integrationId", integrationId)
            .custom("previousStatus", previousStatus != null ? previousStatus.name() : null)
            .custom("newStatus", AuthAttemptStatus.EXPIRED.name())
            .custom(
                "expiresAt",
                snapshot.getExpiresAt() != null ? snapshot.getExpiresAt().toString() : null)
            .custom("reason", "scheduled_expiry")
            .custom("processedAt", now.toString())
            .toJson();

    auditLogService.log(
        AuditLog.builder()
            .eventType(EventType.AUTH_ATTEMPT_EXPIRED)
            .eventAction(AuthAttemptAuditConstants.AUTH_ATTEMPT_EXPIRED_SCHEDULER)
            .eventStatus(EventStatus.SUCCESS)
            .apiName(ApiName.ADMIN_API)
            .tenantId(tenantId)
            .integrationId(integrationId)
            .enrollmentId(enrollmentId)
            .authAttemptId(snapshot.getAuthAttemptId())
            .authAttemptCreatedAt(snapshot.getCreatedAt())
            .eventDetails(details)
            .build());
  }
}
