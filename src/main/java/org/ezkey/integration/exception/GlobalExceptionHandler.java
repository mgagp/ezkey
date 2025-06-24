package org.ezkey.integration.exception;

import org.ezkey.integration.dto.common.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

/**
 * Global exception handler for the Integration module.
 * <p>
 * This class provides centralized exception handling for all controllers
 * in the Integration module, ensuring consistent error responses.
 * </p>
 *
 * @author Ezkey contributors
 * @since 2025
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    /**
     * Handles ResourceNotFoundException and returns a 404 response.
     *
     * @param ex the exception that was thrown
     * @param request the web request that caused the exception
     * @return ResponseEntity with 404 status and error details
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException ex, WebRequest request) {
        ErrorResponse error = new ErrorResponse("NOT_FOUND", ex.getMessage(), request.getDescription(false));
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }
    
    /**
     * Handles generic exceptions and returns a 500 response.
     *
     * @param ex the exception that was thrown
     * @param request the web request that caused the exception
     * @return ResponseEntity with 500 status and error details
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, WebRequest request) {
        ErrorResponse error = new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred", request.getDescription(false));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
} 