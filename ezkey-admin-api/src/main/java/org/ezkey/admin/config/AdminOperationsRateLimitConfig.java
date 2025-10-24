/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: AdminOperationsRateLimitConfig
 * Description: Spring configuration for admin operations rate limiting functionality using Bucket4j.
 */

package org.ezkey.admin.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for admin operations rate limiting functionality.
 *
 * <p>This configuration class sets up the rate limiting for admin-authenticated operations when 
 * rate limiting is enabled via configuration properties. Uses Bucket4j library for efficient 
 * token bucket rate limiting implementation.
 *
 * <p><b>Conditional Activation:</b> Rate limiting is only activated when
 * ezkey.admin-operations.rate-limit.enabled=true in configuration.
 *
 * <p><b>Security Note:</b> Rate limiting for admin operations is based on admin identification
 * and provides protection for high-value operations like API key creation and enrollment reset.
 *
 * <p><b>Dependencies:</b> Requires AdminOperationsRateLimitProperties for configuration and 
 * Bucket4j libraries for rate limiting implementation.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see AdminOperationsRateLimitProperties
 */
@Configuration
@ConditionalOnProperty(
    name = "ezkey.admin-operations.rate-limit.enabled",
    havingValue = "true",
    matchIfMissing = true)
@EnableConfigurationProperties(AdminOperationsRateLimitProperties.class)
public class AdminOperationsRateLimitConfig {

  private static final Logger logger = LoggerFactory.getLogger(AdminOperationsRateLimitConfig.class);

  /**
   * Constructor that logs configuration information.
   */
  public AdminOperationsRateLimitConfig() {
    logger.info("Admin Operations Rate Limiting configuration loaded");
    logger.info("Rate limiting is enabled for admin operations");
  }
}
