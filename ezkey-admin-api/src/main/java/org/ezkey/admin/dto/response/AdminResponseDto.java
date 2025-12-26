/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Record: AdminResponseDto
 * Description: Response DTO for administrator listing in admin API.
 */

package org.ezkey.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/**
 * Response DTO for administrator listing in admin API.
 *
 * <p>This DTO represents administrator information returned by the admin API for listing
 * administrators. It includes comprehensive administrator details while excluding sensitive
 * information like recovery codes and enrollment credentials.
 *
 * <p><b>Usage Context:</b> Used by admin API endpoints to return administrator information to
 * administrators for monitoring and management purposes. Contains all non-sensitive data needed for
 * administrator administration.
 *
 * <p><b>Core Identification Fields:</b>
 *
 * <ul>
 *   <li><b>adminId:</b> Unique identifier for the administrator
 *   <li><b>username:</b> Username for the administrator
 *   <li><b>email:</b> Email address (optional)
 *   <li><b>firstName:</b> First name (optional)
 *   <li><b>lastName:</b> Last name (optional)
 * </ul>
 *
 * <p><b>Administrator Type and Scope:</b>
 *
 * <ul>
 *   <li><b>adminType:</b> Type of administrator (GLOBAL_ADMIN, TENANT_ADMIN, INTEGRATION_ADMIN)
 *   <li><b>tenantId:</b> Tenant ID (null for global admins)
 * </ul>
 *
 * <p><b>Status Fields:</b>
 *
 * <ul>
 *   <li><b>active:</b> Flag indicating if the administrator is currently active
 *   <li><b>createdAt:</b> Timestamp when the administrator was created
 * </ul>
 *
 * <p><b>Security Note:</b> This DTO excludes sensitive information like recovery codes, enrollment
 * proof tokens, and challenge codes. These are only available during provisioning.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @param adminId Unique identifier for the administrator (auto-generated primary key)
 * @param username Username for the administrator
 * @param email Email address (optional, required for GLOBAL_ADMIN)
 * @param firstName First name (optional, required for GLOBAL_ADMIN)
 * @param lastName Last name (optional, required for GLOBAL_ADMIN)
 * @param adminType Type of administrator (GLOBAL_ADMIN, TENANT_ADMIN, INTEGRATION_ADMIN)
 * @param tenantId Tenant ID (null for global admins)
 * @param active Flag indicating if the administrator is currently active
 * @param createdAt Timestamp when the administrator was created (with timezone)
 * @author Ezkey contributors
 * @since 2025
 * @see org.ezkey.integration.domain.entity.EzkeyAdmin
 */
@Schema(description = "Response DTO containing administrator information for listing purposes")
public record AdminResponseDto(
    @Schema(description = "Unique identifier for the administrator", example = "1") Integer adminId,
    @Schema(description = "Username for the administrator", example = "john.doe") String username,
    @Schema(description = "Email address", example = "john.doe@example.com") String email,
    @Schema(description = "First name", example = "John") String firstName,
    @Schema(description = "Last name", example = "Doe") String lastName,
    @Schema(
            description = "Type of administrator",
            example = "TENANT_ADMIN",
            allowableValues = {"GLOBAL_ADMIN", "TENANT_ADMIN", "INTEGRATION_ADMIN"})
        String adminType,
    @Schema(description = "Tenant ID (null for global admins)", example = "1") Integer tenantId,
    @Schema(description = "Flag indicating if the administrator is currently active", example = "true")
        Boolean active,
    @Schema(
            description = "Timestamp when the administrator was created",
            example = "2025-10-15T14:30:00Z")
        OffsetDateTime createdAt) {}

