/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: AdminApiKeyAuthAttemptsProperties
 * Description: Gates API-key M2M auth-attempt acceptance on Admin API.
 */

package org.ezkey.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Controls whether Admin API accepts API-key authentication for auth-attempt flows.
 *
 * <p>Default {@code false} (deny). Set {@code true} only for documented minimal installations that
 * run Admin + Auth without an Integration API binary. Canonical M2M surface is Integration API.
 *
 * <p><b>Configuration prefix:</b> {@code ezkey.admin.auth}
 *
 * @author Ezkey contributors
 * @since 2026
 */
@ConfigurationProperties(prefix = "ezkey.admin.auth")
public class AdminApiKeyAuthAttemptsProperties {

  /**
   * When {@code false} (default), requests authenticated with {@code ROLE_API_KEY} are rejected
   * with RFC 9457 {@code 403}. When {@code true}, Admin API accepts API-key auth-attempt traffic
   * (minimal install escape hatch).
   */
  private boolean apiKeyAuthAttemptsEnabled = false;

  /**
   * Returns whether API-key auth-attempt traffic is accepted on this Admin API instance.
   *
   * @return {@code true} when API-key M2M auth attempts are enabled
   */
  public boolean isApiKeyAuthAttemptsEnabled() {
    return apiKeyAuthAttemptsEnabled;
  }

  /**
   * Sets whether API-key auth-attempt traffic is accepted on this Admin API instance.
   *
   * @param apiKeyAuthAttemptsEnabled {@code true} to allow API-key M2M on Admin API
   */
  public void setApiKeyAuthAttemptsEnabled(boolean apiKeyAuthAttemptsEnabled) {
    this.apiKeyAuthAttemptsEnabled = apiKeyAuthAttemptsEnabled;
  }
}
