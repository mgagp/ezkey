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
import java.io.InputStream;
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
 *   <li>POST /api/v1/admin/auth/login — per client IP (login bucket cache)
 *   <li>POST /api/v1/admin/auth/passwordless-wait — wait cache: per-IP + ip+authAttemptId +
 *       backstop
 *   <li>POST /api/v1/admin/auth/passwordless-cancel — same wait budgets as passwordless-wait
 * </ul>
 *
 * <p><b>Rate Limiting Strategy (#831 / #833 C2):</b>
 *
 * <ul>
 *   <li>Login and passwordless-wait use <em>separate Caffeine caches</em> so rotating fake attempt
 *       ids cannot evict {@code login:} / login-backstop buckets
 *   <li>Wait/cancel keyed by {@code ip+authAttemptId} (not bare sequential ids) plus a per-IP
 *       aggregate and an unkeyed wait backstop
 *   <li>Wait/cancel JSON bodies capped at {@value #MAX_WAIT_BODY_BYTES} bytes
 *   <li>Login: greedy refill ({@code refillGreedy}) for smoother recovery after bursts
 *   <li>Retry-After: remaining nanos to refill from {@code tryConsumeAndReturnRemaining}
 *   <li>IP block after configurable consecutive failures (login surface only)
 * </ul>
 *
 * <p><b>Response Behavior:</b>
 *
 * <ul>
 *   <li>HTTP 429 Too Many Requests when limit exceeded
 *   <li>HTTP 413 Payload Too Large when wait/cancel body exceeds the cap
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

  /** Cap for wait/cancel JSON bodies (authAttemptId + waiterSecret). */
  static final int MAX_WAIT_BODY_BYTES = 4_096;

  private static final int BUCKET_CACHE_MAX_SIZE = 1_000;
  private static final Duration BUCKET_CACHE_TTL = Duration.ofHours(1);
  private static final int FAILURE_COUNT_CACHE_MAX_SIZE = 10_000;
  private static final Duration FAILURE_COUNT_CACHE_TTL = Duration.ofHours(1);
  private static final int BLOCKED_UNTIL_CACHE_MAX_SIZE = 10_000;
  private static final Duration BLOCKED_UNTIL_CACHE_TTL = Duration.ofHours(2);

  private final AdminRateLimitProperties properties;
  private final TrustedProxyProperties trustedProxyProperties;
  private final MeterRegistry meterRegistry;

  /** Login + login-backstop buckets only — isolated from wait-cache churn (#833 C2). */
  private final Cache<String, Bucket> loginBucketCache;

  /** Wait/cancel per-IP, ip+id, and wait-backstop buckets. */
  private final Cache<String, Bucket> waitBucketCache;

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

    this.loginBucketCache =
        Caffeine.newBuilder()
            .maximumSize(BUCKET_CACHE_MAX_SIZE)
            .expireAfterAccess(BUCKET_CACHE_TTL)
            .build();
    this.waitBucketCache =
        Caffeine.newBuilder()
            .maximumSize(BUCKET_CACHE_MAX_SIZE)
            .expireAfterAccess(BUCKET_CACHE_TTL)
            .build();

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
            + " (passwordless-wait: {} per {} min ip+id, {} per {} min per-IP, separate caches)",
        properties.getLogin().getRequests(),
        properties.getLogin().getWindowMinutes(),
        properties.getPasswordlessWait().getRequests(),
        properties.getPasswordlessWait().getWindowMinutes(),
        properties.getPasswordlessWait().getIpRequests(),
        properties.getPasswordlessWait().getIpWindowMinutes());
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

    RateLimitResult backstop = checkLoginBackstop();
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
   * Applies per-IP, ip+id, and wait-backstop budgets, then forwards a replayable body.
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

    byte[] body = readBodyCapped(req.getInputStream(), MAX_WAIT_BODY_BYTES);
    if (body == null) {
      logger.warn(
          "passwordless-wait/cancel body exceeds {} bytes from IP {}",
          MAX_WAIT_BODY_BYTES,
          clientIdForLog);
      res.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
      return;
    }

    Integer authAttemptId = extractAuthAttemptId(body);
    HttpServletRequest requestToForward = new CachedBodyHttpServletRequest(req, body);

    // Order: ip+id → backstop → per-IP (create per-IP bucket only after backstop; #833 n4).
    String attemptBucketKey = passwordlessWaitBucketKey(authAttemptId, clientId);
    RateLimitResult attemptResult = checkPasswordlessWaitAttemptRateLimit(attemptBucketKey);
    if (!attemptResult.allowed) {
      rejectWait(res, clientIdForLog, "passwordless-wait", attemptResult);
      return;
    }

    RateLimitResult backstop = checkPasswordlessWaitBackstop();
    if (!backstop.allowed) {
      logger.warn(
          "Rate-limit backstop exceeded for passwordless-wait (per-instance, unkeyed) - Retry"
              + " after {} seconds",
          backstop.retryAfterSeconds);
      meterRegistry.counter(BACKSTOP_METRIC, "endpoint", "passwordless-wait").increment();
      res.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
      res.setHeader("Retry-After", String.valueOf(backstop.retryAfterSeconds));
      return;
    }

    RateLimitResult ipResult = checkPasswordlessWaitIpRateLimit(clientId);
    if (!ipResult.allowed) {
      rejectWait(res, clientIdForLog, "passwordless-wait-ip", ipResult);
      return;
    }

    chain.doFilter(requestToForward, res);
  }

  private void rejectWait(
      HttpServletResponse res, String clientIdForLog, String endpoint, RateLimitResult result)
      throws IOException {
    logger.warn(
        "Rate limit exceeded for IP: {} path={} - Retry after {} seconds",
        clientIdForLog,
        endpoint,
        result.retryAfterSeconds);

    meterRegistry.counter("rate_limit.login_blocked.total", "endpoint", endpoint).increment();

    res.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
    res.setHeader("Retry-After", String.valueOf(result.retryAfterSeconds));
    AdminRateLimitProperties.PasswordlessWaitConfig config = properties.getPasswordlessWait();
    if ("passwordless-wait-ip".equals(endpoint)) {
      res.setHeader("X-Rate-Limit-Limit", String.valueOf(config.getIpRequests()));
      res.setHeader("X-Rate-Limit-Window", String.valueOf(config.getIpWindowMinutes()));
    } else {
      res.setHeader("X-Rate-Limit-Limit", String.valueOf(config.getRequests()));
      res.setHeader("X-Rate-Limit-Window", String.valueOf(config.getWindowMinutes()));
    }
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
        loginBucketCache.get(
            bucketKey, _ -> createGreedyBucket(config.getRequests(), config.getWindowMinutes()));
    return probe(bucket);
  }

  private RateLimitResult checkPasswordlessWaitIpRateLimit(String clientId) {
    AdminRateLimitProperties.PasswordlessWaitConfig config = properties.getPasswordlessWait();
    String bucketKey = "passwordless-wait:ip:" + clientId;
    Bucket bucket =
        waitBucketCache.get(
            bucketKey,
            _ -> createGreedyBucket(config.getIpRequests(), config.getIpWindowMinutes()));
    return probe(bucket);
  }

  private RateLimitResult checkPasswordlessWaitAttemptRateLimit(String bucketKey) {
    AdminRateLimitProperties.PasswordlessWaitConfig config = properties.getPasswordlessWait();
    Bucket bucket =
        waitBucketCache.get(
            bucketKey, _ -> createGreedyBucket(config.getRequests(), config.getWindowMinutes()));
    return probe(bucket);
  }

  private RateLimitResult checkLoginBackstop() {
    AdminRateLimitProperties.BackstopConfig backstop = properties.getBackstop();
    if (backstop == null || !backstop.isEnabled() || backstop.getLogin() == null) {
      return new RateLimitResult(true, 0);
    }
    AdminRateLimitProperties.LoginConfig config = backstop.getLogin();
    Bucket bucket =
        loginBucketCache.get(
            "backstop:login",
            _ -> createGreedyBucket(config.getRequests(), config.getWindowMinutes()));
    return probe(bucket);
  }

  private RateLimitResult checkPasswordlessWaitBackstop() {
    AdminRateLimitProperties.BackstopConfig backstop = properties.getBackstop();
    if (backstop == null || !backstop.isEnabled() || backstop.getPasswordlessWait() == null) {
      return new RateLimitResult(true, 0);
    }
    AdminRateLimitProperties.PasswordlessWaitConfig config = backstop.getPasswordlessWait();
    Bucket bucket =
        waitBucketCache.get(
            "backstop:passwordless-wait",
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
   * Builds the passwordless-wait attempt bucket key. Always includes client IP so a caller cannot
   * debit another client's per-attempt budget with a guessed sequential id (#833 C2).
   *
   * @param authAttemptId parsed attempt id, or null
   * @param clientId resolved client IP
   * @return cache key
   */
  static String passwordlessWaitBucketKey(Integer authAttemptId, String clientId) {
    if (authAttemptId != null) {
      return "passwordless-wait:ip:" + clientId + ":attempt:" + authAttemptId;
    }
    return "passwordless-wait:ip:" + clientId + ":noid";
  }

  /**
   * Reads at most {@code maxBytes} from the stream. Returns {@code null} when the body is larger.
   *
   * @param in request input stream
   * @param maxBytes inclusive maximum
   * @return body bytes, or null when oversized
   * @throws IOException if reading fails
   */
  static byte[] readBodyCapped(InputStream in, int maxBytes) throws IOException {
    byte[] buffer = in.readNBytes(maxBytes + 1);
    if (buffer.length > maxBytes) {
      return null;
    }
    return buffer;
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

  /**
   * Estimated wait-bucket cache size for eviction tests.
   *
   * @return approximate entry count
   */
  long waitBucketCacheEstimatedSize() {
    return waitBucketCache.estimatedSize();
  }

  /**
   * Estimated login-bucket cache size for isolation tests.
   *
   * @return approximate entry count
   */
  long loginBucketCacheEstimatedSize() {
    return loginBucketCache.estimatedSize();
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
