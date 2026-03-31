/*
 * Ezkey - Open Source Cryptographic MFA Platform
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
import java.util.List;

/**
 * Response DTO for administrator provisioning.
 *
 * <p>This DTO contains basic information about the newly created administrator. Enrollment proof
 * token and challenge are still retrieved via GET /api/v1/admins/{id}/onboarding.
 *
 * <p><b>Recovery codes:</b> Plain recovery codes are included once in this response at creation
 * time only (same moment as provisioning). They cannot be retrieved later from the API. Clients
 * must display them immediately and instruct the operator to store them securely.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
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
        "Response DTO for administrator provisioning. Recovery codes are present once at"
            + " creation; enrollment token/challenge via GET /api/v1/admins/{id}/onboarding.")
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
        OffsetDateTime createdAt,
    @Schema(
            description =
                "Single-use recovery codes (plain text). Shown once at creation; save securely."
                    + " Null if none are available.")
        List<String> recoveryCodes) {}
