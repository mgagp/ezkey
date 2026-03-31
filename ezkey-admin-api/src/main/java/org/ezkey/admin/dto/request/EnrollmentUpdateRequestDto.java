/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: EnrollmentUpdateRequestDto
 * Description: Request DTO for partial update of enrollment metadata.
 */

package org.ezkey.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

/**
 * Request DTO for partial update of enrollment metadata.
 *
 * <p>Uses partial-update semantics: only non-null fields are applied. Fields set to {@code null} in
 * the JSON body are ignored and the existing values are preserved.
 *
 * <p><b>Updatable Fields:</b>
 *
 * <ul>
 *   <li>enrollmentName: Typo correction, device renamed (must be unique per integration for
 *       VERIFIED)
 *   <li>contactEmail: Contact info correction
 *   <li>expiresAt: Pending invitation window only (bind/verify); must be in future if provided
 *   <li>authAttemptChallengeRequired: Security preference toggle per enrollment
 *   <li>userIdentifier: Optional app user reference (correlation); unique per integration where
 *       enforced by policy
 * </ul>
 *
 * <p><b>Optimistic Locking:</b> Include {@code version} from the GET response to prevent concurrent
 * update conflicts. When version mismatch occurs, the API returns 409 Conflict.
 *
 * <p><b>Constraints:</b> Only active, non-revoked VERIFIED enrollments can be updated.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @param version optimistic lock version from GET response (optional; when provided, enforces
 *     concurrency check)
 * @param enrollmentName new enrollment name (must be unique per integration for VERIFIED status)
 * @param contactEmail new contact email for the end-user
 * @param expiresAt new expiration timestamp (must be in future; null = no expiration)
 * @param authAttemptChallengeRequired whether auth attempts require challenge
 * @param userIdentifier optional integrating-app user reference
 * @param clearContactEmail when true, clears {@code contactEmail}; takes precedence over {@code
 *     contactEmail}
 * @param clearExpiresAt when true, clears {@code expiresAt}; ignored when {@code expiresAt} is
 *     non-null (new expiry wins)
 * @author Ezkey contributors
 * @since 2025
 */
@Schema(description = "Request DTO for partial update of enrollment metadata")
public record EnrollmentUpdateRequestDto(
    @Schema(
            description =
                "Optimistic lock version from GET response. When provided, update fails with 409 if"
                    + " resource was modified since last fetch.",
            example = "0",
            requiredMode = RequiredMode.NOT_REQUIRED)
        Long version,
    @Schema(
            description = "Human-readable name for the enrollment",
            example = "John's iPhone",
            requiredMode = RequiredMode.NOT_REQUIRED)
        @Size(max = 255, message = "Enrollment name must not exceed 255 characters")
        String enrollmentName,
    @Schema(
            description =
                "Optional contact email for the end-user (validated when non-blank; use"
                    + " clearContactEmail to remove)",
            example = "john@example.com",
            requiredMode = RequiredMode.NOT_REQUIRED)
        @Size(max = 255, message = "Contact email must not exceed 255 characters")
        String contactEmail,
    @Schema(
            description =
                "Optional expiration timestamp (must be in future). Null = no expiration.",
            requiredMode = RequiredMode.NOT_REQUIRED)
        OffsetDateTime expiresAt,
    @Schema(
            description = "Whether authentication attempts require challenge",
            example = "false",
            requiredMode = RequiredMode.NOT_REQUIRED)
        Boolean authAttemptChallengeRequired,
    @Schema(
            description = "Optional user identifier from the integrating application",
            requiredMode = RequiredMode.NOT_REQUIRED)
        @Size(max = 255, message = "User identifier must not exceed 255 characters")
        String userIdentifier,
    @Schema(
            description =
                "When true, clears contact email. Takes precedence over contactEmail in the same"
                    + " request.",
            requiredMode = RequiredMode.NOT_REQUIRED)
        Boolean clearContactEmail,
    @Schema(
            description =
                "When true, clears invitation expiry (expiresAt). Ignored if expiresAt is set to a"
                    + " non-null instant in the same request.",
            requiredMode = RequiredMode.NOT_REQUIRED)
        Boolean clearExpiresAt) {}
