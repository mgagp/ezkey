/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: AdminLoginRequestDto
 * Description: Request DTO for administrator login.
 */

package org.ezkey.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for administrator login (passwordless-only).
 *
 * <p>This DTO contains the credentials required for passwordless administrator authentication using
 * Ezkey's cryptographic authentication system. Passwords are not supported.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @param username Administrator username for passwordless authentication
 * @param challengeRequested Request challenge verification on device (6-digit code)
 * @author Ezkey contributors
 * @since 2025
 */
@Schema(description = "Request DTO for passwordless administrator login")
public record AdminLoginRequestDto(
    @Schema(
            description = "Administrator username for passwordless authentication",
            example = "admin",
            required = true)
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
        String username,
    @Schema(
            description =
                "Request challenge verification on device (6-digit code). When true, returns"
                    + " authAttemptId and challengeCode for two-step flow",
            example = "false",
            required = false)
        Boolean challengeRequested) {}
