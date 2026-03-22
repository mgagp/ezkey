/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: ApiKeyRateLimitConfig
 * Description: Spring configuration for API key rate limiting when enabled.
 */

package org.ezkey.integration.api.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration that activates API key rate limiting for the Integration API.
 *
 * <p>Active when {@code ezkey.api-key.rate-limit.enabled=true} (the default). When this
 * configuration is active, {@link org.ezkey.integration.api.security.RateLimitService} is created
 * by its own {@code @Service} + {@code @ConditionalOnProperty} annotation.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Configuration
@ConditionalOnProperty(
    name = "ezkey.api-key.rate-limit.enabled",
    havingValue = "true",
    matchIfMissing = true)
@EnableConfigurationProperties(ApiKeyRateLimitProperties.class)
public class ApiKeyRateLimitConfig {

  private static final Logger logger = LoggerFactory.getLogger(ApiKeyRateLimitConfig.class);

  /** Logs that rate limiting is active. */
  public ApiKeyRateLimitConfig() {
    logger.info("Integration API Key Rate Limiting is ENABLED");
  }
}
