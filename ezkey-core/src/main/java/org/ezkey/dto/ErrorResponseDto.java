/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: ErrorResponse
 * Description: Common error response DTO for standardized API error handling.
 */

package org.ezkey.dto;

import java.time.LocalDateTime;

/**
 * Common error response DTO for standardized API error handling.
 *
 * <p>This DTO provides a consistent structure for all error responses across the Ezkey API. It
 * includes error code, message, timestamp, and optional request path information.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * <p><b>Usage:</b> Global exception handler and error responses
 *
 * @author Ezkey contributors
 * @since 2025
 */
public class ErrorResponseDto {

  /**
   * Error code identifier. Used to categorize and identify specific error types (e.g., "NOT_FOUND",
   * "VALIDATION_ERROR").
   */
  private String code;

  /** Human-readable error message. Provides a clear description of what went wrong. */
  private String message;

  /**
   * Timestamp when the error occurred. Automatically set to the current time when the error
   * response is created.
   */
  private LocalDateTime timestamp;

  /**
   * Optional request path that caused the error. Useful for debugging and identifying the
   * problematic endpoint.
   */
  private String path;

  /**
   * Constructs an ErrorResponse with code and message.
   *
   * <p>The timestamp is automatically set to the current time.
   *
   * @param code the error code identifier
   * @param message the human-readable error message
   */
  public ErrorResponseDto(String code, String message) {
    this.code = code;
    this.message = message;
    this.timestamp = LocalDateTime.now();
  }

  /**
   * Constructs an ErrorResponse with code, message, and request path.
   *
   * <p>The timestamp is automatically set to the current time.
   *
   * @param code the error code identifier
   * @param message the human-readable error message
   * @param path the request path that caused the error
   */
  public ErrorResponseDto(String code, String message, String path) {
    this(code, message);
    this.path = path;
  }

  /**
   * Gets the error code identifier.
   *
   * @return the error code
   */
  public String getCode() {
    return code;
  }

  /**
   * Sets the error code identifier.
   *
   * @param code the error code to set
   */
  public void setCode(String code) {
    this.code = code;
  }

  /**
   * Gets the human-readable error message.
   *
   * @return the error message
   */
  public String getMessage() {
    return message;
  }

  /**
   * Sets the human-readable error message.
   *
   * @param message the error message to set
   */
  public void setMessage(String message) {
    this.message = message;
  }

  /**
   * Gets the timestamp when the error occurred.
   *
   * @return the error timestamp
   */
  public LocalDateTime getTimestamp() {
    return timestamp;
  }

  /**
   * Sets the timestamp when the error occurred.
   *
   * @param timestamp the error timestamp to set
   */
  public void setTimestamp(LocalDateTime timestamp) {
    this.timestamp = timestamp;
  }

  /**
   * Gets the request path that caused the error.
   *
   * @return the request path, or null if not set
   */
  public String getPath() {
    return path;
  }

  /**
   * Sets the request path that caused the error.
   *
   * @param path the request path to set
   */
  public void setPath(String path) {
    this.path = path;
  }
}
