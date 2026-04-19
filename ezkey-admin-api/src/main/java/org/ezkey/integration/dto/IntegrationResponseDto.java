/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Record: IntegrationResponseDto
 * Description: Response DTO for integration data in admin API.
 */

package org.ezkey.integration.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import org.ezkey.integration.domain.IntegrationLifecycleStatus;

/**
 * Response DTO for integration data in admin API.
 *
 * <p>This DTO represents the complete integration information returned by the admin API for
 * administrative purposes. It includes comprehensive integration details, configuration, metadata,
 * name, and description while excluding sensitive cryptographic material for security purposes.
 *
 * <p><b>Usage Context:</b> Used by admin API endpoints to return integration information to
 * administrators for monitoring and management purposes.
 *
 * <p><b>Security Note:</b> This DTO excludes sensitive cryptographic keys and provides only the
 * information necessary for administrative operations and client display.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @param id Unique identifier for the integration (auto-generated primary key from the database)
 * @param code Unique business identifier code for the integration (must be unique per tenant)
 * @param tenantId Tenant ID that owns this integration (for multi-tenant isolation verification)
 * @param lifecycleStatus Explicit lifecycle status for the integration
 * @param operational Whether the integration is currently operational: {@code ACTIVE} lifecycle
 *     state <em>and</em> parent tenant active
 * @param createdAt Timestamp when the integration was created (used for audit trails and sorting
 *     purposes, with timezone)
 * @param name Display name for the integration
 * @param description Optional description of the integration
 * @param isSystemIntegration Whether this is the system integration (e.g. admin MFA); used by UI to
 *     hide or adapt actions on the integration detail page
 * @author Ezkey contributors
 * @since 2025
 * @see org.ezkey.integration.domain.entity.Integration
 * @see IntegrationCreateRequestDto
 */
@Schema(description = "Response DTO containing complete integration details")
public record IntegrationResponseDto(
    @Schema(description = "Unique identifier for the integration", example = "1") Integer id,
    @Schema(
            description = "Unique business identifier code for the integration",
            example = "web-portal")
        String code,
    @Schema(description = "Tenant ID that owns this integration", example = "2") Integer tenantId,
    @Schema(description = "Explicit integration lifecycle status", example = "ACTIVE")
        IntegrationLifecycleStatus lifecycleStatus,
    @Schema(
            description =
                "Whether the integration is currently operational: ACTIVE lifecycle state and"
                    + " parent tenant active",
            example = "true")
        Boolean operational,
    @Schema(
            description = "Timestamp when the integration was created (with timezone)",
            example = "2025-01-15T10:30:00+01:00")
        OffsetDateTime createdAt,
    @Schema(description = "Display name for the integration", example = "Web Portal") String name,
    @Schema(description = "Optional description of the integration") String description,
    @Schema(
            description =
                "Whether this is the system integration (e.g. admin MFA); used by UI to adapt"
                    + " detail page")
        Boolean isSystemIntegration) {}
