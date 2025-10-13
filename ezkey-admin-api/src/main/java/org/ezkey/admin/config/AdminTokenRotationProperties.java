/*
 * Ezkey - Open Source MFA/Passkey Alternative
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
 * Configuration properties for admin token rotation.
 *
 * <p>This class defines externalized configuration for the token rotation feature, which
 * deactivates old tokens when an administrator logs in. This ensures that only one active token
 * exists per administrator at any time.
 *
 * <p><b>Configuration Properties:</b>
 *
 * <ul>
 *   <li><b>rotationOnLoginEnabled:</b> Enable or disable rotation on login
 * </ul>
 *
 * <p><b>Example Configuration:</b>
 *
 * <pre>
 * ezkey.admin.token.rotation-on-login=true
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
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
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
}
