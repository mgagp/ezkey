/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * SDK: EzkeyException
 * Description: Exception class for Ezkey SDK operations
 */

package org.ezkey.sdk;

/**
 * Exception thrown by Ezkey SDK operations.
 * <p>
 * This exception wraps underlying API exceptions and provides a consistent
 * error handling mechanism for SDK users.
 * </p>
 * 
 * @since 2025
 */
public class EzkeyException extends Exception {
    private final int statusCode;
    private final String responseBody;
    
    /**
     * Creates a new exception with the specified message.
     *
     * @param message the error message
     */
    public EzkeyException(String message) {
        super(message);
        this.statusCode = -1;
        this.responseBody = null;
    }
    
    /**
     * Creates a new exception with the specified message and cause.
     *
     * @param message the error message
     * @param cause the underlying cause
     */
    public EzkeyException(String message, Throwable cause) {
        super(message, cause);
        
        // Extract status code and response body from API exception if available
        if (cause instanceof org.ezkey.sdk.admin.generated.client.ApiException) {
            org.ezkey.sdk.admin.generated.client.ApiException apiEx = 
                (org.ezkey.sdk.admin.generated.client.ApiException) cause;
            this.statusCode = apiEx.getCode();
            this.responseBody = apiEx.getResponseBody();
        } else if (cause instanceof org.ezkey.sdk.auth.generated.client.ApiException) {
            org.ezkey.sdk.auth.generated.client.ApiException apiEx = 
                (org.ezkey.sdk.auth.generated.client.ApiException) cause;
            this.statusCode = apiEx.getCode();
            this.responseBody = apiEx.getResponseBody();
        } else {
            this.statusCode = -1;
            this.responseBody = null;
        }
    }
    
    /**
     * Gets the HTTP status code from the underlying API exception.
     *
     * @return the status code, or -1 if not available
     */
    public int getStatusCode() {
        return statusCode;
    }
    
    /**
     * Gets the response body from the underlying API exception.
     *
     * @return the response body, or null if not available
     */
    public String getResponseBody() {
        return responseBody;
    }
    
    /**
     * Returns whether this exception represents a client error (4xx status code).
     *
     * @return true if this is a client error
     */
    public boolean isClientError() {
        return statusCode >= 400 && statusCode < 500;
    }
    
    /**
     * Returns whether this exception represents a server error (5xx status code).
     *
     * @return true if this is a server error
     */
    public boolean isServerError() {
        return statusCode >= 500 && statusCode < 600;
    }
}