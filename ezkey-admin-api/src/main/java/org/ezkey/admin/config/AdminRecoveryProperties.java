/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: AdminRecoveryProperties
 * Description: Configuration properties for admin recovery codes and temporary tokens.
 */

package org.ezkey.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for admin recovery codes and temporary tokens.
 *
 * <p>These properties control the generation and usage of recovery codes for emergency admin access
 * when a device is lost. Recovery codes are single-use, cryptographically secure, and grant limited
 * temporary access for enrollment re-binding.
 *
 * <p><b>Security Model:</b>
 *
 * <ul>
 *   <li>10 recovery codes per admin (configurable)
 *   <li>Format: XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX (32 digits, 106-bit entropy)
 *   <li>BCrypt hashed storage
 *   <li>Single-use: Removed after successful validation
 *   <li>Limited access: Temporary token valid for configurable duration (default 30 minutes)
 * </ul>
 *
 * <p><b>Configuration Example:</b>
 *
 * <pre>
 * ezkey.admin.recovery.codes-count=10
 * ezkey.admin.recovery.temp-token-duration-minutes=30
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
@ConfigurationProperties(prefix = "ezkey.admin.recovery")
public class AdminRecoveryProperties {

  /** Number of recovery codes generated per admin. Default: 10 */
  private int codesCount = 10;

  /** Temporary recovery token validity duration in minutes. Default: 30 minutes */
  private int tempTokenDurationMinutes = 30;

  /**
   * Gets the number of recovery codes to generate per admin.
   *
   * @return the number of recovery codes (default: 10)
   */
  public int getCodesCount() {
    return codesCount;
  }

  /**
   * Sets the number of recovery codes to generate per admin.
   *
   * @param codesCount the number of recovery codes
   */
  public void setCodesCount(int codesCount) {
    this.codesCount = codesCount;
  }

  /**
   * Gets the temporary recovery token validity duration in minutes.
   *
   * @return the validity duration in minutes (default: 30)
   */
  public int getTempTokenDurationMinutes() {
    return tempTokenDurationMinutes;
  }

  /**
   * Sets the temporary recovery token validity duration in minutes.
   *
   * @param tempTokenDurationMinutes the validity duration in minutes
   */
  public void setTempTokenDurationMinutes(int tempTokenDurationMinutes) {
    this.tempTokenDurationMinutes = tempTokenDurationMinutes;
  }
}
