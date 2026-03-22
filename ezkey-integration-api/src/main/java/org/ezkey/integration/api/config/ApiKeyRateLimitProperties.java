/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: ApiKeyRateLimitProperties
 * Description: Externalized rate limiting configuration for Integration API key operations.
 */

package org.ezkey.integration.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Integration API key rate limiting.
 *
 * <p><b>Configuration prefix:</b> {@code ezkey.api-key.rate-limit}
 *
 * <p>Example:
 *
 * <pre>
 * ezkey.api-key.rate-limit.enabled=true
 * ezkey.api-key.rate-limit.create-auth-attempt.requests=100
 * ezkey.api-key.rate-limit.create-auth-attempt.window-minutes=1
 * ezkey.api-key.rate-limit.wait-auth-attempt.requests=200
 * ezkey.api-key.rate-limit.wait-auth-attempt.window-minutes=1
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
   * Global enablement flag. When {@code false} a no-op implementation is used and no requests are
   * blocked.
   */
  private boolean enabled = true;

  private CreateAuthAttemptConfig createAuthAttempt = new CreateAuthAttemptConfig();
  private WaitAuthAttemptConfig waitAuthAttempt = new WaitAuthAttemptConfig();

  /** Rate limit configuration for the create-auth-attempt operation. */
  public static class CreateAuthAttemptConfig {

    /** Maximum requests per window. Default: 100. */
    private int requests = 100;

    /** Sliding window size in minutes. Default: 1. */
    private int windowMinutes = 1;

    /**
     * Returns the maximum number of create auth attempt requests allowed per window.
     *
     * @return request limit
     */
    public int getRequests() {
      return requests;
    }

    /**
     * Sets the maximum number of create auth attempt requests per window.
     *
     * @param requests request limit to set
     */
    public void setRequests(int requests) {
      this.requests = requests;
    }

    /**
     * Returns the sliding window size in minutes.
     *
     * @return window size in minutes
     */
    public int getWindowMinutes() {
      return windowMinutes;
    }

    /**
     * Sets the sliding window size in minutes.
     *
     * @param windowMinutes window size to set
     */
    public void setWindowMinutes(int windowMinutes) {
      this.windowMinutes = windowMinutes;
    }
  }

  /** Rate limit configuration for the wait/cancel-auth-attempt operations. */
  public static class WaitAuthAttemptConfig {

    /** Maximum requests per window. Default: 200. */
    private int requests = 200;

    /** Sliding window size in minutes. Default: 1. */
    private int windowMinutes = 1;

    /**
     * Returns the maximum number of wait auth attempt requests allowed per window.
     *
     * @return request limit
     */
    public int getRequests() {
      return requests;
    }

    /**
     * Sets the maximum number of wait auth attempt requests per window.
     *
     * @param requests request limit to set
     */
    public void setRequests(int requests) {
      this.requests = requests;
    }

    /**
     * Returns the sliding window size in minutes.
     *
     * @return window size in minutes
     */
    public int getWindowMinutes() {
      return windowMinutes;
    }

    /**
     * Sets the sliding window size in minutes.
     *
     * @param windowMinutes window size to set
     */
    public void setWindowMinutes(int windowMinutes) {
      this.windowMinutes = windowMinutes;
    }
  }

  /**
   * Returns whether rate limiting is globally enabled.
   *
   * @return {@code true} if enabled
   */
  public boolean isEnabled() {
    return enabled;
  }

  /**
   * Sets the global rate limiting enablement flag.
   *
   * @param enabled {@code true} to enable
   */
  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  /**
   * Returns the create-auth-attempt rate limit configuration.
   *
   * @return create auth attempt config
   */
  public CreateAuthAttemptConfig getCreateAuthAttempt() {
    return createAuthAttempt;
  }

  /**
   * Sets the create-auth-attempt rate limit configuration.
   *
   * @param createAuthAttempt config to set
   */
  public void setCreateAuthAttempt(CreateAuthAttemptConfig createAuthAttempt) {
    this.createAuthAttempt = createAuthAttempt;
  }

  /**
   * Returns the wait-auth-attempt rate limit configuration.
   *
   * @return wait auth attempt config
   */
  public WaitAuthAttemptConfig getWaitAuthAttempt() {
    return waitAuthAttempt;
  }

  /**
   * Sets the wait-auth-attempt rate limit configuration.
   *
   * @param waitAuthAttempt config to set
   */
  public void setWaitAuthAttempt(WaitAuthAttemptConfig waitAuthAttempt) {
    this.waitAuthAttempt = waitAuthAttempt;
  }
}
