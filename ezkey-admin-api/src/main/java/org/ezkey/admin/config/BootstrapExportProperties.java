/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: BootstrapExportProperties
 * Description: Configuration properties for bootstrap credentials file export.
 */

package org.ezkey.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for bootstrap credentials file export.
 *
 * <p>Controls whether bootstrap credentials are exported to a JSON file. Docker profiles write
 * under {@code /var/lib/ezkey/bootstrap/}; native {@code mvn spring-boot:run} and the {@code
 * windows} profile default to a user-writable path under {@code ${user.home}/.ezkey/bootstrap/}.
 *
 * <p><b>Configuration Example:</b>
 *
 * <pre>
 * ezkey.admin.bootstrap.export.enabled=true
 * ezkey.admin.bootstrap.export.path=${user.home}/.ezkey/bootstrap/bootstrap-credentials.json
 * ezkey.admin.bootstrap.export.allow-non-posix-permissions=false
 * </pre>
 *
 * <p><b>Security Note:</b> Prefer owner-only mode ({@code 0600}). Non-POSIX fallback is opt-in via
 * {@code allow-non-posix-permissions} (windows profile only) and logs a single WARN.
 *
 * @since 2025
 */
@Component
@ConfigurationProperties(prefix = "ezkey.admin.bootstrap.export")
public class BootstrapExportProperties {

  /**
   * Enable or disable bootstrap credentials file export.
   *
   * <p>When enabled, bootstrap credentials are written to a JSON file after global admin enrollment
   * is created. Plaintext recovery codes are also persisted when present even if this flag is
   * false, because they are no longer emitted to logs.
   *
   * <p><b>Default:</b> false
   */
  private boolean enabled = false;

  /**
   * Path to the bootstrap credentials file.
   *
   * <p><b>Default (non-Docker):</b> {@code
   * ${user.home}/.ezkey/bootstrap/bootstrap-credentials.json} — never {@code /var/lib/ezkey}
   * outside Docker. Docker profiles override to the volume path.
   */
  private String path =
      System.getProperty("user.home", ".") + "/.ezkey/bootstrap/bootstrap-credentials.json";

  /**
   * When {@code true}, allow writing the credentials file if the filesystem does not support POSIX
   * owner-only permissions (log one WARN; path and exception type only). Default {@code false}:
   * refuse the write (fail-closed when recovery codes must be persisted). Set {@code true} only in
   * the {@code windows} profile.
   */
  private boolean allowNonPosixPermissions = false;

  /**
   * Gets the export enabled status.
   *
   * @return true if export is enabled
   */
  public boolean isEnabled() {
    return enabled;
  }

  /**
   * Sets the export enabled status.
   *
   * @param enabled true to enable export
   */
  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  /**
   * Gets the export file path.
   *
   * @return the file path
   */
  public String getPath() {
    return path;
  }

  /**
   * Sets the export file path.
   *
   * @param path the file path
   */
  public void setPath(String path) {
    this.path = path;
  }

  /**
   * Whether non-POSIX filesystems may receive the credentials file without {@code 0600}.
   *
   * @return true when the windows-dev fallback is enabled
   */
  public boolean isAllowNonPosixPermissions() {
    return allowNonPosixPermissions;
  }

  /**
   * Sets whether non-POSIX writes are allowed.
   *
   * @param allowNonPosixPermissions true only for the windows profile
   */
  public void setAllowNonPosixPermissions(boolean allowNonPosixPermissions) {
    this.allowNonPosixPermissions = allowNonPosixPermissions;
  }
}
