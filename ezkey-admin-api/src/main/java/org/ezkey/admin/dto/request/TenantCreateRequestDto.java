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
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating a tenant.
 *
 * <p>This DTO contains the information required to create a new tenant in the multi-tenant Ezkey
 * system. Only global administrators can create tenants. All fields except {@code tenantName} are
 * optional for backward compatibility.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @param tenantName unique name for the tenant (required, 3-100 chars)
 * @param tenantDescription optional description (max 500 chars)
 * @param organizationName legal organization name
 * @param organizationDomain primary domain (e.g. acme.com)
 * @param countryCode ISO 3166-1 alpha-2 country code
 * @param timezone IANA timezone identifier
 * @param primaryContactName primary contact full name
 * @param primaryContactEmail primary contact email address
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
        String tenantDescription,
    @Schema(
            description = "Legal name of the organization",
            example = "Acme Corporation Inc.",
            requiredMode = RequiredMode.NOT_REQUIRED)
        @Size(max = 255, message = "Organization name must not exceed 255 characters")
        String organizationName,
    @Schema(
            description = "Primary domain of the organization",
            example = "acme.com",
            requiredMode = RequiredMode.NOT_REQUIRED)
        @Size(max = 255, message = "Domain must not exceed 255 characters")
        @Pattern(regexp = "^[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$", message = "Invalid domain format")
        String organizationDomain,
    @Schema(
            description = "ISO 3166-1 alpha-2 country code",
            example = "CA",
            requiredMode = RequiredMode.NOT_REQUIRED)
        @Size(min = 2, max = 2, message = "Country code must be 2 characters")
        @Pattern(regexp = "^[A-Z]{2}$", message = "Country code must be 2 uppercase letters")
        String countryCode,
    @Schema(
            description = "IANA timezone identifier",
            example = "America/Montreal",
            requiredMode = RequiredMode.NOT_REQUIRED)
        @Size(max = 50, message = "Timezone must not exceed 50 characters")
        String timezone,
    @Schema(
            description = "Primary contact full name",
            example = "Jane Doe",
            requiredMode = RequiredMode.NOT_REQUIRED)
        @Size(max = 255, message = "Contact name must not exceed 255 characters")
        String primaryContactName,
    @Schema(
            description = "Primary contact email address",
            example = "jane.doe@acme.com",
            requiredMode = RequiredMode.NOT_REQUIRED)
        @Email(message = "Invalid email format")
        @Size(max = 255, message = "Contact email must not exceed 255 characters")
        String primaryContactEmail) {}
