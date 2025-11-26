/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Record: IntegrationCreateRequestDto
 * Description: Request DTO for creating new Integration entities in admin API.
 */

package org.ezkey.integration.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import java.util.List;

/**
 * Request DTO for creating new Integration entities in admin API.
 *
 * <p>This DTO contains the data required to create a new Integration through the admin API.
 * Integrations represent applications or systems that will be protected by Ezkey MFA. It includes
 * basic integration information and optional internationalization data for multi-language support.
 *
 * <p><b>Usage Context:</b> Used by administrators to create new integrations that will use Ezkey
 * for MFA authentication. Contains all necessary data for integration setup including branding and
 * localization.
 *
 * <p><b>Fields:</b>
 *
 * <ul>
 *   <li><b>code:</b> Unique business identifier for the integration
 *   <li><b>logo:</b> URL or path to the integration's logo image
 *   <li><b>i18n:</b> Optional internationalization data for multi-language support
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @param logo URL or path to the integration's logo image displayed in mobile app and web
 *     interfaces
 * @param i18n Optional list of internationalization entries containing localized name and
 *     description for different languages
 * @author Ezkey contributors
 * @since 2025
 * @see org.ezkey.integration.domain.IntegrationCreateRequest
 * @see IntegrationI18nCreateDto
 */
@Schema(description = "Request DTO for creating new Integration entities")
public record IntegrationCreateRequestDto(
    @Schema(
            description = "URL or path to the integration's logo image",
            example = "https://example.com/logo.png")
        String logo,
    @Schema(description = "List of internationalization entries for multi-language support")
        @Valid
        List<IntegrationI18nCreateDto> i18n) {}
