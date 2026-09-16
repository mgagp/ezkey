/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Record: DashboardAlertItemDto
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
 * @param alertId alert primary key
 * @param alertType alert type
 * @param severity alert severity
 * @param status alert status
 * @param createdAt alert creation timestamp
 * @param payload producer-defined JSON payload (string)
 * @since 2026
 */
@Schema(description = "Admin console alert for dashboard overview (Global Admin only)")
public record DashboardAlertItemDto(
    @Schema(description = "Alert primary key") Long alertId,
    @Schema(description = "Alert type") AlertType alertType,
    @Schema(description = "Alert severity") AlertSeverity severity,
    @Schema(description = "Alert status") AlertStatus status,
    @Schema(description = "Alert creation timestamp") OffsetDateTime createdAt,
    @Schema(
            description =
                "Producer-defined JSON payload (string). Shape depends on alertType; clients aware"
                    + " of the type render structured fields.")
        String payload) {}
