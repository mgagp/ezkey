/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: NoOpApiKeyRateLimitConfig
 * Description: Spring configuration for API key rate limiting when rate limiting is disabled.
 */

package org.ezkey.admin.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.ezkey.admin.security.NoOpRateLimitServiceWrapper;
import org.ezkey.admin.security.RateLimitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for API key rate limiting when rate limiting is disabled.
 *
 * <p>This configuration ensures that {@link ApiKeyRateLimitProperties} and {@link RateLimitService}
 * beans are always available, even when rate limiting is disabled. This allows controllers to
 * inject RateLimitService without dependency injection failures.
 *
 * <p><b>Activation Conditions:</b>
 *
 * <ul>
 *   <li>When ezkey.api-key.rate-limit.enabled=false (explicitly disabled)
 *   <li>When ApiKeyRateLimitProperties or RateLimitService beans are not available
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see ApiKeyRateLimitProperties
 * @see ApiKeyRateLimitConfig
 */
@Configuration
public class NoOpApiKeyRateLimitConfig {

  private static final Logger logger = LoggerFactory.getLogger(NoOpApiKeyRateLimitConfig.class);

  /**
   * Creates ApiKeyRateLimitProperties bean when rate limiting is disabled.
   *
   * <p>This bean is only created when:
   *
   * <ul>
   *   <li>Rate limiting is explicitly disabled (ezkey.api-key.rate-limit.enabled=false)
   *   <li>OR no other ApiKeyRateLimitProperties bean exists
   * </ul>
   *
   * @return ApiKeyRateLimitProperties instance with default values
   */
  @Bean
  @ConditionalOnMissingBean(ApiKeyRateLimitProperties.class)
  @ConfigurationProperties(prefix = "ezkey.api-key.rate-limit")
  public ApiKeyRateLimitProperties apiKeyRateLimitProperties() {
    logger.warn("╔══════════════════════════════════════════════════════════════╗");
    logger.warn("║   SECURITY WARNING: API Key Rate Limiting                  ║");
    logger.warn("║   DISABLED                                                  ║");
    logger.warn("╚══════════════════════════════════════════════════════════════╝");
    logger.warn("");
    logger.warn("API key rate limiting is DISABLED.");
    logger.warn("This means:");
    logger.warn("  - Create auth attempt operations are NOT rate limited");
    logger.warn("  - Wait auth attempt operations are NOT rate limited");
    logger.warn("  - No protection against abuse of API key operations");
    logger.warn("");
    logger.warn("⚠️  PRODUCTION RISK: This configuration is NOT recommended for production!");
    logger.warn("");
    logger.warn("To enable rate limiting, set: ezkey.api-key.rate-limit.enabled=true");
    logger.warn("Configuration file: application.properties or application-{profile}.properties");
    logger.warn("");

    ApiKeyRateLimitProperties properties = new ApiKeyRateLimitProperties();
    properties.setEnabled(false);
    return properties;
  }

  /**
   * Creates RateLimitService bean (NoOp implementation) when rate limiting is disabled.
   *
   * <p>This bean is created when the real RateLimitService is not available (rate limiting
   * disabled). It provides a NoOp implementation that allows controllers to inject RateLimitService
   * without null checks.
   *
   * @param properties the rate limiting properties (required by wrapper constructor)
   * @param meterRegistry the metrics registry
   * @return RateLimitService wrapper that delegates to NoOp implementation
   */
  @Bean
  @ConditionalOnMissingBean(RateLimitService.class)
  public RateLimitService rateLimitService(
      ApiKeyRateLimitProperties properties, MeterRegistry meterRegistry) {
    return new NoOpRateLimitServiceWrapper(properties, meterRegistry);
  }
}
