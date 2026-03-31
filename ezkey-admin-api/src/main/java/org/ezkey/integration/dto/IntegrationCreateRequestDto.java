/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Record: IntegrationCreateRequestDto
 * Description: Request DTO for creating new Integration entities in admin API.
 */

package org.ezkey.integration.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating new Integration entities in admin API.
 *
 * <p>This DTO contains the data required to create a new Integration through the admin API.
 * Integrations represent applications or systems that will be protected by Ezkey MFA. It includes
 * code, name, and optional description.
 *
 * <p><b>Usage Context:</b> Used by administrators to create new integrations that will use Ezkey
 * for MFA authentication.
 *
 * <p><b>Fields:</b>
 *
 * <ul>
 *   <li><b>code:</b> Unique business identifier for the integration
 *   <li><b>name:</b> Display name for the integration
 *   <li><b>description:</b> Optional description of the integration
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @param code Unique business identifier for the integration (required, must be unique per tenant,
 *     alphanumeric with hyphens/underscores)
 * @param name Display name for the integration (required)
 * @param description Optional description of the integration
 * @author Ezkey contributors
 * @since 2025
 * @see org.ezkey.integration.domain.IntegrationCreateRequest
 */
@Schema(description = "Request DTO for creating new Integration entities")
public record IntegrationCreateRequestDto(
    @NotBlank(message = "code must not be blank")
        @Size(min = 2, max = 100, message = "code must be between 2 and 100 characters")
        @Pattern(
            regexp = "^[a-zA-Z0-9_-]+$",
            message = "code must contain only alphanumeric characters, hyphens, and underscores")
        @Schema(
            description = "Unique business identifier code for the integration",
            example = "web-portal")
        String code,
    @NotBlank(message = "name must not be blank")
        @Size(max = 255, message = "name must be at most 255 characters")
        @Schema(description = "Display name for the integration", example = "Web Portal")
        String name,
    @Size(max = 500, message = "description must be at most 500 characters")
        @Schema(
            description = "Optional description of the integration",
            example = "Administration console")
        String description) {}
