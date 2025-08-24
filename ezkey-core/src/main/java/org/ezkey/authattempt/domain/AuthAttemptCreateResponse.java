/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Domain: AuthAttemptCreateResponse
 * Description: Domain response object for creating authentication attempts.
 */

package org.ezkey.authattempt.domain;

/**
 * Domain response object for creating authentication attempts.
 * <p>
 * This domain object represents the response data returned internally by the service layer
 * after successfully creating an authentication attempt. It contains the created attempt's
 * ID enabling tracking and monitoring of the authentication process.
 * </p>
 *
 * <p>
 * <b>Usage Context:</b> Used by the AuthAttemptService to return authentication
 * attempt creation results to both admin and auth API controllers. The service layer
 * transforms this domain object into appropriate API DTOs for external consumption.
 * </p>
 *
 * <p>
 * <b>Authentication Flow:</b> This response confirms that an authentication attempt
 * has been successfully created and is now pending user approval through their
 * enrolled mobile device. The authentication attempt ID can be used for tracking
 * and monitoring the authentication process.
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
 * @see org.ezkey.authattempt.service.AuthAttemptService
 * @see org.ezkey.authattempt.domain.AuthAttemptCreateRequest
 * @see org.ezkey.authattempt.domain.entity.AuthAttempt
 */
public class AuthAttemptCreateResponse {

    /**
     * Unique identifier of the created authentication attempt.
     * <p>
     * Used to reference this specific authentication attempt in subsequent
     * operations and tracking. This ID links the authentication request
     * to the user's approval or denial decision and enables monitoring
     * of the authentication flow status.
     * </p>
     */
    private Integer authAttemptId;

    /**
     * Gets the authentication attempt ID.
     *
     * @return the unique identifier of the created authentication attempt
     */
    public Integer getAuthAttemptId() {
        return authAttemptId;
    }

    /**
     * Sets the authentication attempt ID.
     *
     * @param authAttemptId the authentication attempt ID to set
     */
    public void setAuthAttemptId(Integer authAttemptId) {
        this.authAttemptId = authAttemptId;
    }

}