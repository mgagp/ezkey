/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: TenantCreateRequestDto
 * Description: Request DTO for creating a tenant.
 */

package org.ezkey.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating a tenant.
 *
 * <p>This DTO contains the information required to create a new tenant in the multi-tenant Ezkey
 * system. Only global administrators can create tenants.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @param tenantName Unique name for the tenant (required, 3-100 characters)
 * @param tenantDescription Optional description of the tenant (max 500 characters)
 * @author Ezkey contributors
 * @since 2025
 */
@Schema(description = "Request DTO for creating a tenant")
public record TenantCreateRequestDto(
    @Schema(
            description = "Unique name for the tenant",
            example = "Acme Corporation",
            requiredMode = RequiredMode.REQUIRED)
        @NotBlank(message = "Tenant name is required")
        @Size(min = 3, max = 100, message = "Tenant name must be between 3 and 100 characters")
        String tenantName,
    @Schema(
            description = "Optional description of the tenant",
            example = "Acme Corp's Ezkey tenant for MFA authentication",
            requiredMode = RequiredMode.NOT_REQUIRED)
        @Size(max = 500, message = "Tenant description must not exceed 500 characters")
        String tenantDescription) {}
