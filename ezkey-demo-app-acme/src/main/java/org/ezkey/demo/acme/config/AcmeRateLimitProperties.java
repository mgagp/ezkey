/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: AcmeRateLimitProperties
 * Description: Configurable rate limiting thresholds for the ACME demo application.
 */

package org.ezkey.demo.acme.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configurable rate limiting thresholds for the ACME demo application.
 *
 * @author Ezkey contributors
 * @since 2025
 */
@ConfigurationProperties(prefix = "ezkey.rate-limit")
public class AcmeRateLimitProperties {

  private boolean enabled = true;

  /**
   * Per-slot+IP (or IP-only for self-service / invalid {@code /t}) ceiling. Default 20 / 5 minutes.
   */
  private EndpointConfig login = new EndpointConfig(20, 5);

  /**
   * Wider per-IP bound consumed on every {@code POST /login} in slot mode, in addition to the
   * slot+IP bucket. Caps aggregate attempts across many slots from one IP. Default 60 / 5 minutes.
   */
  private EndpointConfig loginIpBound = new EndpointConfig(60, 5);

  private EndpointConfig applyApiKey = new EndpointConfig(5, 10);

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public EndpointConfig getLogin() {
    return login;
  }

  public void setLogin(EndpointConfig login) {
    this.login = login;
  }

  public EndpointConfig getLoginIpBound() {
    return loginIpBound;
  }

  public void setLoginIpBound(EndpointConfig loginIpBound) {
    this.loginIpBound = loginIpBound;
  }

  public EndpointConfig getApplyApiKey() {
    return applyApiKey;
  }

  public void setApplyApiKey(EndpointConfig applyApiKey) {
    this.applyApiKey = applyApiKey;
  }

  /** Endpoint-specific token bucket configuration. */
  public static class EndpointConfig {

    private int requests;
    private int windowMinutes;

    public EndpointConfig() {}

    public EndpointConfig(int requests, int windowMinutes) {
      this.requests = requests;
      this.windowMinutes = windowMinutes;
    }

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
}
