/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Class: RateLimitExceededException
 * Description: Exception thrown when API key rate limits are exceeded.
 */

package org.ezkey.exception;

/**
 * Exception thrown when an API key exceeds its rate limit for a specific operation.
 *
 * <p>This exception is thrown by the RateLimitService when an API key attempts to perform an
 * operation that would exceed the configured rate limits. It provides information about the
 * rate limit status to help clients understand when they can retry the operation.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
public class RateLimitExceededException extends RuntimeException {

  private final String operation;
  private final int limit;
  private final int current;
  private final int windowSizeMinutes;

  /**
   * Constructs a new RateLimitExceededException with the specified details.
   *
   * @param operation the operation that exceeded the rate limit
   * @param limit the maximum number of operations allowed per window
   * @param current the current number of operations in the window
   * @param windowSizeMinutes the size of the rate limiting window in minutes
   */
  public RateLimitExceededException(String operation, int limit, int current, int windowSizeMinutes) {
    super(String.format(
        "Rate limit exceeded for operation '%s': %d/%d operations in %d-minute window",
        operation, current, limit, windowSizeMinutes));
    this.operation = operation;
    this.limit = limit;
    this.current = current;
    this.windowSizeMinutes = windowSizeMinutes;
  }

  /**
   * Gets the operation that exceeded the rate limit.
   *
   * @return the operation name
   */
  public String getOperation() {
    return operation;
  }

  /**
   * Gets the maximum number of operations allowed per window.
   *
   * @return the rate limit
   */
  public int getLimit() {
    return limit;
  }

  /**
   * Gets the current number of operations in the window.
   *
   * @return the current count
   */
  public int getCurrent() {
    return current;
  }

  /**
   * Gets the size of the rate limiting window in minutes.
   *
   * @return the window size in minutes
   */
  public int getWindowSizeMinutes() {
    return windowSizeMinutes;
  }

  /**
   * Gets the number of remaining operations in the current window.
   *
   * @return the remaining operations count
   */
  public int getRemaining() {
    return Math.max(0, limit - current);
  }
}
