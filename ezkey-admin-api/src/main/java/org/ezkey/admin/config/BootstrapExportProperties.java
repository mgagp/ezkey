/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: BootstrapExportProperties
 * Description: Configuration properties for bootstrap credentials file export (Docker-only).
 */

package org.ezkey.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for bootstrap credentials file export.
 *
 * <p>These properties control whether bootstrap credentials are exported to a file for Docker
 * automation. This feature is intended for Docker/demo environments only and should be disabled in
 * production.
 *
 * <p><b>Configuration Example:</b>
 *
 * <pre>
 * ezkey.admin.bootstrap.export.enabled=true
 * ezkey.admin.bootstrap.export.path=/var/lib/ezkey/bootstrap/bootstrap-credentials.json
 * </pre>
 *
 * <p><b>Security Note:</b> This feature exports enrollment credentials (excluding recovery codes)
 * to a file. It should only be enabled in Docker/demo profiles, never in production.
 *
 * @since 2025
 */
@Component
@ConfigurationProperties(prefix = "ezkey.admin.bootstrap.export")
public class BootstrapExportProperties {

  /**
   * Enable or disable bootstrap credentials file export.
   *
   * <p>When enabled, bootstrap credentials (enrollmentId, enrollmentProofToken,
   * enrollmentChallengeCode, username) are written to a JSON file after global admin enrollment is
   * created. Recovery codes are NOT exported (logs only).
   *
   * <p><b>Default:</b> false (disabled by default for security)
   *
   * <p><b>Docker:</b> Enable via docker profile properties
   */
  private boolean enabled = false;

  /**
   * Path to the bootstrap credentials file.
   *
   * <p>This file will be created in a Docker volume mount. The directory must exist and be
   * writable.
   *
   * <p><b>Default:</b> /var/lib/ezkey/bootstrap/bootstrap-credentials.json
   */
  private String path = "/var/lib/ezkey/bootstrap/bootstrap-credentials.json";

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
}
