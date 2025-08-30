/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Domain: AuthAttemptRespondResponse
 * Description: Domain response object for authentication attempt submission results.
 */

package org.ezkey.authattempt.domain;

/**
 * Domain response object for authentication attempt submission results.
 * <p>
 * This domain object represents the response data returned by the service layer
 * after a mobile device submits an authentication attempt response. It provides
 * clear, unambiguous feedback about the authentication outcome and includes
 * descriptive messaging for client applications and user interfaces.
 * </p>
 *
 * <p>
 * <b>Usage Context:</b> Returned by the AuthAttemptService after processing
 * authentication attempt responses from mobile devices. The service layer
 * creates this response object to communicate the final authentication result
 * and any relevant status information back through the API layers.
 * </p>
 *
 * <p>
 * <b>Authentication Flow:</b> This response represents the final outcome of
 * the authentication challenge-response flow. After the service validates
 * the device signature, user consent, and any additional challenges, it
 * returns this response to indicate whether authentication was successful.
 * </p>
 *
 * <p>
 * <b>Result Types:</b> Contains standardized authentication results including
 * APPROVED (user accepted and validation passed), DENIED (user rejected),
 * FAILED (technical error occurred), and EXPIRED (attempt timed out).
 * Each result type includes appropriate default messaging for user feedback.
 * </p>
 *
 * <p>
 * <b>Error Handling:</b> Provides both structured result codes and human-readable
 * messages that can be displayed to users or logged for debugging purposes.
 * The messaging system supports both default messages and custom error details
 * for specific failure scenarios.
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
 * @see org.ezkey.authattempt.domain.AuthAttemptRespondRequest
 * @see org.ezkey.authattempt.domain.AuthenticationResult
 * @see org.ezkey.authattempt.domain.entity.AuthAttempt
 */
public class AuthAttemptRespondResponse {

    /**
     * The authentication result indicating the outcome of the attempt.
     * <p>
     * Provides a standardized enumeration value representing the final
     * authentication decision. This result determines whether the
     * authentication challenge was successfully completed and approved,
     * or if it failed due to user denial, technical errors, or timeout.
     * </p>
     *
     * @see AuthenticationResult
     */
    private AuthenticationResult result;

    /**
     * Human-readable message describing the authentication outcome.
     * <p>
     * Contains descriptive text that can be displayed to users or logged
     * for debugging purposes. The message provides context about the
     * authentication result and may include specific error details when
     * authentication fails due to technical issues or policy violations.
     * </p>
     */
    private String message;

    /**
     * Default constructor.
     * <p>
     * Creates an empty response object. The result and message should be
     * set explicitly using the setter methods or use one of the parameterized
     * constructors for convenience.
     * </p>
     */
    public AuthAttemptRespondResponse() {
    }

    /**
     * Constructor with result and default message.
     * <p>
     * Creates a response with the specified authentication result and
     * automatically sets an appropriate default message based on the
     * result type. This is the recommended constructor for standard
     * authentication outcomes.
     * </p>
     *
     * @param result the authentication result
     */
    public AuthAttemptRespondResponse(AuthenticationResult result) {
        this.result = result;
        this.message = getDefaultMessage(result);
    }

    /**
     * Constructor with result and custom message.
     * <p>
     * Creates a response with the specified authentication result and
     * a custom message. Use this constructor when you need to provide
     * specific error details or contextual information beyond the
     * standard default messages.
     * </p>
     *
     * @param result the authentication result
     * @param message the custom message describing the outcome
     */
    public AuthAttemptRespondResponse(AuthenticationResult result, String message) {
        this.result = result;
        this.message = message;
    }

    /**
     * Gets the authentication result.
     *
     * @return the authentication result indicating the outcome
     */
    public AuthenticationResult getResult() {
        return result;
    }

    /**
     * Sets the authentication result.
     *
     * @param result the authentication result to set
     */
    public void setResult(AuthenticationResult result) {
        this.result = result;
    }

    /**
     * Gets the response message.
     *
     * @return the human-readable message describing the outcome
     */
    public String getMessage() {
        return message;
    }

    /**
     * Sets the response message.
     *
     * @param message the response message to set
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * Gets the default message for a given authentication result.
     * <p>
     * Provides standardized default messages for each authentication
     * result type. These messages are suitable for display to end users
     * and provide clear feedback about the authentication outcome.
     * </p>
     *
     * @param result the authentication result
     * @return the appropriate default message for the result
     */
    private String getDefaultMessage(AuthenticationResult result) {
        switch (result) {
            case APPROVED:
                return "Authentication approved";
            case DENIED:
                return "Authentication denied by user";
            case FAILED:
                return "Authentication failed due to technical error";
            default:
                return "Unknown authentication result";
        }
    }
}