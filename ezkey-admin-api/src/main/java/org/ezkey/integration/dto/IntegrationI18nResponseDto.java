/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Record: IntegrationI18nResponseDto
 * Description: Response DTO for integration internationalization data in admin API.
 */

package org.ezkey.integration.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response DTO for integration internationalization data in admin API.
 *
 * <p>This DTO represents the complete localized content information for integrations returned by
 * the admin API. It provides translated names and descriptions in different languages, enabling
 * multi-language support in client applications. Contains all internationalization data for
 * administrative purposes.
 *
 * <p><b>Usage Context:</b> Used by admin API endpoints to return internationalization information
 * as part of integration responses. This data enables clients to display integration information in
 * the user's preferred language.
 *
 * <p><b>Internationalization:</b> Follows standard ISO language codes for consistent language
 * identification across the system. Supports complete localization for integration names and
 * descriptions.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @param id Unique identifier for the internationalization record (auto-generated primary key from
 *     the database)
 * @param language Language code for the localized content using standard ISO language codes (e.g.,
 *     "en", "fr", "es")
 * @param name Localized name of the integration (display name in the specified language)
 * @param description Localized description of the integration (detailed description in the
 *     specified language)
 * @author Ezkey contributors
 * @since 2025
 * @see org.ezkey.integration.domain.entity.IntegrationI18n
 * @see IntegrationResponseDto
 * @see IntegrationI18nCreateDto
 */
@Schema(description = "Response DTO for integration internationalization data")
public record IntegrationI18nResponseDto(
    @Schema(description = "Unique identifier for the internationalization record", example = "1")
        Integer id,
    @Schema(description = "Language code for the localized content", example = "en")
        String language,
    @Schema(description = "Localized name of the integration", example = "ACME Corporation")
        String name,
    @Schema(
            description = "Localized description of the integration",
            example = "Secure authentication system for ACME applications")
        String description) {}
