/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 *
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: TrustedProxyConfig
 *
 * Description: Enables trusted proxy configuration properties for client IP resolution.
 */

package org.ezkey.m2m.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Enables {@link TrustedProxyProperties} for m2m-api. The trusted proxy CIDR list is used by {@link
 * org.ezkey.m2m.security.ApiKeyAuthenticationFilter} for audit client IP.
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Configuration
@EnableConfigurationProperties(TrustedProxyProperties.class)
public class TrustedProxyConfig {}
