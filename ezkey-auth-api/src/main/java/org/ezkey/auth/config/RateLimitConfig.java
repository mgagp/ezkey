/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 *
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: RateLimitConfig
 *
 * Description: Spring configuration for rate limiting functionality using Bucket4j.
 */

package org.ezkey.auth.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/**
 * Spring configuration for rate limiting functionality.
 *
 * <p>This configuration class sets up the rate limiting filter and related beans when rate limiting
 * is enabled via configuration properties. Uses Bucket4j library for efficient token bucket rate
 * limiting implementation.
 *
 * <p><b>Conditional Activation:</b> Rate limiting is only activated when
 * ezkey.rate-limit.enabled=true in configuration.
 *
 * <p><b>Dependencies:</b> Requires RateLimitProperties for configuration and Bucket4j libraries for
 * rate limiting implementation.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see RateLimitProperties
 * @see RateLimitFilter
 */
@Configuration
@ConditionalOnProperty(
    name = "ezkey.rate-limit.enabled",
    havingValue = "true",
    matchIfMissing = false)
@EnableConfigurationProperties({RateLimitProperties.class, TrustedProxyProperties.class})
public class RateLimitConfig {

  private static final Logger logger = LoggerFactory.getLogger(RateLimitConfig.class);

  /**
   * Logs security warnings about IP header spoofing at application startup.
   *
   * <p>This method is called after the bean is initialized to warn administrators about potential
   * security issues with IP-based rate limiting.
   */
  @PostConstruct
  public void logSecurityWarnings() {
    logger.warn("=".repeat(80));
    logger.warn("SECURITY WARNING: Rate Limiting IP Detection");
    logger.warn("=".repeat(80));
    logger.warn("Rate limiting is enabled and relies on HTTP headers for client IP detection.");
    logger.warn("");
    logger.warn("IP Header Priority (most trusted to least trusted):");
    logger.warn("  1. CF-Connecting-IP (Cloudflare) - TRUSTED");
    logger.warn("  2. X-Forwarded-For (Proxies) - CAN BE SPOOFED");
    logger.warn("  3. X-Real-IP (Nginx/HAProxy) - CAN BE SPOOFED");
    logger.warn("  4. getRemoteAddr() (Direct) - FALLBACK");
    logger.warn("");
    logger.warn("SECURITY RECOMMENDATIONS:");
    logger.warn("  - Use Cloudflare or similar CDN with CF-Connecting-IP");
    logger.warn("  - Configure trusted proxies to strip spoofed headers");
    logger.warn("  - Monitor rate limiting effectiveness");
    logger.warn("  - Consider additional authentication layers");
    logger.warn("");
    logger.warn("For more details, see docs/OPERATIONAL.md");
    logger.warn("=".repeat(80));
  }

  /**
   * Creates the rate limiting filter bean.
   *
   * <p>This filter intercepts HTTP requests and applies rate limiting based on the configured
   * properties for specific endpoints. For the respond endpoint, the filter parses the request body
   * to extract authAttemptId; ObjectMapper is required for that JSON parsing.
   *
   * @param properties the rate limiting configuration properties
   * @param objectMapper the Jackson ObjectMapper for parsing respond request body
   * @return configured RateLimitFilter instance
   */
  @Bean
  public RateLimitFilter rateLimitFilter(
      RateLimitProperties properties,
      TrustedProxyProperties trustedProxyProperties,
      ObjectMapper objectMapper) {
    return new RateLimitFilter(properties, trustedProxyProperties, objectMapper);
  }

  /**
   * Provides a minimal fallback ObjectMapper for native contexts where Jackson auto-configuration
   * does not materialize the primary bean.
   *
   * @return reusable Jackson object mapper
   */
  @Bean
  @ConditionalOnMissingBean(ObjectMapper.class)
  public ObjectMapper objectMapper() {
    return new ObjectMapper();
  }
}
