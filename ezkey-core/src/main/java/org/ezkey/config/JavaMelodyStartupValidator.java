/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Utility: JavaMelodyStartupValidator
 * Description: Fail-closed startup checks for JavaMelody and management-port separation.
 */

package org.ezkey.config;

/**
 * Fail-closed startup validation for JavaMelody and management-port separation.
 *
 * <p>When JavaMelody is enabled without {@code
 * javamelody.management-endpoint-monitoring-enabled=true}, the starter serves the report on the
 * business port. Refuse to start in that configuration. Also refuse when the management port equals
 * the business port (Actuator would share the business surface).
 *
 * @since 2026
 */
public final class JavaMelodyStartupValidator {

  private JavaMelodyStartupValidator() {}

  /**
   * Enforces JavaMelody and management-port startup invariants.
   *
   * @param javaMelodyEnabled whether {@code javamelody.enabled} is true
   * @param managementEndpointMonitoringEnabled whether {@code
   *     javamelody.management-endpoint-monitoring-enabled} is true
   * @param serverPort business HTTP port ({@code server.port}); may be null when unset
   * @param managementServerPort management HTTP port ({@code management.server.port}); may be null
   *     when unset (Actuator shares the business port)
   * @throws IllegalStateException when JavaMelody is enabled without management-endpoint monitoring,
   *     or when both ports are set and equal
   */
  public static void enforce(
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
