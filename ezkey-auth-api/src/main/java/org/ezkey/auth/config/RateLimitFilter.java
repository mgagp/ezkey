/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 *
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Filter: RateLimitFilter
 *
 * Description: HTTP filter for applying rate limiting to specific auth-api endpoints.
 */

package org.ezkey.auth.config;

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
import java.time.Duration;
import java.util.List;
import org.ezkey.audit.util.ClientIpResolver;
import org.ezkey.auth.controller.AuthAttemptController;
import org.ezkey.auth.controller.EnrollmentController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

/**
 * HTTP filter for applying rate limiting to specific auth-api endpoints.
 *
 * <p>This filter intercepts HTTP requests and applies configurable rate limiting to the pending
 * authentication and enrollment verification endpoints. Uses Bucket4j token bucket algorithm for
 * efficient rate limiting.
 *
 * <p><b>Targeted Endpoints:</b>
 *
 * <ul>
 *   <li>POST /api/v1/auth-attempts/pending
 *   <li>POST /api/v1/auth-attempts/respond
 *   <li>POST /api/v1/enrollments/verify
 *   <li>POST /api/v1/enrollments/bind
 *   <li>POST /api/v1/enrollments/instance-info (shares bind rate-limit config)
 * </ul>
 *
 * <p><b>Rate Limiting Strategy:</b>
 *
 * <ul>
 *   <li>Pending: With {@code enrollment-id}, an IP ceiling only. The per-enrollment budget is
 *       consumed after proof token and device signature, from the validated enrollment (#1124)
 *   <li>Respond: With {@code auth-attempt-id}, filter applies the fail-IP ceiling only; per-{@code
 *       authAttemptId} budget is consumed after a valid device signature, and that proven call
 *       refunds the IP token (#912 C1, #918)
 *   <li>Verify: By client IP only
 *   <li>Bind: By client IP only
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
 * @see RateLimitProperties
 * @see Bucket4j
 */
public class RateLimitFilter implements Filter {

  private static final Logger LOG = LoggerFactory.getLogger(RateLimitFilter.class);

  private static final String BACKSTOP_METRIC = "ezkey.rate_limit.backstop.rejected";

  private final RateLimitProperties properties;

  private final TrustedProxyProperties trustedProxyProperties;

  private final MeterRegistry meterRegistry;

  private final AuthRespondRateLimitService authRespondRateLimitService;

  private final AuthPendingRateLimitService authPendingRateLimitService;

  private final Cache<String, Bucket> bucketCache;

  /**
   * Constructs the rate limiting filter.
   *
   * @param properties the rate limiting configuration properties
   * @param trustedProxyProperties the trusted proxy CIDR list (for client IP resolution); may be
   *     null
   */
  public RateLimitFilter(
      RateLimitProperties properties, TrustedProxyProperties trustedProxyProperties) {
    this(properties, trustedProxyProperties, null, null, null);
  }

  /**
   * Constructs the filter with an optional metrics registry for backstop rejections.
   *
   * @param properties the rate limiting configuration properties
   * @param trustedProxyProperties the trusted proxy CIDR list (for client IP resolution); may be
   *     null
   * @param meterRegistry optional Micrometer registry; null skips backstop counters
   */
  public RateLimitFilter(
      RateLimitProperties properties,
      TrustedProxyProperties trustedProxyProperties,
      MeterRegistry meterRegistry) {
    this(properties, trustedProxyProperties, meterRegistry, null, null);
  }

  /**
   * Constructs the filter with post-ownership respond and pending budgets.
   *
   * @param properties the rate limiting configuration properties
   * @param trustedProxyProperties the trusted proxy CIDR list (for client IP resolution); may be
   *     null
   * @param meterRegistry optional Micrometer registry; null skips backstop counters
   * @param authRespondRateLimitService respond budgets; null keeps respond on the client-IP bucket
   * @param authPendingRateLimitService pending budgets; null keeps {@code enrollment-id} pending on
   *     the client-IP bucket and still does not read the body
   */
  public RateLimitFilter(
      RateLimitProperties properties,
      TrustedProxyProperties trustedProxyProperties,
      MeterRegistry meterRegistry,
      AuthRespondRateLimitService authRespondRateLimitService,
      AuthPendingRateLimitService authPendingRateLimitService) {
    this.properties = properties;
    this.trustedProxyProperties = trustedProxyProperties;
    this.meterRegistry = meterRegistry;
    this.authRespondRateLimitService = authRespondRateLimitService;
    this.authPendingRateLimitService = authPendingRateLimitService;

    // Client-IP buckets for verify, bind, and pending/respond when those services are absent.
    // Target-id budgets live in the services so an IP flood cannot evict them (#918).
    this.bucketCache =
        Caffeine.newBuilder().maximumSize(1000).expireAfterAccess(Duration.ofHours(1)).build();
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {

    HttpServletRequest req = (HttpServletRequest) request;
    HttpServletResponse res = (HttpServletResponse) response;

    String requestUri = req.getRequestURI();
    String requestMethod = req.getMethod();

    // Apply rate limiting only to targeted endpoints
    if (shouldApplyRateLimit(requestUri, requestMethod)) {
      RateLimitResult result = checkRateLimit(requestUri, req);

      if (!result.isAllowed()) {
        res.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        res.setHeader("Retry-After", String.valueOf(result.getRetryAfterSeconds()));
        return;
      }
    }

    chain.doFilter(req, response);
  }

  /**
   * Determines if rate limiting should be applied to the given request.
   *
   * @param requestUri the request URI
   * @param requestMethod the HTTP method
   * @return true if rate limiting should be applied
   */
  private boolean shouldApplyRateLimit(String requestUri, String requestMethod) {
    // Only apply to POST requests for our targeted endpoints
    if (!"POST".equals(requestMethod)) {
      return false;
    }

    // Check for pending endpoint
    if (requestUri.contains(AuthAttemptController.FULL_PATH_PENDING)) {
      return true;
    }

    // Check for verify endpoint
    if (requestUri.contains(EnrollmentController.FULL_PATH_VERIFY)) {
      return true;
    }

    // Check for bind endpoint
    if (requestUri.contains(EnrollmentController.FULL_PATH_BIND)) {
      return true;
    }

    // Check for enrolled instance-info endpoint
    if (requestUri.contains(EnrollmentController.FULL_PATH_INSTANCE_INFO)) {
      return true;
    }

    // Check for respond endpoint
    if (requestUri.contains(AuthAttemptController.FULL_PATH_RESPOND)) {
      return true;
    }

    return false;
  }

  /**
   * Checks rate limit for the given request and returns the result.
   *
   * @param requestUri the request URI
   * @param request the HTTP request
   * @return rate limit check result
   */
  private RateLimitResult checkRateLimit(String requestUri, HttpServletRequest request) {
    // Respond + auth-attempt-id: fail-IP only. The id budget is post-signature (#912 C1).
    if (requestUri.contains(AuthAttemptController.FULL_PATH_RESPOND)
        && "auth-attempt-id".equals(properties.getRespond().getKeyStrategy())
        && authRespondRateLimitService != null) {
      return consumeIpCeiling(
          authRespondRateLimitService.tryConsumeFailIp(resolveClientIp(request)));
    }

    // Pending + enrollment-id: fail-IP only. The enrollment budget is post-proof (#1124).
    if (requestUri.contains(AuthAttemptController.FULL_PATH_PENDING)
        && "enrollment-id".equals(properties.getPending().getKeyStrategy())
        && authPendingRateLimitService != null) {
      return consumeIpCeiling(
          authPendingRateLimitService.tryConsumeFailIp(resolveClientIp(request)));
    }

    String clientId = resolveClientIp(request);
    String bucketKey = requestUri + ":" + clientId;

    Bucket bucket = bucketCache.get(bucketKey, this::createBucket);
    RateLimitResult keyed = probe(bucket);
    if (!keyed.isAllowed()) {
      return keyed;
    }

    RateLimitResult backstop = checkBackstop(requestUri);
    if (!backstop.isAllowed()) {
      return backstop;
    }
    return new RateLimitResult(true, 0);
  }

  private static RateLimitResult consumeIpCeiling(RateLimitTokens.Decision decision) {
    if (!decision.allowed()) {
      return new RateLimitResult(false, decision.retryAfterSeconds());
    }
    return new RateLimitResult(true, 0);
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
   * Unkeyed per-process cap on client-IP-keyed enrollment surfaces. {@code pending} and {@code
   * respond} are target-id keyed and are not subject to this backstop.
   */
  private RateLimitResult checkBackstop(String requestUri) {
    RateLimitProperties.BackstopConfig backstop = properties.getBackstop();
    if (backstop == null || !backstop.isEnabled()) {
      return new RateLimitResult(true, 0);
    }
    RateLimitProperties.EndpointConfig config = backstopConfigFor(requestUri);
    if (config == null) {
      return new RateLimitResult(true, 0);
    }
    String family = backstopFamily(requestUri);
    Bucket bucket = bucketCache.get("backstop:" + family, _ -> createBackstopBucket(config));
    if (bucket.tryConsume(1)) {
      return new RateLimitResult(true, 0);
    }
    LOG.warn("Rate-limit backstop exceeded for {} (per-instance, unkeyed)", family);
    if (meterRegistry != null) {
      meterRegistry.counter(BACKSTOP_METRIC, "endpoint", family).increment();
    }
    long retryAfter = Math.max(1L, config.getWindowMinutes() * 60L);
    return new RateLimitResult(false, retryAfter);
  }

  private RateLimitProperties.EndpointConfig backstopConfigFor(String requestUri) {
    RateLimitProperties.BackstopConfig backstop = properties.getBackstop();
    if (requestUri.contains(EnrollmentController.FULL_PATH_VERIFY)) {
      return backstop.getVerify();
    }
    if (requestUri.contains(EnrollmentController.FULL_PATH_BIND)
        || requestUri.contains(EnrollmentController.FULL_PATH_INSTANCE_INFO)) {
      return backstop.getBind();
    }
    return null;
  }

  private static String backstopFamily(String requestUri) {
    if (requestUri.contains(EnrollmentController.FULL_PATH_VERIFY)) {
      return "verify";
    }
    return "bind";
  }

  private static Bucket createBackstopBucket(RateLimitProperties.EndpointConfig config) {
    Bandwidth limit =
        Bandwidth.builder()
            .capacity(config.getRequests())
            .refillIntervally(config.getRequests(), Duration.ofMinutes(config.getWindowMinutes()))
            .build();
    return Bucket.builder().addLimit(limit).build();
  }

  /**
   * Resolves the client IP used when a budget is keyed by address rather than a proven id.
   *
   * <p>Pending and respond per-id keys are never derived here. Those budgets live in the
   * post-ownership services.
   *
   * @param request the HTTP request
   * @return client IP string
   */
  private String resolveClientIp(HttpServletRequest request) {
    List<String> cidrs = trustedProxyProperties != null ? trustedProxyProperties.getCidrs() : null;
    boolean trustCf =
        trustedProxyProperties != null && trustedProxyProperties.isTrustCloudflareConnectingIp();
    return ClientIpResolver.resolve(request, cidrs, trustCf);
  }

  /**
   * Creates a new rate limiting bucket based on endpoint configuration.
   *
   * @param bucketKey the bucket key
   * @return configured rate limiting bucket
   */
  private Bucket createBucket(String bucketKey) {
    RateLimitProperties.EndpointConfig config = getConfigForEndpoint(bucketKey);

    Bandwidth limit =
        Bandwidth.builder()
            .capacity(config.getRequests())
            .refillIntervally(config.getRequests(), Duration.ofMinutes(config.getWindowMinutes()))
            .build();

    return Bucket.builder().addLimit(limit).build();
  }

  /**
   * Gets the rate limiting configuration for the given endpoint.
   *
   * @param bucketKey the bucket key containing endpoint information
   * @return endpoint-specific rate limiting configuration
   */
  private RateLimitProperties.EndpointConfig getConfigForEndpoint(String bucketKey) {
    if (bucketKey.contains(AuthAttemptController.FULL_PATH_PENDING)) {
      return properties.getPending();
    } else if (bucketKey.contains(EnrollmentController.FULL_PATH_VERIFY)) {
      return properties.getVerify();
    } else if (bucketKey.contains(EnrollmentController.FULL_PATH_BIND)) {
      return properties.getBind();
    } else if (bucketKey.contains(EnrollmentController.FULL_PATH_INSTANCE_INFO)) {
      // Shares bind client-ip budget (opportunistic branding refresh; low volume)
      return properties.getBind();
    } else if (bucketKey.contains(AuthAttemptController.FULL_PATH_RESPOND)) {
      return properties.getRespond();
    }

    // Default configuration
    return new RateLimitProperties.EndpointConfig();
  }

  /** Result of a rate limit check operation. */
  private static class RateLimitResult {
    private final boolean allowed;

    private final long retryAfterSeconds;

    RateLimitResult(boolean allowed, long retryAfterSeconds) {
      this.allowed = allowed;
      this.retryAfterSeconds = retryAfterSeconds;
    }

    public boolean isAllowed() {
      return allowed;
    }

    public long getRetryAfterSeconds() {
      return retryAfterSeconds;
    }
  }
}
