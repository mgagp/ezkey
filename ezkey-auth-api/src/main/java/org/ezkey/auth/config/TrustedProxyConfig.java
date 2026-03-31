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

package org.ezkey.auth.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Enables {@link TrustedProxyProperties} for auth-api. The trusted proxy CIDR list is used by
 * {@link RateLimitFilter} (when rate limiting is enabled) and by controllers that call {@link
 * org.ezkey.auth.util.AuditHelper#extractClientIp(jakarta.servlet.http.HttpServletRequest,
 * java.util.Collection)} for audit logging.
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Configuration
@EnableConfigurationProperties(TrustedProxyProperties.class)
public class TrustedProxyConfig {}
