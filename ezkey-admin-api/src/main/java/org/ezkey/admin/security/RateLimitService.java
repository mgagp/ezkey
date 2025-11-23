/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Class: RateLimitService
 * Description: Rate limiting service for API key operations to prevent abuse and ensure fair usage.
 */

package org.ezkey.admin.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.ezkey.admin.config.ApiKeyRateLimitProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Service for implementing rate limiting on API key operations.
 *
 * <p>This service provides rate limiting functionality to prevent abuse of API key operations,
 * specifically for authentication attempt creation and validation (wait) operations. It uses
 * Bucket4j token bucket algorithm for efficient rate limiting with configurable limits.
 *
 * <p><b>Rate Limiting Strategy:</b> Uses Bucket4j token bucket algorithm where each API key has a
 * bucket with configurable capacity and refill rate. This provides smooth rate limiting with burst
 * capacity while maintaining overall rate limits.
 *
 * <p><b>Configuration:</b> Rate limits are externally configurable per operation type:
 *
 * <ul>
 *   <li><b>CREATE_AUTH_ATTEMPT:</b> Configurable via ezkey.api-key.rate-limit.create-auth-attempt.*
 *   <li><b>WAIT_AUTH_ATTEMPT:</b> Configurable via ezkey.api-key.rate-limit.wait-auth-attempt.*
 * </ul>
 *
 * <p><b>Conditional Activation:</b> This service is only created when
 * ezkey.api-key.rate-limit.enabled=true. When disabled, controllers should handle the absence of
 * this service gracefully or use a NoOp implementation.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
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

  // Bucket4j-based rate limiting with Caffeine cache
  private final Cache<String, Bucket> createAttemptBuckets;
  private final Cache<String, Bucket> waitAttemptBuckets;

  /**
   * Constructs the rate limiting service with configuration properties.
   *
   * @param properties the rate limiting configuration properties
   * @param meterRegistry the metrics registry for monitoring
   */
  public RateLimitService(ApiKeyRateLimitProperties properties, MeterRegistry meterRegistry) {
    this.properties = properties;
    this.meterRegistry = meterRegistry;

    // Initialize Caffeine caches for bucket storage
    this.createAttemptBuckets =
        Caffeine.newBuilder().maximumSize(10000).expireAfterAccess(Duration.ofHours(1)).build();

    this.waitAttemptBuckets =
        Caffeine.newBuilder().maximumSize(10000).expireAfterAccess(Duration.ofHours(1)).build();

    logger.info("RateLimitService initialized with Bucket4j implementation");
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
   * Checks if an API key can perform a create auth attempt operation.
   *
   * <p>This method implements rate limiting for authentication attempt creation using Bucket4j
   * token bucket algorithm. It checks if the API key has sufficient tokens in its bucket.
   *
   * @param apiKeyId the API key identifier
   * @return true if the operation is allowed, false if rate limit exceeded
   */
  public boolean canCreateAuthAttempt(String apiKeyId) {
    String bucketKey = "create:" + apiKeyId;
    Bucket bucket = createAttemptBuckets.get(bucketKey, key -> createCreateAttemptBucket());

    boolean allowed = bucket.tryConsume(1);

    // Record metrics
    meterRegistry
        .counter("rate_limit.checks.total", "operation", "create_auth_attempt", "type", "api_key")
        .increment();

    if (!allowed) {
      meterRegistry
          .counter(
              "rate_limit.exceeded.total", "operation", "create_auth_attempt", "type", "api_key")
          .increment();
    }

    return allowed;
  }

  /**
   * Checks if an API key can perform a wait auth attempt operation.
   *
   * <p>This method implements rate limiting for authentication attempt waiting using Bucket4j token
   * bucket algorithm. It checks if the API key has sufficient tokens in its bucket.
   *
   * @param apiKeyId the API key identifier
   * @return true if the operation is allowed, false if rate limit exceeded
   */
  public boolean canWaitAuthAttempt(String apiKeyId) {
    String bucketKey = "wait:" + apiKeyId;
    Bucket bucket = waitAttemptBuckets.get(bucketKey, key -> createWaitAttemptBucket());

    boolean allowed = bucket.tryConsume(1);

    // Record metrics
    meterRegistry
        .counter("rate_limit.checks.total", "operation", "wait_auth_attempt", "type", "api_key")
        .increment();

    if (!allowed) {
      meterRegistry
          .counter("rate_limit.exceeded.total", "operation", "wait_auth_attempt", "type", "api_key")
          .increment();
    }

    return allowed;
  }

  /**
   * Records a successful create auth attempt operation for rate limiting tracking.
   *
   * <p>Note: With Bucket4j token bucket algorithm, tokens are consumed during the check, so this
   * method is kept for API compatibility but doesn't need to do additional work.
   *
   * @param apiKeyId the API key identifier
   */
  public void recordCreateAuthAttempt(String apiKeyId) {
    // With Bucket4j, tokens are consumed during canCreateAuthAttempt() check
    // This method is kept for API compatibility
    logger.debug("Recorded create auth attempt for API key: {}", apiKeyId);
  }

  /**
   * Records a successful wait auth attempt operation for rate limiting tracking.
   *
   * <p>Note: With Bucket4j token bucket algorithm, tokens are consumed during the check, so this
   * method is kept for API compatibility but doesn't need to do additional work.
   *
   * @param apiKeyId the API key identifier
   */
  public void recordWaitAuthAttempt(String apiKeyId) {
    // With Bucket4j, tokens are consumed during canWaitAuthAttempt() check
    // This method is kept for API compatibility
    logger.debug("Recorded wait auth attempt for API key: {}", apiKeyId);
  }

  /**
   * Gets the current rate limit status for an API key.
   *
   * @param apiKeyId the API key identifier
   * @return rate limit status information
   */
  public RateLimitStatus getRateLimitStatus(String apiKeyId) {
    String bucketKey = "create:" + apiKeyId;
    Bucket bucket = createAttemptBuckets.getIfPresent(bucketKey);

    if (bucket == null) {
      return new RateLimitStatus(
          properties.getCreateAuthAttempt().getRequests(),
          0,
          properties.getCreateAuthAttempt().getWindowMinutes());
    }

    // Get available tokens (this is an approximation)
    long availableTokens = bucket.getAvailableTokens();
    int limit = properties.getCreateAuthAttempt().getRequests();
    int current = (int) (limit - availableTokens);

    return new RateLimitStatus(
        limit, current, properties.getCreateAuthAttempt().getWindowMinutes());
  }

  /**
   * Creates a new Bucket4j bucket for create auth attempt operations.
   *
   * @return configured rate limiting bucket for create operations
   */
  private Bucket createCreateAttemptBucket() {
    ApiKeyRateLimitProperties.CreateAuthAttemptConfig config = properties.getCreateAuthAttempt();

    Bandwidth limit =
        Bandwidth.builder()
            .capacity(config.getRequests())
            .refillIntervally(config.getRequests(), Duration.ofMinutes(config.getWindowMinutes()))
            .build();

    return Bucket.builder().addLimit(limit).build();
  }

  /**
   * Creates a new Bucket4j bucket for wait auth attempt operations.
   *
   * @return configured rate limiting bucket for wait operations
   */
  private Bucket createWaitAttemptBucket() {
    ApiKeyRateLimitProperties.WaitAuthAttemptConfig config = properties.getWaitAuthAttempt();

    Bandwidth limit =
        Bandwidth.builder()
            .capacity(config.getRequests())
            .refillIntervally(config.getRequests(), Duration.ofMinutes(config.getWindowMinutes()))
            .build();

    return Bucket.builder().addLimit(limit).build();
  }

  /** Data class representing rate limit status information. */
  public static class RateLimitStatus {
    private final int limit;
    private final int current;
    private final int windowSizeMinutes;

    public RateLimitStatus(int limit, int current, int windowSizeMinutes) {
      this.limit = limit;
      this.current = current;
      this.windowSizeMinutes = windowSizeMinutes;
    }

    public int getLimit() {
      return limit;
    }

    public int getCurrent() {
      return current;
    }

    public int getWindowSizeMinutes() {
      return windowSizeMinutes;
    }

    public int getRemaining() {
      return Math.max(0, limit - current);
    }

    public boolean isLimitExceeded() {
      return current >= limit;
    }
  }
}
