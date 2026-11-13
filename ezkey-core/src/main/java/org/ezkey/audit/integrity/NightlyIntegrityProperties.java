/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: NightlyIntegrityProperties
 * Description: Configuration for the nightly retroactive integrity validation batch.
 */

package org.ezkey.audit.integrity;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for the nightly retroactive integrity validation batch.
 *
 * <p><b>Configuration prefix:</b> {@code ezkey.audit.integrity.nightly}
 *
 * @since 2026
 */
@Configuration
@ConfigurationProperties(prefix = "ezkey.audit.integrity.nightly")
public class NightlyIntegrityProperties {

  /**
   * Enable or disable the nightly job <em>and</em> operator {@code POST
   * …/integrity-validation/run}. When false, the POST fail-closes with HTTP 409.
   */
  private boolean enabled = true;

  /** Cron expression for the nightly job (default: 02:00 UTC daily). */
  private String cron = "0 0 2 * * ?";

  /** Retroactive validation window length in hours (default: 24). */
  private int windowHours = 24;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getCron() {
    return cron;
  }

  public void setCron(String cron) {
    this.cron = cron;
  }

  public int getWindowHours() {
    return windowHours;
  }

  public void setWindowHours(int windowHours) {
    this.windowHours = windowHours;
  }
}
