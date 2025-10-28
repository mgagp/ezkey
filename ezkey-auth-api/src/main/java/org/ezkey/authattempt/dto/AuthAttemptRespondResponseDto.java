/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: AuthAttemptRespondResponseDto
 * Description: Response DTO for authentication attempt submissions in auth API.
 */

package org.ezkey.authattempt.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response DTO for authentication attempt submissions in auth API.
 *
 * <p>This DTO represents the response data returned to mobile devices after they submit their
 * authentication attempt response. It provides clear, unambiguous feedback on the authentication
 * result.
 *
 * <p><b>Usage Context:</b> Returned by auth-api when mobile devices submit authentication
 * responses. Provides immediate feedback on the authentication result.
 *
 * <p><b>Response Handling:</b> The mobile app should check the result field to determine the
 * authentication outcome. The message field provides additional context for user feedback.
 *
 * <p><b>Fields:</b>
 *
 * <ul>
 *   <li><b>result:</b> The authentication result (APPROVED, DENIED, FAILED, EXPIRED)
 *   <li><b>message:</b> Additional information or error details
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see org.ezkey.authattempt.domain.AuthAttemptRespondResponse
 * @see AuthAttemptRespondRequestDto
 */
@Schema(description = "Response DTO for authentication attempt submissions")
public record AuthAttemptRespondResponseDto(
    /**
     * The authentication result indicating the outcome of the authentication attempt.
     *
     * <p>Provides clear, unambiguous states: - APPROVED: User approved the authentication - DENIED:
     * User denied the authentication - FAILED: Technical error occurred
     */
    @Schema(
            description = "The authentication result",
            example = "APPROVED",
            allowableValues = {"APPROVED", "DENIED", "FAILED", "EXPIRED"},
            required = true)
        String result,

    /**
     * Additional message providing context about the authentication result.
     *
     * <p>Contains success confirmation or detailed error information for user feedback. Mobile apps
     * can display this message to inform users about the authentication attempt status.
     */
    @Schema(
            description = "Success confirmation or error details for user feedback",
            example = "Authentication approved",
            required = true)
        String message) {}
