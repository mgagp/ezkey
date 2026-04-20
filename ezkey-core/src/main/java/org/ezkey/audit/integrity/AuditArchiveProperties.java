/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: AuditArchiveProperties
 * Description: Policy properties for automated audit archive lifecycle progression.
 */

package org.ezkey.audit.integrity;

import java.time.Period;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Policy properties for automated audit archive lifecycle progression.
 *
 * <p>Phase 1 introduces these properties as the source of truth for future policy-driven SEAL, even
 * before the automation scheduler is implemented.
 *
 * @since 2026
 */
@Configuration
@ConfigurationProperties(prefix = "ezkey.audit.archive")
public class AuditArchiveProperties {

  private final AutoSeal autoSeal = new AutoSeal();
  private final Purge purge = new Purge();
  private boolean externalArchivalEnabled;
  private Period retentionPeriod = Period.ofMonths(12);
  private Period sealDelay = Period.ZERO;
  private Period purgeDelay = Period.ofDays(30);

  public AutoSeal getAutoSeal() {
    return autoSeal;
  }

  public Purge getPurge() {
    return purge;
  }

  public boolean isExternalArchivalEnabled() {
    return externalArchivalEnabled;
  }

  public void setExternalArchivalEnabled(boolean externalArchivalEnabled) {
    this.externalArchivalEnabled = externalArchivalEnabled;
  }

  public Period getRetentionPeriod() {
    return retentionPeriod;
  }

  public void setRetentionPeriod(Period retentionPeriod) {
    this.retentionPeriod = retentionPeriod;
  }

  public Period getSealDelay() {
    return sealDelay;
  }

  public void setSealDelay(Period sealDelay) {
    this.sealDelay = sealDelay;
  }

  public Period getPurgeDelay() {
    return purgeDelay;
  }

  public void setPurgeDelay(Period purgeDelay) {
    this.purgeDelay = purgeDelay;
  }

  /** Nested properties for automatic SEAL policy controls. */
  public static class AutoSeal {

    private boolean enabled = true;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }
  }

  /** Nested properties for lifecycle-driven purge execution. */
  public static class Purge {

    private boolean enabled = true;
    private String cron = "0 0 2 * * ?";

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
  }
}
