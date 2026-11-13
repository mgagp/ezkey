/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Exception: AuthPendingRateLimitExceededException
 * Description: Per-enrollment pending budget exhausted after ownership (#1124).
 */

package org.ezkey.exception.auth;

/**
 * Thrown when the post-ownership pending budget for an enrollment is exhausted.
 *
 * <p>Mapped by Auth API to HTTP 429 with an empty body and {@code Retry-After} (same surface as the
 * servlet filter). Dummy pending calls that fail the proof token or device signature do not throw
 * this exception and do not consume the enrollment budget.
 *
 * @author Ezkey contributors
 * @since 2026
 */
@SuppressWarnings("serial")
public class AuthPendingRateLimitExceededException extends RuntimeException {

  private final long retryAfterSeconds;

  /**
   * Creates the exception with a positive retry-after hint.
   *
   * @param retryAfterSeconds seconds until a token may refill (at least 1)
   */
  public AuthPendingRateLimitExceededException(long retryAfterSeconds) {
    super("Pending rate limit exceeded for enrollment after ownership");
    this.retryAfterSeconds = Math.max(1L, retryAfterSeconds);
  }

  /**
   * Returns the {@code Retry-After} value in seconds.
   *
   * @return retry-after seconds (≥ 1)
   */
  public long getRetryAfterSeconds() {
    return retryAfterSeconds;
  }
}
