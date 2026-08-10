/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: EnrollmentInstanceInfoResponseDto
 * Description: Integration-signed installation branding for enrolled Auth API clients.
 */

package org.ezkey.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

/**
 * Response DTO for enrolled instance-info with integration Ed25519 signature.
 *
 * @param enrollmentId enrollment that authenticated the request
 * @param authApiPublicBaseUrl optional public Auth API base URL
 * @param instanceName instance / organization display name
 * @param instanceDescription optional description
 * @param aboutUrl optional about URL
 * @param instanceInfoPayloadSignedByIntegration Base64URL Ed25519 signature
 * @since 2026
 */
@Schema(description = "Integration-signed installation branding for enrolled clients")
public record EnrollmentInstanceInfoResponseDto(
    @Schema(
            description = "Enrollment ID that authenticated the request",
            example = "123",
            requiredMode = RequiredMode.REQUIRED)
        Integer enrollmentId,
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
            description = "Optional URL for About / learn more",
            nullable = true,
            example = "https://www.example.com/about-ezkey")
        String aboutUrl,
    @Schema(
            description =
                "Base64URL (no padding) Ed25519 signature over the canonical INSTANCE_INFO"
                    + " payload",
            requiredMode = RequiredMode.REQUIRED)
        String instanceInfoPayloadSignedByIntegration) {}
