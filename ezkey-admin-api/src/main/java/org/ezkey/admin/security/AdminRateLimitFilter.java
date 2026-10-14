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
import io.github.bucket4j.ConsumptionProbe;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.ezkey.admin.config.AdminRateLimitProperties;
import org.ezkey.admin.config.TrustedProxyProperties;
import org.ezkey.audit.util.ClientIpResolver;
import org.ezkey.util.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

/**
 * HTTP filter for applying rate limiting to admin authentication endpoints.
 *
 * <p>This filter intercepts HTTP requests and applies configurable rate limiting to the admin login
 * and passwordless-wait endpoints to prevent brute force attacks. Uses Bucket4j token bucket
 * algorithm for efficient rate limiting.
 *
 * <p><b>Targeted Endpoints:</b>
 *
 * <ul>
 *   <li>POST /api/v1/admin/auth/login — per client IP (login bucket)
 *   <li>POST /api/v1/admin/auth/passwordless-wait — separate bucket, keyed per authAttemptId
 *   <li>POST /api/v1/admin/auth/passwordless-cancel — same wait bucket as passwordless-wait
 * </ul>
 *
 * <p><b>Rate Limiting Strategy (#831):</b>
 *
 * <ul>
 *   <li>Login and passwordless-wait use <em>separate</em> buckets so Cancel + retry is not charged
 *       twice against the login budget
 *   <li>Login: greedy refill ({@code refillGreedy}) for smoother recovery after bursts
 *   <li>Retry-After: remaining nanos to refill from {@code tryConsumeAndReturnRemaining}
 *   <li>IP block after configurable consecutive failures; unkeyed per-instance login backstop
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
  private static final String PASSWORDLESS_CANCEL_ENDPOINT_PATH =
      "/api/v1/admin/auth/passwordless-cancel";
  private static final String BACKSTOP_METRIC = "ezkey.rate_limit.backstop.rejected";

  private static final Pattern AUTH_ATTEMPT_ID_PATTERN =
      Pattern.compile("\"authAttemptId\"\\s*:\\s*(\\d+)");

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
        "AdminRateLimitFilter initialized with login limit: {} requests per {} minutes"
            + " (passwordless-wait: {} per {} min, separate bucket)",
        properties.getLogin().getRequests(),
        properties.getLogin().getWindowMinutes(),
        properties.getPasswordlessWait().getRequests(),
        properties.getPasswordlessWait().getWindowMinutes());
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

    if (isLoginPath(requestUri, requestMethod)) {
      enforceLoginRateLimit(req, res, chain);
      return;
    }
    if (isPasswordlessWaitPath(requestUri, requestMethod)) {
      enforcePasswordlessWaitRateLimit(req, res, chain);
      return;
    }

    chain.doFilter(request, response);
  }

  /**
   * Applies login IP block + login bucket + optional backstop, then forwards the request.
   *
   * @param req HTTP request
   * @param res HTTP response
   * @param chain remaining filter chain
   * @throws IOException if the chain fails
   * @throws ServletException if the chain fails
   */
  private void enforceLoginRateLimit(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws IOException, ServletException {
    String clientId = ClientIpResolver.resolve(req, trustedProxyProperties.getCidrs());
    String clientIdForLog = LogSanitizer.sanitizeForLog(clientId);

    if (isBlocked(clientId)) {
      long blockedUntil = blockedUntilMap.get(clientId);
      long retryAfter = (blockedUntil - System.currentTimeMillis()) / 1000;

      logger.warn(
          "Blocked IP attempting login: {} - Retry after {} seconds", clientIdForLog, retryAfter);

      meterRegistry.counter("rate_limit.ip_blocked.total").increment();

      res.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
      res.setHeader("Retry-After", String.valueOf(Math.max(1, retryAfter)));
      res.setHeader("X-Rate-Limit-Reason", "IP blocked due to too many failed attempts");
      return;
    }

    RateLimitResult result = checkLoginRateLimit(clientId);
    if (!result.allowed) {
      logger.warn(
          "Rate limit exceeded for IP: {} path=login - Retry after {} seconds",
          clientIdForLog,
          result.retryAfterSeconds);

      meterRegistry.counter("rate_limit.login_blocked.total", "endpoint", "login").increment();

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

    chain.doFilter(req, res);
  }

  /**
   * Applies the passwordless-wait/cancel bucket (keyed by authAttemptId when present), then
   * forwards a replayable body.
   *
   * @param req HTTP request
   * @param res HTTP response
   * @param chain remaining filter chain
   * @throws IOException if the body cannot be read or the chain fails
   * @throws ServletException if the chain fails
   */
  private void enforcePasswordlessWaitRateLimit(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws IOException, ServletException {
    String clientId = ClientIpResolver.resolve(req, trustedProxyProperties.getCidrs());
    String clientIdForLog = LogSanitizer.sanitizeForLog(clientId);

    // Wait/cancel JSON bodies are small; cache full body so @RequestBody still works.
    byte[] body = req.getInputStream().readAllBytes();
    Integer authAttemptId = extractAuthAttemptId(body);
    String waitBucketKey = passwordlessWaitBucketKey(authAttemptId, clientId);
    HttpServletRequest requestToForward = new CachedBodyHttpServletRequest(req, body);

    RateLimitResult result = checkPasswordlessWaitRateLimit(waitBucketKey);
    if (!result.allowed) {
      logger.warn(
          "Rate limit exceeded for IP: {} path=passwordless-wait - Retry after {} seconds",
          clientIdForLog,
          result.retryAfterSeconds);

      meterRegistry
          .counter("rate_limit.login_blocked.total", "endpoint", "passwordless-wait")
          .increment();

      res.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
      res.setHeader("Retry-After", String.valueOf(result.retryAfterSeconds));
      res.setHeader(
          "X-Rate-Limit-Limit", String.valueOf(properties.getPasswordlessWait().getRequests()));
      res.setHeader(
          "X-Rate-Limit-Window",
          String.valueOf(properties.getPasswordlessWait().getWindowMinutes()));
      return;
    }

    chain.doFilter(requestToForward, res);
  }

  private static boolean isLoginPath(String requestUri, String requestMethod) {
    return "POST".equals(requestMethod)
        && requestUri != null
        && requestUri.contains(LOGIN_ENDPOINT_PATH);
  }

  private static boolean isPasswordlessWaitPath(String requestUri, String requestMethod) {
    if (!"POST".equals(requestMethod) || requestUri == null) {
      return false;
    }
    return requestUri.contains(PASSWORDLESS_WAIT_ENDPOINT_PATH)
        || requestUri.contains(PASSWORDLESS_CANCEL_ENDPOINT_PATH);
  }

  /**
   * Checks the login rate limit for the given client IP.
   *
   * @param clientId the client identifier
   * @return rate limit check result
   */
  private RateLimitResult checkLoginRateLimit(String clientId) {
    String bucketKey = "login:" + clientId;
    AdminRateLimitProperties.LoginConfig config = properties.getLogin();
    Bucket bucket =
        bucketCache.get(
            bucketKey, _ -> createGreedyBucket(config.getRequests(), config.getWindowMinutes()));
    return probe(bucket);
  }

  /**
   * Checks the passwordless-wait rate limit for the given bucket key.
   *
   * @param bucketKey cache key ({@code passwordless-wait:attempt:{id}} or IP fallback)
   * @return rate limit check result
   */
  private RateLimitResult checkPasswordlessWaitRateLimit(String bucketKey) {
    AdminRateLimitProperties.PasswordlessWaitConfig config = properties.getPasswordlessWait();
    Bucket bucket =
        bucketCache.get(
            bucketKey, _ -> createGreedyBucket(config.getRequests(), config.getWindowMinutes()));
    return probe(bucket);
  }

  private RateLimitResult checkBackstop() {
    AdminRateLimitProperties.BackstopConfig backstop = properties.getBackstop();
    if (backstop == null || !backstop.isEnabled() || backstop.getLogin() == null) {
      return new RateLimitResult(true, 0);
    }
    AdminRateLimitProperties.LoginConfig config = backstop.getLogin();
    Bucket bucket =
        bucketCache.get(
            "backstop:login",
            _ -> createGreedyBucket(config.getRequests(), config.getWindowMinutes()));
    return probe(bucket);
  }

  private static RateLimitResult probe(Bucket bucket) {
    ConsumptionProbe consumption = bucket.tryConsumeAndReturnRemaining(1);
    if (consumption.isConsumed()) {
      return new RateLimitResult(true, 0);
    }
    long retryAfterSeconds =
        Math.max(1L, Duration.ofNanos(consumption.getNanosToWaitForRefill()).toSeconds());
    return new RateLimitResult(false, retryAfterSeconds);
  }

  /**
   * Creates a greedy-refill token bucket.
   *
   * @param capacity maximum tokens
   * @param windowMinutes refill period in minutes
   * @return configured bucket
   */
  private static Bucket createGreedyBucket(int capacity, int windowMinutes) {
    Bandwidth limit =
        Bandwidth.builder()
            .capacity(capacity)
            .refillGreedy(capacity, Duration.ofMinutes(windowMinutes))
            .build();
    return Bucket.builder().addLimit(limit).build();
  }

  /**
   * Builds the passwordless-wait bucket key. Prefer authAttemptId when present so reconnects share
   * a per-attempt budget; fall back to client IP when the body omits the id.
   *
   * @param authAttemptId parsed attempt id, or null
   * @param clientId resolved client IP
   * @return cache key
   */
  static String passwordlessWaitBucketKey(Integer authAttemptId, String clientId) {
    if (authAttemptId != null) {
      return "passwordless-wait:attempt:" + authAttemptId;
    }
    return "passwordless-wait:ip:" + clientId;
  }

  /**
   * Extracts {@code authAttemptId} from a JSON body without logging secrets.
   *
   * @param body cached request body bytes
   * @return attempt id or null when absent / unparseable
   */
  static Integer extractAuthAttemptId(byte[] body) {
    if (body == null || body.length == 0) {
      return null;
    }
    String json = new String(body, StandardCharsets.UTF_8);
    Matcher matcher = AUTH_ATTEMPT_ID_PATTERN.matcher(json);
    if (!matcher.find()) {
      return null;
    }
    try {
      return Integer.valueOf(matcher.group(1));
    } catch (NumberFormatException ex) {
      return null;
    }
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
      logger.info("Block expired for IP: {}", LogSanitizer.sanitizeForLog(clientId));
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

    String clientIdForLog = LogSanitizer.sanitizeForLog(clientId);
    logger.debug("Failed login attempt {} for IP: {}", failures, clientIdForLog);

    // Block if threshold exceeded
    if (failures >= blockAfterFailures) {
      long blockDuration =
          Duration.ofMinutes(properties.getLogin().getBlockDurationMinutes()).toMillis();
      long blockedUntil = System.currentTimeMillis() + blockDuration;

      blockedUntilMap.put(clientId, blockedUntil);

      logger.warn(
          "IP BLOCKED due to {} failed attempts: {} - Blocked for {} minutes",
          failures,
          clientIdForLog,
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
    logger.debug(
        "Successful login for IP: {} - Failure count reset", LogSanitizer.sanitizeForLog(clientId));
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
