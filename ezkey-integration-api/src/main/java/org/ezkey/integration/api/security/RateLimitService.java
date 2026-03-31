/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: RateLimitService
 * Description: Rate limiting service for Integration API key operations using Bucket4j + Caffeine.
 */

package org.ezkey.integration.api.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.ezkey.integration.api.config.ApiKeyRateLimitProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Rate limiting service for Integration API key operations.
 *
 * <p>Uses the Bucket4j token bucket algorithm backed by a Caffeine in-memory cache. Each API key
 * has independent buckets for create-attempt and wait-attempt operations.
 *
 * <p><b>Note on distribution:</b> Buckets are per-instance and non-distributed. In a multi-node
 * deployment each instance maintains its own quotas. A shared Redis backend can be introduced when
 * the volume warrants it.
 *
 * <p>This implementation is a direct copy of the admin-api {@code RateLimitService} scoped to the
 * Integration API module package. Extraction to a shared module is deferred to a future phase.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Service
@ConditionalOnProperty(
    name = "ezkey.api-key.rate-limit.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class RateLimitService {

  private static final Logger logger = LoggerFactory.getLogger(RateLimitService.class);

  private final ApiKeyRateLimitProperties properties;
  private final MeterRegistry meterRegistry;

  private final Cache<String, Bucket> createAttemptBuckets;
  private final Cache<String, Bucket> waitAttemptBuckets;

  /**
   * Constructs the rate limiting service.
   *
   * @param properties rate limiting configuration properties
   * @param meterRegistry metrics registry for monitoring
   */
  public RateLimitService(ApiKeyRateLimitProperties properties, MeterRegistry meterRegistry) {
    this.properties = properties;
    this.meterRegistry = meterRegistry;

    this.createAttemptBuckets =
        Caffeine.newBuilder().maximumSize(10000).expireAfterAccess(Duration.ofHours(1)).build();

    this.waitAttemptBuckets =
        Caffeine.newBuilder().maximumSize(10000).expireAfterAccess(Duration.ofHours(1)).build();

    logger.info("Integration API RateLimitService initialized");
    logger.info(
        "Create auth attempt limit: {} requests per {} minutes",
        properties.getCreateAuthAttempt().getRequests(),
        properties.getCreateAuthAttempt().getWindowMinutes());
    logger.info(
        "Wait auth attempt limit: {} requests per {} minutes",
        properties.getWaitAuthAttempt().getRequests(),
        properties.getWaitAuthAttempt().getWindowMinutes());
  }

  /**
   * Checks whether the given API key may create a new auth attempt.
   *
   * @param apiKeyId the API key identifier used as the rate limit bucket key
   * @return {@code true} if the operation is within limits, {@code false} if the limit is exceeded
   */
  public boolean canCreateAuthAttempt(String apiKeyId) {
    Bucket bucket =
        createAttemptBuckets.get("create:" + apiKeyId, k -> createCreateAttemptBucket());
    boolean allowed = bucket.tryConsume(1);

    meterRegistry
        .counter(
            "rate_limit.checks.total",
            "operation",
            "create_auth_attempt",
            "type",
            "integration_api")
        .increment();
    if (!allowed) {
      meterRegistry
          .counter(
              "rate_limit.exceeded.total",
              "operation",
              "create_auth_attempt",
              "type",
              "integration_api")
          .increment();
    }
    return allowed;
  }

  /**
   * Checks whether the given API key may call the wait/cancel endpoint.
   *
   * @param apiKeyId the API key identifier
   * @return {@code true} if the operation is within limits
   */
  public boolean canWaitAuthAttempt(String apiKeyId) {
    Bucket bucket = waitAttemptBuckets.get("wait:" + apiKeyId, k -> createWaitAttemptBucket());
    boolean allowed = bucket.tryConsume(1);

    meterRegistry
        .counter(
            "rate_limit.checks.total", "operation", "wait_auth_attempt", "type", "integration_api")
        .increment();
    if (!allowed) {
      meterRegistry
          .counter(
              "rate_limit.exceeded.total",
              "operation",
              "wait_auth_attempt",
              "type",
              "integration_api")
          .increment();
    }
    return allowed;
  }

  /**
   * Records a successful create auth attempt operation.
   *
   * <p>Kept for API symmetry; with Bucket4j tokens are already consumed during the check.
   *
   * @param apiKeyId the API key identifier (informational)
   */
  public void recordCreateAuthAttempt(String apiKeyId) {
    logger.debug("Recorded create auth attempt for API key: {}", apiKeyId);
  }

  /**
   * Records a successful wait/cancel auth attempt operation.
   *
   * <p>Kept for API symmetry; with Bucket4j tokens are already consumed during the check.
   *
   * @param apiKeyId the API key identifier (informational)
   */
  public void recordWaitAuthAttempt(String apiKeyId) {
    logger.debug("Recorded wait auth attempt for API key: {}", apiKeyId);
  }

  /**
   * Returns a snapshot of the current rate limit status for the create-attempt bucket.
   *
   * @param apiKeyId the API key identifier
   * @return rate limit status information
   */
  public RateLimitStatus getRateLimitStatus(String apiKeyId) {
    Bucket bucket = createAttemptBuckets.getIfPresent("create:" + apiKeyId);
    int limit = properties.getCreateAuthAttempt().getRequests();

    if (bucket == null) {
      return new RateLimitStatus(limit, 0, properties.getCreateAuthAttempt().getWindowMinutes());
    }

    long availableTokens = bucket.getAvailableTokens();
    int current = (int) (limit - availableTokens);
    return new RateLimitStatus(
        limit, current, properties.getCreateAuthAttempt().getWindowMinutes());
  }

  private Bucket createCreateAttemptBucket() {
    ApiKeyRateLimitProperties.CreateAuthAttemptConfig config = properties.getCreateAuthAttempt();
    Bandwidth limit =
        Bandwidth.builder()
            .capacity(config.getRequests())
            .refillIntervally(config.getRequests(), Duration.ofMinutes(config.getWindowMinutes()))
            .build();
    return Bucket.builder().addLimit(limit).build();
  }

  private Bucket createWaitAttemptBucket() {
    ApiKeyRateLimitProperties.WaitAuthAttemptConfig config = properties.getWaitAuthAttempt();
    Bandwidth limit =
        Bandwidth.builder()
            .capacity(config.getRequests())
            .refillIntervally(config.getRequests(), Duration.ofMinutes(config.getWindowMinutes()))
            .build();
    return Bucket.builder().addLimit(limit).build();
  }

  /** Snapshot of the current rate limit state for an API key. */
  public static class RateLimitStatus {

    private final int limit;
    private final int current;
    private final int windowSizeMinutes;

    /**
     * Constructs a rate limit status snapshot.
     *
     * @param limit the configured maximum number of requests
     * @param current the number of requests already consumed in the current window
     * @param windowSizeMinutes the size of the sliding window in minutes
     */
    public RateLimitStatus(int limit, int current, int windowSizeMinutes) {
      this.limit = limit;
      this.current = current;
      this.windowSizeMinutes = windowSizeMinutes;
    }

    /**
     * Returns the configured request limit.
     *
     * @return the limit
     */
    public int getLimit() {
      return limit;
    }

    /**
     * Returns the number of requests consumed so far.
     *
     * @return current consumption count
     */
    public int getCurrent() {
      return current;
    }

    /**
     * Returns the window size in minutes.
     *
     * @return window size in minutes
     */
    public int getWindowSizeMinutes() {
      return windowSizeMinutes;
    }

    /**
     * Returns the remaining available requests.
     *
     * @return remaining requests (never negative)
     */
    public int getRemaining() {
      return Math.max(0, limit - current);
    }

    /**
     * Returns whether the rate limit has been exceeded.
     *
     * @return {@code true} if the current count has reached or exceeded the limit
     */
    public boolean isLimitExceeded() {
      return current >= limit;
    }
  }
}
