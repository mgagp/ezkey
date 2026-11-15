/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: AuthRespondRateLimitService
 * Description: Respond fail-IP ceiling and post-ownership per-id budget (#912 C1, #918).
 */

package org.ezkey.auth.config;

import io.github.bucket4j.Bucket;
import java.time.Duration;
import java.util.OptionalLong;
import org.ezkey.authattempt.service.AuthRespondOwnershipRateLimiter;

/**
 * Bucket4j budgets for Auth API respond.
 *
 * <p>The filter charges the IP ceiling on every respond. {@link #refundFailIpAfterOwnership}
 * returns that token after a valid device signature, so proven calls (including a carrier-NAT
 * neighbor's successful approve) do not fill the ceiling (#918). The per-{@code authAttemptId}
 * budget is a separate cache charged only after ownership (#912 C1). IP keys and id keys do not
 * share a map: rotating client addresses cannot evict an id bucket or resurrect an exhausted IP
 * bucket. IPv6 keys are aggregated to /64.
 *
 * <p>Created only when {@code ezkey.rate-limit.enabled=true}.
 *
 * @author Ezkey contributors
 * @since 2026
 */
public class AuthRespondRateLimitService implements AuthRespondOwnershipRateLimiter {

  /** Resident IP prefixes before further prefixes share one overflow bucket. */
  static final int IP_CACHE_MAX = 100_000;

  /** Resident auth-attempt ids. Only proven responds insert a key. */
  static final int ID_CACHE_MAX = 50_000;

  private static final Duration IDLE_TTL = Duration.ofHours(1);

  private final RateLimitProperties properties;

  private final RateLimitBucketCache ipBuckets;

  private final RateLimitBucketCache idBuckets;

  /**
   * Creates the respond rate-limit service with production cache bounds.
   *
   * @param properties Auth API rate-limit properties
   */
  public AuthRespondRateLimitService(RateLimitProperties properties) {
    this(properties, IP_CACHE_MAX, ID_CACHE_MAX);
  }

  /**
   * Creates the service with explicit cache bounds (tests of admission control).
   *
   * @param properties Auth API rate-limit properties
   * @param ipCacheMax resident IP keys before overflow
   * @param idCacheMax resident auth-attempt ids before overflow
   */
  AuthRespondRateLimitService(RateLimitProperties properties, int ipCacheMax, int idCacheMax) {
    this.properties = properties;
    RateLimitProperties.EndpointConfig config = properties.getRespond();
    this.ipBuckets =
        new RateLimitBucketCache(
            ipCacheMax,
            IDLE_TTL,
            RateLimitTokens.newBucket(config.getFailIpRequests(), config.getFailIpWindowMinutes()));
    this.idBuckets =
        new RateLimitBucketCache(
            idCacheMax,
            IDLE_TTL,
            RateLimitTokens.newBucket(config.getRequests(), config.getWindowMinutes()));
  }

  /**
   * Consumes one IP-ceiling token for a respond that has not yet proven ownership.
   *
   * <p>The filter calls this on every respond. A later proven call refunds the token.
   *
   * @param clientIp resolved client IP
   * @return decision with retry-after when denied
   */
  public RateLimitTokens.Decision tryConsumeFailIp(String clientIp) {
    RateLimitProperties.EndpointConfig config = properties.getRespond();
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
  public OptionalLong tryConsumeAfterOwnership(Integer authAttemptId) {
    if (authAttemptId == null) {
      return OptionalLong.empty();
    }
    RateLimitProperties.EndpointConfig config = properties.getRespond();
    String key = "respond:id:" + authAttemptId;
    Bucket bucket =
        idBuckets.resolve(
            key, () -> RateLimitTokens.newBucket(config.getRequests(), config.getWindowMinutes()));
    RateLimitTokens.Decision decision = RateLimitTokens.probe(bucket);
    if (decision.allowed()) {
      return OptionalLong.empty();
    }
    return OptionalLong.of(decision.retryAfterSeconds());
  }

  private static String ipKey(String clientIp) {
    return "respond:fail-ip:" + RateLimitIpKey.normalize(clientIp);
  }
}
