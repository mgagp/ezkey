/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: AdminCreateRequestDto
 * Description: Request DTO for creating an administrator (global or tenant).
 */

package org.ezkey.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating an administrator (global or tenant).
 *
 * <p>This DTO contains the information required to create a new administrator. For global admins,
 * email, firstName, and lastName are required for SOC 2 compliance. For tenant admins, these fields
 * are optional but recommended.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @param username Unique username for the administrator (required, 3-50 characters)
 * @param email Email address (required for global admins, optional for tenant admins)
 * @param firstName First name (required for global admins, optional for tenant admins)
 * @param lastName Last name (required for global admins, optional for tenant admins)
 * @param tenantId Tenant ID (required for tenant admin creation, ignored for global admin)
 * @author Ezkey contributors
 * @since 2025
 */
@Schema(description = "Request DTO for creating an administrator")
public record AdminCreateRequestDto(
    @Schema(
            description = "Unique username for the administrator",
            example = "john.doe",
            requiredMode = RequiredMode.REQUIRED)
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
        String username,
    @Schema(
            description = "Email address (required for global admins, optional for tenant admins)",
            example = "john.doe@example.com",
            requiredMode = RequiredMode.NOT_REQUIRED)
        @Email(message = "Email must be a valid email address")
        @Size(max = 255, message = "Email must not exceed 255 characters")
        String email,
    @Schema(
            description = "First name (required for global admins, optional for tenant admins)",
            example = "John",
            requiredMode = RequiredMode.NOT_REQUIRED)
        @Size(max = 100, message = "First name must not exceed 100 characters")
        String firstName,
    @Schema(
            description = "Last name (required for global admins, optional for tenant admins)",
            example = "Doe",
            requiredMode = RequiredMode.NOT_REQUIRED)
        @Size(max = 100, message = "Last name must not exceed 100 characters")
        String lastName,
    @Schema(
            description =
                "Tenant ID (required for tenant admin creation, ignored for global admin)",
            example = "1",
            requiredMode = RequiredMode.NOT_REQUIRED)
        Integer tenantId) {}
