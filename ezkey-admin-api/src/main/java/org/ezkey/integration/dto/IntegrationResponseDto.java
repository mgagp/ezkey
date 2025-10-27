/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Record: IntegrationResponseDto
 * Description: Response DTO for integration data in admin API including internationalization support.
 */

package org.ezkey.integration.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Response DTO for integration data in admin API including internationalization support.
 *
 * <p>This DTO represents the complete integration information returned by the admin API for
 * administrative purposes. It includes comprehensive integration details, configuration, metadata,
 * and localized content for multiple languages while excluding sensitive cryptographic material for
 * security purposes.
 *
 * <p><b>Usage Context:</b> Used by admin API endpoints to return integration information to
 * administrators for monitoring and management purposes. Contains all non-sensitive data needed for
 * integration administration and client consumption.
 *
 * <p><b>Security Note:</b> This DTO excludes sensitive cryptographic keys and provides only the
 * information necessary for administrative operations and client display.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @param id Unique identifier for the integration (auto-generated primary key from the database)
 * @param logo URL or path to the integration logo image (used for displaying the integration brand in user interfaces)
 * @param active Integration status flag (indicates whether the integration is currently active and available for use)
 * @param createdAt Timestamp when the integration was created (used for audit trails and sorting purposes, with timezone)
 * @param i18n List of internationalized content for the integration (contains localized names and descriptions in multiple languages)
 * @author Ezkey contributors
 * @since 2025
 * @see org.ezkey.integration.domain.entity.Integration
 * @see IntegrationCreateRequestDto
 * @see IntegrationI18nResponseDto
 */
@Schema(description = "Response DTO containing complete integration details")
public record IntegrationResponseDto(
    @Schema(description = "Unique identifier for the integration", example = "1") Integer id,
    @Schema(
            description = "URL or path to the integration logo image",
            example = "https://example.com/logo.png")
        String logo,
    @Schema(description = "Integration status flag", example = "true") Boolean active,
    @Schema(
            description = "Timestamp when the integration was created (with timezone)",
            example = "2025-01-15T10:30:00+01:00")
        OffsetDateTime createdAt,
    @Schema(description = "List of internationalized content for multiple languages")
        List<IntegrationI18nResponseDto> i18n) {}
