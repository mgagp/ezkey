/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Domain: AuthAttemptWaitResponse
 * Description: Domain object for authentication wait response data.
 */

package org.ezkey.authattempt.domain;

import java.time.OffsetDateTime;

import org.ezkey.authattempt.domain.entity.AuthAttempt;

/**
 * Domain object for authentication wait response data.
 * <p>
 * This domain object represents the response from the authentication wait operation that
 * provides the final authentication status after polling for completion. It includes
 * both the raw authentication attempt data and calculated status information for easy
 * consumption by the service layer.
 * </p>
 *
 * <p>
 * <b>Usage Context:</b> Used by the service layer to return authentication wait results.
 * This domain object is mapped to the corresponding DTO and contains the business logic
 * data for authentication status and polling results.
 * </p>
 *
 * <p>
 * <b>Response Fields:</b>
 * <ul>
 * <li><b>authAttempt:</b> Complete authentication attempt data</li>
 * <li><b>status:</b> Calculated status based on authentication state</li>
 * <li><b>completed:</b> Whether the authentication process is complete</li>
 * <li><b>timeoutReached:</b> Whether the wait ended due to timeout</li>
 * <li><b>waitDuration:</b> Actual duration waited in seconds</li>
 * <li><b>completedAt:</b> Timestamp when wait operation completed</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Status Values:</b>
 * <ul>
 * <li><b>PENDING:</b> Authentication request created but not yet read by device</li>
 * <li><b>READ:</b> Device has read the request but not yet responded</li>
 * <li><b>INVALID:</b> Authentication was invalid (wrong signature, challenge, etc.)</li>
 * <li><b>REJECTED:</b> User rejected the authentication request</li>
 * <li><b>ACCEPTED:</b> User accepted the authentication request</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 * </p>
 * <p>
 * <b>License:</b> MIT
 * </p>
 *
 * @author Ezkey contributors
 * @since 2025
 * @see org.ezkey.authattempt.dto.AuthAttemptWaitResponseDto
 * @see org.ezkey.authattempt.domain.entity.AuthAttempt
 */
public class AuthAttemptWaitResponse {

    /**
     * Complete authentication attempt data.
     * Contains all the raw authentication attempt information for detailed processing.
     */
    private AuthAttempt authAttempt;

    /**
     * Calculated authentication status based on the rules defined in ENDPOINT.md.
     * Provides a simplified status for easy integration logic.
     */
    private String status;

    /**
     * Indicates whether the authentication process is complete.
     * True when the device has responded (status is ACCEPTED, REJECTED, or INVALID).
     */
    private Boolean completed;

    /**
     * Indicates whether the wait operation ended due to timeout.
     * True when the maximum wait duration was reached before completion.
     */
    private Boolean timeoutReached;

    /**
     * Actual duration waited in seconds before returning the response.
     * Useful for monitoring and debugging wait operations.
     */
    private Integer waitDuration;

    /**
     * Timestamp when the wait operation completed.
     * Used for auditing and monitoring purposes.
     */
    private OffsetDateTime completedAt;

    /**
     * Default constructor for AuthAttemptWaitResponse.
     */
    public AuthAttemptWaitResponse() {
    }

    /**
     * Constructor with all required fields.
     *
     * @param authAttempt the authentication attempt data
     * @param status the calculated status
     * @param completed whether authentication is complete
     * @param timeoutReached whether timeout was reached
     * @param waitDuration actual wait duration in seconds
     * @param completedAt timestamp when wait completed
     */
    public AuthAttemptWaitResponse(AuthAttempt authAttempt, String status, Boolean completed, 
                                  Boolean timeoutReached, Integer waitDuration, OffsetDateTime completedAt) {
        this.authAttempt = authAttempt;
        this.status = status;
        this.completed = completed;
        this.timeoutReached = timeoutReached;
        this.waitDuration = waitDuration;
        this.completedAt = completedAt;
    }

    /**
     * Gets the authentication attempt data.
     *
     * @return the complete authentication attempt data
     */
    public AuthAttempt getAuthAttempt() {
        return authAttempt;
    }

    /**
     * Sets the authentication attempt data.
     *
     * @param authAttempt the authentication attempt data to set
     */
    public void setAuthAttempt(AuthAttempt authAttempt) {
        this.authAttempt = authAttempt;
    }

    /**
     * Gets the calculated authentication status.
     *
     * @return the calculated status (PENDING, READ, INVALID, REJECTED, ACCEPTED)
     */
    public String getStatus() {
        return status;
    }

    /**
     * Sets the calculated authentication status.
     *
     * @param status the calculated status to set
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * Gets whether the authentication process is complete.
     *
     * @return true if authentication is complete, false otherwise
     */
    public Boolean getCompleted() {
        return completed;
    }

    /**
     * Sets whether the authentication process is complete.
     *
     * @param completed true if authentication is complete, false otherwise
     */
    public void setCompleted(Boolean completed) {
        this.completed = completed;
    }

    /**
     * Gets whether the wait operation ended due to timeout.
     *
     * @return true if timeout was reached, false otherwise
     */
    public Boolean getTimeoutReached() {
        return timeoutReached;
    }

    /**
     * Sets whether the wait operation ended due to timeout.
     *
     * @param timeoutReached true if timeout was reached, false otherwise
     */
    public void setTimeoutReached(Boolean timeoutReached) {
        this.timeoutReached = timeoutReached;
    }

    /**
     * Gets the actual wait duration in seconds.
     *
     * @return the wait duration in seconds
     */
    public Integer getWaitDuration() {
        return waitDuration;
    }

    /**
     * Sets the actual wait duration in seconds.
     *
     * @param waitDuration the wait duration in seconds to set
     */
    public void setWaitDuration(Integer waitDuration) {
        this.waitDuration = waitDuration;
    }

    /**
     * Gets the timestamp when the wait operation completed.
     *
     * @return the completion timestamp
     */
    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    /**
     * Sets the timestamp when the wait operation completed.
     *
     * @param completedAt the completion timestamp to set
     */
    public void setCompletedAt(OffsetDateTime completedAt) {
        this.completedAt = completedAt;
    }

}
