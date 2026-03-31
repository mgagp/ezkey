/*
 * Ezkey - Open Source Cryptographic MFA Platform
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

/**
 * Configuration class to enable ezkey-core properties.
 *
 * <p>This configuration class enables the {@link EzkeyCoreProperties} to be automatically
 * configured by Spring Boot when ezkey-core is used as a dependency in other applications.
 *
 * <p><b>Usage:</b> Applications using ezkey-core should import this configuration or
 * use @EnableConfigurationProperties(EzkeyCoreProperties.class) in their own configuration.
 *
 * <p><b>Note:</b> Scheduling is NOT enabled here. Each application must explicitly enable
 * scheduling via @EnableScheduling on its main application class if it needs scheduled tasks. This
 * prevents scheduled jobs from running in applications that don't need them (e.g., Auth API).
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Configuration
@EnableConfigurationProperties({
  EzkeyCoreProperties.class,
  EnrollmentProperties.class,
  EzkeyDemoProperties.class
})
public class EzkeyCoreConfiguration {
  // Configuration class - no additional implementation needed
}
