/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: AuditChainProperties
 * Description: Configuration properties for periodic audit chain checkpoints.
 */

package org.ezkey.audit.integrity;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for periodic audit log chain checkpoints.
 *
 * <p>Controls the batch job that creates chain checkpoints every N minutes, linking consecutive
 * time windows for completeness proof (detects row insertion, deletion, reordering).
 *
 * <p><b>Configuration prefix:</b> {@code ezkey.audit.chain}
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2026
 */
@Configuration
@ConfigurationProperties(prefix = "ezkey.audit.chain")
public class AuditChainProperties {

  /** Enable or disable periodic chain checkpoints. */
  private boolean enabled = true;

  /** Time window size in minutes for each checkpoint (default: 5). */
  private int windowMinutes = 5;

  /** Lookback window in minutes for catch-up after restarts (default: 60). */
  private int lookbackMinutes = 60;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public int getWindowMinutes() {
    return windowMinutes;
  }

  public void setWindowMinutes(int windowMinutes) {
    this.windowMinutes = windowMinutes;
  }

  public int getLookbackMinutes() {
    return lookbackMinutes;
  }

  public void setLookbackMinutes(int lookbackMinutes) {
    this.lookbackMinutes = lookbackMinutes;
  }
}
