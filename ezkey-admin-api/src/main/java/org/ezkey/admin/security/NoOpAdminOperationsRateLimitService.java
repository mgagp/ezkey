/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Class: NoOpAdminOperationsRateLimitService
 * Description: No-op implementation of AdminOperationsRateLimitService when rate limiting is disabled.
 */

package org.ezkey.admin.security;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * No-op implementation of AdminOperationsRateLimitService when rate limiting is disabled.
 *
 * <p>This service provides a safe no-operation implementation that allows controllers to call rate
 * limit methods without null checks. All methods return true (operation allowed) and have no
 * effect. This implements the Null Object Pattern.
 *
 * <p><b>Activation Conditions:</b>
 *
 * <ul>
 *   <li>When ezkey.admin-operations.rate-limit.enabled=false (explicitly disabled)
 *   <li>When AdminOperationsRateLimitService bean is not available
 * </ul>
 *
 * <p><b>Design Pattern:</b> Null Object Pattern - provides a valid but inactive implementation
 * instead of null, eliminating the need for null checks throughout the codebase.
 *
 * <p><b>Benefits:</b>
 *
 * <ul>
 *   <li>Eliminates all null checks in controllers (cleaner code)
 *   <li>Type-safe alternative to null references
 *   <li>Consistent API whether rate limiting is enabled or disabled
 *   <li>Zero performance overhead (all methods are no-ops)
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see AdminOperationsRateLimitService
 */
/**
 * No-op implementation of AdminOperationsRateLimitService when rate limiting is disabled.
 *
 * <p>Note: This class is not annotated with @Service. It is instantiated by
 * NoOpAdminOperationsRateLimitServiceWrapper which extends AdminOperationsRateLimitService,
 * allowing Spring to inject it as AdminOperationsRateLimitService type.
 */
public class NoOpAdminOperationsRateLimitService {

  private static final Logger logger =
      LoggerFactory.getLogger(NoOpAdminOperationsRateLimitService.class);

  /**
   * Constructs the no-op rate limiting service.
   *
   * @param meterRegistry the metrics registry (passed but unused by no-op implementation)
   */
  @SuppressWarnings("unused")
  public NoOpAdminOperationsRateLimitService(MeterRegistry meterRegistry) {
    logger.warn("╔══════════════════════════════════════════════════════════════╗");
    logger.warn("║   SECURITY WARNING: Admin Operations Rate Limiting         ║");
    logger.warn("║   DISABLED                                                  ║");
    logger.warn("╚══════════════════════════════════════════════════════════════╝");
    logger.warn("");
    logger.warn("Using NoOpAdminOperationsRateLimitService (Null Object Pattern)");
    logger.warn("All rate limit checks will return ALLOWED (no protection)");
    logger.warn("");
    logger.warn("⚠️  PRODUCTION RISK: Rate limiting is disabled!");
    logger.warn("   - API key creation: NO rate limits");
    logger.warn("   - Enrollment reset: NO rate limits");
    logger.warn("");
    logger.warn("To enable rate limiting, set: ezkey.admin-operations.rate-limit.enabled=true");
    logger.warn("");
  }

  /**
   * No-op implementation: always returns true (operation allowed).
   *
   * @param adminId the admin identifier (unused)
   * @return always true (operation allowed)
   */
  public boolean canCreateApiKey(String adminId) {
    return true;
  }

  /**
   * No-op implementation: always returns true (operation allowed).
   *
   * @param tokenOrAdminId the admin identifier or recovery token (unused)
   * @return always true (operation allowed)
   */
  public boolean canResetEnrollment(String tokenOrAdminId) {
    return true;
  }

  /**
   * No-op implementation: does nothing.
   *
   * @param adminId the admin identifier (unused)
   */
  public void recordCreateApiKey(String adminId) {
    // No-op
  }

  /**
   * No-op implementation: does nothing.
   *
   * @param tokenOrAdminId the admin identifier or recovery token (unused)
   */
  public void recordResetEnrollment(String tokenOrAdminId) {
    // No-op
  }
}
