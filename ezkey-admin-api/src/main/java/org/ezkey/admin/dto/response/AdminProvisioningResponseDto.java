/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: AdminProvisioningResponseDto
 * Description: Response DTO for administrator provisioning with onboarding credentials.
 */

package org.ezkey.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/**
 * Response DTO for administrator provisioning (without sensitive credentials).
 *
 * <p>This DTO contains basic information about the newly created administrator. Sensitive
 * onboarding credentials (enrollment proof token, challenge code, recovery codes) are NOT included
 * in this response for security reasons. They must be retrieved separately via GET
 * /api/v1/admins/{id}/onboarding endpoint.
 *
 * <p><b>Security Note:</b> This follows the same pattern as the enrollment API, where sensitive
 * credentials are separated from the creation response. This prevents credentials from appearing in
 * logs and provides better control over credential access.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @param adminId Unique identifier for the administrator
 * @param username Username for the administrator
 * @param email Email address
 * @param firstName First name
 * @param lastName Last name
 * @param adminType Type of administrator (GLOBAL_ADMIN, TENANT_ADMIN)
 * @param tenantId Tenant ID (null for global admins)
 * @param enrollmentId Enrollment ID for passwordless authentication (use this to retrieve
 *     onboarding credentials)
 * @param createdAt Timestamp when the administrator was created
 * @author Ezkey contributors
 * @since 2025
 */
@Schema(
    description =
        "Response DTO containing administrator provisioning information (without sensitive"
            + " credentials). Use GET /api/v1/admins/{id}/onboarding to retrieve onboarding"
            + " credentials.")
public record AdminProvisioningResponseDto(
    @Schema(description = "Unique identifier for the administrator", example = "1") Integer adminId,
    @Schema(description = "Username for the administrator", example = "john.doe") String username,
    @Schema(description = "Email address", example = "john.doe@example.com") String email,
    @Schema(description = "First name", example = "John") String firstName,
    @Schema(description = "Last name", example = "Doe") String lastName,
    @Schema(
            description = "Type of administrator",
            example = "GLOBAL_ADMIN",
            allowableValues = {"GLOBAL_ADMIN", "TENANT_ADMIN"})
        String adminType,
    @Schema(description = "Tenant ID (null for global admins)", example = "1") Integer tenantId,
    @Schema(
            description =
                "Enrollment ID for passwordless authentication. Use this ID to retrieve onboarding"
                    + " credentials via GET /api/v1/admins/{id}/onboarding",
            example = "123")
        Integer enrollmentId,
    @Schema(
            description = "Timestamp when the administrator was created",
            example = "2025-10-15T14:30:00Z")
        OffsetDateTime createdAt) {}
