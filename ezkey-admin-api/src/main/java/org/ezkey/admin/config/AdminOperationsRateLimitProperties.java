/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: AdminOperationsRateLimitProperties
 * Description: Externalized configuration properties for admin operations rate limiting functionality.
 */

package org.ezkey.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for admin operations rate limiting functionality.
 *
 * <p>This class provides externalized configuration for rate limiting settings for
 * admin-authenticated operations, allowing administrators to adjust rate limits without code
 * changes. Rate limiting is applied to sensitive admin operations to prevent abuse and ensure
 * security.
 *
 * <p><b>Configuration Prefix:</b> ezkey.admin-operations.rate-limit
 *
 * <p><b>Example Configuration:</b>
 *
 * <pre>
 * ezkey.admin-operations.rate-limit.enabled=true
 * ezkey.admin-operations.rate-limit.api-key-create.requests=5
 * ezkey.admin-operations.rate-limit.api-key-create.window-minutes=15
 * ezkey.admin-operations.rate-limit.enrollment-reset.requests=3
 * ezkey.admin-operations.rate-limit.enrollment-reset.window-minutes=30
 * ezkey.admin-operations.rate-limit.api-key-revoke.requests=10
 * ezkey.admin-operations.rate-limit.api-key-revoke.window-minutes=15
 * ezkey.admin-operations.rate-limit.api-key-update.requests=20
 * ezkey.admin-operations.rate-limit.api-key-update.window-minutes=15
 * </pre>
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@ConfigurationProperties(prefix = "ezkey.admin-operations.rate-limit")
public class AdminOperationsRateLimitProperties {

  /**
   * Global rate limiting enablement flag. When disabled, no rate limiting is applied to any admin
   * operations.
   */
  private boolean enabled = true;

  /** Rate limiting configuration for API key creation operations. */
  private ApiKeyCreateConfig apiKeyCreate = new ApiKeyCreateConfig();

  /** Rate limiting configuration for enrollment reset operations. */
  private EnrollmentResetConfig enrollmentReset = new EnrollmentResetConfig();

  /** Rate limiting configuration for API key revocation operations. */
  private ApiKeyRevokeConfig apiKeyRevoke = new ApiKeyRevokeConfig();

  /** Rate limiting configuration for API key update operations. */
  private ApiKeyUpdateConfig apiKeyUpdate = new ApiKeyUpdateConfig();

  /** Configuration for API key creation rate limiting behavior. */
  public static class ApiKeyCreateConfig {
    /**
     * Maximum number of API key creation requests allowed within the time window. Default: 5
     * requests per 15 minutes.
     */
    private int requests = 5;

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

  /** Configuration for API key revocation rate limiting behavior. */
  public static class ApiKeyRevokeConfig {
    /**
     * Maximum number of API key revocation requests allowed within the time window. Default: 10
     * requests per 15 minutes.
     */
    private int requests = 10;

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

  /** Configuration for API key update rate limiting behavior. */
  public static class ApiKeyUpdateConfig {
    /**
     * Maximum number of API key update requests allowed within the time window. Default: 20
     * requests per 15 minutes.
     */
    private int requests = 20;

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

  /** Configuration for enrollment reset rate limiting behavior. */
  public static class EnrollmentResetConfig {
    /**
     * Maximum number of enrollment reset requests allowed within the time window. Default: 3
     * requests per 30 minutes.
     */
    private int requests = 3;

    /** Time window in minutes for the rate limit. Default: 30 minutes. */
    private int windowMinutes = 30;

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

  // Getters and Setters for AdminOperationsRateLimitProperties

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public ApiKeyCreateConfig getApiKeyCreate() {
    return apiKeyCreate;
  }

  public void setApiKeyCreate(ApiKeyCreateConfig apiKeyCreate) {
    this.apiKeyCreate = apiKeyCreate;
  }

  public EnrollmentResetConfig getEnrollmentReset() {
    return enrollmentReset;
  }

  public void setEnrollmentReset(EnrollmentResetConfig enrollmentReset) {
    this.enrollmentReset = enrollmentReset;
  }

  public ApiKeyRevokeConfig getApiKeyRevoke() {
    return apiKeyRevoke;
  }

  public void setApiKeyRevoke(ApiKeyRevokeConfig apiKeyRevoke) {
    this.apiKeyRevoke = apiKeyRevoke;
  }

  public ApiKeyUpdateConfig getApiKeyUpdate() {
    return apiKeyUpdate;
  }

  public void setApiKeyUpdate(ApiKeyUpdateConfig apiKeyUpdate) {
    this.apiKeyUpdate = apiKeyUpdate;
  }
}
