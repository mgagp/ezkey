/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: AdminRateLimitProperties
 * Description: Externalized configuration properties for admin API rate limiting functionality.
 */

package org.ezkey.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for admin API rate limiting functionality.
 *
 * <p>This class provides externalized configuration for rate limiting settings, allowing
 * administrators to adjust rate limits without code changes. Rate limiting is applied to
 * authentication endpoints to prevent brute force attacks.
 *
 * <p><b>Configuration Prefix:</b> ezkey.admin.rate-limit
 *
 * <p><b>Example Configuration:</b>
 *
 * <pre>
 * ezkey.admin.rate-limit.enabled=true
 * ezkey.admin.rate-limit.login.requests=5
 * ezkey.admin.rate-limit.login.window-minutes=5
 * ezkey.admin.rate-limit.login.block-after-failures=10
 * ezkey.admin.rate-limit.login.block-duration-minutes=30
 * </pre>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@ConfigurationProperties(prefix = "ezkey.admin.rate-limit")
public class AdminRateLimitProperties {

  /**
   * Global rate limiting enablement flag. When disabled, no rate limiting is applied to any
   * endpoints.
   */
  private boolean enabled = true;

  /** Rate limiting configuration for login endpoint. */
  private LoginConfig login = new LoginConfig();

  /** Configuration for login endpoint rate limiting behavior. */
  public static class LoginConfig {
    /**
     * Maximum number of requests allowed within the time window. Default: 5 requests per 5 minutes.
     */
    private int requests = 5;

    /** Time window in minutes for the rate limit. Default: 5 minutes. */
    private int windowMinutes = 5;

    /**
     * Strategy for identifying clients for rate limiting. Options: "client-ip" (default) Default:
     * "client-ip"
     */
    private String keyStrategy = "client-ip";

    /**
     * Number of consecutive failures before blocking the client IP. Set to 0 to disable blocking.
     * Default: 10 failures.
     */
    private int blockAfterFailures = 10;

    /** Duration in minutes to block a client IP after too many failures. Default: 30 minutes. */
    private int blockDurationMinutes = 30;

    // Getters and Setters

    public int getRequests() {
      return requests;
    }

    public void setRequests(int requests) {
      this.requests = requests;
    }

    public int getWindowMinutes() {
      return windowMinutes;
    }

    public void setWindowMinutes(int windowMinutes) {
      this.windowMinutes = windowMinutes;
    }

    public String getKeyStrategy() {
      return keyStrategy;
    }

    public void setKeyStrategy(String keyStrategy) {
      this.keyStrategy = keyStrategy;
    }

    public int getBlockAfterFailures() {
      return blockAfterFailures;
    }

    public void setBlockAfterFailures(int blockAfterFailures) {
      this.blockAfterFailures = blockAfterFailures;
    }

    public int getBlockDurationMinutes() {
      return blockDurationMinutes;
    }

    public void setBlockDurationMinutes(int blockDurationMinutes) {
      this.blockDurationMinutes = blockDurationMinutes;
    }
  }

  // Getters and Setters for AdminRateLimitProperties

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public LoginConfig getLogin() {
    return login;
  }

  public void setLogin(LoginConfig login) {
    this.login = login;
  }
}
