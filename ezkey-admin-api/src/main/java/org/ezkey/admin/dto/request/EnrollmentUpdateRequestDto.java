/*
 * Ezkey - Open Source MFA/Passkey Alternative
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
import jakarta.validation.constraints.Email;
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
 *   <li>expiresAt: Extend or shorten enrollment lifetime (must be in future if provided; null = no
 *       expiration)
 *   <li>authAttemptChallengeRequired: Security preference toggle per enrollment
 * </ul>
 *
 * <p><b>Optimistic Locking:</b> Include {@code version} from the GET response to prevent concurrent
 * update conflicts. When version mismatch occurs, the API returns 409 Conflict.
 *
 * <p><b>Constraints:</b> Only active, non-revoked VERIFIED enrollments can be updated.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @param version optimistic lock version from GET response (optional; when provided, enforces
 *     concurrency check)
 * @param enrollmentName new enrollment name (must be unique per integration for VERIFIED status)
 * @param contactEmail new contact email for the end-user
 * @param expiresAt new expiration timestamp (must be in future; null = no expiration)
 * @param authAttemptChallengeRequired whether auth attempts require challenge
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
            description = "Optional contact email for the end-user",
            example = "john@example.com",
            requiredMode = RequiredMode.NOT_REQUIRED)
        @Email(message = "Invalid email format")
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
        Boolean authAttemptChallengeRequired) {}
