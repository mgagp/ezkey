/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Interface: AuthRespondOwnershipRateLimiter
 * Description: Optional per-authAttemptId budget after device-signature ownership (#912 C1).
 */

package org.ezkey.authattempt.service;

import java.util.OptionalLong;

/**
 * Optional rate limiter for {@code POST /auth-attempts/respond} after ownership is proven.
 *
 * <p>Auth API implements this when {@code ezkey.rate-limit.enabled=true}. The servlet filter must
 * <em>not</em> debit the per-{@code authAttemptId} budget before signature verification (#912 C1).
 *
 * @author Ezkey contributors
 * @since 2026
 */
public interface AuthRespondOwnershipRateLimiter {

  /**
   * Attempts to consume one token for the given auth attempt after a valid device signature.
   *
   * @param authAttemptId attempt id under respond
   * @return empty when allowed; otherwise retry-after seconds (at least 1)
   */
  OptionalLong tryConsumeAfterOwnership(Integer authAttemptId);

  /**
   * Returns the IP-ceiling token the filter consumed, once device ownership is proven.
   *
   * <p>Proven respond calls must not accumulate on a shared carrier NAT (#918). The default does
   * nothing when rate limiting is off.
   *
   * @param clientIp resolved client IP; ignored when null or blank
   */
  default void refundFailIpAfterOwnership(String clientIp) {}
}
