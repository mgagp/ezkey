/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: EnrollmentInstanceInfoRequestDto
 * Description: Request for integration-signed installation branding (enrolled Auth API clients).
 */

package org.ezkey.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

/**
 * Request DTO for enrolled instance-info (proof-token authentication).
 *
 * @param enrollmentProofToken enrollment proof token identifying the enrollment
 * @since 2026
 */
@Schema(description = "Request DTO for integration-signed installation branding")
public record EnrollmentInstanceInfoRequestDto(
    @Schema(
            description = "Enrollment proof token for authentication",
            example = "abc123-def456-ghi789",
            requiredMode = RequiredMode.REQUIRED)
        String enrollmentProofToken) {}
