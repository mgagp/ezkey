/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: ApiKeyRateLimitProperties
 * Description: Externalized configuration properties for API key rate limiting functionality.
 */

package org.ezkey.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for API key rate limiting functionality.
 *
 * <p>This class provides externalized configuration for rate limiting settings for API key operations,
 * allowing administrators to adjust rate limits without code changes. Rate limiting is applied to
 * API key operations to prevent abuse and ensure fair usage.
 *
 * <p><b>Configuration Prefix:</b> ezkey.api-key.rate-limit
 *
 * <p><b>Example Configuration:</b>
 *
 * <pre>
 * ezkey.api-key.rate-limit.enabled=true
 * ezkey.api-key.rate-limit.create-auth-attempt.requests=100
 * ezkey.api-key.rate-limit.create-auth-attempt.window-minutes=15
 * ezkey.api-key.rate-limit.wait-auth-attempt.requests=200
 * ezkey.api-key.rate-limit.wait-auth-attempt.window-minutes=15
 * </pre>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@ConfigurationProperties(prefix = "ezkey.api-key.rate-limit")
public class ApiKeyRateLimitProperties {

  /**
   * Global rate limiting enablement flag. When disabled, no rate limiting is applied to any
   * API key operations.
   */
  private boolean enabled = true;

  /** Rate limiting configuration for create auth attempt operations. */
  private CreateAuthAttemptConfig createAuthAttempt = new CreateAuthAttemptConfig();

  /** Rate limiting configuration for wait auth attempt operations. */
  private WaitAuthAttemptConfig waitAuthAttempt = new WaitAuthAttemptConfig();

  /** Configuration for create auth attempt rate limiting behavior. */
  public static class CreateAuthAttemptConfig {
    /**
     * Maximum number of create auth attempt requests allowed within the time window. 
     * Default: 100 requests per 15 minutes.
     */
    private int requests = 100;

    /** Time window in minutes for the rate limit. Default: 15 minutes. */
    private int windowMinutes = 15;

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
  }

  /** Configuration for wait auth attempt rate limiting behavior. */
  public static class WaitAuthAttemptConfig {
    /**
     * Maximum number of wait auth attempt requests allowed within the time window. 
     * Default: 200 requests per 15 minutes.
     */
    private int requests = 200;

    /** Time window in minutes for the rate limit. Default: 15 minutes. */
    private int windowMinutes = 15;

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
  }

  // Getters and Setters for ApiKeyRateLimitProperties

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public CreateAuthAttemptConfig getCreateAuthAttempt() {
    return createAuthAttempt;
  }

  public void setCreateAuthAttempt(CreateAuthAttemptConfig createAuthAttempt) {
    this.createAuthAttempt = createAuthAttempt;
  }

  public WaitAuthAttemptConfig getWaitAuthAttempt() {
    return waitAuthAttempt;
  }

  public void setWaitAuthAttempt(WaitAuthAttemptConfig waitAuthAttempt) {
    this.waitAuthAttempt = waitAuthAttempt;
  }
}
