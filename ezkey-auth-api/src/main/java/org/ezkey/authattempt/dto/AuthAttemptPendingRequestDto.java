/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: AuthAttemptPendingRequestDto
 * Description: Request DTO for checking pending authentication attempts in auth API.
 */

package org.ezkey.authattempt.dto;

/**
 * Request DTO for checking pending authentication attempts in auth API.
 * <p>
 * This DTO represents the request data sent by mobile devices to check for
 * pending authentication attempts. It contains cryptographic signatures that
 * prove the authenticity of the request and ensure that only legitimate
 * enrolled devices can access pending authentication requests.
 * </p>
 *
 * <p>
 * <b>Usage Context:</b> Used by mobile devices to poll the auth-api for pending
 * authentication requests. The mobile app calls this endpoint with cryptographic
 * proof to retrieve authentication challenges waiting for user approval.
 * </p>
 *
 * <p>
 * <b>Security Model:</b> Contains cryptographic signatures that validate the
 * device's identity and ensure request authenticity. This prevents unauthorized
 * access to pending authentication attempts.
 * </p>
 *
 * <p>
 * <b>Fields:</b>
 * <ul>
 * <li><b>enrollmentId:</b> Target enrollment to check for pending attempts</li>
 * <li><b>authAttemptEnrolleeCode:</b> Device's enrollee code for authentication</li>
 * <li><b>authAttemptEnrolleeCodeSigned:</b> Cryptographically signed enrollee code</li>
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
 * @see org.ezkey.authattempt.domain.AuthAttemptPendingRequest
 * @see AuthAttemptPendingResponseDto
 */
public class AuthAttemptPendingRequestDto {

    /**
     * The enrollment ID to check for pending authentication attempts.
     * Must reference an existing and active enrollment.
     */
    private Integer enrollmentId;

    /**
     * The device's enrollee code for authentication.
     * Used to identify the requesting device during the authentication flow.
     */
    private String authAttemptEnrolleeCode;

    /**
     * Cryptographically signed enrollee code.
     * Provides proof of device authenticity and prevents request forgery.
     */
    private String authAttemptEnrolleeCodeSigned;

    /**
     * Gets the enrollment ID.
     *
     * @return the enrollment ID
     */
    public Integer getEnrollmentId(){
        return enrollmentId;
    }

    /**
     * Sets the enrollment ID.
     *
     * @param enrollmentId the enrollment ID to set
     */
    public void setEnrollmentId(Integer enrollmentId){
        this.enrollmentId = enrollmentId;
    }

    /**
     * Gets the authentication attempt enrollee code.
     *
     * @return the authentication attempt enrollee code
     */
    public String getAuthAttemptEnrolleeCode(){
        return authAttemptEnrolleeCode;
    }

    /**
     * Sets the authentication attempt enrollee code.
     *
     * @param authAttemptEnrolleeCode the authentication attempt enrollee code to set
     */
    public void setAuthAttemptEnrolleeCode(String authAttemptEnrolleeCode){
        this.authAttemptEnrolleeCode = authAttemptEnrolleeCode;
    }

    /**
     * Gets the signed authentication attempt enrollee code.
     *
     * @return the signed authentication attempt enrollee code
     */
    public String getAuthAttemptEnrolleeCodeSigned(){
        return authAttemptEnrolleeCodeSigned;
    }

    /**
     * Sets the signed authentication attempt enrollee code.
     *
     * @param authAttemptEnrolleeCodeSigned the signed authentication attempt enrollee code to set
     */
    public void setAuthAttemptEnrolleeCodeSigned(String authAttemptEnrolleeCodeSigned){
        this.authAttemptEnrolleeCodeSigned = authAttemptEnrolleeCodeSigned;
    }

}