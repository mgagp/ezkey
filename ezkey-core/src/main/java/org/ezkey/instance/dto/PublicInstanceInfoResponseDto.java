/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: PublicInstanceInfoResponseDto
 * Description: Public, unauthenticated instance metadata for Admin UI, Auth API clients, and
 * operators.
 */

package org.ezkey.instance.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Public instance information (no authentication required).
 *
 * <p>Used by the Admin UI login shell, the Auth API public {@code instance-info} endpoint, and for
 * operator visibility. {@code authApiPublicBaseUrl} mirrors the {@code authUrl} field embedded in
 * enrollment QR codes when {@code ezkey.qr.auth-base-url} is configured.
 */
@Schema(
    description =
        "Public instance metadata (branding, optional public Auth API URL for QR alignment)")
public record PublicInstanceInfoResponseDto(
    @Schema(
            description =
                "Public base URL of the Auth API (same as authUrl in enrollment QR JSON when"
                    + " configured)",
            example = "https://auth.example.com:8080",
            nullable = true)
        String authApiPublicBaseUrl,
    @Schema(description = "Instance / organization display name", example = "Acme Corporation")
        String instanceName,
    @Schema(
            description = "Optional instance or organization description",
            nullable = true,
            example = "Acme Corp Ezkey MFA")
        String instanceDescription,
    @Schema(
            description = "Optional URL for About / learn more (e.g. company instance page)",
            nullable = true,
            example = "https://www.example.com/about-ezkey")
        String aboutUrl) {}
