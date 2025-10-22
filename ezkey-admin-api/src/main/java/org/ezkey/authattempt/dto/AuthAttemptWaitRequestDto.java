/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: AuthAttemptWaitRequestDto
 * Description: Request DTO for waiting for authentication response in admin API.
 */

package org.ezkey.authattempt.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for waiting for authentication response in admin API.
 *
 * <p>This DTO represents the request parameters for the authentication wait endpoint that allows
 * applications to poll for authentication completion. It provides configurable timeout and polling
 * interval parameters for flexible waiting behavior.
 *
 * <p><b>Usage Context:</b> Used by integrating applications to wait for mobile device responses to
 * authentication requests. This enables synchronous-like behavior in the asynchronous MFA
 * authentication flow.
 *
 * <p><b>Parameters:</b>
 *
 * <ul>
 *   <li><b>timeout:</b> Maximum duration to wait for authentication completion (seconds)
 *   <li><b>polling:</b> Interval between status checks during waiting (seconds)
 * </ul>
 *
 * <p><b>Security Note:</b> This endpoint is part of the admin API and should only be accessible to
 * authorized applications. The polling mechanism prevents excessive resource consumption while
 * providing responsive authentication status updates.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @param timeout maximum wait duration in seconds (1-300)
 * @param polling polling interval in seconds (1-60)
 * @author Ezkey contributors
 * @since 2025
 * @see AuthAttemptDto
 * @see org.ezkey.authattempt.domain.entity.AuthAttempt
 */
@Schema(description = "Request parameters for waiting for authentication response")
public record AuthAttemptWaitRequestDto(
    @Schema(
            description = "Maximum wait duration in seconds",
            example = "30",
            defaultValue = "30",
            minimum = "1",
            maximum = "300")
        @NotNull(message = "Timeout cannot be null")
        @Min(value = 1, message = "Timeout must be at least 1 second")
        @Max(value = 300, message = "Timeout cannot exceed 300 seconds")
        Integer timeout,
    @Schema(
            description = "Polling interval in seconds",
            example = "2",
            defaultValue = "2",
            minimum = "1",
            maximum = "60")
        @NotNull(message = "Polling cannot be null")
        @Min(value = 1, message = "Polling interval must be at least 1 second")
        @Max(value = 60, message = "Polling interval cannot exceed 60 seconds")
        Integer polling) {

    /**
     * Default constructor for AuthAttemptWaitRequestDto. Initializes with default values:
     * timeout=30, polling=2.
     */
    public AuthAttemptWaitRequestDto() {
        this(30, 2);
    }

    /**
     * Compact constructor with validation. Ensures polling interval is less than timeout to prevent
     * invalid configurations.
     *
     * @throws IllegalArgumentException if polling interval is greater than or equal to timeout
     */
    public AuthAttemptWaitRequestDto {
        if (timeout != null && polling != null && polling >= timeout) {
            throw new IllegalArgumentException(
                    "Polling interval must be less than timeout duration");
        }
    }
}