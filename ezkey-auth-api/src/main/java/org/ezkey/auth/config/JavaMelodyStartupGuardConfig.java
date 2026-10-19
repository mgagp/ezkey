/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: JavaMelodyStartupGuardConfig
 * Description: Fail-closed JavaMelody / management-port checks at Auth API startup.
 */

package org.ezkey.auth.config;

import org.ezkey.config.JavaMelodyStartupValidator;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Runs {@link JavaMelodyStartupValidator} before Auth API accepts traffic.
 *
 * @author Ezkey contributors
 * @since 2026
 */
@Configuration
public class JavaMelodyStartupGuardConfig {

  /**
   * Validates JavaMelody and management-port separation from the active environment.
   *
   * @param environment Spring environment (ports and JavaMelody flags)
   */
  public JavaMelodyStartupGuardConfig(Environment environment) {
    JavaMelodyStartupValidator.enforce(
        // Starter matchIfMissing=true — default true so a missing property stays fail-closed.
        environment.getProperty("javamelody.enabled", Boolean.class, true),
        environment.getProperty(
            "javamelody.management-endpoint-monitoring-enabled", Boolean.class, false),
        environment.getProperty("server.port", Integer.class),
        environment.getProperty("management.server.port", Integer.class));
  }
}
