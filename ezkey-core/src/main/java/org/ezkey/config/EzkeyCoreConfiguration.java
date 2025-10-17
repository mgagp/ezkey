/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: EzkeyCoreConfiguration
 * Description: Configuration class to enable ezkey-core properties.
 */

package org.ezkey.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Configuration class to enable ezkey-core properties.
 *
 * <p>This configuration class enables the {@link EzkeyCoreProperties} to be automatically
 * configured by Spring Boot when ezkey-core is used as a dependency in other applications.
 *
 * <p><b>Usage:</b> Applications using ezkey-core should import this configuration or
 * use @EnableConfigurationProperties(EzkeyCoreProperties.class) in their own configuration.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Configuration
@EnableConfigurationProperties(EzkeyCoreProperties.class)
@EnableScheduling
public class EzkeyCoreConfiguration {
  // Configuration class - no additional implementation needed
}
