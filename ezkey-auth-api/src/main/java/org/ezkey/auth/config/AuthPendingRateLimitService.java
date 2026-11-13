/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: AuthPendingRateLimitService
 * Description: Pending fail-IP ceiling and post-ownership per-enrollment budget (#1124, #918).
 */

package org.ezkey.auth.config;

import io.github.bucket4j.Bucket;
import java.time.Duration;
import java.util.OptionalLong;
import org.ezkey.authattempt.service.AuthPendingOwnershipRateLimiter;

/**
 * Bucket4j budgets for Auth API pending when the key strategy is {@code enrollment-id}.
 *
 * <p>The filter charges only the IP ceiling, and never reads {@code enrollmentId} from the body.
 * {@link #tryConsumeAfterOwnership} runs after the proof token and device signature, keyed by the
 * validated enrollment id (#1124). {@link #refundFailIpAfterOwnership} returns the IP token for
 * that proven call so a shared carrier NAT is not charged for legitimate polls. IP keys and
 * enrollment keys live in separate caches; IPv6 keys use a /64 (#918).
 *
 * <p>Created only when {@code ezkey.rate-limit.enabled=true}.
 *
 * @author Ezkey contributors
 * @since 2026
 */
public class AuthPendingRateLimitService implements AuthPendingOwnershipRateLimiter {

  /** Resident IP prefixes before further prefixes share one overflow bucket. */
  static final int IP_CACHE_MAX = 100_000;

  /** Resident enrollments. Only proven pending calls insert a key. */
  static final int ENROLLMENT_CACHE_MAX = 50_000;

  private static final Duration IDLE_TTL = Duration.ofHours(1);

  private final RateLimitProperties properties;

  private final RateLimitBucketCache ipBuckets;

  private final RateLimitBucketCache enrollmentBuckets;

  /**
   * Creates the pending rate-limit service with production cache bounds.
   *
   * @param properties Auth API rate-limit properties
   */
  public AuthPendingRateLimitService(RateLimitProperties properties) {
    this(properties, IP_CACHE_MAX, ENROLLMENT_CACHE_MAX);
  }

  /**
   * Creates the service with explicit cache bounds (tests of admission control).
   *
   * @param properties Auth API rate-limit properties
   * @param ipCacheMax resident IP keys before overflow
   * @param enrollmentCacheMax resident enrollment ids before overflow
   */
  AuthPendingRateLimitService(
      RateLimitProperties properties, int ipCacheMax, int enrollmentCacheMax) {
    this.properties = properties;
    RateLimitProperties.EndpointConfig config = properties.getPending();
    this.ipBuckets =
        new RateLimitBucketCache(
            ipCacheMax,
            IDLE_TTL,
            RateLimitTokens.newBucket(config.getFailIpRequests(), config.getFailIpWindowMinutes()));
    this.enrollmentBuckets =
        new RateLimitBucketCache(
            enrollmentCacheMax,
            IDLE_TTL,
            RateLimitTokens.newBucket(config.getRequests(), config.getWindowMinutes()));
  }

  /**
   * Consumes one IP-ceiling token for a pending request that has not yet proven ownership.
   *
   * @param clientIp resolved client IP
   * @return decision with retry-after when denied
   */
  public RateLimitTokens.Decision tryConsumeFailIp(String clientIp) {
    RateLimitProperties.EndpointConfig config = properties.getPending();
    String key = ipKey(clientIp);
    Bucket bucket =
        ipBuckets.resolve(
            key,
            () ->
                RateLimitTokens.newBucket(
                    config.getFailIpRequests(), config.getFailIpWindowMinutes()));
    return RateLimitTokens.probe(bucket);
  }

  @Override
  public void refundFailIpAfterOwnership(String clientIp) {
    if (clientIp == null || clientIp.isBlank()) {
      return;
    }
    ipBuckets.refundOne(ipKey(clientIp));
  }

  @Override
  public OptionalLong tryConsumeAfterOwnership(Integer enrollmentId) {
    if (enrollmentId == null) {
      return OptionalLong.empty();
    }
    if (!"enrollment-id".equals(properties.getPending().getKeyStrategy())) {
      return OptionalLong.empty();
    }
    RateLimitProperties.EndpointConfig config = properties.getPending();
    String key = "pending:enrollment:" + enrollmentId;
    Bucket bucket =
        enrollmentBuckets.resolve(
            key, () -> RateLimitTokens.newBucket(config.getRequests(), config.getWindowMinutes()));
    RateLimitTokens.Decision decision = RateLimitTokens.probe(bucket);
    if (decision.allowed()) {
      return OptionalLong.empty();
    }
    return OptionalLong.of(decision.retryAfterSeconds());
  }

  private static String ipKey(String clientIp) {
    return "pending:fail-ip:" + RateLimitIpKey.normalize(clientIp);
  }
}
