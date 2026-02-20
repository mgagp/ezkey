/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: AuthAttemptCreateRequestDto
 * Description: Request DTO for creating a new authentication attempt via the M2M API.
 */

package org.ezkey.authattempt.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for creating a new authentication attempt via the M2M API.
 *
 * <p>Supports two identification strategies for locating the target enrollment:
 *
 * <ul>
 *   <li><b>enrollmentId</b>: Direct reference to the enrollment (preferred for precision)
 *   <li><b>userIdentifier</b>: Indirect lookup by user identifier (convenient for single-device
 *       users)
 * </ul>
 *
 * <p>At least one of {@code enrollmentId} or {@code userIdentifier} must be provided. When both are
 * given, they are validated for consistency. The integration is always derived from the API key
 * credential used for authentication.
 *
 * <p><b>Note:</b> {@code integrationId} is NOT required here because it is always derived from the
 * API key used to authenticate the request. This differs from the admin API where an admin may
 * specify a target integration explicitly.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @param enrollmentId direct enrollment ID (optional; required if userIdentifier not provided)
 * @param userIdentifier the user identifier to look up a verified enrollment (optional; required if
 *     enrollmentId not provided)
 * @param challengeRequested whether a numeric challenge should be generated and sent to the device
 * @author Ezkey contributors
 * @since 2025
 */
@Schema(description = "Request body for creating a new authentication attempt")
public record AuthAttemptCreateRequestDto(
    /**
     * Direct reference to the enrollment. Optional when userIdentifier is provided; when both are
     * given they must refer to the same enrollment.
     */
    @Schema(
            description = "Enrollment ID (optional if userIdentifier is provided)",
            example = "7",
            requiredMode = RequiredMode.NOT_REQUIRED)
        Integer enrollmentId,
    /**
     * User identifier used to locate the verified enrollment within the API key's integration.
     * Optional when enrollmentId is provided.
     */
    @Schema(
            description =
                "User identifier for enrollment lookup (optional if enrollmentId is provided)",
            example = "john.doe@example.com",
            requiredMode = RequiredMode.NOT_REQUIRED)
        String userIdentifier,
    /** Whether to generate and send a numeric challenge code to the mobile device. */
    @NotNull
        @Schema(
            description = "Whether to generate a numeric challenge for additional verification",
            example = "true",
            requiredMode = RequiredMode.REQUIRED)
        Boolean challengeRequested) {}
