/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: NoOpApiKeyRateLimitConfig
 * Description: Fallback configuration providing no-op beans when rate limiting is disabled.
 */

package org.ezkey.integration.api.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.ezkey.integration.api.security.NoOpRateLimitServiceWrapper;
import org.ezkey.integration.api.security.RateLimitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Fallback configuration that registers no-op beans when API key rate limiting is disabled.
 *
 * <p>Ensures {@link ApiKeyRateLimitProperties} and {@link RateLimitService} injection points are
 * always satisfied, even when {@code ezkey.api-key.rate-limit.enabled=false}.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Configuration
public class NoOpApiKeyRateLimitConfig {

  private static final Logger logger = LoggerFactory.getLogger(NoOpApiKeyRateLimitConfig.class);

  /**
   * Provides a default {@link ApiKeyRateLimitProperties} bean when none exists (i.e., when rate
   * limiting is disabled and {@link ApiKeyRateLimitConfig} was not activated).
   *
   * @return a properties instance with {@code enabled=false}
   */
  @Bean
  @ConditionalOnMissingBean(ApiKeyRateLimitProperties.class)
  @ConfigurationProperties(prefix = "ezkey.api-key.rate-limit")
  public ApiKeyRateLimitProperties apiKeyRateLimitProperties() {
    logger.warn("Integration API Key Rate Limiting is DISABLED — using no-op implementation");
    ApiKeyRateLimitProperties properties = new ApiKeyRateLimitProperties();
    properties.setEnabled(false);
    return properties;
  }

  /**
   * Provides a no-op {@link RateLimitService} bean when the real one is absent.
   *
   * @param properties the rate limiting properties
   * @param meterRegistry the metrics registry
   * @return a {@link NoOpRateLimitServiceWrapper} that always allows all operations
   */
  @Bean
  @ConditionalOnMissingBean(RateLimitService.class)
  public RateLimitService rateLimitService(
      ApiKeyRateLimitProperties properties, MeterRegistry meterRegistry) {
    logger.warn("Creating NoOpRateLimitServiceWrapper for Integration API");
    return new NoOpRateLimitServiceWrapper(properties, meterRegistry);
  }
}
