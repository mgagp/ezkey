/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Class: ExceptionHandlerBase
 * Description: Base class for exception handlers providing shared RFC 9457 ProblemDetail construction.
 */

package org.ezkey.exception;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

/**
 * Base class for exception handlers providing shared RFC 9457 ProblemDetail
 * construction.
 *
 * <p>
 * <b>Purpose:</b> This abstract base class eliminates code duplication across
 * exception handlers
 * by providing a centralized method for building RFC 9457 ProblemDetail
 * responses. Each exception
 * handler extends this class and uses {@link #buildProblemDetail} to construct
 * standardized error
 * responses.
 *
 * <p>
 * <b>RFC 9457 Compliance:</b> All responses built by this class conform to RFC
 * 9457 (Problem
 * Details for HTTP APIs) standard:
 *
 * <ul>
 * <li><code>type</code> - Problem URI identifying the error category
 * <li><code>title</code> - Human-readable error title
 * <li><code>status</code> - HTTP status code
 * <li><code>detail</code> - Specific error message
 * <li><code>path</code> - Request URI where the error occurred
 * </ul>
 *
 * <p>
 * <b>Example Usage:</b>
 *
 * <pre>
 * {
 *     &#64;code
 *     &#64;Component
 *     public class MyExceptionHandler extends ExceptionHandlerBase {
 *         @ExceptionHandler(MyException.class)
 *         public ResponseEntity<ProblemDetail> handleMyException(
 *                 MyException ex, HttpServletRequest request) {
 *             return buildProblemDetail(ex, HttpStatus.BAD_REQUEST,
 *                     "https://ezkey.io/problems/my-error",
 *                     "My Error Title", request);
 *         }
 *     }
 * }
 * </pre>
 *
 * <p>
 * <b>Design Pattern:</b> This implements the Template Method pattern, allowing
 * subclasses to
 * focus on exception-to-status mapping while delegating response construction
 * to the base class.
 *
 * <p>
 * <b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p>
 * <b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see org.springframework.http.ProblemDetail
 * @see org.springframework.http.ResponseEntity
 */
public abstract class ExceptionHandlerBase {

    /**
     * Builds a RFC 9457 ProblemDetail response for the given exception.
     *
     * <p>
     * <b>Responsibilities:</b>
     *
     * <ul>
     * <li>Create a ProblemDetail with the specified HTTP status and detail message
     * <li>Set the problem type URI for error categorization
     * <li>Set a human-readable title for the error
     * <li>Include the request URI path for debugging
     * <li>Wrap in a ResponseEntity with the appropriate HTTP status
     * </ul>
     *
     * <p>
     * <b>RFC 9457 Structure:</b> The returned ProblemDetail includes:
     *
     * <pre>
     * {
     *   "type": "https://ezkey.io/problems/...",
     *   "title": "Human Readable Title",
     *   "status": 400,
     *   "detail": "Specific error message from exception",
     *   "path": "/api/v1/admin/endpoint"
     * }
     * </pre>
     *
     * @param ex      exception containing the error message (detail)
     * @param status  HTTP status code for the response (e.g.,
     *                HttpStatus.BAD_REQUEST)
     * @param typeUri problem type URI for error categorization (e.g.,
     *                "https://ezkey.io/problems/validation/invalid-input")
     * @param title   human-readable error title (e.g., "Invalid Input")
     * @param request HTTP servlet request for extracting the request URI path
     * @return ResponseEntity containing the ProblemDetail and HTTP status, ready to
     *         send to client
     * @throws NullPointerException if any parameter is null
     */
    protected ResponseEntity<ProblemDetail> buildProblemDetail(
            Exception ex,
            HttpStatus status,
            String typeUri,
            String title,
            jakarta.servlet.http.HttpServletRequest request) {

        // Create ProblemDetail with status and exception message
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());

        // Set RFC 9457 type and title fields
        problem.setType(URI.create(typeUri));
        problem.setTitle(title);

        // Include request path for debugging
        problem.setProperty("path", request.getRequestURI());

        return ResponseEntity.status(status).body(problem);
    }
}
