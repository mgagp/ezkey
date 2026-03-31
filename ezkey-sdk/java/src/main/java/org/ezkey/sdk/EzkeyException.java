/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Exception: EzkeyException
 * Description: Checked exception for Ezkey SDK operations.
 */

package org.ezkey.sdk;

/**
 * Checked exception thrown by Ezkey SDK operations.
 *
 * <p>Wraps HTTP errors, network failures, and JSON parsing issues with structured information
 * including HTTP status code and response body when available.
 *
 * @since 2025
 */
public class EzkeyException extends Exception {

  private static final long serialVersionUID = 386483597859702234L;

  private final int statusCode;
  private final String responseBody;

  /**
   * Creates an exception with the specified message.
   *
   * @param message the error message
   */
  public EzkeyException(String message) {
    super(message);
    this.statusCode = -1;
    this.responseBody = null;
  }

  /**
   * Creates an exception with the specified message and cause.
   *
   * @param message the error message
   * @param cause the underlying cause
   */
  public EzkeyException(String message, Throwable cause) {
    super(message, cause);
    this.statusCode = -1;
    this.responseBody = null;
  }

  /**
   * Creates an exception from an HTTP error response.
   *
   * @param message the error message
   * @param statusCode the HTTP status code
   * @param responseBody the raw response body
   */
  public EzkeyException(String message, int statusCode, String responseBody) {
    super(message);
    this.statusCode = statusCode;
    this.responseBody = responseBody;
  }

  /**
   * Gets the HTTP status code from the failed request.
   *
   * @return the status code, or {@code -1} if not an HTTP error
   */
  public int getStatusCode() {
    return statusCode;
  }

  /**
   * Gets the raw response body from the failed request.
   *
   * @return the response body, or {@code null} if not available
   */
  public String getResponseBody() {
    return responseBody;
  }

  /**
   * Returns whether this exception represents a client error (4xx status code).
   *
   * @return {@code true} if this is a client error
   */
  public boolean isClientError() {
    return statusCode >= 400 && statusCode < 500;
  }

  /**
   * Returns whether this exception represents a server error (5xx status code).
   *
   * @return {@code true} if this is a server error
   */
  public boolean isServerError() {
    return statusCode >= 500 && statusCode < 600;
  }

  /**
   * Returns whether this exception represents an authentication error (401).
   *
   * @return {@code true} if authentication failed
   */
  public boolean isUnauthorized() {
    return statusCode == 401;
  }
}
