/*
 * Ezkey - Open Source Cryptographic MFA Platform
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
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.ezkey.admin.config.AdminRateLimitProperties;
import org.ezkey.admin.config.TrustedProxyProperties;
import org.ezkey.audit.util.ClientIpResolver;
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
 *   <li>POST /api/v1/admin/auth/passwordless-wait
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
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
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
  private static final String PASSWORDLESS_WAIT_ENDPOINT_PATH =
      "/api/v1/admin/auth/passwordless-wait";
  private static final String BACKSTOP_METRIC = "ezkey.rate_limit.backstop.rejected";

  /** POST admin auth endpoints sharing the login rate-limit bucket (per client IP). */
  private static final List<String> RATE_LIMITED_PATHS =
      List.of(LOGIN_ENDPOINT_PATH, PASSWORDLESS_WAIT_ENDPOINT_PATH);

  private static final int FAILURE_COUNT_CACHE_MAX_SIZE = 10_000;
  private static final Duration FAILURE_COUNT_CACHE_TTL = Duration.ofHours(1);
  private static final int BLOCKED_UNTIL_CACHE_MAX_SIZE = 10_000;
  private static final Duration BLOCKED_UNTIL_CACHE_TTL = Duration.ofHours(2);

  private final AdminRateLimitProperties properties;
  private final TrustedProxyProperties trustedProxyProperties;
  private final MeterRegistry meterRegistry;
  private final Cache<String, Bucket> bucketCache;
  private final Cache<String, AtomicInteger> failureCountCache;
  private final Cache<String, Long> blockedUntilCache;
  private final Map<String, AtomicInteger> failureCountMap;
  private final Map<String, Long> blockedUntilMap;

  /**
   * Constructs the rate limiting filter with configuration properties.
   *
   * @param properties the rate limiting configuration properties
   * @param trustedProxyProperties the trusted proxy CIDR list for client IP resolution (never null)
   * @param meterRegistry the metrics registry for monitoring
   */
  public AdminRateLimitFilter(
      AdminRateLimitProperties properties,
      TrustedProxyProperties trustedProxyProperties,
      MeterRegistry meterRegistry) {
    this.properties = properties;
    this.trustedProxyProperties = trustedProxyProperties;
    this.meterRegistry = meterRegistry;

    // Cache buckets for 1 hour with maximum 1000 entries
    this.bucketCache =
        Caffeine.newBuilder().maximumSize(1000).expireAfterAccess(Duration.ofHours(1)).build();

    // Track failure counts and blocked IPs with bounded eviction (SEC-003)
    this.failureCountCache =
        Caffeine.newBuilder()
            .maximumSize(FAILURE_COUNT_CACHE_MAX_SIZE)
            .expireAfterWrite(FAILURE_COUNT_CACHE_TTL)
            .build();
    this.failureCountMap = failureCountCache.asMap();
    this.blockedUntilCache =
        Caffeine.newBuilder()
            .maximumSize(BLOCKED_UNTIL_CACHE_MAX_SIZE)
            .expireAfterWrite(BLOCKED_UNTIL_CACHE_TTL)
            .build();
    this.blockedUntilMap = blockedUntilCache.asMap();

    logger.info(
        "AdminRateLimitFilter initialized with login limit: {} requests per {} minutes",
        properties.getLogin().getRequests(),
        properties.getLogin().getWindowMinutes());
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {

    // Skip rate limiting if disabled at runtime
    if (!properties.isEnabled()) {
      chain.doFilter(request, response);
      return;
    }

    HttpServletRequest req = (HttpServletRequest) request;
    HttpServletResponse res = (HttpServletResponse) response;

    String requestUri = req.getRequestURI();
    String requestMethod = req.getMethod();

    // Apply rate limiting to admin auth POST endpoints (login + passwordless-wait)
    if (shouldApplyRateLimit(requestUri, requestMethod)) {
      String clientId = ClientIpResolver.resolve(req, trustedProxyProperties.getCidrs());

      // Check if IP is blocked
      if (isBlocked(clientId)) {
        long blockedUntil = blockedUntilMap.get(clientId);
        long retryAfter = (blockedUntil - System.currentTimeMillis()) / 1000;

        logger.warn(
            "Blocked IP attempting login: {} - Retry after {} seconds", clientId, retryAfter);

        // Record metrics
        meterRegistry.counter("rate_limit.ip_blocked.total").increment();

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

        // Record metrics
        meterRegistry.counter("rate_limit.login_blocked.total").increment();

        res.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        res.setHeader("Retry-After", String.valueOf(result.retryAfterSeconds));
        res.setHeader("X-Rate-Limit-Limit", String.valueOf(properties.getLogin().getRequests()));
        res.setHeader(
            "X-Rate-Limit-Window", String.valueOf(properties.getLogin().getWindowMinutes()));
        return;
      }

      RateLimitResult backstop = checkBackstop();
      if (!backstop.allowed) {
        logger.warn(
            "Rate-limit backstop exceeded for admin login (per-instance, unkeyed) - Retry after {}"
                + " seconds",
            backstop.retryAfterSeconds);
        meterRegistry.counter(BACKSTOP_METRIC, "endpoint", "login").increment();
        res.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        res.setHeader("Retry-After", String.valueOf(backstop.retryAfterSeconds));
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
    if (!"POST".equals(requestMethod)) {
      return false;
    }

    return RATE_LIMITED_PATHS.stream().anyMatch(requestUri::contains);
  }

  /**
   * Checks rate limit for the given client and returns the result.
   *
   * @param clientId the client identifier
   * @return rate limit check result
   */
  private RateLimitResult checkRateLimit(String clientId) {
    String bucketKey = "login:" + clientId;

    Bucket bucket = bucketCache.get(bucketKey, _ -> createBucket());

    if (bucket.tryConsume(1)) {
      return new RateLimitResult(true, 0);
    } else {
      // Calculate retry after based on refill time
      long retryAfter = properties.getLogin().getWindowMinutes() * 60L;
      return new RateLimitResult(false, retryAfter);
    }
  }

  private RateLimitResult checkBackstop() {
    AdminRateLimitProperties.BackstopConfig backstop = properties.getBackstop();
    if (backstop == null || !backstop.isEnabled() || backstop.getLogin() == null) {
      return new RateLimitResult(true, 0);
    }
    AdminRateLimitProperties.LoginConfig config = backstop.getLogin();
    Bucket bucket = bucketCache.get("backstop:login", _ -> createBackstopBucket(config));
    if (bucket.tryConsume(1)) {
      return new RateLimitResult(true, 0);
    }
    long retryAfter = Math.max(1L, config.getWindowMinutes() * 60L);
    return new RateLimitResult(false, retryAfter);
  }

  private static Bucket createBackstopBucket(AdminRateLimitProperties.LoginConfig config) {
    Bandwidth limit =
        Bandwidth.builder()
            .capacity(config.getRequests())
            .refillIntervally(config.getRequests(), Duration.ofMinutes(config.getWindowMinutes()))
            .build();
    return Bucket.builder().addLimit(limit).build();
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
        failureCountMap.computeIfAbsent(clientId, _ -> new AtomicInteger(0));
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

  /**
   * Returns the number of tracked failure-count entries. Package-private for unit tests verifying
   * bounded cache eviction (SEC-003).
   *
   * @return current failure-count map size
   */
  int failureTrackingEntryCount() {
    return failureCountMap.size();
  }

  /** Triggers Caffeine maintenance for unit tests verifying bounded eviction (SEC-003). */
  void runFailureTrackingMaintenanceForTest() {
    failureCountCache.cleanUp();
    blockedUntilCache.cleanUp();
  }

  /** Result of a rate limit check operation. */
  private static class RateLimitResult {
    private final boolean allowed;
    private final long retryAfterSeconds;

    RateLimitResult(boolean allowed, long retryAfterSeconds) {
      this.allowed = allowed;
      this.retryAfterSeconds = retryAfterSeconds;
    }
  }
}
