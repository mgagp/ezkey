/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: AdminMfaProperties
 * Description: Configuration properties for admin MFA bootstrap and behavior.
 */

package org.ezkey.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for admin MFA bootstrap and behavior.
 *
 * <p>These properties control how the admin MFA infrastructure is initialized and how MFA is
 * enforced for administrator authentication. The bootstrap process automatically creates
 * Integration Zero and Enrollment Zero at application startup.
 *
 * <p><b>Configuration Example:</b>
 *
 * <pre>
 * ezkey.admin.mfa.mode=dev
 * ezkey.admin.mfa.bootstrap.enabled=true
 * ezkey.admin.mfa.bootstrap.auto-enrollment=true
 * </pre>
 *
 * <p><b>MFA Modes:</b>
 *
 * <ul>
 *   <li><b>dev:</b> MFA is optional, administrators can choose to enable it
 *   <li><b>prod:</b> MFA is required for all administrators
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
@ConfigurationProperties(prefix = "ezkey.admin.mfa")
public class AdminMfaProperties {

  /**
   * MFA enforcement mode.
   *
   * <p><b>dev:</b> MFA is optional, administrators can choose to enable it. Suitable for
   * development and testing environments.
   *
   * <p><b>prod:</b> MFA is required for all administrators. Recommended for production
   * environments.
   */
  private String mode = "dev";

  /** Bootstrap configuration for automatic MFA infrastructure creation. */
  private BootstrapConfig bootstrap = new BootstrapConfig();

  /**
   * Gets the MFA enforcement mode.
   *
   * @return the MFA mode (dev or prod)
   */
  public String getMode() {
    return mode;
  }

  /**
   * Sets the MFA enforcement mode.
   *
   * @param mode the MFA mode (dev or prod)
   */
  public void setMode(String mode) {
    this.mode = mode;
  }

  /**
   * Gets the bootstrap configuration.
   *
   * @return the bootstrap configuration
   */
  public BootstrapConfig getBootstrap() {
    return bootstrap;
  }

  /**
   * Sets the bootstrap configuration.
   *
   * @param bootstrap the bootstrap configuration
   */
  public void setBootstrap(BootstrapConfig bootstrap) {
    this.bootstrap = bootstrap;
  }

  /**
   * Bootstrap configuration for automatic MFA infrastructure creation.
   *
   * <p>Controls whether Integration Zero and Enrollment Zero are automatically created at
   * application startup, enabling the "Eat Your Own Dog Food" approach where Ezkey uses its own MFA
   * solution for admin authentication.
   */
  public static class BootstrapConfig {

    /**
     * Enable or disable automatic MFA bootstrap.
     *
     * <p>When enabled, the application automatically creates Integration Zero and optionally
     * Enrollment Zero at startup if they don't exist.
     */
    private boolean enabled = true;

    /**
     * Enable or disable automatic enrollment creation.
     *
     * <p>When enabled, the application automatically creates Enrollment Zero for the admin user at
     * startup. This is recommended for development but should be carefully considered for
     * production.
     *
     * <p><b>Development:</b> true (convenient for testing)<br>
     * <b>Production:</b> false (manual enrollment for security)
     */
    private boolean autoEnrollment = true;

    /**
     * Gets the bootstrap enabled status.
     *
     * @return true if bootstrap is enabled
     */
    public boolean isEnabled() {
      return enabled;
    }

    /**
     * Sets the bootstrap enabled status.
     *
     * @param enabled true to enable bootstrap
     */
    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    /**
     * Gets the auto-enrollment enabled status.
     *
     * @return true if auto-enrollment is enabled
     */
    public boolean isAutoEnrollment() {
      return autoEnrollment;
    }

    /**
     * Sets the auto-enrollment enabled status.
     *
     * @param autoEnrollment true to enable auto-enrollment
     */
    public void setAutoEnrollment(boolean autoEnrollment) {
      this.autoEnrollment = autoEnrollment;
    }
  }
}
