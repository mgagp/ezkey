/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Config: DemoDeviceProperties
 * Description: Demo-device allowlist and related lab-only settings (CodeQL #9/#10, 2026-10-13).
 */

package org.ezkey.demo.device.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Type-safe configuration for the demo device lab simulator.
 *
 * @since 2026
 */
@ConfigurationProperties(prefix = "ezkey.demo-device")
public class DemoDeviceProperties {

  /**
   * Hostnames allowed as Auth API targets for QR {@code authUrl} and bind overrides. Empty means
   * derive the default allowlist from {@code ezkey.auth.api.url} plus {@code localhost} and {@code
   * 127.0.0.1}.
   */
  private List<String> allowedAuthApiHosts = new ArrayList<>();

  /**
   * Returns the configured host allowlist (may be empty when defaults should apply).
   *
   * @return mutable list bound by Spring (never {@code null} after construction)
   */
  public List<String> getAllowedAuthApiHosts() {
    return allowedAuthApiHosts;
  }

  /**
   * Sets the host allowlist from configuration.
   *
   * @param allowedAuthApiHosts hostnames only (no scheme or port)
   */
  public void setAllowedAuthApiHosts(List<String> allowedAuthApiHosts) {
    this.allowedAuthApiHosts =
        allowedAuthApiHosts != null ? new ArrayList<>(allowedAuthApiHosts) : new ArrayList<>();
  }
}
