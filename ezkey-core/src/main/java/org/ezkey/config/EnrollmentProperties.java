/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: EnrollmentProperties
 * Description: Configuration properties for enrollment expiration and cleanup.
 */

package org.ezkey.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for enrollment expiration (pending phase) and expired-enrollment
 * cleanup.
 *
 * <p>Used to set optional expiration when creating enrollments and to configure the scheduled job
 * that marks expired pending enrollments as EXPIRED and emits ENROLLMENT_EXPIRED audit events.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@ConfigurationProperties(prefix = "ezkey.enrollment")
@Validated
public class EnrollmentProperties {

  /**
   * Optional number of days after creation before a pending enrollment expires. When set (e.g. 30),
   * new enrollments get {@code expires_at = created_at + this many days}. Null or not set means no
   * expiration (current behavior).
   */
  private Integer pendingExpirationDays;

  /**
   * Cron expression for the job that marks expired pending enrollments (CREATED + expires_at &lt;
   * now) as EXPIRED and emits ENROLLMENT_EXPIRED. Default: once per day at 1 AM.
   */
  private String expiredCleanupCron = "0 0 1 * * ?";

  public Integer getPendingExpirationDays() {
    return pendingExpirationDays;
  }

  public void setPendingExpirationDays(Integer pendingExpirationDays) {
    this.pendingExpirationDays = pendingExpirationDays;
  }

  public String getExpiredCleanupCron() {
    return expiredCleanupCron;
  }

  public void setExpiredCleanupCron(String expiredCleanupCron) {
    this.expiredCleanupCron = expiredCleanupCron;
  }
}
