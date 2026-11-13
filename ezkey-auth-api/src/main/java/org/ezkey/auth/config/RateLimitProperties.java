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
   * devices can check for pending authentication requests. With {@code enrollment-id}, {@link
   * #requests} applies only after proof; {@link EndpointConfig#getFailIpRequests()} is the unproven
   * IP ceiling (#1124).
   */
  private EndpointConfig pending = defaultPendingConfig();

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

  /**
   * Process-wide (no key) budgets on client-IP-keyed device surfaces. Per-instance, not distributed
   * — N replicas imply N times this headroom (ADR-0010).
   */
  private BackstopConfig backstop = new BackstopConfig();

  private static EndpointConfig defaultPendingConfig() {
    EndpointConfig config = new EndpointConfig();
    config.setRequests(10);
    config.setWindowMinutes(1);
    config.setKeyStrategy("client-ip");
    // Unproven pending flood ceiling. Higher than respond: one /64 may be a household, and proven
    // polls refund this token (#1124, #918).
    config.setFailIpRequests(60);
    config.setFailIpWindowMinutes(1);
    return config;
  }

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
     *
     * <p>For respond + {@code auth-attempt-id}: the filter applies the {@link #failIpRequests} /
     * {@link #failIpWindowMinutes} IP ceiling on the way in. That token is refunded after a valid
     * device signature, so proven calls do not fill the ceiling (#918). The per-{@code
     * authAttemptId} budget ({@link #requests} / {@link #windowMinutes}) is consumed only after
     * that signature (#912 C1). The attempt id is never taken from an unproven body.
     *
     * <p>For pending + {@code enrollment-id}: the filter applies the same IP ceiling and does not
     * read {@code enrollmentId} from the body. {@link #requests} / {@link #windowMinutes} are
     * consumed only after a valid enrollment proof token and device signature, keyed by the
     * enrollment located from the proof token (#1124).
     */
    private String keyStrategy = "client-ip";

    /**
     * IP ceiling for pending ({@code enrollment-id}) and respond ({@code auth-attempt-id}). The
     * filter consumes one token per request; a proven call refunds it. Default: 30 (respond).
     * Pending's own default is 60.
     */
    private int failIpRequests = 30;

    /** IP-ceiling window in minutes for pending and respond. Default: 1. */
    private int failIpWindowMinutes = 1;

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

    public int getFailIpRequests() {
      return failIpRequests;
    }

    public void setFailIpRequests(int failIpRequests) {
      this.failIpRequests = failIpRequests;
    }

    public int getFailIpWindowMinutes() {
      return failIpWindowMinutes;
    }

    public void setFailIpWindowMinutes(int failIpWindowMinutes) {
      this.failIpWindowMinutes = failIpWindowMinutes;
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

  public BackstopConfig getBackstop() {
    return backstop;
  }

  public void setBackstop(BackstopConfig backstop) {
    this.backstop = backstop;
  }

  /**
   * Unkeyed per-process caps for IP-keyed enrollment surfaces ({@code verify}, {@code bind}, {@code
   * instance-info}).
   */
  public static class BackstopConfig {

    /** When false, only the keyed buckets apply. Default true. */
    private boolean enabled = true;

    /** Process-wide verify budget. Default: 100 / 1 minute. */
    private EndpointConfig verify = defaultBackstop(100, 1);

    /** Process-wide bind + enrolled instance-info budget. Default: 60 / 1 minute. */
    private EndpointConfig bind = defaultBackstop(60, 1);

    private static EndpointConfig defaultBackstop(int requests, int windowMinutes) {
      EndpointConfig config = new EndpointConfig();
      config.setRequests(requests);
      config.setWindowMinutes(windowMinutes);
      return config;
    }

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
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
  }
}
