/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 *
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: DeliveryConnectorConfig
 */

package org.ezkey.admin.connector;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the delivery connector settings.
 *
 * @since 2026
 */
@Configuration
@EnableConfigurationProperties(DeliveryConnectorProperties.class)
public class DeliveryConnectorConfig {}
