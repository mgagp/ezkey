/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Record: AuthAttemptCreateRequestDto
 * Description: Request DTO for creating authorization attempts in admin API.
 */

package org.ezkey.authattempt.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating authorization attempts in admin API.
 *
 * <p>This DTO represents the request data needed to create a new authorization attempt through the
 * admin API. It supports two identification modes:
 *
 * <ul>
 *   <li><b>By enrollmentId:</b> Direct reference to an enrollment (legacy, SDK, admin)
 *   <li><b>By userIdentifier:</b> User reference (e.g. username); resolves to enrollment within
 *       integration scope. When using API key, integrationId is derived from the key. When using
 *       admin token, integrationId must be provided in the request.
 * </ul>
 *
 * <p>When both enrollmentId and userIdentifier are provided, the API validates that they resolve to
 * the same enrollment (consistency check). If they conflict, returns 400.
 *
 * <p><b>Multi-device:</b> When userIdentifier matches multiple enrollments (user has multiple
 * devices), the API returns 400 with a message to specify enrollmentId.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @param enrollmentId The enrollment ID (optional if userIdentifier provided)
 * @param userIdentifier The user identifier for lookup within integration scope (optional if
 *     enrollmentId provided)
 * @param integrationId Required when userIdentifier is used with admin token; ignored when API key
 *     (derived from key)
 * @param challengeRequested Whether a challenge is requested for this attempt
 * @param contextTitle Optional short title displayed as the card header on the mobile device (max
 *     200 chars). Example: "Payment Approval", "Deploy Confirmation".
 * @param contextMessage Optional descriptive message for the approver (max 2 000 chars). Example:
 *     "Authorize payment batch #1497 to Acme Corp for $1,400".
 * @param demoMitmSignatureRequested Optional demo-only: opt into simulated MITM on Pending when
 *     Auth API demo MITM is enabled
 * @author Ezkey contributors
 * @since 2025
 * @see org.ezkey.authattempt.domain.AuthAttemptCreateRequest
 * @see AuthAttemptCreateResponseDto
 */
@Schema(
    description =
        "Request to create an auth attempt. Provide enrollmentId OR userIdentifier. "
            + "When userIdentifier is used with admin token, integrationId is required. "
            + "Optional context fields (contextTitle, contextMessage) attach business context "
            + "visible to the approver on the mobile device. Optional demoMitmSignatureRequested "
            + "flags the attempt for tampered Pending responses in demo mode.")
public record AuthAttemptCreateRequestDto(
    @Schema(
            description =
                "Enrollment ID (use this OR userIdentifier). Direct reference to enrollment.",
            example = "123")
        @JsonProperty("enrollmentId")
        Integer enrollmentId,
    @Schema(
            description =
                "User identifier (username, user_id) for lookup within integration scope. Use this"
                    + " OR enrollmentId. When used with admin token, integrationId is required.",
            example = "alice")
        @JsonProperty("userIdentifier")
        String userIdentifier,
    @Schema(
            description =
                "Integration ID. Required when userIdentifier is used with admin token. "
                    + "Ignored when API key (derived from credentials).",
            example = "1")
        @JsonProperty("integrationId")
        Integer integrationId,
    @Schema(
            description = "Whether a challenge code is requested for this attempt",
            example = "false",
            requiredMode = RequiredMode.REQUIRED)
        @NotNull(message = "Challenge requested flag is required")
        @JsonProperty("challengeRequested")
        Boolean challengeRequested,
    /**
     * Optional short title displayed as the card header on the mobile device (max 200 characters).
     * When present, replaces the generic "PENDING" label in the mobile UI.
     */
    @Schema(
            description =
                "Optional short title for the approval request, displayed as the mobile card"
                    + " header. Max 200 characters. Example: \"Payment Approval\"",
            example = "Payment Approval",
            requiredMode = RequiredMode.NOT_REQUIRED)
        @Size(max = 200, message = "contextTitle must not exceed 200 characters")
        @JsonProperty("contextTitle")
        String contextTitle,
    /**
     * Optional descriptive message providing the approver with full business context (max 2 000
     * characters). Displayed as the card body on the mobile device.
     */
    @Schema(
            description =
                "Optional descriptive message explaining what the approver is authorizing."
                    + " Max 2000 characters.",
            example = "Authorize payment batch #1497 to Acme Corp for $1,400",
            requiredMode = RequiredMode.NOT_REQUIRED)
        @Size(max = 2000, message = "contextMessage must not exceed 2000 characters")
        @JsonProperty("contextMessage")
        String contextMessage,
    @Schema(
            description =
                "Demo only: when true, this attempt is flagged for simulated MITM (tampered Pending"
                    + " body after signing) if Auth API ezkey.demo.mitm-signature-enabled is true.",
            example = "false",
            requiredMode = RequiredMode.NOT_REQUIRED)
        @JsonProperty("demoMitmSignatureRequested")
        Boolean demoMitmSignatureRequested) {}
