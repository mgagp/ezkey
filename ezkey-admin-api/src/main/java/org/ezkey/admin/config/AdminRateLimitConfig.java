/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: AdminRateLimitConfig
 * Description: Spring configuration for admin API rate limiting functionality using Bucket4j.
 */

package org.ezkey.admin.config;

import org.ezkey.admin.security.AdminRateLimitFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Spring configuration for admin API rate limiting functionality.
 *
 * <p>This configuration class sets up the rate limiting filter and related beans when rate limiting
 * is enabled via configuration properties. Uses Bucket4j library for efficient token bucket rate
 * limiting implementation.
 *
 * <p><b>Conditional Activation:</b> Rate limiting is only activated when
 * ezkey.admin.rate-limit.enabled=true in configuration.
 *
 * <p><b>Security Note:</b> Rate limiting relies on client IP detection through HTTP headers. In
 * production environments behind proxies, ensure proper configuration of X-Forwarded-For headers
 * and trusted proxy settings.
 *
 * <p><b>Dependencies:</b> Requires AdminRateLimitProperties for configuration and Bucket4j
 * libraries for rate limiting implementation.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see AdminRateLimitProperties
 * @see AdminRateLimitFilter
 */
@Configuration
@ConditionalOnProperty(
    name = "ezkey.admin.rate-limit.enabled",
    havingValue = "true",
    matchIfMissing = false)
@EnableConfigurationProperties(AdminRateLimitProperties.class)
public class AdminRateLimitConfig {

  private static final Logger logger = LoggerFactory.getLogger(AdminRateLimitConfig.class);

  /**
   * Constructor that logs a security warning about IP detection.
   *
   * <p>This warning alerts administrators to potential security issues about potential security
   * issues with IP-based rate limiting.
   */
  public AdminRateLimitConfig() {
    logger.warn("═══════════════════════════════════════════════════════════════");
    logger.warn("SECURITY WARNING: Admin API Rate Limiting IP Detection");
    logger.warn("═══════════════════════════════════════════════════════════════");
    logger.warn("Rate limiting is enabled and relies on HTTP headers for client IP detection.");
    logger.warn("HTTP headers like X-Forwarded-For can be spoofed by malicious clients.");
    logger.warn("");
    logger.warn("RECOMMENDED ACTIONS:");
    logger.warn("  1. Deploy behind a trusted reverse proxy (nginx, Apache, CloudFlare)");
    logger.warn("  2. Configure proxy to set X-Forwarded-For correctly");
    logger.warn("  3. Use firewall rules to restrict proxy access");
    logger.warn("  4. Monitor rate limiting effectiveness");
    logger.warn("  5. Consider additional authentication mechanisms");
    logger.warn("");
    logger.warn("For production deployments, ensure proper network security measures.");
    logger.warn("═══════════════════════════════════════════════════════════════");
  }

  /**
   * Creates the rate limiting filter bean.
   *
   * <p>This filter intercepts HTTP requests and applies rate limiting to the admin login endpoint
   * based on configured limits.
   *
   * @param properties the rate limiting configuration properties
   * @param meterRegistry the metrics registry for monitoring
   * @return configured AdminRateLimitFilter instance
   */
  @Bean
  public AdminRateLimitFilter adminRateLimitFilter(AdminRateLimitProperties properties, MeterRegistry meterRegistry) {
    logger.info("Initializing Admin API Rate Limiting with configuration:");
    logger.info(
        "  - Login requests: {} per {} minutes",
        properties.getLogin().getRequests(),
        properties.getLogin().getWindowMinutes());
    logger.info("  - Key strategy: {}", properties.getLogin().getKeyStrategy());
    logger.info("  - Block after failures: {}", properties.getLogin().getBlockAfterFailures());
    logger.info("  - Block duration: {} minutes", properties.getLogin().getBlockDurationMinutes());

    return new AdminRateLimitFilter(properties, meterRegistry);
  }
}
