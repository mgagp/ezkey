/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Exception: ForbiddenException
 * Description: Custom exception for handling access forbidden scenarios across all Ezkey modules.
 */

package org.ezkey.exception;

/**
 * Exception thrown when access to a resource is forbidden in the Ezkey system.
 *
 * <p>This exception is used throughout all Ezkey modules and controllers to indicate that access to
 * a requested resource or operation is not allowed for the current authentication context. It
 * extends {@link RuntimeException} to avoid forcing callers to handle checked exceptions for
 * business logic scenarios.
 *
 * <p><b>Usage Examples:</b>
 *
 * <ul>
 *   <li>API key attempting enrollment management: <code>
 *       throw new ForbiddenException("API keys cannot manage enrollments")</code>
 *   <li>Insufficient permissions: <code>
 *       throw new ForbiddenException("Insufficient permissions to access this resource")</code>
 *   <li>Integration scope violation: <code>
 *       throw new ForbiddenException("Access denied to resource outside integration scope")</code>
 * </ul>
 *
 * <p><b>Exception Handling:</b> This exception is automatically caught by the centralized {@link
 * GlobalExceptionHandler} and converted to a standardized HTTP 403 Forbidden response with
 * appropriate error details for API consumers.
 *
 * <p><b>Security Context:</b> This exception is commonly used for authorization failures where the
 * user is authenticated but lacks the necessary permissions or scope to perform the requested
 * operation.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * <p><b>Usage:</b> Authorization and access control scenarios in any Ezkey API module
 *
 * @author Ezkey contributors
 * @since 2025
 * @see GlobalExceptionHandler
 * @see RuntimeException
 */
public class ForbiddenException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new ForbiddenException with the specified detail message.
     *
     * <p>The exception message should clearly describe why access was denied, helping developers and
     * API consumers understand the authorization constraint that was violated.
     *
     * <p><b>Message Examples:</b>
     *
     * <ul>
     *   <li>"API keys cannot manage enrollments"
     *   <li>"Access denied to resource outside integration scope"
     *   <li>"Insufficient permissions for this operation"
     * </ul>
     *
     * @param message the detail message explaining why access was forbidden
     */
    public ForbiddenException(String message) {
        super(message);
    }

    /**
     * Constructs a new ForbiddenException with the specified detail message and cause.
     *
     * <p>This constructor is useful when wrapping another exception that indicates an authorization
     * failure, allowing the original exception to be preserved in the stack trace.
     *
     * @param message the detail message explaining why access was forbidden
     * @param cause the underlying cause of the forbidden access (or null if nonexistent or unknown)
     */
    public ForbiddenException(String message, Throwable cause) {
        super(message, cause);
    }
}
