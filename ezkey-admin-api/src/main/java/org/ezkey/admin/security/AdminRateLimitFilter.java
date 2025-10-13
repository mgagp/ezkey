/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Filter: AdminRateLimitFilter
 * Description: HTTP filter for applying rate limiting to admin authentication endpoints.
 */

package org.ezkey.admin.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.ezkey.admin.config.AdminRateLimitProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

/**
 * HTTP filter for applying rate limiting to admin authentication endpoints.
 *
 * <p>This filter intercepts HTTP requests and applies configurable rate limiting to the admin login
 * endpoint to prevent brute force attacks. Uses Bucket4j token bucket algorithm for efficient rate
 * limiting.
 *
 * <p><b>Targeted Endpoints:</b>
 *
 * <ul>
 *   <li>POST /api/v1/admin/auth/login
 * </ul>
 *
 * <p><b>Rate Limiting Strategy:</b>
 *
 * <ul>
 *   <li>Login: By client IP (configurable)
 *   <li>Blocking: After configurable number of failures
 * </ul>
 *
 * <p><b>Response Behavior:</b>
 *
 * <ul>
 *   <li>HTTP 429 Too Many Requests when limit exceeded
 *   <li>Retry-After header with seconds to wait
 *   <li>Pass-through for non-targeted endpoints
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see AdminRateLimitProperties
 * @see Bucket4j
 */
public class AdminRateLimitFilter implements Filter {

  private static final Logger logger = LoggerFactory.getLogger(AdminRateLimitFilter.class);
  private static final String LOGIN_ENDPOINT_PATH = "/api/v1/admin/auth/login";

  private final AdminRateLimitProperties properties;
  private final Cache<String, Bucket> bucketCache;
  private final ConcurrentHashMap<String, AtomicInteger> failureCountMap;
  private final ConcurrentHashMap<String, Long> blockedUntilMap;

  /**
   * Constructs the rate limiting filter with configuration properties.
   *
   * @param properties the rate limiting configuration properties
   */
  public AdminRateLimitFilter(AdminRateLimitProperties properties) {
    this.properties = properties;

    // Cache buckets for 1 hour with maximum 1000 entries
    this.bucketCache =
        Caffeine.newBuilder().maximumSize(1000).expireAfterAccess(Duration.ofHours(1)).build();

    // Track failure counts and blocked IPs
    this.failureCountMap = new ConcurrentHashMap<>();
    this.blockedUntilMap = new ConcurrentHashMap<>();

    logger.info(
        "AdminRateLimitFilter initialized with login limit: {} requests per {} minutes",
        properties.getLogin().getRequests(),
        properties.getLogin().getWindowMinutes());
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {

    HttpServletRequest req = (HttpServletRequest) request;
    HttpServletResponse res = (HttpServletResponse) response;

    String requestUri = req.getRequestURI();
    String requestMethod = req.getMethod();

    // Apply rate limiting only to login endpoint
    if (shouldApplyRateLimit(requestUri, requestMethod)) {
      String clientId = extractClientId(req);

      // Check if IP is blocked
      if (isBlocked(clientId)) {
        long blockedUntil = blockedUntilMap.get(clientId);
        long retryAfter = (blockedUntil - System.currentTimeMillis()) / 1000;

        logger.warn(
            "Blocked IP attempting login: {} - Retry after {} seconds", clientId, retryAfter);

        res.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        res.setHeader("Retry-After", String.valueOf(Math.max(1, retryAfter)));
        res.setHeader("X-Rate-Limit-Reason", "IP blocked due to too many failed attempts");
        return;
      }

      // Check rate limit
      RateLimitResult result = checkRateLimit(clientId);

      if (!result.allowed) {
        logger.warn(
            "Rate limit exceeded for IP: {} - Retry after {} seconds",
            clientId,
            result.retryAfterSeconds);

        res.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        res.setHeader("Retry-After", String.valueOf(result.retryAfterSeconds));
        res.setHeader("X-Rate-Limit-Limit", String.valueOf(properties.getLogin().getRequests()));
        res.setHeader(
            "X-Rate-Limit-Window", String.valueOf(properties.getLogin().getWindowMinutes()));
        return;
      }
    }

    chain.doFilter(request, response);
  }

  /**
   * Determines if rate limiting should be applied to the given request.
   *
   * @param requestUri the request URI
   * @param requestMethod the HTTP method
   * @return true if rate limiting should be applied
   */
  private boolean shouldApplyRateLimit(String requestUri, String requestMethod) {
    // Only apply to POST requests on login endpoint
    if (!"POST".equals(requestMethod)) {
      return false;
    }

    return requestUri.contains(LOGIN_ENDPOINT_PATH);
  }

  /**
   * Checks rate limit for the given client and returns the result.
   *
   * @param clientId the client identifier
   * @return rate limit check result
   */
  private RateLimitResult checkRateLimit(String clientId) {
    String bucketKey = "login:" + clientId;

    Bucket bucket = bucketCache.get(bucketKey, key -> createBucket());

    if (bucket.tryConsume(1)) {
      return new RateLimitResult(true, 0);
    } else {
      // Calculate retry after based on refill time
      long retryAfter = properties.getLogin().getWindowMinutes() * 60L;
      return new RateLimitResult(false, retryAfter);
    }
  }

  /**
   * Extracts client identifier for rate limiting.
   *
   * @param request the HTTP request
   * @return client identifier string
   */
  private String extractClientId(HttpServletRequest request) {
    // Priority 1: X-Forwarded-For (standard proxy header)
    String xForwardedFor = request.getHeader("X-Forwarded-For");
    if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
      String firstIP = xForwardedFor.split(",")[0].trim();
      if (isValidIP(firstIP)) {
        return firstIP;
      }
    }

    // Priority 2: X-Real-IP (nginx proxy header)
    String xRealIP = request.getHeader("X-Real-IP");
    if (xRealIP != null && !xRealIP.isEmpty()) {
      if (isValidIP(xRealIP)) {
        return xRealIP;
      }
    }

    // Fallback: Direct connection IP
    return request.getRemoteAddr();
  }

  /**
   * Validates if a string is a valid IP address.
   *
   * @param ip the IP string to validate
   * @return true if valid IP address
   */
  private boolean isValidIP(String ip) {
    if (ip == null || ip.isEmpty()) {
      return false;
    }

    // Simple validation for IPv4 and IPv6
    String ipv4Pattern =
        "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$";
    String ipv6Pattern = "^([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$";

    return ip.matches(ipv4Pattern)
        || ip.matches(ipv6Pattern)
        || ip.equals("::1")
        || ip.equals("0:0:0:0:0:0:0:1");
  }

  /**
   * Creates a new rate limiting bucket based on configuration.
   *
   * @return configured rate limiting bucket
   */
  private Bucket createBucket() {
    AdminRateLimitProperties.LoginConfig config = properties.getLogin();

    Bandwidth limit =
        Bandwidth.builder()
            .capacity(config.getRequests())
            .refillIntervally(config.getRequests(), Duration.ofMinutes(config.getWindowMinutes()))
            .build();

    return Bucket.builder().addLimit(limit).build();
  }

  /**
   * Checks if a client IP is currently blocked.
   *
   * @param clientId the client identifier
   * @return true if blocked
   */
  private boolean isBlocked(String clientId) {
    Long blockedUntil = blockedUntilMap.get(clientId);
    if (blockedUntil == null) {
      return false;
    }

    // Check if block has expired
    if (System.currentTimeMillis() > blockedUntil) {
      // Clean up expired block
      blockedUntilMap.remove(clientId);
      failureCountMap.remove(clientId);
      logger.info("Block expired for IP: {}", clientId);
      return false;
    }

    return true;
  }

  /**
   * Records a failed authentication attempt for a client. May trigger IP blocking if threshold is
   * exceeded.
   *
   * @param clientId the client identifier
   */
  public void recordFailedAttempt(String clientId) {
    int blockAfterFailures = properties.getLogin().getBlockAfterFailures();

    // Skip if blocking is disabled
    if (blockAfterFailures <= 0) {
      return;
    }

    AtomicInteger failureCount =
        failureCountMap.computeIfAbsent(clientId, k -> new AtomicInteger(0));
    int failures = failureCount.incrementAndGet();

    logger.debug("Failed login attempt {} for IP: {}", failures, clientId);

    // Block if threshold exceeded
    if (failures >= blockAfterFailures) {
      long blockDuration =
          Duration.ofMinutes(properties.getLogin().getBlockDurationMinutes()).toMillis();
      long blockedUntil = System.currentTimeMillis() + blockDuration;

      blockedUntilMap.put(clientId, blockedUntil);

      logger.warn(
          "IP BLOCKED due to {} failed attempts: {} - Blocked for {} minutes",
          failures,
          clientId,
          properties.getLogin().getBlockDurationMinutes());
    }
  }

  /**
   * Records a successful authentication attempt for a client. Clears failure count and any blocks.
   *
   * @param clientId the client identifier
   */
  public void recordSuccessfulAttempt(String clientId) {
    failureCountMap.remove(clientId);
    blockedUntilMap.remove(clientId);
    logger.debug("Successful login for IP: {} - Failure count reset", clientId);
  }

  /** Result of a rate limit check operation. */
  private static class RateLimitResult {
    final boolean allowed;
    final long retryAfterSeconds;

    RateLimitResult(boolean allowed, long retryAfterSeconds) {
      this.allowed = allowed;
      this.retryAfterSeconds = retryAfterSeconds;
    }
  }
}
