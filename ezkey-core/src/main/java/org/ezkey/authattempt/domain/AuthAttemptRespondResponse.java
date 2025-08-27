package org.ezkey.authattempt.domain;

/**
 * Response domain object for authentication attempt submissions.
 * <p>
 * Provides a clear, unambiguous response to mobile devices after they
 * submit their authentication attempt response.
 * </p>
 */
public class AuthAttemptRespondResponse {

    private AuthenticationResult result;
    private String message;

    /**
     * Default constructor.
     */
    public AuthAttemptRespondResponse() {
    }

    /**
     * Constructor with result and default message.
     *
     * @param result the authentication result
     */
    public AuthAttemptRespondResponse(AuthenticationResult result) {
        this.result = result;
        this.message = getDefaultMessage(result);
    }

    /**
     * Constructor with result and custom message.
     *
     * @param result the authentication result
     * @param message the custom message
     */
    public AuthAttemptRespondResponse(AuthenticationResult result, String message) {
        this.result = result;
        this.message = message;
    }

    /**
     * Gets the authentication result.
     *
     * @return the authentication result
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
     * @return the response message
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
     * Gets the default message for a given result.
     *
     * @param result the authentication result
     * @return the default message
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