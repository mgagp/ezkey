/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: NoOpAdminOperationsRateLimitConfig
 * Description: Spring configuration for admin operations rate limiting when rate limiting is disabled.
 */

package org.ezkey.admin.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.ezkey.admin.security.AdminOperationsRateLimitService;
import org.ezkey.admin.security.NoOpAdminOperationsRateLimitServiceWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for admin operations rate limiting when rate limiting is disabled.
 *
 * <p>This configuration ensures that {@link AdminOperationsRateLimitProperties} bean is always
 * available, even when rate limiting is disabled. This allows {@link
 * AdminOperationsRateLimitService} to be conditionally created without dependency injection
 * failures.
 *
 * <p><b>Activation Conditions:</b>
 *
 * <ul>
 *   <li>When ezkey.admin-operations.rate-limit.enabled=false (explicitly disabled)
 *   <li>When AdminOperationsRateLimitProperties bean is not available from other configuration
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see AdminOperationsRateLimitProperties
 * @see AdminOperationsRateLimitConfig
 */
@Configuration
public class NoOpAdminOperationsRateLimitConfig {

  private static final Logger logger =
      LoggerFactory.getLogger(NoOpAdminOperationsRateLimitConfig.class);

  /**
   * Creates AdminOperationsRateLimitProperties bean when rate limiting is disabled.
   *
   * <p>This bean is only created when:
   *
   * <ul>
   *   <li>Rate limiting is explicitly disabled (ezkey.admin-operations.rate-limit.enabled=false)
   *   <li>OR no other AdminOperationsRateLimitProperties bean exists
   * </ul>
   *
   * <p>This ensures that AdminOperationsRateLimitService can be conditionally created without
   * dependency injection failures.
   *
   * @return AdminOperationsRateLimitProperties instance with default values
   */
  @Bean
  @ConditionalOnMissingBean(AdminOperationsRateLimitProperties.class)
  @ConfigurationProperties(prefix = "ezkey.admin-operations.rate-limit")
  public AdminOperationsRateLimitProperties adminOperationsRateLimitProperties() {
    logger.warn("╔══════════════════════════════════════════════════════════════╗");
    logger.warn("║   SECURITY WARNING: Admin Operations Rate Limiting         ║");
    logger.warn("║   DISABLED                                                  ║");
    logger.warn("╚══════════════════════════════════════════════════════════════╝");
    logger.warn("");
    logger.warn("Admin operations rate limiting is DISABLED.");
    logger.warn("This means:");
    logger.warn("  - API key creation operations are NOT rate limited");
    logger.warn("  - Enrollment reset operations are NOT rate limited");
    logger.warn("  - No protection against abuse of admin operations");
    logger.warn("");
    logger.warn("⚠️  PRODUCTION RISK: This configuration is NOT recommended for production!");
    logger.warn("");
    logger.warn("To enable rate limiting, set: ezkey.admin-operations.rate-limit.enabled=true");
    logger.warn("Configuration file: application.properties or application-{profile}.properties");
    logger.warn("");

    AdminOperationsRateLimitProperties properties = new AdminOperationsRateLimitProperties();
    properties.setEnabled(false);
    return properties;
  }

  /**
   * Creates AdminOperationsRateLimitService bean (NoOp implementation) when rate limiting is disabled.
   *
   * <p>This bean is created when the real AdminOperationsRateLimitService is not available (rate
   * limiting disabled). It provides a NoOp implementation that allows controllers to inject
   * AdminOperationsRateLimitService without null checks.
   *
   * @param properties the rate limiting properties (required by wrapper constructor)
   * @param meterRegistry the metrics registry
   * @return AdminOperationsRateLimitService wrapper that delegates to NoOp implementation
   */
  @Bean
  @ConditionalOnMissingBean(AdminOperationsRateLimitService.class)
  public AdminOperationsRateLimitService adminOperationsRateLimitService(
      AdminOperationsRateLimitProperties properties, MeterRegistry meterRegistry) {
    return new NoOpAdminOperationsRateLimitServiceWrapper(properties, meterRegistry);
  }
}
