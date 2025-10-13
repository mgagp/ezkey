/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: AdminTokenCleanupProperties
 * Description: Configuration properties for automatic token cleanup.
 */

package org.ezkey.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for admin token cleanup.
 *
 * <p>This class defines externalized configuration for the automatic cleanup of expired and
 * inactive admin tokens. The cleanup service runs periodically to remove old tokens from the
 * database, improving security and performance.
 *
 * <p><b>Configuration Properties:</b>
 *
 * <ul>
 *   <li><b>enabled:</b> Enable or disable the cleanup service
 *   <li><b>schedule:</b> Cron expression for scheduling cleanup runs
 * </ul>
 *
 * <p><b>Example Configuration:</b>
 *
 * <pre>
 * ezkey.admin.token.cleanup.enabled=true
 * ezkey.admin.token.cleanup.schedule=0 0 * * * *  # Every hour
 * </pre>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Component
@ConfigurationProperties(prefix = "ezkey.admin.token.cleanup")
public class AdminTokenCleanupProperties {

  /**
   * Flag to enable or disable token cleanup service.
   *
   * <p>When disabled, expired tokens will not be automatically cleaned up. Default is true.
   */
  private boolean enabled = true;

  /**
   * Cron expression for scheduling token cleanup.
   *
   * <p>Default: Every hour (at minute 0 of every hour)
   *
   * <p>Example schedules:
   *
   * <ul>
   *   <li>Every hour: 0 0 * * * *
   *   <li>Every 4 hours: 0 0 0/4 * * *
   *   <li>Daily at midnight: 0 0 0 * * *
   * </ul>
   */
  private String schedule = "0 0 * * * *";

  /**
   * Gets the enabled flag.
   *
   * @return true if cleanup is enabled, false otherwise
   */
  public boolean isEnabled() {
    return enabled;
  }

  /**
   * Sets the enabled flag.
   *
   * @param enabled true to enable cleanup, false to disable
   */
  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  /**
   * Gets the cleanup schedule cron expression.
   *
   * @return the cron expression for cleanup schedule
   */
  public String getSchedule() {
    return schedule;
  }

  /**
   * Sets the cleanup schedule cron expression.
   *
   * @param schedule the cron expression for cleanup schedule
   */
  public void setSchedule(String schedule) {
    this.schedule = schedule;
  }
}
