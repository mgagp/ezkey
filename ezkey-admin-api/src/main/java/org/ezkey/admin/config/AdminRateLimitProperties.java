/*
 * Ezkey - Open Source Cryptographic MFA Platform
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
 * ezkey.admin.rate-limit.login.requests=15
 * ezkey.admin.rate-limit.login.window-minutes=1
 * ezkey.admin.rate-limit.login.block-after-failures=10
 * ezkey.admin.rate-limit.login.block-duration-minutes=30
 * ezkey.admin.rate-limit.passwordless-wait.requests=10
 * ezkey.admin.rate-limit.passwordless-wait.window-minutes=1
 * </pre>
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
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

  /** Rate limiting configuration for login endpoint (per client IP). */
  private LoginConfig login = new LoginConfig();

  /**
   * Rate limiting for passwordless-wait / passwordless-cancel (separate from login; keyed per
   * client IP + authAttemptId, with an additional per-IP aggregate).
   */
  private PasswordlessWaitConfig passwordlessWait = new PasswordlessWaitConfig();

  /**
   * Process-wide (no key) budgets on admin login and passwordless-wait/cancel. Per-instance, not
   * distributed (ADR-0010).
   */
  private BackstopConfig backstop = new BackstopConfig();

  /** Configuration for login endpoint rate limiting behavior. */
  public static class LoginConfig {
    /**
     * Maximum number of login requests allowed within the time window. Default: 15 requests per 1
     * minute (#831 — room for Cancel + retry without sharing tokens with passwordless-wait).
     */
    private int requests = 15;

    /** Time window in minutes for the rate limit. Default: 1 minute. */
    private int windowMinutes = 1;

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

  /**
   * Configuration for passwordless-wait / passwordless-cancel rate limiting (separate bucket from
   * login).
   */
  public static class PasswordlessWaitConfig {
    /**
     * Maximum wait/cancel requests per {@code clientIp + authAttemptId} (or per IP when attempt id
     * is absent) within the window. Default: 10 per 1 minute.
     */
    private int requests = 10;

    /** Time window in minutes for the per-attempt (ip+id) rate limit. Default: 1 minute. */
    private int windowMinutes = 1;

    /**
     * Maximum wait/cancel requests per client IP across all attempt ids (stops rotating-id DoS).
     * Default: 20 per 1 minute.
     */
    private int ipRequests = 20;

    /** Window for the per-IP aggregate wait/cancel budget. Default: 1 minute. */
    private int ipWindowMinutes = 1;

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

    public int getIpRequests() {
      return ipRequests;
    }

    public void setIpRequests(int ipRequests) {
      this.ipRequests = ipRequests;
    }

    public int getIpWindowMinutes() {
      return ipWindowMinutes;
    }

    public void setIpWindowMinutes(int ipWindowMinutes) {
      this.ipWindowMinutes = ipWindowMinutes;
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

  public PasswordlessWaitConfig getPasswordlessWait() {
    return passwordlessWait;
  }

  public void setPasswordlessWait(PasswordlessWaitConfig passwordlessWait) {
    this.passwordlessWait = passwordlessWait;
  }

  public BackstopConfig getBackstop() {
    return backstop;
  }

  public void setBackstop(BackstopConfig backstop) {
    this.backstop = backstop;
  }

  /** Unkeyed per-process caps for IP-keyed admin auth surfaces. */
  public static class BackstopConfig {

    /** When false, only the keyed login/wait buckets apply. Default true. */
    private boolean enabled = true;

    /** Process-wide login budget. Default: 50 / 1 minute. */
    private LoginConfig login = defaultBackstopLogin();

    /** Process-wide wait/cancel budget. Default: 100 / 1 minute. */
    private PasswordlessWaitConfig passwordlessWait = defaultBackstopWait();

    private static LoginConfig defaultBackstopLogin() {
      LoginConfig config = new LoginConfig();
      config.setRequests(50);
      config.setWindowMinutes(1);
      return config;
    }

    private static PasswordlessWaitConfig defaultBackstopWait() {
      PasswordlessWaitConfig config = new PasswordlessWaitConfig();
      config.setRequests(100);
      config.setWindowMinutes(1);
      return config;
    }

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

    public PasswordlessWaitConfig getPasswordlessWait() {
      return passwordlessWait;
    }

    public void setPasswordlessWait(PasswordlessWaitConfig passwordlessWait) {
      this.passwordlessWait = passwordlessWait;
    }
  }
}
