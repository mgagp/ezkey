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
import java.util.List;

/**
 * Response DTO for administrator provisioning with onboarding credentials.
 *
 * <p>This DTO contains all information needed for the newly created administrator to complete
 * passwordless enrollment, including enrollment credentials and recovery codes. These credentials
 * are shown once and should be saved securely.
 *
 * <p><b>Security Note:</b> Enrollment credentials and recovery codes are shown only once during
 * provisioning. They cannot be retrieved again without database access.
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
 * @param enrollmentId Enrollment ID for passwordless authentication
 * @param enrollmentProofToken Enrollment proof token (shown once)
 * @param enrollmentChallenge Enrollment challenge code (6 digits, shown once)
 * @param recoveryCodes List of recovery codes (shown once, single-use)
 * @param createdAt Timestamp when the administrator was created
 * @author Ezkey contributors
 * @since 2025
 */
@Schema(
    description =
        "Response DTO containing administrator provisioning information with onboarding"
            + " credentials")
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
    @Schema(description = "Enrollment ID for passwordless authentication", example = "123")
        Integer enrollmentId,
    @Schema(
            description = "Enrollment proof token (shown once - save securely)",
            example = "EZK-ABC123-DEF456")
        String enrollmentProofToken,
    @Schema(
            description = "Enrollment challenge code (6 digits, shown once - save securely)",
            example = "654321")
        Integer enrollmentChallenge,
    @Schema(
            description = "List of recovery codes (shown once, single-use - save securely)",
            example = "[\"1234-5678-9012-3456-7890-1234-5678-9012\"]")
        List<String> recoveryCodes,
    @Schema(
            description = "Timestamp when the administrator was created",
            example = "2025-10-15T14:30:00Z")
        OffsetDateTime createdAt) {}
