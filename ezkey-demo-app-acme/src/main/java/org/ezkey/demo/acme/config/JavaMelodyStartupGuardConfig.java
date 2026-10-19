/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: JavaMelodyStartupGuardConfig
 * Description: Fail-closed JavaMelody / management-port checks at demo-app-acme startup.
 */

package org.ezkey.demo.acme.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Fail-closed startup checks for demo-app-acme (mirrors {@code
 * org.ezkey.config.JavaMelodyStartupValidator} without depending on ezkey-core).
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
    enforce(
        environment.getProperty("javamelody.enabled", Boolean.class, false),
        environment.getProperty(
            "javamelody.management-endpoint-monitoring-enabled", Boolean.class, false),
        environment.getProperty("server.port", Integer.class),
        environment.getProperty("management.server.port", Integer.class));
  }

  /**
   * Enforces the same invariants as the core validator (kept local — acme does not depend on
   * ezkey-core).
   *
   * @param javaMelodyEnabled whether JavaMelody is enabled
   * @param managementEndpointMonitoringEnabled whether management-endpoint monitoring is enabled
   * @param serverPort business port
   * @param managementServerPort management port
   */
  static void enforce(
      boolean javaMelodyEnabled,
      boolean managementEndpointMonitoringEnabled,
      Integer serverPort,
      Integer managementServerPort) {
    if (javaMelodyEnabled && !managementEndpointMonitoringEnabled) {
      throw new IllegalStateException(
          "javamelody.enabled=true requires"
              + " javamelody.management-endpoint-monitoring-enabled=true so reports stay on the"
              + " management port (/actuator/monitoring) and /monitoring on the business port"
              + " returns 403.");
    }
    // Port 0 means "pick a random free port" in Spring Boot tests — skip equality then.
    if (serverPort != null
        && managementServerPort != null
        && serverPort > 0
        && managementServerPort > 0
        && serverPort.intValue() == managementServerPort.intValue()) {
      throw new IllegalStateException(
          "management.server.port must differ from server.port (got "
              + serverPort
              + " for both). Actuator and monitoring must not share the business port.");
    }
  }
}
