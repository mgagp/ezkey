/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: ApiKeyRateLimitConfig
 * Description: Spring configuration for API key rate limiting functionality using Bucket4j.
 */

package org.ezkey.admin.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for API key rate limiting functionality.
 *
 * <p>This configuration class sets up the rate limiting for API key operations when rate limiting
 * is enabled via configuration properties. Uses Bucket4j library for efficient token bucket rate
 * limiting implementation.
 *
 * <p><b>Conditional Activation:</b> Rate limiting is only activated when
 * ezkey.api-key.rate-limit.enabled=true in configuration.
 *
 * <p><b>Security Note:</b> Rate limiting for API key operations is based on API key identification
 * rather than IP addresses, providing more granular control for machine-to-machine authentication.
 *
 * <p><b>Dependencies:</b> Requires ApiKeyRateLimitProperties for configuration and Bucket4j
 * libraries for rate limiting implementation.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see ApiKeyRateLimitProperties
 */
@Configuration
@ConditionalOnProperty(
    name = "ezkey.api-key.rate-limit.enabled",
    havingValue = "true",
    matchIfMissing = true)
@EnableConfigurationProperties(ApiKeyRateLimitProperties.class)
public class ApiKeyRateLimitConfig {

  private static final Logger logger = LoggerFactory.getLogger(ApiKeyRateLimitConfig.class);

  /** Constructor that logs configuration information. */
  public ApiKeyRateLimitConfig() {
    logger.info("API Key Rate Limiting configuration loaded");
    logger.info("Rate limiting is enabled for API key operations");
  }
}
