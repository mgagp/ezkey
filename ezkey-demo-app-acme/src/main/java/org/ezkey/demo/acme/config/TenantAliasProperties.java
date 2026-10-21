/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: TenantAliasProperties
 * Description: External hot-reloaded tenant-alias mapping file for the Acme demo.
 */

package org.ezkey.demo.acme.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Path and reload throttle for the external tenant-alias mapping file.
 *
 * <p>The file holds only {@code alias → integration-key + secret-key + label} lines for the
 * community demo path ({@code /t/{alias}}). It is mounted on the config volume, outside the image
 * and the git repo. Reload is request-driven (mtime check), never periodic polling.
 *
 * @author Ezkey contributors
 * @since 2026
 */
@ConfigurationProperties(prefix = "ezkey.tenant-aliases")
public class TenantAliasProperties {

  /**
   * Absolute or relative path to the mapping file. Empty disables external-file loading (base {@code
   * ezkey.access-codes} in application.properties still apply).
   */
  private String file = "/app/config/tenant-aliases.properties";

  /**
   * Minimum seconds between mtime checks on URL access. {@code 0} checks mtime on every access
   * (still rereads only when mtime changed).
   */
  private int reloadThrottleSeconds = 5;

  public String getFile() {
    return file;
  }

  public void setFile(String file) {
    this.file = file;
  }

  public int getReloadThrottleSeconds() {
    return reloadThrottleSeconds;
  }

  public void setReloadThrottleSeconds(int reloadThrottleSeconds) {
    this.reloadThrottleSeconds = Math.max(0, reloadThrottleSeconds);
  }
}
