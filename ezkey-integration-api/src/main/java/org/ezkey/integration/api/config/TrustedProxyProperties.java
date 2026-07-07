/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 *
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: TrustedProxyProperties
 *
 * Description: Configuration properties for trusted proxy CIDR list used for client IP resolution.
 */

package org.ezkey.integration.api.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for trusted proxy CIDR list.
 *
 * <p>When the application is behind a reverse proxy, client IP extraction for audit uses headers
 * (X-Forwarded-For, X-Real-IP, CF-Connecting-IP). These headers are only trusted when the direct
 * connection comes from an IP in this list.
 *
 * <p><b>Configuration Prefix:</b> ezkey.trusted-proxies
 *
 * @author Ezkey contributors
 * @since 2025
 */
@ConfigurationProperties(prefix = "ezkey.trusted-proxies")
public class TrustedProxyProperties {

  /**
   * When {@code true}, startup fails if {@link #cidrs} is empty or contains invalid entries
   * (SEC-011). Default {@code false} preserves local and direct-access deployments.
   */
  private boolean required = false;

  private List<String> cidrs = new ArrayList<>();

  public boolean isRequired() {
    return required;
  }

  public void setRequired(boolean required) {
    this.required = required;
  }

  public List<String> getCidrs() {
    return cidrs == null ? Collections.emptyList() : Collections.unmodifiableList(cidrs);
  }

  public void setCidrs(List<String> cidrs) {
    this.cidrs = cidrs == null ? new ArrayList<>() : new ArrayList<>(cidrs);
  }
}
