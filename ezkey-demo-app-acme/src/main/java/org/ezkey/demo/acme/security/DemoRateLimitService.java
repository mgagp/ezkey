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
 * <p>Login and access-link ({@code /t/{code}}) share the {@code ezkey.rate-limit.login.*} ceiling
 * (default 20 requests / 5 minutes). Buckets are keyed by client IP alone for self-service login
 * and unknown/invalid access codes, and by {@code slotId + IP} once a code has resolved to a valid
 * slot (or when login runs with that slot in session). Challenge-wait polling does not call this
 * service and therefore does not consume tokens.
 *
 * <p>Anti-enumeration: {@link #checkAccessLink} always consumes the IP-only bucket first so that
 * guessing codes cannot obtain a more generous (slot-isolated) allowance, and exhausting the IP
 * bucket with invalid codes also blocks subsequent valid-code activations from that IP (no
 * bucket-state oracle). Rate-limit UI messages remain identical for valid and invalid codes.
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

  /**
   * Checks the IP-only login bucket (self-service / no active slot).
   *
   * @param request current HTTP request
   * @return rate-limit decision
   */
  public RateLimitDecision checkLogin(HttpServletRequest request) {
    return checkLogin(request, null);
  }

  /**
   * Checks the login rate-limit bucket for the given client and optional access-code slot.
   *
   * <p>When {@code slotId} is non-blank, the bucket key is {@code slot + IP} so testers behind the
   * same NAT with different slots do not share a budget. When {@code slotId} is null or blank, the
   * IP-only bucket is used (self-service login).
   *
   * @param request current HTTP request
   * @param slotId active access-code slot id, or null for IP-only
   * @return rate-limit decision
   */
  public RateLimitDecision checkLogin(HttpServletRequest request, String slotId) {
    return checkRequest("login", request, slotId, loginBuckets, properties.getLogin());
  }

  /**
   * Rate-limits {@code GET /t/{code}} with anti-enumeration semantics.
   *
   * <p>Always consumes the IP-only login bucket first (same ceiling as {@code
   * ezkey.rate-limit.login.requests}). If that bucket denies, the caller must show the generic
   * rate-limit page whether or not the code was valid. When {@code slotId} is present (valid code),
   * also consumes the slot+IP bucket so activation shares the tester's login budget.
   *
   * @param request current HTTP request
   * @param slotId resolved slot id for a valid code, or null when the code is unknown/invalid
   * @return rate-limit decision (denied if either applicable bucket is exhausted)
   */
  public RateLimitDecision checkAccessLink(HttpServletRequest request, String slotId) {
    RateLimitDecision ipDecision = checkLogin(request, null);
    if (!ipDecision.allowed()) {
      return ipDecision;
    }
    if (slotId == null || slotId.isBlank()) {
      return ipDecision;
    }
    return checkLogin(request, slotId);
  }

  public RateLimitDecision checkApplyApiKey(HttpServletRequest request) {
    return checkRequest(
        "apply-api-key", request, null, applyApiKeyBuckets, properties.getApplyApiKey());
  }

  private RateLimitDecision checkRequest(
      String operation,
      HttpServletRequest request,
      String slotId,
      Cache<String, Bucket> bucketCache,
      AcmeRateLimitProperties.EndpointConfig config) {
    String clientId = ClientIpResolver.resolve(request, trustedProxyProperties.getCidrs());

    if (!properties.isEnabled()) {
      return new RateLimitDecision(true, 0, clientId);
    }

    String bucketKey = bucketKey(operation, clientId, slotId);
    Bucket bucket = bucketCache.get(bucketKey, _ -> createBucket(config));
    ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
    if (probe.isConsumed()) {
      return new RateLimitDecision(true, 0, clientId);
    }

    long retryAfterSeconds =
        Math.max(1, Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds());
    return new RateLimitDecision(false, retryAfterSeconds, clientId);
  }

  /**
   * Builds the Caffeine cache key for a rate-limit bucket.
   *
   * <p>Package-visible for tests. Keys never include access codes — only operation, optional slot
   * id, and client IP.
   *
   * @param operation operation name ({@code login} or {@code apply-api-key})
   * @param clientId resolved client IP
   * @param slotId optional slot id
   * @return cache key
   */
  static String bucketKey(String operation, String clientId, String slotId) {
    if (slotId != null && !slotId.isBlank()) {
      return operation + ":slot:" + slotId + ":ip:" + clientId;
    }
    return operation + ":ip:" + clientId;
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
