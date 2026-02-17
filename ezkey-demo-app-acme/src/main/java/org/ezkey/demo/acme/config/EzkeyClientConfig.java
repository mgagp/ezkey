/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: EzkeyClientConfig
 * Description: Spring configuration for Ezkey integration in the demo app.
 */

package org.ezkey.demo.acme.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for Ezkey integration in the ACME demo app.
 *
 * <p>Credentials are loaded from {@link AcmeProperties} at startup and can be overridden at runtime
 * via the "Apply API Key" dialog. {@link EzkeyClientProvider} supplies {@link
 * org.ezkey.sdk.EzkeyClient} instances built from the current credentials.
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Configuration
@EnableConfigurationProperties(AcmeProperties.class)
public class EzkeyClientConfig {}
