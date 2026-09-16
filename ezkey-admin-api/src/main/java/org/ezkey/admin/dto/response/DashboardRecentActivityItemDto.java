/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Record: DashboardRecentActivityItemDto
 * Description: Single recent audit log entry for dashboard overview.
 */

package org.ezkey.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/**
 * A single recent audit log entry for the dashboard recent activity widget.
 *
 * @param auditLogId audit log primary key
 * @param eventType event type name
 * @param eventStatus event status name
 * @param eventAction free-text event action
 * @param apiName API name that produced the event
 * @param adminId administrator id when applicable
 * @param createdAt event creation timestamp
 */
@Schema(description = "Recent audit log entry for dashboard overview")
public record DashboardRecentActivityItemDto(
    @Schema(description = "Audit log primary key") Long auditLogId,
    @Schema(description = "Event type name") String eventType,
    @Schema(description = "Event status name") String eventStatus,
    @Schema(description = "Free-text event action") String eventAction,
    @Schema(description = "API name that produced the event") String apiName,
    @Schema(description = "Administrator id when applicable") Integer adminId,
    @Schema(description = "Event creation timestamp") OffsetDateTime createdAt) {}
