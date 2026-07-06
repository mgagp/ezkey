/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: AuditHmacProperties
 * Description: Configuration properties for audit log HMAC integrity signing.
 */

package org.ezkey.audit.integrity;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for audit log HMAC integrity signing.
 *
 * <p>Controls the per-entry HMAC-SHA256 signing of audit log entries, providing tamper-evidence for
 * SOC 2 compliance in self-hosted deployments. The HMAC key is intentionally separate from the Tink
 * encryption master key (separation of concerns: integrity vs. confidentiality).
 *
 * <p><b>Configuration prefix:</b> {@code ezkey.audit.integrity}
 *
 * <p><b>Key properties:</b>
 *
 * <ul>
 *   <li>{@code enabled} - Enable/disable HMAC signing (default: true)
 *   <li>{@code hmac-key-file} - Path to the HMAC key file (Base64-encoded 256-bit key)
 *   <li>{@code instance-id} - Instance identifier for HA tracking (from EZKEY_INSTANCE_ID env var)
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2026
 */
@Configuration
@ConfigurationProperties(prefix = "ezkey.audit.integrity")
public class AuditHmacProperties {

  /**
   * Enable or disable HMAC signing of audit log entries.
   *
   * <p>When disabled, audit entries are saved without HMAC signatures. This may be appropriate for
   * development environments but should be enabled in production for SOC 2 compliance.
   */
  private boolean enabled = true;

  /**
   * When true, the application fails to start if integrity signing is enabled in configuration but
   * the HMAC key cannot be loaded (SEC-008). Default false preserves backward-compatible degraded
   * mode.
   */
  private boolean required = false;

  /**
   * Path to the HMAC key file.
   *
   * <p>The file must contain a Base64-encoded 256-bit (32-byte) secret key used for HMAC-SHA256
   * computation. This key is separate from the Tink encryption master key by design (SOC 2
   * separation of duties).
   *
   * <p>Typical paths:
   *
   * <ul>
   *   <li>Docker: {@code /etc/ezkey/secrets/audit-hmac.key}
   *   <li>Windows: {@code C:\ProgramData\ezkey\secrets\audit-hmac.key}
   * </ul>
   */
  private String hmacKeyFile;

  /**
   * Application instance identifier for HA deployments.
   *
   * <p>Populated from the {@code EZKEY_INSTANCE_ID} environment variable. Used to track which
   * application instance created each audit log entry, enabling forensic analysis in multi-instance
   * deployments.
   *
   * <p>Examples: {@code admin-api-1}, {@code auth-api-2}. NULL for single-instance deployments.
   */
  private String instanceId;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public boolean isRequired() {
    return required;
  }

  public void setRequired(boolean required) {
    this.required = required;
  }

  public String getHmacKeyFile() {
    return hmacKeyFile;
  }

  public void setHmacKeyFile(String hmacKeyFile) {
    this.hmacKeyFile = hmacKeyFile;
  }

  public String getInstanceId() {
    return instanceId;
  }

  public void setInstanceId(String instanceId) {
    this.instanceId = instanceId;
  }
}
