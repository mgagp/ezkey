/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: IntegrityWindowCapEmptyBindingTest
 * Description: Empty property values cannot remove the Integrity hour cap.
 */

package org.ezkey.audit.integrity;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationPropertiesBindException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * Pins Spring binding behaviour for empty Integrity window-cap properties: an empty value fails
 * startup with {@link ConfigurationPropertiesBindException} (primitive {@code int} — the shared
 * 193h default cannot be removed via {@code ...=}).
 *
 * @since 2026
 */
@DisplayName("Integrity window-cap empty property binding")
class IntegrityWindowCapEmptyBindingTest {

  private final ApplicationContextRunner retroactiveRunner =
      new ApplicationContextRunner().withUserConfiguration(RetroactiveConfig.class);

  private final ApplicationContextRunner verifyReportRunner =
      new ApplicationContextRunner().withUserConfiguration(VerifyReportConfig.class);

  @Test
  @DisplayName("empty operator-max-window-hours fails startup (cannot remove cap)")
  void emptyOperatorMaxWindowHours_failsStartup() {
    retroactiveRunner
        .withPropertyValues("ezkey.audit.integrity.retroactive.operator-max-window-hours=")
        .run(
            context -> {
              Throwable failure = context.getStartupFailure();
              assertNotNull(failure, "empty binding must fail startup");
              assertInstanceOf(ConfigurationPropertiesBindException.class, failure);
            });
  }

  @Test
  @DisplayName("empty verify-report.max-window-hours fails startup (cannot remove cap)")
  void emptyVerifyReportMaxWindowHours_failsStartup() {
    verifyReportRunner
        .withPropertyValues("ezkey.audit.integrity.verify-report.max-window-hours=")
        .run(
            context -> {
              Throwable failure = context.getStartupFailure();
              assertNotNull(failure, "empty binding must fail startup");
              assertInstanceOf(ConfigurationPropertiesBindException.class, failure);
            });
  }

  @Configuration
  @EnableConfigurationProperties(RetroactiveIntegrityProperties.class)
  static class RetroactiveConfig {}

  @Configuration
  @EnableConfigurationProperties(IntegrityVerifyReportProperties.class)
  static class VerifyReportConfig {}
}
