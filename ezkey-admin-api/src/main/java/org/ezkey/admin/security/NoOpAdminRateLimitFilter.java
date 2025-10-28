/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Filter: NoOpAdminRateLimitFilter
 * Description: No-operation implementation of AdminRateLimitFilter (Null Object Pattern).
 */

package org.ezkey.admin.security;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import org.ezkey.admin.config.AdminRateLimitProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * No-operation implementation of AdminRateLimitFilter (Null Object Pattern).
 *
 * <p>This class provides a safe no-op implementation of {@link AdminRateLimitFilter} that can be
 * used when rate limiting is disabled or not configured. It eliminates the need for null checks
 * throughout the codebase by providing a valid but inactive implementation.
 *
 * <p><b>Design Pattern:</b> Null Object Pattern
 *
 * <p><b>Behavior:</b>
 *
 * <ul>
 *   <li>All methods are no-ops (do nothing)
 *   <li>No rate limiting is applied
 *   <li>No IP blocking occurs
 *   <li>Filter chain always proceeds normally
 * </ul>
 *
 * <p><b>Usage Context:</b> Automatically used by Spring when rate limiting is disabled via
 * configuration or when AdminRateLimitProperties is not available. Allows controllers to call rate
 * limit methods without null checks.
 *
 * <p><b>Benefits:</b>
 *
 * <ul>
 *   <li>Eliminates null checks in controllers (cleaner code)
 *   <li>Type-safe alternative to null
 *   <li>Maintains consistent API across enabled/disabled states
 *   <li>No performance overhead (methods are empty)
 * </ul>
 *
 * <p><b>Example:</b>
 *
 * <pre>
 * // With Null Object Pattern - no null check needed
 * rateLimitFilter.recordSuccessfulAttempt(clientIp);
 *
 * // Without pattern (old way) - null check required
 * if (rateLimitFilter != null) {
 *   rateLimitFilter.recordSuccessfulAttempt(clientIp);
 * }
 * </pre>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see AdminRateLimitFilter
 */
public class NoOpAdminRateLimitFilter extends AdminRateLimitFilter {

  private static final Logger logger = LoggerFactory.getLogger(NoOpAdminRateLimitFilter.class);

  /**
   * Constructs a no-op rate limit filter.
   *
   * <p>Creates a dummy AdminRateLimitProperties with safe defaults to satisfy the parent
   * constructor. These properties are never actually used since all methods are no-ops.
   *
   * @param meterRegistry the metrics registry (unused but required by parent)
   */
  public NoOpAdminRateLimitFilter(MeterRegistry meterRegistry) {
    super(createDummyProperties(), meterRegistry);
    logger.info("NoOpAdminRateLimitFilter initialized - Rate limiting is DISABLED");
  }

  /**
   * Creates dummy properties for the parent constructor.
   *
   * @return dummy rate limit properties with safe defaults
   */
  private static AdminRateLimitProperties createDummyProperties() {
    AdminRateLimitProperties props = new AdminRateLimitProperties();
    AdminRateLimitProperties.LoginConfig loginConfig = new AdminRateLimitProperties.LoginConfig();
    loginConfig.setRequests(Integer.MAX_VALUE);
    loginConfig.setWindowMinutes(1);
    loginConfig.setBlockAfterFailures(0); // Disable blocking
    loginConfig.setBlockDurationMinutes(0);
    props.setLogin(loginConfig);
    return props;
  }

  /**
   * No-op filter implementation - always passes through.
   *
   * <p>This method does nothing and immediately passes the request to the next filter in the chain.
   * No rate limiting or IP blocking is performed.
   *
   * @param request the servlet request
   * @param response the servlet response
   * @param chain the filter chain
   * @throws IOException if an I/O error occurs
   * @throws ServletException if a servlet error occurs
   */
  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    // No-op: just pass through
    chain.doFilter(request, response);
  }

  /**
   * No-op implementation - does nothing.
   *
   * <p>This method is called when a login attempt fails, but since rate limiting is disabled, no
   * action is taken. No failure count is tracked and no IP blocking occurs.
   *
   * @param clientId the client identifier (ignored)
   */
  @Override
  public void recordFailedAttempt(String clientId) {
    // No-op: rate limiting is disabled
    logger.trace(
        "recordFailedAttempt called but rate limiting is disabled - clientId: {}", clientId);
  }

  /**
   * No-op implementation - does nothing.
   *
   * <p>This method is called when a login attempt succeeds, but since rate limiting is disabled, no
   * action is taken. No failure count needs to be cleared.
   *
   * @param clientId the client identifier (ignored)
   */
  @Override
  public void recordSuccessfulAttempt(String clientId) {
    // No-op: rate limiting is disabled
    logger.trace(
        "recordSuccessfulAttempt called but rate limiting is disabled - clientId: {}", clientId);
  }
}
