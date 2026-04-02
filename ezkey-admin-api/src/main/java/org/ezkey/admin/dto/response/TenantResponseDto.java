/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: TenantResponseDto
 * Description: Response DTO for tenant data.
 */

package org.ezkey.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/**
 * Response DTO for tenant data.
 *
 * <p>This DTO represents tenant information returned by the admin API for administrative purposes,
 * including organizational identity, contact information, and lifecycle metadata.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @param tenantId unique identifier for the tenant
 * @param tenantName unique name of the tenant
 * @param tenantDescription optional description of the tenant
 * @param organizationName legal organization name
 * @param organizationDomain primary domain of the organization
 * @param countryCode ISO 3166-1 alpha-2 country code
 * @param timezone IANA timezone identifier
 * @param primaryContactName primary contact full name
 * @param primaryContactEmail primary contact email address
 * @param primaryContactPhoneNumber primary contact phone number
 * @param createdAt timestamp when the tenant was created
 * @param updatedAt timestamp of last modification
 * @param active flag indicating if the tenant is active
 * @param isSystemTenant flag indicating if this is the system tenant
 * @param deactivatedAt timestamp when the tenant was deactivated (null if never deactivated or
 *     reactivated; preserved for audit traceability)
 * @author Ezkey contributors
 * @since 2025
 */
@Schema(description = "Response DTO containing tenant information")
public record TenantResponseDto(
    @Schema(description = "Unique identifier for the tenant", example = "1") Integer tenantId,
    @Schema(
            description =
                "Optimistic lock version. Include in PATCH/PUT requests to prevent concurrent"
                    + " update conflicts.",
            example = "0")
        Long version,
    @Schema(description = "Unique name of the tenant", example = "Acme Corporation")
        String tenantName,
    @Schema(
            description = "Optional description of the tenant",
            example = "Acme Corp's Ezkey tenant")
        String tenantDescription,
    @Schema(description = "Legal name of the organization", example = "Acme Corporation Inc.")
        String organizationName,
    @Schema(description = "Primary domain of the organization", example = "acme.com")
        String organizationDomain,
    @Schema(description = "ISO 3166-1 alpha-2 country code", example = "CA") String countryCode,
    @Schema(description = "IANA timezone identifier", example = "America/Montreal") String timezone,
    @Schema(description = "Primary contact full name", example = "Jane Doe")
        String primaryContactName,
    @Schema(description = "Primary contact email address", example = "jane.doe@acme.com")
        String primaryContactEmail,
    @Schema(
            description = "Primary contact phone number in canonical E.164 format",
            example = "+15145551234")
        String primaryContactPhoneNumber,
    @Schema(description = "Timestamp when the tenant was created", example = "2025-10-15T14:30:00Z")
        OffsetDateTime createdAt,
    @Schema(description = "Timestamp of the last modification", example = "2025-11-01T09:15:00Z")
        OffsetDateTime updatedAt,
    @Schema(description = "Flag indicating if the tenant is active", example = "true")
        Boolean active,
    @Schema(description = "Flag indicating if this is the system tenant", example = "false")
        Boolean isSystemTenant,
    @Schema(
            description =
                "Timestamp when the tenant was deactivated (null if never deactivated; preserved"
                    + " for audit traceability)",
            example = "2025-11-15T10:00:00Z")
        OffsetDateTime deactivatedAt) {}
