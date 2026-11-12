/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Exception: AuthAttemptCreationCooldownException
 * Description: Create suspended after repeated rejected or expired attempts.
 */

package org.ezkey.exception.auth;

import java.io.Serial;

/**
 * Create was suspended because the enrollment crossed the platform cooldown threshold.
 *
 * <p>The message is for server logs. Clients receive the fixed problem type and detail, plus {@code
 * Retry-After} for the remaining suspension. This is not the per-API-key rate limit.
 *
 * @since 2026
 */
public class AuthAttemptCreationCooldownException extends IllegalStateException {

  @Serial private static final long serialVersionUID = 1L;

  /** Stable RFC 9457 problem type. */
  public static final String TYPE =
      "https://ezkey.io/problems/domain/auth-attempt-creation-cooldown";

  /** Stable problem title. */
  public static final String TITLE = "Authentication attempt creation suspended";

  /** Fixed client detail. No enrollment id and no username. */
  public static final String DETAIL =
      "Authentication attempt creation is temporarily suspended for this enrollment.";

  private final long retryAfterSeconds;

  /**
   * Creates the suspension.
   *
   * @param retryAfterSeconds remaining suspension, at least 1
   */
  public AuthAttemptCreationCooldownException(long retryAfterSeconds) {
    super(DETAIL);
    this.retryAfterSeconds = Math.max(1L, retryAfterSeconds);
  }

  /**
   * Seconds the client should wait before creating again.
   *
   * @return remaining suspension, at least 1
   */
  public long getRetryAfterSeconds() {
    return retryAfterSeconds;
  }
}
