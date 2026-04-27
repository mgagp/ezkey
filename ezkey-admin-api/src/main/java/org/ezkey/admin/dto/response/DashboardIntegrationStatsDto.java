/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: DashboardIntegrationStatsDto
 * Description: Integration counts for dashboard overview.
 */

package org.ezkey.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Integration counts for the dashboard overview.
 *
 * <p>Provides total, active, and retired integration counts for the current scope (tenant or
 * instance).
 */
@Schema(description = "Integration counts for dashboard overview")
public class DashboardIntegrationStatsDto {

  private long total;
  private long active;
  private long retired;

  public DashboardIntegrationStatsDto() {}

  public DashboardIntegrationStatsDto(long total, long active, long retired) {
    this.total = total;
    this.active = active;
    this.retired = retired;
  }

  public long getTotal() {
    return total;
  }

  public void setTotal(long total) {
    this.total = total;
  }

  public long getActive() {
    return active;
  }

  public void setActive(long active) {
    this.active = active;
  }

  public long getRetired() {
    return retired;
  }

  public void setRetired(long retired) {
    this.retired = retired;
  }
}
