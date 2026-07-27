/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Class: AdminOperationsRateLimitService
 * Description: Rate limiting service for admin operations to prevent abuse and ensure security.
 */

package org.ezkey.admin.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.ezkey.admin.config.AdminOperationsRateLimitProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Service for implementing rate limiting on admin operations.
 *
 * <p>This service provides rate limiting functionality to prevent abuse of admin operations,
 * specifically for API key creation and enrollment reset operations. It uses Bucket4j token bucket
 * algorithm for efficient rate limiting with configurable limits.
 *
 * <p><b>Rate Limiting Strategy:</b> Uses Bucket4j token bucket algorithm where each admin has a
 * bucket with configurable capacity and refill rate. This provides smooth rate limiting with burst
 * capacity while maintaining overall rate limits.
 *
 * <p><b>Configuration:</b> Rate limits are externally configurable per operation type:
 *
 * <ul>
 *   <li><b>API_KEY_CREATE:</b> Configurable via ezkey.admin-operations.rate-limit.api-key-create.*
 *   <li><b>API_KEY_REVOKE:</b> Configurable via ezkey.admin-operations.rate-limit.api-key-revoke.*
 *   <li><b>API_KEY_UPDATE:</b> Configurable via ezkey.admin-operations.rate-limit.api-key-update.*
 *   <li><b>ENROLLMENT_RESET:</b> Configurable via
 *       ezkey.admin-operations.rate-limit.enrollment-reset.*
 * </ul>
 *
 * <p><b>Conditional Activation:</b> This service is only created when
 * ezkey.admin-operations.rate-limit.enabled=true. When disabled, controllers should handle the
 * absence of this service gracefully or use a NoOp implementation.
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
    name = "ezkey.admin-operations.rate-limit.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class AdminOperationsRateLimitService {

  private static final Logger logger =
      LoggerFactory.getLogger(AdminOperationsRateLimitService.class);

  private final AdminOperationsRateLimitProperties properties;
  private final MeterRegistry meterRegistry;

  // Bucket4j-based rate limiting with Caffeine cache
  private final Cache<String, Bucket> apiKeyCreateBuckets;
  private final Cache<String, Bucket> apiKeyRevokeBuckets;
  private final Cache<String, Bucket> apiKeyUpdateBuckets;
  private final Cache<String, Bucket> enrollmentResetBuckets;

  /**
   * Constructs the rate limiting service with configuration properties.
   *
   * @param properties the rate limiting configuration properties
   * @param meterRegistry the metrics registry for monitoring
   */
  public AdminOperationsRateLimitService(
      AdminOperationsRateLimitProperties properties, MeterRegistry meterRegistry) {
    this.properties = properties;
    this.meterRegistry = meterRegistry;

    // Initialize Caffeine caches for bucket storage
    this.apiKeyCreateBuckets =
        Caffeine.newBuilder().maximumSize(1000).expireAfterAccess(Duration.ofHours(1)).build();

    this.apiKeyRevokeBuckets =
        Caffeine.newBuilder().maximumSize(1000).expireAfterAccess(Duration.ofHours(1)).build();

    this.apiKeyUpdateBuckets =
        Caffeine.newBuilder().maximumSize(1000).expireAfterAccess(Duration.ofHours(1)).build();

    this.enrollmentResetBuckets =
        Caffeine.newBuilder().maximumSize(1000).expireAfterAccess(Duration.ofHours(1)).build();

    logger.info("AdminOperationsRateLimitService initialized with Bucket4j implementation");
    logger.info(
        "API key create limit: {} requests per {} minutes",
        properties.getApiKeyCreate().getRequests(),
        properties.getApiKeyCreate().getWindowMinutes());
    logger.info(
        "API key revoke limit: {} requests per {} minutes",
        properties.getApiKeyRevoke().getRequests(),
        properties.getApiKeyRevoke().getWindowMinutes());
    logger.info(
        "API key update limit: {} requests per {} minutes",
        properties.getApiKeyUpdate().getRequests(),
        properties.getApiKeyUpdate().getWindowMinutes());
    logger.info(
        "Enrollment reset limit: {} requests per {} minutes",
        properties.getEnrollmentReset().getRequests(),
        properties.getEnrollmentReset().getWindowMinutes());
  }

  /**
   * Checks if an admin can perform an API key creation operation.
   *
   * <p>This method implements rate limiting for API key creation using Bucket4j token bucket
   * algorithm. It checks if the admin has sufficient tokens in its bucket.
   *
   * @param adminId the admin identifier
   * @return true if the operation is allowed, false if rate limit exceeded
   */
  public boolean canCreateApiKey(String adminId) {
    String bucketKey = "api_key_create:" + adminId;
    Bucket bucket = apiKeyCreateBuckets.get(bucketKey, _ -> createApiKeyCreateBucket());

    boolean allowed = bucket.tryConsume(1);

    // Record metrics
    meterRegistry
        .counter("rate_limit.checks.total", "operation", "api_key_create", "type", "admin")
        .increment();

    if (!allowed) {
      meterRegistry
          .counter("rate_limit.exceeded.total", "operation", "api_key_create", "type", "admin")
          .increment();
    }

    return allowed;
  }

  /**
   * Checks if an admin can perform an enrollment reset operation.
   *
   * <p>This method implements rate limiting for enrollment reset using Bucket4j token bucket
   * algorithm. It checks if the admin has sufficient tokens in its bucket.
   *
   * @param tokenOrAdminId the admin identifier or recovery token
   * @return true if the operation is allowed, false if rate limit exceeded
   */
  public boolean canResetEnrollment(String tokenOrAdminId) {
    String bucketKey = "enrollment_reset:" + tokenOrAdminId;
    Bucket bucket = enrollmentResetBuckets.get(bucketKey, _ -> createEnrollmentResetBucket());

    boolean allowed = bucket.tryConsume(1);

    // Record metrics
    meterRegistry
        .counter("rate_limit.checks.total", "operation", "enrollment_reset", "type", "admin")
        .increment();

    if (!allowed) {
      meterRegistry
          .counter("rate_limit.exceeded.total", "operation", "enrollment_reset", "type", "admin")
          .increment();
    }

    return allowed;
  }

  /**
   * Records a successful API key creation operation for rate limiting tracking.
   *
   * <p>Note: With Bucket4j token bucket algorithm, tokens are consumed during the check, so this
   * method is kept for API compatibility but doesn't need to do additional work.
   *
   * @param adminId the admin identifier
   */
  public void recordCreateApiKey(String adminId) {
    // With Bucket4j, tokens are consumed during canCreateApiKey() check
    // This method is kept for API compatibility
    logger.debug("Recorded API key creation for admin: {}", adminId);
  }

  /**
   * Checks if an admin can perform an API key revocation operation.
   *
   * <p>This method implements rate limiting for API key revocation using Bucket4j token bucket
   * algorithm. It checks if the admin has sufficient tokens in its bucket.
   *
   * @param adminId the admin identifier
   * @return true if the operation is allowed, false if rate limit exceeded
   */
  public boolean canRevokeApiKey(String adminId) {
    String bucketKey = "api_key_revoke:" + adminId;
    Bucket bucket = apiKeyRevokeBuckets.get(bucketKey, _ -> createApiKeyRevokeBucket());

    boolean allowed = bucket.tryConsume(1);

    meterRegistry
        .counter("rate_limit.checks.total", "operation", "api_key_revoke", "type", "admin")
        .increment();

    if (!allowed) {
      meterRegistry
          .counter("rate_limit.exceeded.total", "operation", "api_key_revoke", "type", "admin")
          .increment();
    }

    return allowed;
  }

  /**
   * Records a successful API key revocation operation for rate limiting tracking.
   *
   * <p>Note: With Bucket4j token bucket algorithm, tokens are consumed during the check, so this
   * method is kept for API compatibility but doesn't need to do additional work.
   *
   * @param adminId the admin identifier
   */
  public void recordRevokeApiKey(String adminId) {
    logger.debug("Recorded API key revocation for admin: {}", adminId);
  }

  /**
   * Checks if an admin can perform an API key update operation.
   *
   * <p>This method implements rate limiting for API key updates using Bucket4j token bucket
   * algorithm. It checks if the admin has sufficient tokens in its bucket.
   *
   * @param adminId the admin identifier
   * @return true if the operation is allowed, false if rate limit exceeded
   */
  public boolean canUpdateApiKey(String adminId) {
    String bucketKey = "api_key_update:" + adminId;
    Bucket bucket = apiKeyUpdateBuckets.get(bucketKey, _ -> createApiKeyUpdateBucket());

    boolean allowed = bucket.tryConsume(1);

    meterRegistry
        .counter("rate_limit.checks.total", "operation", "api_key_update", "type", "admin")
        .increment();

    if (!allowed) {
      meterRegistry
          .counter("rate_limit.exceeded.total", "operation", "api_key_update", "type", "admin")
          .increment();
    }

    return allowed;
  }

  /**
   * Records a successful API key update operation for rate limiting tracking.
   *
   * <p>Note: With Bucket4j token bucket algorithm, tokens are consumed during the check, so this
   * method is kept for API compatibility but doesn't need to do additional work.
   *
   * @param adminId the admin identifier
   */
  public void recordUpdateApiKey(String adminId) {
    logger.debug("Recorded API key update for admin: {}", adminId);
  }

  /**
   * Records a successful enrollment reset operation for rate limiting tracking.
   *
   * <p>Note: With Bucket4j token bucket algorithm, tokens are consumed during the check, so this
   * method is kept for API compatibility but doesn't need to do additional work.
   *
   * @param tokenOrAdminId the admin identifier or recovery token
   */
  public void recordResetEnrollment(String tokenOrAdminId) {
    // With Bucket4j, tokens are consumed during canResetEnrollment() check
    // This method is kept for API compatibility
    logger.debug("Recorded enrollment reset for admin/token: {}", tokenOrAdminId);
  }

  /**
   * Creates a new Bucket4j bucket for API key creation operations.
   *
   * @return configured rate limiting bucket for API key creation operations
   */
  private Bucket createApiKeyCreateBucket() {
    AdminOperationsRateLimitProperties.ApiKeyCreateConfig config = properties.getApiKeyCreate();

    Bandwidth limit =
        Bandwidth.builder()
            .capacity(config.getRequests())
            .refillIntervally(config.getRequests(), Duration.ofMinutes(config.getWindowMinutes()))
            .build();

    return Bucket.builder().addLimit(limit).build();
  }

  /**
   * Creates a new Bucket4j bucket for API key revocation operations.
   *
   * @return configured rate limiting bucket for API key revocation operations
   */
  private Bucket createApiKeyRevokeBucket() {
    AdminOperationsRateLimitProperties.ApiKeyRevokeConfig config = properties.getApiKeyRevoke();

    Bandwidth limit =
        Bandwidth.builder()
            .capacity(config.getRequests())
            .refillIntervally(config.getRequests(), Duration.ofMinutes(config.getWindowMinutes()))
            .build();

    return Bucket.builder().addLimit(limit).build();
  }

  /**
   * Creates a new Bucket4j bucket for API key update operations.
   *
   * @return configured rate limiting bucket for API key update operations
   */
  private Bucket createApiKeyUpdateBucket() {
    AdminOperationsRateLimitProperties.ApiKeyUpdateConfig config = properties.getApiKeyUpdate();

    Bandwidth limit =
        Bandwidth.builder()
            .capacity(config.getRequests())
            .refillIntervally(config.getRequests(), Duration.ofMinutes(config.getWindowMinutes()))
            .build();

    return Bucket.builder().addLimit(limit).build();
  }

  /**
   * Creates a new Bucket4j bucket for enrollment reset operations.
   *
   * @return configured rate limiting bucket for enrollment reset operations
   */
  private Bucket createEnrollmentResetBucket() {
    AdminOperationsRateLimitProperties.EnrollmentResetConfig config =
        properties.getEnrollmentReset();

    Bandwidth limit =
        Bandwidth.builder()
            .capacity(config.getRequests())
            .refillIntervally(config.getRequests(), Duration.ofMinutes(config.getWindowMinutes()))
            .build();

    return Bucket.builder().addLimit(limit).build();
  }
}
