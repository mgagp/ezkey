/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: AuditChainHeartbeatProperties
 * Description: Fail-closed heartbeat supervision for peripheral APIs relative to Admin checkpoints.
 */

package org.ezkey.audit.integrity;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Heartbeat supervision configuration for Auth API and Integration API processes.
 *
 * <p>The Admin API advances periodic chain checkpoints; peripherals compare wall-clock time against
 * the latest checkpoint {@code window_end} plus grace windows and enter fail-closed mode when the
 * checkpoint scheduler appears stalled beyond an acceptable threshold.
 *
 * <p><b>Configuration prefix:</b> {@code ezkey.audit.chain.heartbeat}
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2026
 */
@Configuration
@ConfigurationProperties(prefix = "ezkey.audit.chain.heartbeat")
public class AuditChainHeartbeatProperties {

  /** When false, heartbeat guards are not registered (development convenience). */
  private boolean enabled = true;

  /** When false, heartbeat evaluation runs but peripheral servlet filters never block requests. */
  private boolean required = true;

  /**
   * Number of checkpoint window lengths after {@code latest.window_end} used for the outer phase
   * boundary before subtracting {@link #stopBeforeNextWindow}.
   */
  private int graceWindows = 2;

  /** Subtracted from {@code latest.window_end + graceWindows * windowMinutes} for fail-closed. */
  private Duration stopBeforeNextWindow = Duration.ofMinutes(1);

  /** Cache TTL for checkpoint lookups inside heartbeat evaluation (reduces DB load per request). */
  private Duration cacheTtl = Duration.ofSeconds(5);

  /** Grace period after process start when no checkpoints exist yet (bootstrap / empty DB). */
  private Duration bootstrapGrace = Duration.ofMinutes(10);

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

  public int getGraceWindows() {
    return graceWindows;
  }

  public void setGraceWindows(int graceWindows) {
    this.graceWindows = graceWindows;
  }

  public Duration getStopBeforeNextWindow() {
    return stopBeforeNextWindow;
  }

  public void setStopBeforeNextWindow(Duration stopBeforeNextWindow) {
    this.stopBeforeNextWindow = stopBeforeNextWindow;
  }

  public Duration getCacheTtl() {
    return cacheTtl;
  }

  public void setCacheTtl(Duration cacheTtl) {
    this.cacheTtl = cacheTtl;
  }

  public Duration getBootstrapGrace() {
    return bootstrapGrace;
  }

  public void setBootstrapGrace(Duration bootstrapGrace) {
    this.bootstrapGrace = bootstrapGrace;
  }
}
