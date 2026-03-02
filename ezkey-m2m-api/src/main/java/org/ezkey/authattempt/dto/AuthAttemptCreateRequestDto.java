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

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

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
 * @param contextTitle optional short title for the approval request (max 200 chars)
 * @param contextMessage optional descriptive message for the approver (max 2 000 chars)
 * @author Ezkey contributors
 * @since 2025
 */
@Schema(
    description =
        "Request body for creating a new authentication attempt. Optional context fields "
            + "(contextTitle, contextMessage) attach business context visible to the approver "
            + "on the mobile device.")
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
        Boolean challengeRequested,
    /**
     * Optional short title displayed as the card header on the mobile device (max 200 characters).
     * Example: "Payment Approval", "Deploy Confirmation".
     */
    @Schema(
            description =
                "Optional short title for the approval request, displayed as the mobile card"
                    + " header. Max 200 characters.",
            example = "Payment Approval",
            requiredMode = RequiredMode.NOT_REQUIRED)
        @Size(max = 200, message = "contextTitle must not exceed 200 characters")
        @JsonProperty("contextTitle")
        String contextTitle,
    /**
     * Optional descriptive message providing the approver with full business context (max 2 000
     * characters).
     */
    @Schema(
            description =
                "Optional descriptive message explaining what the approver is authorizing."
                    + " Max 2000 characters.",
            example = "Authorize payment batch #1497 to Acme Corp for $1,400",
            requiredMode = RequiredMode.NOT_REQUIRED)
        @Size(max = 2000, message = "contextMessage must not exceed 2000 characters")
        @JsonProperty("contextMessage")
        String contextMessage) {}
