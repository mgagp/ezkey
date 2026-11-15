/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Interface: AuthPendingOwnershipRateLimiter
 * Description: Optional per-enrollment pending budget after device ownership (#1124).
 */

package org.ezkey.authattempt.service;

import java.util.OptionalLong;

/**
 * Optional rate limiter for {@code POST /auth-attempts/pending} after ownership is proven.
 *
 * <p>Auth API implements this when {@code ezkey.rate-limit.enabled=true} and the pending key
 * strategy is {@code enrollment-id}. The servlet filter must not debit the per-enrollment budget
 * from the request body (#1124). The enrollment id passed here is the id of the enrollment located
 * by proof-token hash, after the device signature has been checked.
 *
 * @author Ezkey contributors
 * @since 2026
 */
public interface AuthPendingOwnershipRateLimiter {

  /**
   * Attempts to consume one token for the enrollment after proof token and device signature.
   *
   * @param enrollmentId enrollment id from the validated enrollment
   * @return empty when allowed or when this limiter does not apply; otherwise retry-after seconds
   *     (at least 1)
   */
  OptionalLong tryConsumeAfterOwnership(Integer enrollmentId);

  /**
   * Returns the IP-ceiling token the filter consumed, once ownership is proven.
   *
   * <p>Proven pending polls must not accumulate on a shared carrier NAT. The default does nothing
   * when rate limiting is off.
   *
   * @param clientIp resolved client IP; ignored when null or blank
   */
  default void refundFailIpAfterOwnership(String clientIp) {}
}
