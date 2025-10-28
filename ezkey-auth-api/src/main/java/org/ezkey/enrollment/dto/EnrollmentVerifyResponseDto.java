/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: EnrollmentVerifyResponseDto
 * Description: Response DTO for enrollment verification completion in auth API.
 */

package org.ezkey.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;

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
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @param active Whether the enrollment is now active and ready for authentication
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
            required = true)
        boolean active) {}
