/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Class: RateLimitService
 * Description: Rate limiting service for API key operations to prevent abuse and ensure fair usage.
 */

package org.ezkey.admin.security;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service for implementing rate limiting on API key operations.
 *
 * <p>This service provides rate limiting functionality to prevent abuse of API key operations,
 * specifically for authentication attempt creation and validation (wait) operations. It implements
 * a sliding window rate limiting algorithm to ensure fair usage across all API keys.
 *
 * <p><b>Rate Limiting Strategy:</b> Uses a sliding window approach where each API key has a
 * limited number of operations per time window. The current implementation uses a simple in-memory
 * approach suitable for single-instance deployments.
 *
 * <p><b>Configuration:</b> Rate limits are configurable per operation type:
 *
 * <ul>
 *   <li><b>CREATE_AUTH_ATTEMPT:</b> Maximum auth attempts that can be created per window
 *   <li><b>WAIT_AUTH_ATTEMPT:</b> Maximum wait operations per window
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Service
public class RateLimitService {

  private static final Logger logger = LoggerFactory.getLogger(RateLimitService.class);

  // Rate limiting configuration
  private static final int CREATE_AUTH_ATTEMPT_LIMIT = 100; // per window
  private static final int WAIT_AUTH_ATTEMPT_LIMIT = 200; // per window
  private static final int WINDOW_SIZE_MINUTES = 15; // 15-minute windows

  // In-memory storage for rate limiting (suitable for single-instance deployments)
  private final Map<String, RateLimitWindow> rateLimitWindows = new ConcurrentHashMap<>();

  /**
   * Checks if an API key can perform a create auth attempt operation.
   *
   * <p>This method implements rate limiting for authentication attempt creation. It checks if the
   * API key has exceeded the maximum number of create operations allowed within the current time
   * window.
   *
   * @param apiKeyId the API key identifier
   * @return true if the operation is allowed, false if rate limit exceeded
   */
  public boolean canCreateAuthAttempt(String apiKeyId) {
    return checkRateLimit(apiKeyId, "CREATE_AUTH_ATTEMPT", CREATE_AUTH_ATTEMPT_LIMIT);
  }

  /**
   * Checks if an API key can perform a wait auth attempt operation.
   *
   * <p>This method implements rate limiting for authentication attempt waiting. It checks if the
   * API key has exceeded the maximum number of wait operations allowed within the current time
   * window.
   *
   * @param apiKeyId the API key identifier
   * @return true if the operation is allowed, false if rate limit exceeded
   */
  public boolean canWaitAuthAttempt(String apiKeyId) {
    return checkRateLimit(apiKeyId, "WAIT_AUTH_ATTEMPT", WAIT_AUTH_ATTEMPT_LIMIT);
  }

  /**
   * Records a successful create auth attempt operation for rate limiting tracking.
   *
   * @param apiKeyId the API key identifier
   */
  public void recordCreateAuthAttempt(String apiKeyId) {
    recordOperation(apiKeyId, "CREATE_AUTH_ATTEMPT");
  }

  /**
   * Records a successful wait auth attempt operation for rate limiting tracking.
   *
   * @param apiKeyId the API key identifier
   */
  public void recordWaitAuthAttempt(String apiKeyId) {
    recordOperation(apiKeyId, "WAIT_AUTH_ATTEMPT");
  }

  /**
   * Gets the current rate limit status for an API key.
   *
   * @param apiKeyId the API key identifier
   * @return rate limit status information
   */
  public RateLimitStatus getRateLimitStatus(String apiKeyId) {
    String key = apiKeyId + "_CREATE_AUTH_ATTEMPT";
    RateLimitWindow window = rateLimitWindows.get(key);
    
    if (window == null) {
      return new RateLimitStatus(CREATE_AUTH_ATTEMPT_LIMIT, 0, WINDOW_SIZE_MINUTES);
    }
    
    return new RateLimitStatus(CREATE_AUTH_ATTEMPT_LIMIT, window.getCount(), WINDOW_SIZE_MINUTES);
  }

  /**
   * Internal method to check rate limiting for a specific operation.
   *
   * @param apiKeyId the API key identifier
   * @param operation the operation type
   * @param limit the maximum number of operations allowed
   * @return true if the operation is allowed, false if rate limit exceeded
   */
  private boolean checkRateLimit(String apiKeyId, String operation, int limit) {
    String key = apiKeyId + "_" + operation;
    RateLimitWindow window = rateLimitWindows.get(key);
    
    LocalDateTime now = LocalDateTime.now();
    
    // If no window exists or window has expired, create a new one
    if (window == null || window.isExpired(now)) {
      window = new RateLimitWindow(now);
      rateLimitWindows.put(key, window);
    }
    
    // Check if limit is exceeded
    if (window.getCount() >= limit) {
      logger.warn(
          "Rate limit exceeded for API key {} operation {}: {}/{} in window starting at {}",
          apiKeyId, operation, window.getCount(), limit, window.getWindowStart());
      return false;
    }
    
    return true;
  }

  /**
   * Internal method to record a successful operation for rate limiting tracking.
   *
   * @param apiKeyId the API key identifier
   * @param operation the operation type
   */
  private void recordOperation(String apiKeyId, String operation) {
    String key = apiKeyId + "_" + operation;
    RateLimitWindow window = rateLimitWindows.get(key);
    
    LocalDateTime now = LocalDateTime.now();
    
    // If no window exists or window has expired, create a new one
    if (window == null || window.isExpired(now)) {
      window = new RateLimitWindow(now);
      rateLimitWindows.put(key, window);
    }
    
    window.incrementCount();
    
    logger.debug(
        "Recorded operation for API key {} operation {}: {}/{} in window starting at {}",
        apiKeyId, operation, window.getCount(), 
        operation.equals("CREATE_AUTH_ATTEMPT") ? CREATE_AUTH_ATTEMPT_LIMIT : WAIT_AUTH_ATTEMPT_LIMIT,
        window.getWindowStart());
  }

  /**
   * Internal class representing a rate limiting window.
   */
  private static class RateLimitWindow {
    private final LocalDateTime windowStart;
    private int count;

    public RateLimitWindow(LocalDateTime windowStart) {
      this.windowStart = windowStart;
      this.count = 0;
    }

    public boolean isExpired(LocalDateTime now) {
      return windowStart.plusMinutes(WINDOW_SIZE_MINUTES).isBefore(now);
    }

    public void incrementCount() {
      this.count++;
    }

    public int getCount() {
      return count;
    }

    public LocalDateTime getWindowStart() {
      return windowStart;
    }
  }

  /**
   * Data class representing rate limit status information.
   */
  public static class RateLimitStatus {
    private final int limit;
    private final int current;
    private final int windowSizeMinutes;

    public RateLimitStatus(int limit, int current, int windowSizeMinutes) {
      this.limit = limit;
      this.current = current;
      this.windowSizeMinutes = windowSizeMinutes;
    }

    public int getLimit() {
      return limit;
    }

    public int getCurrent() {
      return current;
    }

    public int getWindowSizeMinutes() {
      return windowSizeMinutes;
    }

    public int getRemaining() {
      return Math.max(0, limit - current);
    }

    public boolean isLimitExceeded() {
      return current >= limit;
    }
  }
}
