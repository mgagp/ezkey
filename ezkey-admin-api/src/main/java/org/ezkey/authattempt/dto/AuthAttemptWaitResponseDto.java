/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Record: AuthAttemptWaitResponseDto
 * Description: Response DTO for waiting for authentication response in admin API.
 */

package org.ezkey.authattempt.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.time.OffsetDateTime;

/**
 * Response DTO for waiting for authentication response in admin API.
 *
 * <p>This DTO represents the response from the authentication wait endpoint that provides the final
 * authentication status after polling for completion. It includes both the raw authentication
 * attempt data and a calculated status field for easy consumption by integrating applications.
 *
 * <p><b>Usage Context:</b> Returned by the wait endpoint to provide applications with the final
 * authentication result after the device has responded or timeout has been reached. The calculated
 * status field simplifies integration logic.
 *
 * <p><b>Status Calculation Rules (from ENDPOINT.md):</b>
 *
 * <ol>
 *   <li><b>PENDING:</b> authAttemptRead is null or false
 *   <li><b>READ:</b> authAttemptRead is true and authAttemptResponded is null or false
 *   <li><b>INVALID:</b> authAttemptValid is null or false
 *   <li><b>REJECTED:</b> authAttemptAccepted is null or false
 *   <li><b>ACCEPTED:</b> authAttemptAccepted is true
 * </ol>
 *
 * <p><b>Response Fields:</b>
 *
 * <ul>
 *   <li><b>authAttempt:</b> Complete authentication attempt data
 *   <li><b>status:</b> Calculated status based on authentication state
 *   <li><b>completed:</b> Whether the authentication process is complete
 *   <li><b>timeoutReached:</b> Whether the wait ended due to timeout
 *   <li><b>waitDuration:</b> Actual duration waited in seconds
 *   <li><b>completedAt:</b> Timestamp when wait operation completed
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @param authAttempt complete authentication attempt data
 * @param status calculated authentication status (PENDING, READ, INVALID, REJECTED, ACCEPTED)
 * @param completed whether the authentication process is complete
 * @param timeoutReached whether the wait operation ended due to timeout
 * @param waitDuration actual duration waited in seconds
 * @param completedAt timestamp when the wait operation completed (with timezone)
 * @author Ezkey contributors
 * @since 2025
 * @see AuthAttemptDto
 * @see AuthAttemptWaitRequestDto
 * @see org.ezkey.authattempt.domain.entity.AuthAttempt
 */
@Schema(description = "Response DTO for authentication wait operation")
public record AuthAttemptWaitResponseDto(
    /**
     * Complete authentication attempt data. Contains all the raw authentication attempt information
     * for detailed processing.
     */
    @Schema(
            description = "Complete authentication attempt data",
            requiredMode = RequiredMode.REQUIRED)
        AuthAttemptDto authAttempt,
    /**
     * Calculated authentication status based on the rules defined in ENDPOINT.md. Provides a
     * simplified status for easy integration logic.
     *
     * <p><b>Possible Values:</b>
     *
     * <ul>
     *   <li><b>PENDING:</b> Authentication request created but not yet read by device
     *   <li><b>READ:</b> Device has read the request but not yet responded
     *   <li><b>INVALID:</b> Authentication was invalid (wrong signature, challenge, etc.)
     *   <li><b>REJECTED:</b> User rejected the authentication request
     *   <li><b>ACCEPTED:</b> User accepted the authentication request
     * </ul>
     */
    @Schema(
            description = "Calculated authentication status",
            example = "ACCEPTED",
            allowableValues = {"PENDING", "READ", "INVALID", "REJECTED", "ACCEPTED"},
            requiredMode = RequiredMode.REQUIRED)
        String status,
    /**
     * Indicates whether the authentication process is complete. True when the device has responded
     * (status is ACCEPTED, REJECTED, or INVALID).
     */
    @Schema(
            description = "Whether authentication process is complete",
            example = "true",
            requiredMode = RequiredMode.REQUIRED)
        Boolean completed,
    /**
     * Indicates whether the wait operation ended due to timeout. True when the maximum wait
     * duration was reached before completion.
     */
    @Schema(
            description = "Whether wait ended due to timeout",
            example = "false",
            requiredMode = RequiredMode.REQUIRED)
        Boolean timeoutReached,
    /**
     * Actual duration waited in seconds before returning the response. Useful for monitoring and
     * debugging wait operations.
     */
    @Schema(
            description = "Actual duration waited in seconds",
            example = "15",
            requiredMode = RequiredMode.REQUIRED)
        Integer waitDuration,
    /** Timestamp when the wait operation completed. Used for auditing and monitoring purposes. */
    @Schema(
            description = "Timestamp when wait operation completed (with timezone)",
            example = "2025-01-27T10:30:15+01:00",
            requiredMode = RequiredMode.REQUIRED)
        OffsetDateTime completedAt) {}
