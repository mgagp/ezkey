/*
 * Ezkey - Open Source MFA/Passkey Alternative
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
import io.github.bucket4j.Refill;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.ezkey.auth.controller.AuthAttemptController;
import org.ezkey.auth.controller.EnrollmentController;
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
 *   <li>POST /api/v1/enrollments/verify
 * </ul>
 *
 * <p><b>Rate Limiting Strategy:</b>
 *
 * <ul>
 *   <li>Pending: By enrollment ID or client IP (configurable)
 *   <li>Verify: By client IP only
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
 * @see RateLimitProperties
 * @see Bucket4j
 */
public class RateLimitFilter implements Filter {

  private final RateLimitProperties properties;

  private final Cache<String, Bucket> bucketCache;

  /**
   * Constructs the rate limiting filter with configuration properties.
   *
   * @param properties the rate limiting configuration properties
   */
  public RateLimitFilter(RateLimitProperties properties) {
    this.properties = properties;

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

    // Apply rate limiting only to targeted endpoints
    if (shouldApplyRateLimit(requestUri, requestMethod)) {
      RateLimitResult result = checkRateLimit(requestUri, requestMethod, req);

      if (!result.isAllowed()) {
        res.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        res.setHeader("Retry-After", String.valueOf(result.getRetryAfterSeconds()));
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
    // For pending endpoint, use enrollment ID if configured
    if (requestUri.contains(AuthAttemptController.FULL_PATH_PENDING)) {
      if ("enrollment-id".equals(properties.getPending().getKeyStrategy())) {
        return extractEnrollmentIdFromPath(requestUri);
      }
    }

    // Default: use client IP
    return getClientIP(request);
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
        Bandwidth.classic(
            config.getRequests(),
            Refill.intervally(config.getRequests(), Duration.ofMinutes(config.getWindowMinutes())));

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
    }

    // Default configuration
    return new RateLimitProperties.EndpointConfig();
  }

  /**
   * Extracts client IP address from HTTP request, handling proxy headers with security
   * considerations.
   *
   * <p>Priority order for IP extraction: 1. CF-Connecting-IP (Cloudflare - most trusted) 2.
   * X-Forwarded-For (standard proxy header - can be spoofed) 3. X-Real-IP (Nginx/HAProxy - can be
   * spoofed) 4. getRemoteAddr() (direct connection - fallback)
   *
   * <p>Security Warning: X-Forwarded-For and X-Real-IP headers can be easily spoofed by clients.
   * Only CF-Connecting-IP provides reliable client IP when behind Cloudflare.
   *
   * @param request the HTTP request
   * @return client IP address
   */
  private String getClientIP(HttpServletRequest request) {
    // Priority 1: CF-Connecting-IP (Cloudflare - most trusted)
    String cfConnectingIP = request.getHeader("CF-Connecting-IP");
    if (cfConnectingIP != null && !cfConnectingIP.isEmpty() && isValidIP(cfConnectingIP)) {
      return cfConnectingIP.trim();
    }

    // Priority 2: X-Forwarded-For (standard proxy header - can be spoofed)
    String xForwardedFor = request.getHeader("X-Forwarded-For");
    if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
      String firstIP = xForwardedFor.split(",")[0].trim();
      if (isValidIP(firstIP)) {
        return firstIP;
      }
    }

    // Priority 3: X-Real-IP (Nginx/HAProxy - can be spoofed)
    String xRealIP = request.getHeader("X-Real-IP");
    if (xRealIP != null && !xRealIP.isEmpty() && isValidIP(xRealIP)) {
      return xRealIP.trim();
    }

    // Priority 4: Direct connection (fallback)
    return request.getRemoteAddr();
  }

  /**
   * Validates if the given string is a valid IP address.
   *
   * @param ip the IP address string to validate
   * @return true if valid IP address, false otherwise
   */
  private boolean isValidIP(String ip) {
    if (ip == null || ip.trim().isEmpty()) {
      return false;
    }

    try {
      // Basic validation - check if it's a valid IP format
      String[] parts = ip.trim().split("\\.");
      if (parts.length == 4) {
        // IPv4 validation
        for (String part : parts) {
          int num = Integer.parseInt(part);
          if (num < 0 || num > 255) {
            return false;
          }
        }
        return true;
      } else if (ip.contains(":")) {
        // IPv6 - basic format check
        java.net.InetAddress.getByName(ip);
        return true;
      }
      return false;
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * Extracts enrollment ID from request path.
   *
   * @param requestUri the request URI
   * @return enrollment ID string
   */
  private String extractEnrollmentIdFromPath(String requestUri) {
    // Extract from /api/v1/auth-attempts/pending
    String[] parts = requestUri.split("/");
    return parts[parts.length - 1];
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
