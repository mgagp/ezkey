/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: BootstrapCredentialsBindSecretRedactor
 * Description: Admin-API-only redaction of bind secrets from bootstrap-credentials.json.
 */

package org.ezkey.admin.service;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.ezkey.admin.config.BootstrapExportProperties;
import org.ezkey.admin.config.InitialGlobalAdminProperties;
import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.ezkey.security.ApplicationReadyStartupOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Removes enrollment proof token and challenge from {@code bootstrap-credentials.json} once the
 * bootstrap global-admin enrollment is BOUND or VERIFIED.
 *
 * <p>Runs only in Admin API (the credentials volume must not be mounted on Auth API). Triggered at
 * startup (after MFA bootstrap) and on a short ShedLock-guarded schedule so HA has a single writer.
 *
 * @since 2026
 */
@Component
public class BootstrapCredentialsBindSecretRedactor {

  private static final Logger logger =
      LoggerFactory.getLogger(BootstrapCredentialsBindSecretRedactor.class);

  private final BootstrapExportProperties exportProperties;
  private final InitialGlobalAdminProperties initialGlobalAdminProperties;
  private final EzkeyAdminRepository adminRepository;
  private final BootstrapCredentialsFileExporter credentialsFileExporter;

  /**
   * Creates the redactor.
   *
   * @param exportProperties credentials file path
   * @param initialGlobalAdminProperties bootstrap admin username
   * @param adminRepository admin + enrollment lookup
   * @param credentialsFileExporter atomic 0600 file rewrite
   */
  public BootstrapCredentialsBindSecretRedactor(
      BootstrapExportProperties exportProperties,
      InitialGlobalAdminProperties initialGlobalAdminProperties,
      EzkeyAdminRepository adminRepository,
      BootstrapCredentialsFileExporter credentialsFileExporter) {
    this.exportProperties = exportProperties;
    this.initialGlobalAdminProperties = initialGlobalAdminProperties;
    this.adminRepository = adminRepository;
    this.credentialsFileExporter = credentialsFileExporter;
  }

  /** Startup pass after MFA bootstrap so a restart following bind clears leftover bind secrets. */
  @EventListener(ApplicationReadyEvent.class)
  @Order(ApplicationReadyStartupOrder.BOOTSTRAP_CREDENTIALS_REDACT)
  public void redactOnStartup() {
    redactIfBootstrapEnrollmentPastBind();
  }

  /**
   * Periodic pass (default every 60s) so bind completed by Auth API is reflected without mounting
   * the credentials volume on Auth API.
   */
  @Scheduled(
      fixedDelayString = "${ezkey.admin.bootstrap.bind-secret-redact.delay-ms:60000}",
      initialDelayString = "${ezkey.admin.bootstrap.bind-secret-redact.initial-delay-ms:15000}")
  @SchedulerLock(name = "BOOTSTRAP_CREDENTIALS_BIND_SECRET_REDACT", lockAtMostFor = "PT2M")
  public void redactOnSchedule() {
    redactIfBootstrapEnrollmentPastBind();
  }

  /**
   * When the bootstrap global-admin enrollment is BOUND or VERIFIED, strip proof token and
   * challenge from the credentials file (recovery codes retained). Idempotent.
   */
  void redactIfBootstrapEnrollmentPastBind() {
    String path = exportProperties.getPath();
    if (path == null || path.isBlank()) {
      return;
    }

    String username = initialGlobalAdminProperties.getUsername();
    if (username == null || username.isBlank()) {
      return;
    }

    EzkeyAdmin admin = adminRepository.findByUsernameWithEnrollment(username).orElse(null);
    if (admin == null || admin.getEnrollment() == null) {
      return;
    }

    Enrollment enrollment = admin.getEnrollment();
    EnrollmentStatus status = enrollment.getStatus();
    if (status != EnrollmentStatus.BOUND && status != EnrollmentStatus.VERIFIED) {
      logger.debug(
          "Bootstrap credentials redact skipped (enrollmentId={}, status={})",
          enrollment.getEnrollmentId(),
          status);
      return;
    }

    boolean changed =
        credentialsFileExporter.removeBindSecretsKeepRecoveryCodes(enrollment.getEnrollmentId());
    if (changed) {
      logger.info(
          "Bootstrap credentials bind secrets redacted (enrollmentId={}, status={})",
          enrollment.getEnrollmentId(),
          status);
    }
  }
}
