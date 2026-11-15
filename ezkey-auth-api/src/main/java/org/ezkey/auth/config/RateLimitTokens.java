/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Class: RateLimitTokens
 * Description: Shared Bucket4j probe helpers for Auth API rate limits.
 */

package org.ezkey.auth.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import java.time.Duration;

/**
 * Bucket4j helpers shared by the respond and pending rate-limit services.
 *
 * @author Ezkey contributors
 * @since 2026
 */
final class RateLimitTokens {

  private RateLimitTokens() {}

  /**
   * Builds an interval-refill bucket.
   *
   * @param capacity tokens per window
   * @param windowMinutes refill window in minutes
   * @return a full bucket
   */
  static Bucket newBucket(int capacity, int windowMinutes) {
    int safeCapacity = Math.max(1, capacity);
    int safeWindow = Math.max(1, windowMinutes);
    Bandwidth limit =
        Bandwidth.builder()
            .capacity(safeCapacity)
            .refillIntervally(safeCapacity, Duration.ofMinutes(safeWindow))
            .build();
    return Bucket.builder().addLimit(limit).build();
  }

  /**
   * Consumes one token.
   *
   * @param bucket bucket to probe
   * @return allowed, or a retry-after of at least one second
   */
  static Decision probe(Bucket bucket) {
    ConsumptionProbe consumption = bucket.tryConsumeAndReturnRemaining(1);
    if (consumption.isConsumed()) {
      return new Decision(true, 0);
    }
    long retryAfterSeconds =
        Math.max(1L, Duration.ofNanos(consumption.getNanosToWaitForRefill()).toSeconds());
    return new Decision(false, retryAfterSeconds);
  }

  /**
   * Result of a token probe.
   *
   * @param allowed whether the caller may proceed
   * @param retryAfterSeconds seconds for {@code Retry-After} when denied; zero when allowed
   */
  record Decision(boolean allowed, long retryAfterSeconds) {}
}
