/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: DemoRateLimitService
 * Description: In-memory Bucket4j rate limiting for internet-exposed ACME demo entry points.
 */

package org.ezkey.demo.acme.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.ezkey.demo.acme.config.AcmeRateLimitProperties;
import org.ezkey.demo.acme.config.TrustedProxyProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * In-memory Bucket4j rate limiting for the ACME demo application's browser entry points.
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Service
public class DemoRateLimitService {

  private static final Logger logger = LoggerFactory.getLogger(DemoRateLimitService.class);

  private final AcmeRateLimitProperties properties;
  private final TrustedProxyProperties trustedProxyProperties;
  private final Cache<String, Bucket> loginBuckets;
  private final Cache<String, Bucket> applyApiKeyBuckets;

  public DemoRateLimitService(
      AcmeRateLimitProperties properties, TrustedProxyProperties trustedProxyProperties) {
    this.properties = properties;
    this.trustedProxyProperties = trustedProxyProperties;
    this.loginBuckets =
        Caffeine.newBuilder().maximumSize(10_000).expireAfterAccess(Duration.ofHours(1)).build();
    this.applyApiKeyBuckets =
        Caffeine.newBuilder().maximumSize(10_000).expireAfterAccess(Duration.ofHours(1)).build();

    logger.info(
        "ACME demo rate limiting initialized: login={} per {} minute(s), applyApiKey={} per {}"
            + " minute(s), enabled={}",
        properties.getLogin().getRequests(),
        properties.getLogin().getWindowMinutes(),
        properties.getApplyApiKey().getRequests(),
        properties.getApplyApiKey().getWindowMinutes(),
        properties.isEnabled());
  }

  public RateLimitDecision checkLogin(HttpServletRequest request) {
    return checkRequest("login", request, loginBuckets, properties.getLogin());
  }

  public RateLimitDecision checkApplyApiKey(HttpServletRequest request) {
    return checkRequest("apply-api-key", request, applyApiKeyBuckets, properties.getApplyApiKey());
  }

  private RateLimitDecision checkRequest(
      String operation,
      HttpServletRequest request,
      Cache<String, Bucket> bucketCache,
      AcmeRateLimitProperties.EndpointConfig config) {
    String clientId = ClientIpResolver.resolve(request, trustedProxyProperties.getCidrs());

    if (!properties.isEnabled()) {
      return new RateLimitDecision(true, 0, clientId);
    }

    Bucket bucket = bucketCache.get(operation + ":" + clientId, key -> createBucket(config));
    ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
    if (probe.isConsumed()) {
      return new RateLimitDecision(true, 0, clientId);
    }

    long retryAfterSeconds =
        Math.max(1, Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds());
    return new RateLimitDecision(false, retryAfterSeconds, clientId);
  }

  private Bucket createBucket(AcmeRateLimitProperties.EndpointConfig config) {
    Bandwidth limit =
        Bandwidth.builder()
            .capacity(config.getRequests())
            .refillIntervally(config.getRequests(), Duration.ofMinutes(config.getWindowMinutes()))
            .build();
    return Bucket.builder().addLimit(limit).build();
  }

  /**
   * Result of a rate-limit check.
   *
   * @param allowed whether the request is allowed
   * @param retryAfterSeconds seconds to wait before retrying when not allowed
   * @param clientId resolved client identifier (typically IP) used for bucketing
   */
  public record RateLimitDecision(boolean allowed, long retryAfterSeconds, String clientId) {}
}
