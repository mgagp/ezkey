/*
 * Ezkey - Open Source MFA/Passkey Alternative
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
 * <p>This DTO represents tenant information returned by the admin API for administrative purposes.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @param tenantId Unique identifier for the tenant
 * @param tenantName Unique name of the tenant
 * @param tenantDescription Optional description of the tenant
 * @param createdAt Timestamp when the tenant was created
 * @param active Flag indicating if the tenant is active
 * @author Ezkey contributors
 * @since 2025
 */
@Schema(description = "Response DTO containing tenant information")
public record TenantResponseDto(
    @Schema(description = "Unique identifier for the tenant", example = "1") Integer tenantId,
    @Schema(description = "Unique name of the tenant", example = "Acme Corporation")
        String tenantName,
    @Schema(
            description = "Optional description of the tenant",
            example = "Acme Corp's Ezkey tenant")
        String tenantDescription,
    @Schema(description = "Timestamp when the tenant was created", example = "2025-10-15T14:30:00Z")
        OffsetDateTime createdAt,
    @Schema(description = "Flag indicating if the tenant is active", example = "true")
        Boolean active) {}
