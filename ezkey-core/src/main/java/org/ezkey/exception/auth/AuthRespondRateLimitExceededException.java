/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Exception: AuthRespondRateLimitExceededException
 * Description: Per-authAttemptId respond budget exhausted after ownership (#912 C1).
 */

package org.ezkey.exception.auth;

/**
 * Thrown when the post-ownership respond budget for an auth attempt is exhausted.
 *
 * <p>Mapped by Auth API to HTTP 429 with empty body and {@code Retry-After} (same surface as the
 * servlet filter).
 *
 * @author Ezkey contributors
 * @since 2026
 */
@SuppressWarnings("serial")
public class AuthRespondRateLimitExceededException extends RuntimeException {

  private final long retryAfterSeconds;

  /**
   * Creates the exception with a positive retry-after hint.
   *
   * @param retryAfterSeconds seconds until a token may refill (at least 1)
   */
  public AuthRespondRateLimitExceededException(long retryAfterSeconds) {
    super("Respond rate limit exceeded for auth attempt after ownership");
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
