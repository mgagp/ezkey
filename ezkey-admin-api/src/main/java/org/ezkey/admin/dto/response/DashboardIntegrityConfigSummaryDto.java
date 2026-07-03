/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: DashboardIntegrityConfigSummaryDto
 * Description: Active integrity configuration summary for dashboard widgets.
 */

package org.ezkey.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Active integrity-related configuration surfaced on the dashboard (non-secret values only).
 *
 * @since 2026
 */
@Schema(description = "Active integrity configuration summary for dashboard widgets")
public class DashboardIntegrityConfigSummaryDto {

  private int chainLookbackMinutes;
  private int nightlyWindowHours;
  private boolean chainCheckpointsEnabled;
  private boolean nightlyValidationEnabled;

  public DashboardIntegrityConfigSummaryDto() {}

  public DashboardIntegrityConfigSummaryDto(
      int chainLookbackMinutes,
      int nightlyWindowHours,
      boolean chainCheckpointsEnabled,
      boolean nightlyValidationEnabled) {
    this.chainLookbackMinutes = chainLookbackMinutes;
    this.nightlyWindowHours = nightlyWindowHours;
    this.chainCheckpointsEnabled = chainCheckpointsEnabled;
    this.nightlyValidationEnabled = nightlyValidationEnabled;
  }

  @Schema(description = "Rolling checkpoint lookback window in minutes")
  public int getChainLookbackMinutes() {
    return chainLookbackMinutes;
  }

  public void setChainLookbackMinutes(int chainLookbackMinutes) {
    this.chainLookbackMinutes = chainLookbackMinutes;
  }

  @Schema(description = "Nightly retroactive validation window in hours")
  public int getNightlyWindowHours() {
    return nightlyWindowHours;
  }

  public void setNightlyWindowHours(int nightlyWindowHours) {
    this.nightlyWindowHours = nightlyWindowHours;
  }

  @Schema(description = "Whether rolling audit chain checkpoints are enabled")
  public boolean isChainCheckpointsEnabled() {
    return chainCheckpointsEnabled;
  }

  public void setChainCheckpointsEnabled(boolean chainCheckpointsEnabled) {
    this.chainCheckpointsEnabled = chainCheckpointsEnabled;
  }

  @Schema(description = "Whether nightly retroactive integrity validation is enabled")
  public boolean isNightlyValidationEnabled() {
    return nightlyValidationEnabled;
  }

  public void setNightlyValidationEnabled(boolean nightlyValidationEnabled) {
    this.nightlyValidationEnabled = nightlyValidationEnabled;
  }
}
