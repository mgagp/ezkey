/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: DashboardAlertItemDto
 * Description: Single admin-console alert (read model) projected from ezkey_alert.
 */

package org.ezkey.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import org.ezkey.alert.domain.AlertSeverity;
import org.ezkey.alert.domain.AlertStatus;
import org.ezkey.alert.domain.AlertType;

/**
 * A single alert intended for the dashboard overview (Global Admin only).
 *
 * <p>Projected from the operator-facing {@code ezkey_alert} table. The {@code payload} is surfaced
 * as a JSON-encoded string; clients render it according to {@link AlertType}.
 *
 * @since 2026
 */
@Schema(description = "Admin console alert for dashboard overview (Global Admin only)")
public class DashboardAlertItemDto {

  private Long alertId;
  private AlertType alertType;
  private AlertSeverity severity;
  private AlertStatus status;
  private OffsetDateTime createdAt;
  private String payload;

  public DashboardAlertItemDto() {}

  public Long getAlertId() {
    return alertId;
  }

  public void setAlertId(Long alertId) {
    this.alertId = alertId;
  }

  public AlertType getAlertType() {
    return alertType;
  }

  public void setAlertType(AlertType alertType) {
    this.alertType = alertType;
  }

  public AlertSeverity getSeverity() {
    return severity;
  }

  public void setSeverity(AlertSeverity severity) {
    this.severity = severity;
  }

  public AlertStatus getStatus() {
    return status;
  }

  public void setStatus(AlertStatus status) {
    this.status = status;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
  }

  @Schema(
      description =
          "Producer-defined JSON payload (string). Shape depends on alertType; clients aware of"
              + " the type render structured fields.")
  public String getPayload() {
    return payload;
  }

  public void setPayload(String payload) {
    this.payload = payload;
  }
}
