/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Record: EnrollmentCreateRequestDto
 * Description: Request DTO for creating enrollments in admin API.
 */

package org.ezkey.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for creating enrollments in admin API.
 *
 * <p>This DTO represents the request data needed to create a new enrollment through the admin API.
 * An enrollment links a user device to an integration and provides the foundation for MFA
 * authentication.
 *
 * <p><b>Usage Context:</b> Used by administrators to create enrollments that will later be bound to
 * mobile devices. The created enrollment generates codes and challenges that mobile devices use to
 * complete the enrollment process.
 *
 * <p><b>Fields:</b>
 *
 * <ul>
 *   <li><b>integrationId:</b> The integration this enrollment belongs to
 *   <li><b>name:</b> Human-readable name for the enrollment (e.g., "John's iPhone")
 *   <li><b>authAttemptChallengeRequired:</b> Whether challenges are required for auth attempts
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @param integrationId The integration ID to which this enrollment belongs (required)
 * @param name Human-readable name for the enrollment (e.g., "John's iPhone")
 * @param authAttemptChallengeRequired Whether authentication attempts require challenge validation
 * @author Ezkey contributors
 * @since 2025
 * @see org.ezkey.enrollment.domain.EnrollmentCreateRequest
 * @see EnrollmentCreateResponseDto
 */
@Schema(description = "Request DTO for creating new enrollments")
public record EnrollmentCreateRequestDto(
    /**
     * The integration ID to which this enrollment belongs. Must reference an existing and active
     * integration.
     */
    @Schema(
            description = "The integration ID to which this enrollment belongs",
            example = "1",
            required = true)
        @NotNull(message = "Integration ID is required")
        Integer integrationId,
    /**
     * Human-readable name for the enrollment. Helps identify the device or user associated with
     * this enrollment.
     */
    @Schema(
            description = "Human-readable name for the enrollment",
            example = "John's iPhone",
            required = true)
        @NotNull(message = "Enrollment name is required")
        @NotBlank(message = "Enrollment name cannot be blank")
        String name,
    /**
     * Indicates whether authentication attempts require challenge validation. When true, auth
     * attempts will include additional challenge data for verification.
     */
    @Schema(
            description = "Whether authentication attempts require challenge validation",
            example = "true")
        Boolean authAttemptChallengeRequired) {}
