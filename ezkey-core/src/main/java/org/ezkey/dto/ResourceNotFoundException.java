/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Exception: ResourceNotFoundException
 * Description: Custom exception for handling resource not found scenarios across all Ezkey modules.
 */

package org.ezkey.dto;

import org.ezkey.exception.GlobalExceptionHandler;

/**
 * Exception thrown when a requested resource cannot be found in the Ezkey system.
 * <p>
 * This exception is used throughout all Ezkey modules and controllers to indicate that a requested entity
 * (such as Integration, User, Enrollment, etc.) could not be located using the provided identifier.
 * It extends {@link RuntimeException} to avoid forcing callers to handle checked exceptions for
 * business logic scenarios.
 * </p>
 *
 * <p>
 * <b>Usage Examples:</b>
 * <ul>
 *   <li>Integration not found by ID: <code>throw new ResourceNotFoundException("Integration", 123)</code></li>
 *   <li>User not found by email: <code>throw new ResourceNotFoundException("User", "user@example.com")</code></li>
 *   <li>Enrollment not found: <code>throw new ResourceNotFoundException("Enrollment", "enroll-uuid")</code></li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Exception Handling:</b>
 * This exception is automatically caught by the centralized {@link GlobalExceptionHandler} and converted
 * to a standardized HTTP 404 Not Found response with appropriate error details for API consumers.
 * </p>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative</p>
 * <p><b>License:</b> MIT</p>
 * <p><b>Usage:</b> Resource not found scenarios in any Ezkey API module</p>
 *
 * @author Ezkey contributors
 * @since 2025
 * @see GlobalExceptionHandler
 * @see RuntimeException
 */
public class ResourceNotFoundException extends RuntimeException {
    
    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new ResourceNotFoundException with a formatted error message.
     * <p>
     * The exception message is automatically formatted as "{resource} with id {id} not found"
     * to provide clear and consistent error information. This format helps with debugging
     * and provides useful information to API consumers.
     * </p>
     *
     * <p>
     * <b>Message Format:</b> "{resource} with id {id} not found"
     * </p>
     *
     * <p>
     * <b>Examples:</b>
     * <ul>
     *   <li><code>new ResourceNotFoundException("Integration", 123)</code> → "Integration with id 123 not found"</li>
     *   <li><code>new ResourceNotFoundException("User", "john@example.com")</code> → "User with id john@example.com not found"</li>
     * </ul>
     * </p>
     *
     * @param resource the type or name of the resource that was not found (e.g., "Integration", "User")
     * @param id the identifier that was used to search for the resource (can be any Object type)
     * @throws IllegalArgumentException if resource is null or empty
     */
    public ResourceNotFoundException(String resource, Object id) {
        super(String.format("%s with id %s not found", resource, id));
    }
}