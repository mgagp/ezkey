/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: AlertResponseDto
 * Description: Response DTO exposing a single ezkey_alert row at API boundaries.
 */

package org.ezkey.alert.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import org.ezkey.alert.domain.AlertResolutionReason;
import org.ezkey.alert.domain.AlertSeverity;
import org.ezkey.alert.domain.AlertStatus;
import org.ezkey.alert.domain.AlertType;

/**
 * Response DTO exposing a single alert row at API boundaries.
 *
 * <p>The {@code payload} field is intentionally surfaced as a JSON-encoded string: the inner
 * structure is owned by the producer and varies per {@link AlertType}. Clients that recognise the
 * type render it as structured data; unknown types fall back to a raw view.
 *
 * @since 2026
 */
@Schema(description = "Operator-facing alert (read model).")
public class AlertResponseDto {

  private Long alertId;
  private AlertType alertType;
  private AlertSeverity severity;
  private AlertStatus status;
  private String dedupeKey;
  private String payload;
  private int occurrenceCount;
  private OffsetDateTime createdAt;
  private OffsetDateTime lastSeenAt;
  private OffsetDateTime resolvedAt;
  private Integer resolvedByAdminId;
  private AlertResolutionReason resolutionReason;

  public AlertResponseDto() {}

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

  public String getDedupeKey() {
    return dedupeKey;
  }

  public void setDedupeKey(String dedupeKey) {
    this.dedupeKey = dedupeKey;
  }

  @Schema(
      description =
          "Producer-defined JSON payload (string). Shape depends on alertType; clients aware of"
              + " the type render structured fields, others fall back to raw display.")
  public String getPayload() {
    return payload;
  }

  public void setPayload(String payload) {
    this.payload = payload;
  }

  public int getOccurrenceCount() {
    return occurrenceCount;
  }

  public void setOccurrenceCount(int occurrenceCount) {
    this.occurrenceCount = occurrenceCount;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public OffsetDateTime getLastSeenAt() {
    return lastSeenAt;
  }

  public void setLastSeenAt(OffsetDateTime lastSeenAt) {
    this.lastSeenAt = lastSeenAt;
  }

  public OffsetDateTime getResolvedAt() {
    return resolvedAt;
  }

  public void setResolvedAt(OffsetDateTime resolvedAt) {
    this.resolvedAt = resolvedAt;
  }

  public Integer getResolvedByAdminId() {
    return resolvedByAdminId;
  }

  public void setResolvedByAdminId(Integer resolvedByAdminId) {
    this.resolvedByAdminId = resolvedByAdminId;
  }

  public AlertResolutionReason getResolutionReason() {
    return resolutionReason;
  }

  public void setResolutionReason(AlertResolutionReason resolutionReason) {
    this.resolutionReason = resolutionReason;
  }
}
