/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Record: EnrollmentCreateRequestDto
 * Description: Request DTO for creating enrollments in admin API.
 */

package org.ezkey.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

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
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @param integrationId The integration ID to which this enrollment belongs (required)
 * @param name Human-readable name for the enrollment (e.g., "John's iPhone"); 1 to 64 characters,
 *     the {@code enrollment_name} column width
 * @param authAttemptChallengeRequired Whether authentication attempts require challenge validation
 * @param contactEmail Optional contact email for the end-user
 * @param contactPhoneNumber Optional contact phone number for the end-user
 * @param userIdentifier Optional user identifier from the integrating application; at most 255
 *     characters, the {@code user_identifier} column width
 * @param expiresAt Optional invitation expiry for the pending bind/verify window; must be in the
 *     future when provided
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
            requiredMode = RequiredMode.REQUIRED)
        @NotNull(message = "Integration ID is required")
        Integer integrationId,
    /**
     * Human-readable name for the enrollment. Helps identify the device or user associated with
     * this enrollment.
     */
    @Schema(
            description = "Human-readable name for the enrollment",
            example = "John's iPhone",
            maxLength = 64,
            requiredMode = RequiredMode.REQUIRED)
        @NotNull(message = "Enrollment name is required")
        @NotBlank(message = "Enrollment name cannot be blank")
        @Size(min = 1, max = 64, message = "Enrollment name must be 1 to 64 characters")
        String name,
    /**
     * Indicates whether authentication attempts require challenge validation. When true, auth
     * attempts will include additional challenge data for verification.
     */
    @Schema(
            description = "Whether authentication attempts require challenge validation",
            example = "true")
        Boolean authAttemptChallengeRequired,
    /**
     * Optional contact email for the end-user (device owner). Used for incident response,
     * revocation notices, support.
     */
    @Schema(description = "Optional contact email for the end-user") String contactEmail,
    /**
     * Optional contact phone number for the end-user (device owner). Accepts common separators and
     * is normalized to E.164 on write.
     */
    @Schema(description = "Optional contact phone number for the end-user")
        String contactPhoneNumber,
    /**
     * Optional reference to the integrating app's user (username, user_id). Unique per integration
     * for lookup. Enables future auth attempt creation by userIdentifier.
     */
    @Schema(
            description = "Optional user identifier from the integrating application",
            maxLength = 255)
        @Size(max = 255, message = "User identifier must not exceed 255 characters")
        String userIdentifier,
    /**
     * Optional expiration for the pending enrollment invitation (bind/verify window only). When
     * omitted, the instance uses {@code ezkey.enrollment.pending-expiration-days} (default 7 days).
     */
    @Schema(
            description =
                "Optional invitation expiry (UTC instant). Pending phase only; must be in the"
                    + " future when set. Omit to use ezkey.enrollment.pending-expiration-days.",
            example = "2026-12-31T23:59:59Z")
        @Future(message = "expiresAt must be in the future")
        OffsetDateTime expiresAt) {

  /**
   * Trims {@code name} and {@code userIdentifier} before validation, so the length bounds apply to
   * the value that is stored (#1457).
   */
  public EnrollmentCreateRequestDto {
    name = name != null ? name.trim() : null;
    userIdentifier = userIdentifier != null ? userIdentifier.trim() : null;
  }
}
