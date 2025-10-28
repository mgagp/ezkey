/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: NoOpAdminRateLimitConfig
 * Description: Spring configuration for no-op rate limiting when rate limiting is disabled.
 */

package org.ezkey.admin.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.ezkey.admin.security.AdminRateLimitFilter;
import org.ezkey.admin.security.NoOpAdminRateLimitFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for no-op rate limiting when rate limiting is disabled.
 *
 * <p>This configuration provides a {@link NoOpAdminRateLimitFilter} bean when rate limiting is
 * explicitly disabled or when the rate limit configuration is missing. This implements the Null
 * Object Pattern, allowing controllers to call rate limit methods without null checks.
 *
 * <p><b>Activation Conditions:</b>
 *
 * <ul>
 *   <li>When ezkey.admin.rate-limit.enabled=false (explicitly disabled)
 *   <li>When ezkey.admin.rate-limit.enabled is not set (default behavior)
 *   <li>When AdminRateLimitFilter bean is not available
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
 * @see NoOpAdminRateLimitFilter
 * @see AdminRateLimitConfig
 */
@Configuration
public class NoOpAdminRateLimitConfig {

  private static final Logger logger = LoggerFactory.getLogger(NoOpAdminRateLimitConfig.class);

  /**
   * Creates a no-op rate limiting filter when rate limiting is disabled.
   *
   * <p>This bean is only created when:
   *
   * <ul>
   *   <li>Rate limiting is explicitly disabled (ezkey.admin.rate-limit.enabled=false)
   *   <li>OR rate limiting configuration is missing (default behavior)
   *   <li>AND no other AdminRateLimitFilter bean exists
   * </ul>
   *
   * <p>The NoOpAdminRateLimitFilter provides a safe no-operation implementation that allows
   * controllers to call rate limit methods without null checks. All methods are empty and have no
   * effect.
   *
   * @param meterRegistry the metrics registry (passed but unused by no-op implementation)
   * @return NoOpAdminRateLimitFilter instance (Null Object Pattern)
   */
  @Bean
  @ConditionalOnMissingBean(AdminRateLimitFilter.class)
  @ConditionalOnProperty(
      name = "ezkey.admin.rate-limit.enabled",
      havingValue = "false",
      matchIfMissing = true)
  public AdminRateLimitFilter adminRateLimitFilter(MeterRegistry meterRegistry) {
    logger.info("╔══════════════════════════════════════════════════════════════╗");
    logger.info("║   Admin API Rate Limiting: DISABLED                         ║");
    logger.info("╚══════════════════════════════════════════════════════════════╝");
    logger.info("Using NoOpAdminRateLimitFilter (Null Object Pattern)");
    logger.info("Controllers can safely call rate limit methods without null checks");
    logger.info("");
    logger.info("To enable rate limiting, set: ezkey.admin.rate-limit.enabled=true");

    return new NoOpAdminRateLimitFilter(meterRegistry);
  }
}
