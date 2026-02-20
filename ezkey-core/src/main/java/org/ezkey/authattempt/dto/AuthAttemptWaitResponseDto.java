/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: AuthAttemptWaitResponseDto
 * Description: Response DTO from the wait endpoint, shared across API modules.
 */

package org.ezkey.authattempt.dto;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

/**
 * Response DTO from the wait endpoint of an API module.
 *
 * <p>
 * Provides a comprehensive view of the wait operation result, including the
 * current
 * authentication attempt state, a calculated status string, and metadata about
 * whether the
 * operation completed or timed out.
 *
 * <p>
 * Shared across admin-api and m2m-api.
 *
 * <p>
 * <b>Calculated Status Values:</b>
 *
 * <ul>
 * <li><b>PENDING</b> – request created but not yet read by the device
 * <li><b>READ</b> – device has read the request but not yet responded
 * <li><b>INVALID</b> – authentication failed validation (wrong signature,
 * challenge, etc.)
 * <li><b>REJECTED</b> – user explicitly rejected the authentication request
 * <li><b>ACCEPTED</b> – user accepted; proof token is available in
 * {@code authAttempt}
 * </ul>
 *
 * <p>
 * <b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p>
 * <b>License:</b> MIT
 *
 * @param authAttempt    current state of the authentication attempt
 * @param status         calculated authentication status string
 * @param completed      whether the device has responded (ACCEPTED, REJECTED,
 *                       or INVALID)
 * @param timeoutReached whether the wait ended due to timeout before a response
 * @param waitDuration   actual number of seconds waited before this response
 *                       was returned
 * @param completedAt    timestamp when the wait operation finished
 * @author Ezkey contributors
 * @since 2025
 */
@Schema(description = "Response from the wait endpoint after waiting for device authentication")
public record AuthAttemptWaitResponseDto(
    /** Current state of the authentication attempt. */
    @Schema(description = "Current authentication attempt state", requiredMode = RequiredMode.REQUIRED) AuthAttemptDto authAttempt,
    /**
     * Calculated authentication status.
     *
     * <ul>
     * <li><b>PENDING:</b> Authentication request created but not yet read by device
     * <li><b>READ:</b> Device has read the request but not yet responded
     * <li><b>INVALID:</b> Authentication was invalid (wrong signature, challenge,
     * etc.)
     * <li><b>REJECTED:</b> User rejected the authentication request
     * <li><b>ACCEPTED:</b> User accepted the authentication request
     * </ul>
     */
    @Schema(description = "Calculated authentication status", example = "ACCEPTED", allowableValues = {
        "PENDING", "READ", "INVALID", "REJECTED", "ACCEPTED" }, requiredMode = RequiredMode.REQUIRED) String status,
    /**
     * Indicates whether the authentication process is complete. True when the
     * device has responded
     * (status is ACCEPTED, REJECTED, or INVALID).
     */
    @Schema(description = "Whether authentication process is complete", example = "true", requiredMode = RequiredMode.REQUIRED) Boolean completed,
    /**
     * Indicates whether the wait operation ended due to timeout. True when the
     * maximum wait
     * duration was reached before completion.
     */
    @Schema(description = "Whether wait ended due to timeout", example = "false", requiredMode = RequiredMode.REQUIRED) Boolean timeoutReached,
    /**
     * Actual duration waited in seconds before returning the response. Useful for
     * monitoring and
     * debugging wait operations.
     */
    @Schema(description = "Actual duration waited in seconds", example = "15", requiredMode = RequiredMode.REQUIRED) Integer waitDuration,
    /**
     * Timestamp when the wait operation completed. Used for auditing and monitoring
     * purposes.
     */
    @Schema(description = "Timestamp when wait operation completed (with timezone)", example = "2025-01-27T10:30:15+01:00", requiredMode = RequiredMode.REQUIRED) OffsetDateTime completedAt) {
}
