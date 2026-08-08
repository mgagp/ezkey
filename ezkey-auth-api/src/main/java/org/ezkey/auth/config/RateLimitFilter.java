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
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

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
 * </ul>
 *
 * <p><b>Rate Limiting Strategy:</b>
 *
 * <ul>
 *   <li>Pending: By enrollment ID from request body or client IP (configurable via
 *       enrollment-id/client-ip)
 *   <li>Respond: By authAttemptId from request body (configurable), fallback to client IP
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

  private final RateLimitProperties properties;

  private final TrustedProxyProperties trustedProxyProperties;

  private final ObjectMapper objectMapper;

  private final Cache<String, Bucket> bucketCache;

  /**
   * Constructs the rate limiting filter with configuration properties and ObjectMapper for parsing
   * respond request body.
   *
   * @param properties the rate limiting configuration properties
   * @param trustedProxyProperties the trusted proxy CIDR list (for client IP resolution); may be
   *     null
   * @param objectMapper the Jackson ObjectMapper for extracting authAttemptId/enrollmentId from
   *     request bodies
   */
  public RateLimitFilter(
      RateLimitProperties properties,
      TrustedProxyProperties trustedProxyProperties,
      ObjectMapper objectMapper) {
    this.properties = properties;
    this.trustedProxyProperties = trustedProxyProperties;
    this.objectMapper = objectMapper;

    // Cache buckets for 1 hour with maximum 1000 entries
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

    // For respond and pending endpoints, wrap request so body can be read for authAttemptId /
    // enrollmentId and re-read by controller
    if ("POST".equals(requestMethod)
        && requestUri != null
        && (requestUri.contains(AuthAttemptController.FULL_PATH_RESPOND)
            || requestUri.contains(AuthAttemptController.FULL_PATH_PENDING))) {
      req = new CachedBodyHttpServletRequestWrapper(req);
    }

    // Apply rate limiting only to targeted endpoints
    if (shouldApplyRateLimit(requestUri, requestMethod)) {
      RateLimitResult result = checkRateLimit(requestUri, requestMethod, req);

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
   * @param requestMethod the HTTP method
   * @param request the HTTP request
   * @return rate limit check result
   */
  private RateLimitResult checkRateLimit(
      String requestUri, String requestMethod, HttpServletRequest request) {
    String clientId = extractClientId(requestUri, request);
    String bucketKey = requestUri + ":" + clientId;

    Bucket bucket = bucketCache.get(bucketKey, this::createBucket);

    if (bucket.tryConsume(1)) {
      return new RateLimitResult(true, 0);
    } else {
      // Calculate retry after based on bucket refill time
      long retryAfter = bucket.getAvailableTokens();
      return new RateLimitResult(false, retryAfter);
    }
  }

  /**
   * Extracts client identifier for rate limiting based on endpoint and configuration.
   *
   * @param requestUri the request URI
   * @param request the HTTP request
   * @return client identifier string
   */
  private String extractClientId(String requestUri, HttpServletRequest request) {
    // For pending endpoint, use enrollment ID from body if configured
    if (requestUri.contains(AuthAttemptController.FULL_PATH_PENDING)) {
      if ("enrollment-id".equals(properties.getPending().getKeyStrategy())) {
        Integer enrollmentId = extractEnrollmentIdFromBody(request);
        if (enrollmentId != null) {
          return "pending:" + enrollmentId;
        }
        // Fallback to client IP if body missing, invalid, or enrollmentId null
      }
    }

    // For respond endpoint, use authAttemptId from body if configured
    if (requestUri.contains(AuthAttemptController.FULL_PATH_RESPOND)) {
      if ("auth-attempt-id".equals(properties.getRespond().getKeyStrategy())) {
        Integer authAttemptId = extractAuthAttemptIdFromBody(request);
        if (authAttemptId != null) {
          return "respond:" + authAttemptId;
        }
        // Fallback to client IP if body missing, invalid, or authAttemptId null
      }
    }

    // Default: use client IP (with trusted-proxy list when configured)
    List<String> cidrs = trustedProxyProperties != null ? trustedProxyProperties.getCidrs() : null;
    return ClientIpResolver.resolve(request, cidrs);
  }

  /**
   * Extracts enrollmentId from the request body when it is a CachedBodyHttpServletRequestWrapper.
   * Returns null if the request is not wrapped, the body is not valid JSON, or enrollmentId is
   * missing or not a number.
   *
   * @param request the HTTP request (must be wrapped for pending path)
   * @return the enrollmentId, or null if not available
   */
  private Integer extractEnrollmentIdFromBody(HttpServletRequest request) {
    if (!(request instanceof CachedBodyHttpServletRequestWrapper)) {
      return null;
    }
    try {
      byte[] body = ((CachedBodyHttpServletRequestWrapper) request).getContentAsByteArray();
      if (body == null || body.length == 0) {
        return null;
      }
      JsonNode root = objectMapper.readTree(body);
      if (root == null || !root.has("enrollmentId")) {
        return null;
      }
      JsonNode idNode = root.get("enrollmentId");
      if (idNode == null || idNode.isNull()) {
        return null;
      }
      if (idNode.isNumber()) {
        return idNode.intValue();
      }
      if (idNode.isString()) {
        return Integer.parseInt(idNode.asString());
      }
      return null;
    } catch (Exception e) { // CHECKSTYLE IGNORE IllegalCatch
      return null;
    }
  }

  /**
   * Extracts authAttemptId from the request body when it is a CachedBodyHttpServletRequestWrapper.
   * Returns null if the request is not wrapped, the body is not valid JSON, or authAttemptId is
   * missing or not a number.
   *
   * @param request the HTTP request (must be wrapped for respond path)
   * @return the authAttemptId, or null if not available
   */
  private Integer extractAuthAttemptIdFromBody(HttpServletRequest request) {
    if (!(request instanceof CachedBodyHttpServletRequestWrapper)) {
      return null;
    }
    try {
      byte[] body = ((CachedBodyHttpServletRequestWrapper) request).getContentAsByteArray();
      if (body == null || body.length == 0) {
        return null;
      }
      JsonNode root = objectMapper.readTree(body);
      if (root == null || !root.has("authAttemptId")) {
        return null;
      }
      JsonNode idNode = root.get("authAttemptId");
      if (idNode == null || idNode.isNull()) {
        return null;
      }
      if (idNode.isNumber()) {
        return idNode.intValue();
      }
      if (idNode.isString()) {
        return Integer.parseInt(idNode.asString());
      }
      return null;
    } catch (Exception e) { // CHECKSTYLE IGNORE IllegalCatch
      return null;
    }
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
