/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 *
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: AuthRateLimitDisabledConfig
 *
 * Description: Logs when Auth API rate limiting is voluntarily disabled (e.g. docker-test profile).
 */

package org.ezkey.auth.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration that runs when Auth API rate limiting is disabled. Logs a clear message so that
 * operators and test runs (e.g. clean start with docker-test profile) see that the rate limit is
 * intentionally off.
 *
 * <p>When {@code ezkey.rate-limit.enabled=false} or the property is not set, {@link
 * RateLimitConfig} is not loaded and no {@link RateLimitFilter} is created. This config does not
 * create any bean; it only logs once at startup.
 *
 * <p><b>Typical use:</b> Docker test mode ({@code application-docker-test.properties}) disables
 * rate limiting so test suites can run without hitting limits. The log makes it explicit that this
 * is voluntary and not a misconfiguration.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see RateLimitConfig
 * @see RateLimitFilter
 */
@Configuration
@ConditionalOnProperty(
    name = "ezkey.rate-limit.enabled",
    havingValue = "false",
    matchIfMissing = true)
public class AuthRateLimitDisabledConfig {

  private static final Logger logger = LoggerFactory.getLogger(AuthRateLimitDisabledConfig.class);

  /**
   * Logs that Auth API rate limiting is voluntarily disabled. Called once at startup when rate
   * limiting is off (e.g. docker-test profile for clean start / test runs).
   */
  @PostConstruct
  public void logRateLimitDisabled() {
    logger.warn("╔══════════════════════════════════════════════════════════════╗");
    logger.warn("║   Auth API Rate Limiting: DISABLED (voluntary)               ║");
    logger.warn("╚══════════════════════════════════════════════════════════════╝");
    logger.warn("");
    logger.warn("Auth API rate limiting is voluntarily DISABLED.");
    logger.warn("This is intended for test runs (e.g. clean start with docker-test profile)");
    logger.warn("so that test suites do not exceed rate limits.");
    logger.warn("");
    logger.warn("Endpoints NOT rate limited: pending, respond, verify, bind.");
    logger.warn("");
    logger.warn("To enable rate limiting, set: ezkey.rate-limit.enabled=true");
    logger.warn("Configuration: application.properties or application-{profile}.properties");
    logger.warn("");
  }
}
