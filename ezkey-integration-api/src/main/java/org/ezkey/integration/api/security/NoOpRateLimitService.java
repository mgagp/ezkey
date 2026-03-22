/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Class: NoOpRateLimitService
 * Description: No-op implementation of RateLimitService — always allows all operations.
 */

package org.ezkey.integration.api.security;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * No-op implementation of {@link RateLimitService} used when rate limiting is disabled.
 *
 * <p>All check methods unconditionally return {@code true}. Instantiated by {@link
 * NoOpRateLimitServiceWrapper} so that Spring can inject the concrete type {@link
 * RateLimitService}.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
public class NoOpRateLimitService {

  private static final Logger logger = LoggerFactory.getLogger(NoOpRateLimitService.class);

  /**
   * Constructs the no-op service and logs a security warning.
   *
   * @param meterRegistry the metrics registry (passed for signature compatibility — unused)
   */
  @SuppressWarnings("unused")
  public NoOpRateLimitService(MeterRegistry meterRegistry) {
    logger.warn("╔══════════════════════════════════════════════════════════════╗");
    logger.warn("║   SECURITY WARNING: Integration API Key Rate Limiting DISABLED      ║");
    logger.warn("╚══════════════════════════════════════════════════════════════╝");
    logger.warn("Using NoOpRateLimitService — all rate limit checks return ALLOWED.");
    logger.warn("To enable: ezkey.api-key.rate-limit.enabled=true");
  }

  /**
   * No-op check — always returns {@code true}.
   *
   * @param apiKeyId unused
   * @return {@code true}
   */
  public boolean canCreateAuthAttempt(String apiKeyId) {
    return true;
  }

  /**
   * No-op check — always returns {@code true}.
   *
   * @param apiKeyId unused
   * @return {@code true}
   */
  public boolean canWaitAuthAttempt(String apiKeyId) {
    return true;
  }

  /**
   * No-op record — does nothing.
   *
   * @param apiKeyId unused
   */
  public void recordCreateAuthAttempt(String apiKeyId) {
    // No-op
  }

  /**
   * No-op record — does nothing.
   *
   * @param apiKeyId unused
   */
  public void recordWaitAuthAttempt(String apiKeyId) {
    // No-op
  }
}
