/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: AdminLoginRequestDto
 * Description: Request DTO for administrator login.
 */

package org.ezkey.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for administrator login (passwordless-only).
 *
 * <p>This DTO contains the credentials required for passwordless administrator authentication using
 * Ezkey's cryptographic authentication system. Passwords are not supported.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @param username Administrator username for passwordless authentication
 * @param challengeRequested Request challenge verification on device (6-digit code)
 * @param nonBlocking Request immediate response with authAttemptId instead of blocking wait
 * @author Ezkey contributors
 * @since 2025
 */
@Schema(description = "Request DTO for passwordless administrator login")
public record AdminLoginRequestDto(
    @Schema(
            description = "Administrator username for passwordless authentication",
            example = "admin",
            requiredMode = RequiredMode.REQUIRED)
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
        String username,
    @Schema(
            description =
                "Request challenge verification on device (6-digit code). When true, returns"
                    + " authAttemptId and challengeCode for two-step flow",
            example = "false",
            requiredMode = RequiredMode.NOT_REQUIRED)
        Boolean challengeRequested,
    @Schema(
            description =
                "Request immediate response with authAttemptId and expiresAt instead of"
                    + " blocking until device responds. Allows client to display countdown"
                    + " timer and poll /passwordless-wait endpoint. When false (default),"
                    + " blocking wait is used when no challenge is required (backward"
                    + " compatible). This flag has no effect when challenge is required"
                    + " (challenge flow is always non-blocking).",
            example = "false",
            requiredMode = RequiredMode.NOT_REQUIRED)
        Boolean nonBlocking) {}
