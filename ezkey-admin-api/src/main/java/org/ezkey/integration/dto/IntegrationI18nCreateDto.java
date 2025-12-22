/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Record: IntegrationI18nCreateDto
 * Description: Create DTO for integration internationalization data in admin API.
 */

package org.ezkey.integration.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Create DTO for integration internationalization data in admin API.
 *
 * <p>This DTO represents the localized content data needed to create internationalization records
 * for integrations. It provides translated names and descriptions for different languages, enabling
 * multi-language support in client applications.
 *
 * <p><b>Usage Context:</b> Used as part of integration creation requests to provide localized
 * content for multiple languages. This enables the mobile app and other clients to display
 * integration information in the user's preferred language.
 *
 * <p><b>Internationalization:</b> Follows standard ISO language codes for consistent language
 * identification across the system.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @param language Language code for the localized content using standard ISO language codes (e.g.,
 *     "en", "fr", "es")
 * @param name Localized name of the integration displayed in the specified language
 * @param description Localized description of the integration providing detailed information in the
 *     specified language
 * @author Ezkey contributors
 * @since 2025
 * @see IntegrationCreateRequestDto
 * @see IntegrationI18nResponseDto
 */
@Schema(description = "Create DTO for integration internationalization data")
public record IntegrationI18nCreateDto(
    @Schema(
            description = "Language code for the localized content",
            example = "en",
            requiredMode = RequiredMode.REQUIRED)
        @NotNull(message = "Language code is required")
        @NotBlank(message = "Language code cannot be blank")
        String language,
    @Schema(
            description = "Localized name of the integration",
            example = "ACME Corporation",
            requiredMode = RequiredMode.REQUIRED)
        @NotNull(message = "Name is required")
        @NotBlank(message = "Name cannot be blank")
        String name,
    @Schema(
            description = "Localized description of the integration",
            example = "Secure authentication system for ACME applications")
        String description) {}
