/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: AdminTokenRotationProperties
 * Description: Configuration properties for token rotation on login.
 */

package org.ezkey.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for admin token rotation and expiration.
 *
 * <p>This class defines externalized configuration for the token rotation feature, which
 * deactivates old tokens when an administrator logs in, and for token expiration (TTL and sliding
 * expiration). Sliding expiration extends the token's expiration time on each validated request, so
 * the session stays valid as long as the user is active.
 *
 * <p><b>Configuration Properties:</b>
 *
 * <ul>
 *   <li><b>rotationOnLoginEnabled:</b> Enable or disable rotation on login
 *   <li><b>expirationHours:</b> Token validity window in hours (initial TTL and sliding window)
 * </ul>
 *
 * <p><b>Example Configuration:</b>
 *
 * <pre>
 * ezkey.admin.token.rotation-on-login=true
 * ezkey.admin.token.expiration-hours=2
 * </pre>
 *
 * <p><b>Security Benefits:</b>
 *
 * <ul>
 *   <li>Limits to one active token per administrator
 *   <li>Stolen tokens become invalid on next legitimate login
 *   <li>Reduces attack surface
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Component
@ConfigurationProperties(prefix = "ezkey.admin.token")
public class AdminTokenRotationProperties {

  /**
   * Flag to enable or disable token rotation on login.
   *
   * <p>When enabled, all existing active tokens for an administrator are deactivated when they log
   * in, ensuring only one active token exists at any time.
   *
   * <p>Default is true.
   */
  private boolean rotationOnLoginEnabled = true;

  /**
   * Gets the rotation on login enabled flag.
   *
   * @return true if rotation on login is enabled, false otherwise
   */
  public boolean isRotationOnLoginEnabled() {
    return rotationOnLoginEnabled;
  }

  /**
   * Sets the rotation on login enabled flag.
   *
   * @param rotationOnLoginEnabled true to enable rotation on login, false to disable
   */
  public void setRotationOnLoginEnabled(boolean rotationOnLoginEnabled) {
    this.rotationOnLoginEnabled = rotationOnLoginEnabled;
  }

  /**
   * Token validity duration in hours.
   *
   * <p>Used for both initial token TTL at login and for sliding expiration: on each validated
   * request, the token's expiration is extended to now + expirationHours. Thus the session remains
   * valid as long as the user is active within the window. Recovery tokens are not extended (they
   * keep their own short duration).
   *
   * <p>Default is 2 hours (reduces exposure window compared to the previous 24h fixed TTL).
   */
  private int expirationHours = 2;

  /**
   * Gets the token expiration duration in hours.
   *
   * @return the expiration duration in hours
   */
  public int getExpirationHours() {
    return expirationHours;
  }

  /**
   * Sets the token expiration duration in hours.
   *
   * @param expirationHours the expiration duration in hours (must be positive)
   */
  public void setExpirationHours(int expirationHours) {
    this.expirationHours = expirationHours;
  }
}
