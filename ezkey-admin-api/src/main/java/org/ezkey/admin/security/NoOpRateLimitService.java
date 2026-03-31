/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Class: NoOpRateLimitService
 * Description: No-op implementation of RateLimitService when rate limiting is disabled.
 */

package org.ezkey.admin.security;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * No-op implementation of RateLimitService when rate limiting is disabled.
 *
 * <p>Note: This class is not annotated with @Service. It is instantiated by
 * NoOpRateLimitServiceWrapper which extends RateLimitService, allowing Spring to inject it as
 * RateLimitService type.
 */
public class NoOpRateLimitService {

  private static final Logger logger = LoggerFactory.getLogger(NoOpRateLimitService.class);

  /**
   * Constructs the no-op rate limiting service.
   *
   * @param meterRegistry the metrics registry (passed but unused by no-op implementation)
   */
  @SuppressWarnings("unused")
  public NoOpRateLimitService(MeterRegistry meterRegistry) {
    logger.warn("╔══════════════════════════════════════════════════════════════╗");
    logger.warn("║   SECURITY WARNING: API Key Rate Limiting                  ║");
    logger.warn("║   DISABLED                                                  ║");
    logger.warn("╚══════════════════════════════════════════════════════════════╝");
    logger.warn("");
    logger.warn("Using NoOpRateLimitService (Null Object Pattern)");
    logger.warn("All rate limit checks will return ALLOWED (no protection)");
    logger.warn("");
    logger.warn("⚠️  PRODUCTION RISK: Rate limiting is disabled!");
    logger.warn("   - Create auth attempt: NO rate limits");
    logger.warn("   - Wait auth attempt: NO rate limits");
    logger.warn("");
    logger.warn("To enable rate limiting, set: ezkey.api-key.rate-limit.enabled=true");
    logger.warn("");
  }

  /**
   * No-op implementation: always returns true (operation allowed).
   *
   * @param apiKeyId the API key identifier (unused)
   * @return always true (operation allowed)
   */
  public boolean canCreateAuthAttempt(String apiKeyId) {
    return true;
  }

  /**
   * No-op implementation: always returns true (operation allowed).
   *
   * @param apiKeyId the API key identifier (unused)
   * @return always true (operation allowed)
   */
  public boolean canWaitAuthAttempt(String apiKeyId) {
    return true;
  }

  /**
   * No-op implementation: does nothing.
   *
   * @param apiKeyId the API key identifier (unused)
   */
  public void recordCreateAuthAttempt(String apiKeyId) {
    // No-op
  }

  /**
   * No-op implementation: does nothing.
   *
   * @param apiKeyId the API key identifier (unused)
   */
  public void recordWaitAuthAttempt(String apiKeyId) {
    // No-op
  }
}
