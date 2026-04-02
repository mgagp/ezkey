/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: AdminUpdateRequestDto
 * Description: Request DTO for partial update of administrator profile.
 */

package org.ezkey.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for partial update of an administrator profile.
 *
 * <p>Uses partial-update semantics: only non-null fields are applied. Fields set to {@code null} in
 * the JSON body are ignored and the existing values are preserved.
 *
 * <p><b>Updatable Fields:</b>
 *
 * <ul>
 *   <li>firstName, lastName: Typo correction, legal name change
 *   <li>email: Contact info correction (must be unique)
 *   <li>phoneNumber: Contact phone correction (normalized to E.164 on write)
 *   <li>challengeRequired: Security preference toggle for login challenge
 * </ul>
 *
 * <p><b>Optimistic Locking:</b> Include {@code version} from the GET response to prevent concurrent
 * update conflicts. When version mismatch occurs, the API returns 409 Conflict.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @param version optimistic lock version from GET response (optional; when provided, enforces
 *     concurrency check)
 * @param firstName new first name
 * @param lastName new last name
 * @param email new email address (must be unique)
 * @param phoneNumber new contact phone number
 * @param challengeRequired whether challenge verification is required during passwordless login
 * @author Ezkey contributors
 * @since 2025
 */
@Schema(description = "Request DTO for partial update of administrator profile")
public record AdminUpdateRequestDto(
    @Schema(
            description =
                "Optimistic lock version from GET response. When provided, update fails with 409 if"
                    + " resource was modified since last fetch.",
            example = "0",
            requiredMode = RequiredMode.NOT_REQUIRED)
        Long version,
    @Schema(
            description = "First name of the administrator",
            example = "John",
            requiredMode = RequiredMode.NOT_REQUIRED)
        @Size(max = 100, message = "First name must not exceed 100 characters")
        String firstName,
    @Schema(
            description = "Last name of the administrator",
            example = "Doe",
            requiredMode = RequiredMode.NOT_REQUIRED)
        @Size(max = 100, message = "Last name must not exceed 100 characters")
        String lastName,
    @Schema(
            description = "Email address (must be unique)",
            example = "john.doe@example.com",
            requiredMode = RequiredMode.NOT_REQUIRED)
        @Email(message = "Invalid email format")
        @Size(max = 255, message = "Email must not exceed 255 characters")
        String email,
    @Schema(
            description =
                "Phone number for the administrator. Accepts common separators and is normalized"
                    + " to E.164 on write.",
            example = "+1 514 555 1234",
            requiredMode = RequiredMode.NOT_REQUIRED)
        @Size(max = 50, message = "Phone number must not exceed 50 characters")
        String phoneNumber,
    @Schema(
            description = "Whether challenge verification is required during passwordless login",
            example = "false",
            requiredMode = RequiredMode.NOT_REQUIRED)
        Boolean challengeRequired) {}
