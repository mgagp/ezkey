/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Class: GlobalExceptionHandler
 * Description: Centralized exception handling for the Integration module REST API.
 */

package org.ezkey.integration.exception;

import org.ezkey.integration.dto.common.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

/**
 * Global exception handler for the Integration module REST API.
 * <p>
 * This class provides centralized exception handling for all controllers in the Integration module,
 * ensuring consistent error responses across the entire API. It intercepts exceptions thrown by
 * controller methods and converts them into standardized HTTP responses with appropriate status codes.
 * </p>
 *
 * <p>
 * The handler supports multiple exception types:
 * <ul>
 *   <li><b>ResourceNotFoundException:</b> Returns HTTP 404 with detailed error information</li>
 *   <li><b>Generic Exceptions:</b> Returns HTTP 500 with generic error message for security</li>
 * </ul>
 * </p>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative</p>
 * <p><b>License:</b> MIT</p>
 * <p><b>Usage:</b> Global exception handling for Integration API</p>
 *
 * @author Ezkey contributors
 * @since 2025
 * @see ResourceNotFoundException
 * @see ErrorResponse
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    /**
     * Handles ResourceNotFoundException and returns a standardized 404 Not Found response.
     * <p>
     * This method intercepts ResourceNotFoundException instances thrown by service or controller
     * methods when requested resources cannot be found. It creates a standardized error response
     * with the exception message and request path information for debugging purposes.
     * </p>
     *
     * <p>
     * <b>HTTP Response:</b>
     * <ul>
     *   <li><b>Status:</b> 404 Not Found</li>
     *   <li><b>Body:</b> ErrorResponse with code "NOT_FOUND"</li>
     *   <li><b>Headers:</b> Standard Spring Boot response headers</li>
     * </ul>
     * </p>
     *
     * @param ex the ResourceNotFoundException that was thrown
     * @param request the web request that caused the exception, used for path information
     * @return ResponseEntity containing ErrorResponse with 404 status code
     * @see ResourceNotFoundException
     * @see ErrorResponse
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException ex, WebRequest request) {
        ErrorResponse error = new ErrorResponse("NOT_FOUND", ex.getMessage(), request.getDescription(false));
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }
    
    /**
     * Handles generic exceptions and returns a standardized 500 Internal Server Error response.
     * <p>
     * This method serves as a catch-all for any unhandled exceptions that occur during request
     * processing. It provides a generic error message to avoid exposing sensitive information
     * while still providing useful debugging information through the request path.
     * </p>
     *
     * <p>
     * <b>Security Note:</b> This handler intentionally uses a generic error message to prevent
     * information leakage that could be exploited by malicious actors.
     * </p>
     *
     * <p>
     * <b>HTTP Response:</b>
     * <ul>
     *   <li><b>Status:</b> 500 Internal Server Error</li>
     *   <li><b>Body:</b> ErrorResponse with code "INTERNAL_ERROR"</li>
     *   <li><b>Headers:</b> Standard Spring Boot response headers</li>
     * </ul>
     * </p>
     *
     * @param ex the generic exception that was thrown
     * @param request the web request that caused the exception, used for path information
     * @return ResponseEntity containing ErrorResponse with 500 status code
     * @see ErrorResponse
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, WebRequest request) {
        ErrorResponse error = new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred", request.getDescription(false));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
} 