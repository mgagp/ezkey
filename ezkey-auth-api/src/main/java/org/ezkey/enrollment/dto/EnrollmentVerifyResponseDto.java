/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: EnrollmentVerifyResponseDto
 * Description: Response DTO for enrollment verification completion in auth API.
 */

package org.ezkey.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

/**
 * Response DTO for enrollment verification completion in auth API.
 *
 * <p>This DTO represents the response data returned to mobile devices after they complete
 * enrollment verification. It provides confirmation of the verification status and indicates
 * whether the enrollment is now active and ready for MFA authentication flows.
 *
 * <p><b>Usage Context:</b> Returned by auth-api when mobile devices submit enrollment verification
 * requests. Provides immediate feedback on whether the verification was successful and the
 * enrollment is now active.
 *
 * <p><b>Enrollment Completion:</b> A successful response indicates that the mobile device is now
 * enrolled and can participate in authentication attempts for the associated integration.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @param active Whether the enrollment is now active and ready for authentication
 * @param enrollmentVerifyMessage User-facing message included in the signed verify result payload
 * @param enrollmentVerifyPayloadSignedByIntegration Ed25519 signature over the canonical verify
 *     result payload
 * @author Ezkey contributors
 * @since 2025
 * @see org.ezkey.enrollment.domain.EnrollmentVerifyResponse
 * @see EnrollmentVerifyRequestDto
 */
@Schema(description = "Response DTO for enrollment verification completion")
public record EnrollmentVerifyResponseDto(
    @Schema(
            description = "Whether the enrollment is now active and ready for authentication",
            example = "true",
            requiredMode = RequiredMode.REQUIRED)
        boolean active,
    @Schema(
            description =
                "Human-readable verify completion message (included in the integration signature)",
            example = "Enrollment verified successfully",
            requiredMode = RequiredMode.REQUIRED)
        String enrollmentVerifyMessage,
    @Schema(
            description =
                "Ed25519 signature (Base64URL, no padding, raw 64 bytes) over"
                    + " proofToken|enrollmentId|VERIFIED|message (see"
                    + " docs/ENROLLMENT_SIGNATURE_PAYLOAD.md)",
            example =
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
            requiredMode = RequiredMode.REQUIRED)
        String enrollmentVerifyPayloadSignedByIntegration) {}
