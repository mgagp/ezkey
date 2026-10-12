/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: IntegrityWindowCapPropertiesTest
 * Description: Days-based window cap binding and validation (Integrity cut 3 PR 0).
 */

package org.ezkey.audit.integrity;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.ezkey.audit.exception.IntegrityWindowOverCapException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationPropertiesBindException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

/**
 * Unit and binding tests for {@link IntegrityWindowCapProperties} and legacy-key fail-fast.
 *
 * @since 2026
 */
@DisplayName("IntegrityWindowCapProperties")
class IntegrityWindowCapPropertiesTest {

  private static final OffsetDateTime FROM =
      OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

  private final IntegrityWindowCapProperties properties = new IntegrityWindowCapProperties();

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(
              WindowCapConfig.class, IntegrityWindowCapLegacyPropertyGuard.class);

  @Test
  @DisplayName("default max-window-days is 92; effective Instant cap is 2209h")
  void defaultIsNinetyTwoDaysWithDstSlack() {
    assertEquals(92, properties.getMaxWindowDays());
    assertEquals(Duration.ofHours(2209), properties.maxWindow());
  }

  @Test
  @DisplayName("binding default 92 when unset")
  void binding_defaultNinetyTwo() {
    contextRunner.run(
        context -> {
          assertEquals(null, context.getStartupFailure());
          IntegrityWindowCapProperties bound = context.getBean(IntegrityWindowCapProperties.class);
          assertEquals(92, bound.getMaxWindowDays());
        });
  }

  @Test
  @DisplayName("max-window-days=0 is rejected at bind time")
  void binding_zeroRejected() {
    contextRunner
        .withPropertyValues("ezkey.audit.integrity.max-window-days=0")
        .run(
            context -> {
              Throwable failure = context.getStartupFailure();
              assertNotNull(failure, "zero must fail startup");
              assertInstanceOf(ConfigurationPropertiesBindException.class, failure);
            });
  }

  @Test
  @DisplayName("exactly 92×24h+1h is accepted; +1s throws with maxWindowDays=92")
  void validateWindow_enforcesDefaultCapBoundary() {
    Duration max = properties.maxWindow();
    assertDoesNotThrow(() -> properties.validateWindow(FROM, FROM.plus(max)));
    IntegrityWindowOverCapException ex =
        assertThrows(
            IntegrityWindowOverCapException.class,
            () -> properties.validateWindow(FROM, FROM.plus(max).plusSeconds(1)));
    assertEquals(92, ex.getMaxWindowDays());
    assertTrue(ex.getMessage().contains("92"));
    assertTrue(ex.getMessage().contains("calendar days"));
    assertTrue(!ex.getMessage().contains("8 calendar days"));
  }

  @Test
  @DisplayName("92 calendar days across America/Toronto fall-back (2209h) is accepted")
  void validateWindow_acceptsNinetyTwoDaysAcrossFallBack() {
    // Aug 4 00:00 EDT → Nov 4 00:00 EST spans fall-back (1 Nov 2026) → 92×24+1 = 2209h.
    OffsetDateTime from = OffsetDateTime.parse("2026-08-04T04:00:00Z");
    OffsetDateTime to = OffsetDateTime.parse("2026-11-04T05:00:00Z");
    assertEquals(2209, Duration.between(from, to).toHours());
    assertDoesNotThrow(() -> properties.validateWindow(from, to));
  }

  @Test
  @DisplayName("null from or to is rejected")
  void validateWindow_requiresBothEnds() {
    assertThrows(IllegalArgumentException.class, () -> properties.validateWindow(null, FROM));
    assertThrows(IllegalArgumentException.class, () -> properties.validateWindow(FROM, null));
  }

  @Test
  @DisplayName("inverted range is rejected")
  void validateWindow_rejectsInverted() {
    assertThrows(
        IllegalArgumentException.class, () -> properties.validateWindow(FROM, FROM.minusHours(1)));
  }

  @Test
  @DisplayName("legacy verify-report.max-window-hours as property fails startup naming replacement")
  void legacyVerifyReportKeyAsProperty_failsStartup() {
    contextRunner
        .withPropertyValues("ezkey.audit.integrity.verify-report.max-window-hours=48")
        .run(
            context -> {
              Throwable failure = context.getStartupFailure();
              assertNotNull(failure);
              String message = rootMessage(failure);
              assertTrue(
                  message.contains(
                      IntegrityWindowCapLegacyPropertyGuard.LEGACY_VERIFY_REPORT_MAX_WINDOW_HOURS));
              assertTrue(
                  message.contains(
                      IntegrityWindowCapLegacyPropertyGuard.REPLACEMENT_MAX_WINDOW_DAYS));
              assertTrue(!message.contains("48"));
            });
  }

  @Test
  @DisplayName("legacy operator-max-window-hours as relaxed env var fails startup")
  void legacyRetroactiveKeyAsRelaxedEnv_failsStartup() {
    StandardEnvironment env = new StandardEnvironment();
    env.getPropertySources()
        .addFirst(
            new SystemEnvironmentPropertySource(
                "testEnv",
                Map.of("EZKEY_AUDIT_INTEGRITY_RETROACTIVE_OPERATORMAXWINDOWHOURS", "24")));

    IllegalStateException ex =
        assertThrows(
            IllegalStateException.class, () -> new IntegrityWindowCapLegacyPropertyGuard(env));
    assertTrue(
        ex.getMessage()
            .contains(
                IntegrityWindowCapLegacyPropertyGuard
                    .LEGACY_RETROACTIVE_OPERATOR_MAX_WINDOW_HOURS));
    assertTrue(
        ex.getMessage()
            .contains(IntegrityWindowCapLegacyPropertyGuard.REPLACEMENT_MAX_WINDOW_DAYS));
    assertTrue(!ex.getMessage().contains("24"));
  }

  @Test
  @DisplayName("legacy verify-report key as EZKEY_…VERIFYREPORT_MAXWINDOWHOURS fails startup")
  void legacyVerifyReportKeyAsRelaxedEnv_failsStartup() {
    StandardEnvironment env = new StandardEnvironment();
    env.getPropertySources()
        .addFirst(
            new SystemEnvironmentPropertySource(
                "testEnv", Map.of("EZKEY_AUDIT_INTEGRITY_VERIFYREPORT_MAXWINDOWHOURS", "96")));

    IllegalStateException ex =
        assertThrows(
            IllegalStateException.class, () -> new IntegrityWindowCapLegacyPropertyGuard(env));
    assertTrue(
        ex.getMessage()
            .contains(IntegrityWindowCapLegacyPropertyGuard.LEGACY_VERIFY_REPORT_MAX_WINDOW_HOURS));
    assertTrue(
        ex.getMessage()
            .contains(IntegrityWindowCapLegacyPropertyGuard.REPLACEMENT_MAX_WINDOW_DAYS));
    assertTrue(!ex.getMessage().contains("96"));
  }

  private static String rootMessage(Throwable failure) {
    Throwable current = failure;
    while (current.getCause() != null && current.getCause() != current) {
      current = current.getCause();
    }
    return current.getMessage() != null ? current.getMessage() : failure.toString();
  }

  @Configuration
  @EnableConfigurationProperties(IntegrityWindowCapProperties.class)
  static class WindowCapConfig {}
}
