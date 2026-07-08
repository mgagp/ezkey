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

package org.ezkey.auth.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for trusted proxy CIDR list.
 *
 * <p>When the application is behind a reverse proxy (e.g. Nginx, Caddy, Cloudflare), client IP
 * extraction for rate limiting and audit uses headers (X-Forwarded-For, X-Real-IP,
 * CF-Connecting-IP). These headers are only trusted when the direct connection to the app comes
 * from an IP in this list. This prevents clients from spoofing the header to bypass rate limits.
 *
 * <p><b>Configuration Prefix:</b> ezkey.trusted-proxies
 *
 * <p><b>Example (application.properties):</b>
 *
 * <pre>
 * ezkey.trusted-proxies[0]=10.0.0.0/8
 * ezkey.trusted-proxies[1]=172.16.0.0/12
 * </pre>
 *
 * <p><b>Example (YAML):</b>
 *
 * <pre>
 * ezkey:
 *   trusted-proxies:
 *     - 10.0.0.0/8
 *     - 172.16.0.0/12
 * </pre>
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
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

  /**
   * List of CIDR blocks or single IP addresses. Connections from these IPs are considered trusted
   * proxies; only then are X-Forwarded-For / X-Real-IP / CF-Connecting-IP used for client IP. Empty
   * or unset means only the direct remote address is used (no header trust).
   */
  private List<String> cidrs = new ArrayList<>();

  public boolean isRequired() {
    return required;
  }

  public void setRequired(boolean required) {
    this.required = required;
  }

  /** Returns the list of trusted proxy CIDRs (read-only). Never null. */
  public List<String> getCidrs() {
    return cidrs == null ? Collections.emptyList() : Collections.unmodifiableList(cidrs);
  }

  /**
   * Sets the list of trusted proxy CIDRs. Called by Spring during property binding.
   *
   * @param cidrs list of CIDR or single-IP strings; null is treated as empty
   */
  public void setCidrs(List<String> cidrs) {
    this.cidrs = cidrs == null ? new ArrayList<>() : new ArrayList<>(cidrs);
  }
}
