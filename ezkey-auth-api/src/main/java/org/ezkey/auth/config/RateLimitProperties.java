/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 *
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: RateLimitProperties
 *
 * Description: Externalized configuration properties for rate limiting functionality.
 */

package org.ezkey.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for rate limiting functionality.
 *
 * <p>This class provides externalized configuration for rate limiting settings, allowing
 * administrators to adjust rate limits without code changes. All settings can be configured via
 * application.properties or environment variables.
 *
 * <p><b>Configuration Prefix:</b> ezkey.rate-limit
 *
 * <p><b>Example Configuration:</b>
 *
 * <pre>
 * ezkey.rate-limit.enabled=true
 * ezkey.rate-limit.pending.requests=10
 * ezkey.rate-limit.pending.window-minutes=1
 * ezkey.rate-limit.verify.requests=5
 * ezkey.rate-limit.verify.window-minutes=5
 * ezkey.rate-limit.respond.requests=1
 * ezkey.rate-limit.respond.window-minutes=5
 * </pre>
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@ConfigurationProperties(prefix = "ezkey.rate-limit")
public class RateLimitProperties {

  /**
   * Global rate limiting enablement flag. When disabled, no rate limiting is applied to any
   * endpoints.
   */
  private boolean enabled = false;

  /**
   * Rate limiting configuration for pending authentication endpoint. Controls how often mobile
   * devices can check for pending authentication requests.
   */
  private EndpointConfig pending = new EndpointConfig();

  /**
   * Rate limiting configuration for enrollment verification endpoint. Controls how often devices
   * can attempt enrollment verification.
   */
  private EndpointConfig verify = new EndpointConfig();

  /**
   * Rate limiting configuration for enrollment binding endpoint. Controls how often devices can
   * attempt enrollment binding with proof token.
   */
  private EndpointConfig bind = new EndpointConfig();

  /**
   * Rate limiting configuration for auth attempt respond endpoint. Controls how many respond
   * requests are allowed per auth attempt (per authAttemptId) within the time window. Default: 1
   * request per 5 minutes, key strategy auth-attempt-id.
   */
  private EndpointConfig respond = defaultRespondConfig();

  private static EndpointConfig defaultRespondConfig() {
    EndpointConfig config = new EndpointConfig();
    config.setRequests(1);
    config.setWindowMinutes(5);
    config.setKeyStrategy("auth-attempt-id");
    return config;
  }

  /** Configuration for a specific endpoint's rate limiting behavior. */
  public static class EndpointConfig {

    /** Maximum number of requests allowed within the time window. Default: 10 requests */
    private int requests = 10;

    /** Time window in minutes for the rate limit. Default: 1 minute */
    private int windowMinutes = 1;

    /**
     * Strategy for identifying clients for rate limiting. Options: "client-ip", "enrollment-id", or
     * "auth-attempt-id" (respond endpoint only). Default: "client-ip"
     */
    private String keyStrategy = "client-ip";

    // Getters and Setters for EndpointConfig
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
  }

  // Getters and Setters for RateLimitProperties
  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public EndpointConfig getPending() {
    return pending;
  }

  public void setPending(EndpointConfig pending) {
    this.pending = pending;
  }

  public EndpointConfig getVerify() {
    return verify;
  }

  public void setVerify(EndpointConfig verify) {
    this.verify = verify;
  }

  public EndpointConfig getBind() {
    return bind;
  }

  public void setBind(EndpointConfig bind) {
    this.bind = bind;
  }

  public EndpointConfig getRespond() {
    return respond;
  }

  public void setRespond(EndpointConfig respond) {
    this.respond = respond;
  }
}
