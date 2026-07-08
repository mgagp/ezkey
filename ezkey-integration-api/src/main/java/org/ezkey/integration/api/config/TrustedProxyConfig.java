/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 *
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: TrustedProxyConfig
 *
 * Description: Enables trusted proxy configuration properties for client IP resolution.
 */

package org.ezkey.integration.api.config;

import org.ezkey.config.TrustedProxyStartupValidator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Enables {@link TrustedProxyProperties} for integration-api. The trusted proxy CIDR list is used
 * by {@link org.ezkey.integration.api.security.ApiKeyAuthenticationFilter} for audit client IP.
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Configuration
@EnableConfigurationProperties(TrustedProxyProperties.class)
public class TrustedProxyConfig {

  /**
   * Validates trusted proxy configuration before the application accepts traffic (SEC-011).
   *
   * @param trustedProxyProperties bound trusted proxy settings
   */
  public TrustedProxyConfig(TrustedProxyProperties trustedProxyProperties) {
    TrustedProxyStartupValidator.enforceRequired(
        trustedProxyProperties.isRequired(), trustedProxyProperties.getCidrs());
  }
}
