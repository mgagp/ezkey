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
 * <p>{@code /t/{code}} always consumes the IP-only login bucket first (default 20 / 5 minutes)
 * across all codes — valid activations also consume slot+IP. {@code POST /login} with a slot uses
 * slot+IP (20) plus a wider per-IP login bound (default 60 / 5 minutes). Self-service login (no
 * slot) uses IP-only at the login ceiling. Challenge-wait polling does not call this service.
 *
 * <p>Anti-enumeration: {@link #checkAccessLink} always consumes the IP-only bucket first so that
 * guessing codes cannot obtain a more generous (slot-isolated) allowance, and exhausting the IP
 * bucket with invalid or valid activations also blocks subsequent access-link attempts from that IP
 * (no bucket-state oracle). Rate-limit UI messages remain identical for valid and invalid codes.
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Service
public class DemoRateLimitService {

  private static final Logger logger = LoggerFactory.getLogger(DemoRateLimitService.class);

  private static final String OP_LOGIN = "login";
  private static final String OP_LOGIN_IP_BOUND = "login-ip-bound";
  private static final String OP_APPLY_API_KEY = "apply-api-key";

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
        "ACME demo rate limiting initialized: login={} per {} minute(s),"
            + " loginIpBound={} per {} minute(s), applyApiKey={} per {} minute(s), enabled={}",
        properties.getLogin().getRequests(),
        properties.getLogin().getWindowMinutes(),
        properties.getLoginIpBound().getRequests(),
        properties.getLoginIpBound().getWindowMinutes(),
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
   * Checks login rate-limit buckets for the given client and optional access-code slot.
   *
   * <p>When {@code slotId} is non-blank (slot mode): consumes the wider per-IP login bound ({@code
   * ezkey.rate-limit.login-ip-bound.*}, default 60 / 5 min) and then the slot+IP bucket ({@code
   * ezkey.rate-limit.login.*}, default 20 / 5 min). When {@code slotId} is null or blank
   * (self-service): consumes the IP-only login bucket only.
   *
   * @param request current HTTP request
   * @param slotId active access-code slot id, or null for IP-only self-service
   * @return rate-limit decision
   */
  public RateLimitDecision checkLogin(HttpServletRequest request, String slotId) {
    if (slotId == null || slotId.isBlank()) {
      return checkRequest(OP_LOGIN, request, null, loginBuckets, properties.getLogin());
    }
    RateLimitDecision ipBound =
        checkRequest(OP_LOGIN_IP_BOUND, request, null, loginBuckets, properties.getLoginIpBound());
    if (!ipBound.allowed()) {
      return ipBound;
    }
    return checkRequest(OP_LOGIN, request, slotId, loginBuckets, properties.getLogin());
  }

  /**
   * Rate-limits {@code GET /t/{code}} with anti-enumeration semantics.
   *
   * <p>Always consumes the IP-only login bucket first (same ceiling as {@code
   * ezkey.rate-limit.login.requests}). If that bucket denies, the caller must show the generic
   * rate-limit page whether or not the code was valid. When {@code slotId} is present (valid code),
   * also consumes the slot+IP bucket so activation shares the tester's per-slot login budget.
   *
   * @param request current HTTP request
   * @param slotId resolved slot id for a valid code, or null when the code is unknown/invalid
   * @return rate-limit decision (denied if either applicable bucket is exhausted)
   */
  public RateLimitDecision checkAccessLink(HttpServletRequest request, String slotId) {
    RateLimitDecision ipDecision =
        checkRequest(OP_LOGIN, request, null, loginBuckets, properties.getLogin());
    if (!ipDecision.allowed()) {
      return ipDecision;
    }
    if (slotId == null || slotId.isBlank()) {
      return ipDecision;
    }
    return checkRequest(OP_LOGIN, request, slotId, loginBuckets, properties.getLogin());
  }

  public RateLimitDecision checkApplyApiKey(HttpServletRequest request) {
    return checkRequest(
        OP_APPLY_API_KEY, request, null, applyApiKeyBuckets, properties.getApplyApiKey());
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

    String key = bucketKey(operation, clientId, slotId);
    Bucket bucket = bucketCache.get(key, _ -> createBucket(config));
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
   * @param operation operation name ({@code login}, {@code login-ip-bound}, or {@code
   *     apply-api-key})
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
