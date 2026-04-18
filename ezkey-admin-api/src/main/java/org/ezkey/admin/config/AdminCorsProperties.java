/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 *
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: AdminCorsProperties
 *
 * Description: CORS settings for browser clients when the Admin UI is served from a different
 * origin than the Admin API (e.g. Cloudflare Pages).
 */

package org.ezkey.admin.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CORS configuration for the Admin API.
 *
 * <p>When {@code allowed-origins} is empty, CORS is not applied (same behavior as before
 * cross-origin support). Set explicit HTTPS origins for split UI/API deployments.
 *
 * <p><b>Configuration prefix:</b> {@code ezkey.admin.cors}
 *
 * @author Ezkey contributors
 * @since 2025
 */
@ConfigurationProperties(prefix = "ezkey.admin.cors")
public class AdminCorsProperties {

  private List<String> allowedOrigins = new ArrayList<>();

  private List<String> allowedMethods =
      new ArrayList<>(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

  private List<String> allowedHeaders =
      new ArrayList<>(
          Arrays.asList(
              "Authorization",
              "Content-Type",
              "Accept",
              "Origin",
              "Access-Control-Request-Method",
              "Access-Control-Request-Headers"));

  private boolean allowCredentials = false;

  /**
   * Allowed browser origins (scheme + host + port). Empty means CORS is disabled.
   *
   * @return immutable view of configured origins
   */
  public List<String> getAllowedOrigins() {
    return allowedOrigins == null
        ? Collections.emptyList()
        : Collections.unmodifiableList(allowedOrigins);
  }

  public void setAllowedOrigins(List<String> allowedOrigins) {
    this.allowedOrigins =
        allowedOrigins == null ? new ArrayList<>() : new ArrayList<>(allowedOrigins);
  }

  /**
   * HTTP methods permitted for CORS requests.
   *
   * @return immutable view of configured methods
   */
  public List<String> getAllowedMethods() {
    return allowedMethods == null
        ? Collections.emptyList()
        : Collections.unmodifiableList(allowedMethods);
  }

  public void setAllowedMethods(List<String> allowedMethods) {
    this.allowedMethods =
        allowedMethods == null ? new ArrayList<>() : new ArrayList<>(allowedMethods);
  }

  /**
   * Request headers the browser may send on cross-origin requests (preflight and actual).
   *
   * @return immutable view of configured headers
   */
  public List<String> getAllowedHeaders() {
    return allowedHeaders == null
        ? Collections.emptyList()
        : Collections.unmodifiableList(allowedHeaders);
  }

  public void setAllowedHeaders(List<String> allowedHeaders) {
    this.allowedHeaders =
        allowedHeaders == null ? new ArrayList<>() : new ArrayList<>(allowedHeaders);
  }

  public boolean isAllowCredentials() {
    return allowCredentials;
  }

  public void setAllowCredentials(boolean allowCredentials) {
    this.allowCredentials = allowCredentials;
  }
}
