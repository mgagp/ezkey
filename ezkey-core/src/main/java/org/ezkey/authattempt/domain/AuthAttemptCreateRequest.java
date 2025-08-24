/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Domain: AuthAttemptCreateRequest
 * Description: Domain request object for creating authentication attempts.
 */

package org.ezkey.authattempt.domain;

/**
 * Domain request object for creating authentication attempts.
 * <p>
 * This domain object represents the request data used internally by the service layer
 * to create new authentication attempts. It contains the enrollment information
 * and challenge requirements needed to initiate an authentication flow for a 
 * specific user enrollment.
 * </p>
 *
 * <p>
 * <b>Usage Context:</b> Used by the AuthAttemptService to process authentication
 * attempt creation requests from both admin and auth APIs. The service layer
 * transforms API DTOs into this domain object for business logic processing.
 * </p>
 *
 * <p>
 * <b>Authentication Flow:</b> This request initiates the authentication process
 * where an external system (integration) requests user authentication through
 * their enrolled mobile device. The mobile device will receive a pending
 * authentication notification and can approve or deny the request.
 * </p>
 *
 * <p>
 * <b>Challenge System:</b> The challengeRequested flag determines whether
 * additional user verification is required beyond cryptographic signatures.
 * When enabled, users must provide additional verification codes during
 * the authentication process.
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
 * @see org.ezkey.authattempt.domain.AuthAttemptCreateResponse
 * @see org.ezkey.authattempt.domain.entity.AuthAttempt
 */
public class AuthAttemptCreateRequest {

    /**
     * The enrollment ID for which to create the authentication attempt.
     * <p>
     * Must reference an existing and active enrollment. This links the
     * authentication request to a specific user's enrolled device, ensuring
     * that authentication notifications are sent to the correct mobile device.
     * </p>
     */
    private Integer enrollmentId;

    /**
     * Flag indicating whether additional challenge validation is requested.
     * <p>
     * When true, the authentication flow will require the user to provide
     * additional verification (such as a numeric code) beyond the standard
     * cryptographic signature. When false, only cryptographic validation
     * is required for authentication completion.
     * </p>
     */
    private Boolean challengeRequested;

    /**
     * Gets the enrollment ID for this authentication attempt.
     *
     * @return the enrollment ID
     */
    public Integer getEnrollmentId(){
        return enrollmentId;
    }

    /**
     * Sets the enrollment ID for this authentication attempt.
     *
     * @param enrollmentId the enrollment ID to set
     */
    public void setEnrollmentId(Integer enrollmentId){
        this.enrollmentId = enrollmentId;
    }

    /**
     * Gets whether additional challenge validation is requested.
     *
     * @return true if challenge is requested, false otherwise
     */
    public Boolean getChallengeRequested(){
        return challengeRequested;
    }

    /**
     * Sets whether additional challenge validation is requested.
     *
     * @param challengeRequested true to request challenge validation, false otherwise
     */
    public void setChallengeRequested(Boolean challengeRequested){
        this.challengeRequested = challengeRequested;
    }
}