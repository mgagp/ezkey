/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Record: EnrollmentCreateResponseDto
 * Description: Response DTO for enrollment creation in admin API.
 */

package org.ezkey.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/**
 * Response DTO for enrollment creation in admin API.
 *
 * <p>This DTO represents the response data returned when an enrollment is successfully created
 * through the admin API. It contains the essential information needed to identify and use the newly
 * created enrollment.
 *
 * <p><b>Usage Context:</b> Returned by admin API when creating enrollments. The enrollment ID can
 * be used for subsequent operations, and the challenge can be used for enrollment verification
 * processes.
 *
 * <p><b>Core Fields:</b>
 *
 * <ul>
 *   <li><b>enrollmentId:</b> Unique identifier of the created enrollment
 *   <li><b>enrollmentChallenge:</b> Challenge number for enrollment verification
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @param enrollmentId Unique identifier of the created enrollment used to reference this enrollment
 *     in subsequent operations
 * @param enrollmentChallenge Challenge number generated for enrollment verification used during the
 *     enrollment binding and verification process
 * @param expiresAt Invitation expiry for the pending bind/verify window, if configured or explicitly
 *     set
 * @author Ezkey contributors
 * @since 2025
 * @see org.ezkey.enrollment.domain.EnrollmentCreateResponse
 * @see EnrollmentCreateRequestDto
 */
@Schema(description = "Response DTO containing created enrollment details")
public record EnrollmentCreateResponseDto(
    @Schema(description = "Unique identifier of the created enrollment", example = "21")
        Integer enrollmentId,
    @Schema(description = "Challenge number for enrollment verification", example = "154982")
        Integer enrollmentChallenge,
    @Schema(
            description =
                "Invitation expiry (UTC) for the pending enrollment phase, when set on the server")
        OffsetDateTime expiresAt) {}
