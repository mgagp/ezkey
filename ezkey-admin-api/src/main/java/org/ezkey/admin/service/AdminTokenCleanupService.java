/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: AdminTokenCleanupService
 * Description: Scheduled service for automatic cleanup of expired admin tokens.
 */

package org.ezkey.admin.service;

import java.time.OffsetDateTime;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.ezkey.admin.config.AdminTokenCleanupProperties;
import org.ezkey.integration.domain.repository.AdminTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for automatic cleanup of expired admin tokens.
 *
 * <p>This service runs periodically to remove expired and inactive tokens from the database,
 * improving security and performance. It uses a scheduled task to automatically clean up tokens
 * based on configurable criteria.
 *
 * <p><b>Cleanup Strategy:</b>
 *
 * <ul>
 *   <li>Deletes tokens that are BOTH expired AND inactive
 *   <li>Preserves active tokens (even if expired) for audit trail
 *   <li>Runs on a configurable schedule (default: hourly)
 *   <li>Can be disabled via configuration
 * </ul>
 *
 * <p><b>Security Benefits:</b>
 *
 * <ul>
 *   <li>Reduces attack surface (fewer tokens in database)
 *   <li>Improves query performance (smaller tables)
 *   <li>Maintains database hygiene
 * </ul>
 *
 * <p><b>Configuration:</b>
 *
 * <pre>
 * ezkey.admin.token.cleanup.enabled=true
 * ezkey.admin.token.cleanup.schedule=0 0 * * * *  # Every hour
 * </pre>
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see AdminTokenCleanupProperties
 */
@Service
public class AdminTokenCleanupService {

  private static final Logger logger = LoggerFactory.getLogger(AdminTokenCleanupService.class);

  private final AdminTokenRepository tokenRepository;
  private final AdminTokenCleanupProperties properties;

  /**
   * Constructs a new admin token cleanup service.
   *
   * @param tokenRepository repository for admin bearer token operations
   * @param properties configuration properties for cleanup
   */
  public AdminTokenCleanupService(
      AdminTokenRepository tokenRepository, AdminTokenCleanupProperties properties) {
    this.tokenRepository = tokenRepository;
    this.properties = properties;
  }

  /**
   * Cleanup expired and inactive tokens periodically.
   *
   * <p>This method runs according to the configured schedule to remove expired and inactive tokens
   * from the database. It only deletes tokens that are both expired AND inactive, preserving active
   * tokens for audit.
   *
   * <p>The schedule is configurable via: {@code ezkey.admin.token.cleanup.schedule}
   *
   * <p>Default schedule: Every hour (0 0 * * * *)
   *
   * <p><b>HA Safety:</b> Uses distributed locking to ensure only one instance executes this job at
   * a time.
   */
  @Scheduled(cron = "${ezkey.admin.token.cleanup.schedule:0 0 * * * *}")
  @SchedulerLock(name = "ADMIN_TOKEN_CLEANUP", lockAtMostFor = "PT10M")
  @Transactional
  public void cleanupExpiredTokens() {
    if (!properties.isEnabled()) {
      logger.debug("Token cleanup disabled by configuration");
      return;
    }

    OffsetDateTime cutoff = OffsetDateTime.now();

    logger.info("🧹 Starting token cleanup - cutoff: {}", cutoff);

    try {
      // Delete bearer tokens that are both expired AND inactive
      int deleted = tokenRepository.deleteByExpiresAtBeforeAndActiveFalse(cutoff);

      if (deleted > 0) {
        logger.info("🧹 Cleaned up {} expired bearer tokens", deleted);
      } else {
        logger.debug("✅ No expired bearer tokens to clean");
      }

    } catch (Exception e) {
      logger.error("❌ Error during token cleanup: {}", e.getMessage(), e);
    }
  }

  /**
   * Get cleanup statistics for monitoring.
   *
   * <p>This method provides statistics about tokens eligible for cleanup for monitoring and
   * reporting purposes.
   *
   * @return count of tokens eligible for cleanup (expired and inactive)
   */
  @Transactional(readOnly = true)
  public long getCleanupCandidatesCount() {
    OffsetDateTime now = OffsetDateTime.now();
    return tokenRepository.countByExpiresAtBeforeAndActiveFalse(now);
  }
}
