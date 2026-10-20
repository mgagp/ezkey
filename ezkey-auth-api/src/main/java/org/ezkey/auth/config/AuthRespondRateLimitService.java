/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: AuthRespondRateLimitService
 * Description: Respond fail-IP ceiling + post-ownership per-id budget (#912 C1).
 */

package org.ezkey.auth.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import java.time.Duration;
import java.util.OptionalLong;
import org.ezkey.authattempt.service.AuthRespondOwnershipRateLimiter;

/**
 * Bucket4j budgets for Auth API respond: IP fail ceiling (filter) and per-id after ownership
 * (service). Created only when {@code ezkey.rate-limit.enabled=true}.
 *
 * @author Ezkey contributors
 * @since 2026
 */
public class AuthRespondRateLimitService implements AuthRespondOwnershipRateLimiter {

  private final RateLimitProperties properties;

  private final Cache<String, Bucket> bucketCache;

  /**
   * Creates the respond rate-limit service.
   *
   * @param properties Auth API rate-limit properties
   */
  public AuthRespondRateLimitService(RateLimitProperties properties) {
    this.properties = properties;
    this.bucketCache =
        Caffeine.newBuilder().maximumSize(1000).expireAfterAccess(Duration.ofHours(1)).build();
  }

  /**
   * Consumes one fail-IP token for a respond probe (filter layer).
   *
   * @param clientIp resolved client IP
   * @return decision with retry-after when denied
   */
  public RateLimitDecision tryConsumeFailIp(String clientIp) {
    RateLimitProperties.EndpointConfig config = properties.getRespond();
    String key = "respond:fail-ip:" + clientIp;
    Bucket bucket =
        bucketCache.get(
            key, _ -> createBucket(config.getFailIpRequests(), config.getFailIpWindowMinutes()));
    return probe(bucket);
  }

  @Override
  public OptionalLong tryConsumeAfterOwnership(Integer authAttemptId) {
    if (authAttemptId == null) {
      return OptionalLong.empty();
    }
    RateLimitProperties.EndpointConfig config = properties.getRespond();
    String key = "respond:id:" + authAttemptId;
    Bucket bucket =
        bucketCache.get(key, _ -> createBucket(config.getRequests(), config.getWindowMinutes()));
    RateLimitDecision decision = probe(bucket);
    if (decision.allowed()) {
      return OptionalLong.empty();
    }
    return OptionalLong.of(decision.retryAfterSeconds());
  }

  private static RateLimitDecision probe(Bucket bucket) {
    ConsumptionProbe consumption = bucket.tryConsumeAndReturnRemaining(1);
    if (consumption.isConsumed()) {
      return new RateLimitDecision(true, 0);
    }
    long retryAfterSeconds =
        Math.max(1L, Duration.ofNanos(consumption.getNanosToWaitForRefill()).toSeconds());
    return new RateLimitDecision(false, retryAfterSeconds);
  }

  private static Bucket createBucket(int capacity, int windowMinutes) {
    Bandwidth limit =
        Bandwidth.builder()
            .capacity(capacity)
            .refillIntervally(capacity, Duration.ofMinutes(windowMinutes))
            .build();
    return Bucket.builder().addLimit(limit).build();
  }

  /**
   * Result of a respond rate-limit probe.
   *
   * @param allowed whether the request may proceed
   * @param retryAfterSeconds seconds for {@code Retry-After} when denied
   */
  public record RateLimitDecision(boolean allowed, long retryAfterSeconds) {}
}
