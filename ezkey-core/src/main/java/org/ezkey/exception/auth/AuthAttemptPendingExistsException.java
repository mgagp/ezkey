/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Exception: AuthAttemptPendingExistsException
 * Description: Create refused because a live authentication attempt already exists.
 */

package org.ezkey.exception.auth;

import java.io.Serial;

/**
 * Create was refused because the integration's pending-attempt policy is {@code REFUSE} and a live
 * attempt already exists.
 *
 * <p>The message is for server logs. Clients receive the fixed problem type and detail, plus {@code
 * Retry-After} equal to the remaining lifetime of the blocking attempt.
 *
 * @since 2026
 */
public class AuthAttemptPendingExistsException extends IllegalStateException {

  @Serial private static final long serialVersionUID = 1L;

  /** Stable RFC 9457 problem type. */
  public static final String TYPE = "https://ezkey.io/problems/domain/auth-attempt-pending-exists";

  /** Stable problem title. */
  public static final String TITLE = "Pending authentication attempt";

  /** Fixed client detail. No enrollment id and no username. */
  public static final String DETAIL =
      "A pending authentication attempt already exists for this enrollment.";

  private final long retryAfterSeconds;

  /**
   * Creates the refusal.
   *
   * @param retryAfterSeconds remaining lifetime of the blocking attempt, at least 1
   */
  public AuthAttemptPendingExistsException(long retryAfterSeconds) {
    super(DETAIL);
    this.retryAfterSeconds = Math.max(1L, retryAfterSeconds);
  }

  /**
   * Seconds the client should wait before creating again.
   *
   * @return remaining TTL, at least 1
   */
  public long getRetryAfterSeconds() {
    return retryAfterSeconds;
  }
}
