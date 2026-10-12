/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: IntegrityWindowCapPropertiesTest
 * Description: Async/sync days-based window caps and legacy-key fail-fast (Integrity cut 3 PR 0).
 */

package org.ezkey.audit.integrity;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
  @DisplayName("defaults: async 92 / 2209h; sync 8 / 193h")
  void defaultsAsyncAndSyncCaps() {
    assertEquals(92, properties.getMaxWindowDays());
    assertEquals(Duration.ofHours(2209), properties.maxWindow());
    assertEquals(8, properties.getMaxSyncWindowDays());
    assertEquals(Duration.ofHours(193), properties.maxSyncWindow());
  }

  @Test
  @DisplayName("binding defaults when unset")
  void binding_defaults() {
    contextRunner.run(
        context -> {
          assertNull(context.getStartupFailure());
          IntegrityWindowCapProperties bound = context.getBean(IntegrityWindowCapProperties.class);
          assertEquals(92, bound.getMaxWindowDays());
          assertEquals(8, bound.getMaxSyncWindowDays());
        });
  }

  @Test
  @DisplayName("max-window-days=0 is rejected at bind time")
  void binding_asyncZeroRejected() {
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
  @DisplayName("max-sync-window-days=0 is rejected at bind time")
  void binding_syncZeroRejected() {
    contextRunner
        .withPropertyValues("ezkey.audit.integrity.max-sync-window-days=0")
        .run(
            context -> {
              Throwable failure = context.getStartupFailure();
              assertNotNull(failure, "zero must fail startup");
              assertInstanceOf(ConfigurationPropertiesBindException.class, failure);
            });
  }

  @Test
  @DisplayName("max-window-days= (empty) fails startup — cannot remove the async cap")
  void binding_asyncEmptyRejected() {
    contextRunner
        .withPropertyValues("ezkey.audit.integrity.max-window-days=")
        .run(
            context -> {
              Throwable failure = context.getStartupFailure();
              assertNotNull(failure, "empty binding must fail startup");
              assertInstanceOf(ConfigurationPropertiesBindException.class, failure);
            });
  }

  @Test
  @DisplayName("max-sync-window-days= (empty) fails startup — cannot remove the sync cap")
  void binding_syncEmptyRejected() {
    contextRunner
        .withPropertyValues("ezkey.audit.integrity.max-sync-window-days=")
        .run(
            context -> {
              Throwable failure = context.getStartupFailure();
              assertNotNull(failure, "empty binding must fail startup");
              assertInstanceOf(ConfigurationPropertiesBindException.class, failure);
            });
  }

  @Test
  @DisplayName("max-window-days=367 is rejected at bind time (@Max 366)")
  void binding_asyncAboveMaxRejected() {
    contextRunner
        .withPropertyValues("ezkey.audit.integrity.max-window-days=367")
        .run(
            context -> {
              Throwable failure = context.getStartupFailure();
              assertNotNull(failure, "367 must fail startup");
              assertInstanceOf(ConfigurationPropertiesBindException.class, failure);
            });
  }

  @Test
  @DisplayName("max-window-days=366 with sync=8 is accepted")
  void binding_asyncMaxAccepted() {
    contextRunner
        .withPropertyValues("ezkey.audit.integrity.max-window-days=366")
        .run(
            context -> {
              assertNull(context.getStartupFailure());
              IntegrityWindowCapProperties bound =
                  context.getBean(IntegrityWindowCapProperties.class);
              assertEquals(366, bound.getMaxWindowDays());
              assertEquals(8, bound.getMaxSyncWindowDays());
            });
  }

  @Test
  @DisplayName("async < sync fails startup (cross-validation)")
  void binding_asyncBelowSyncRejected() {
    contextRunner
        .withPropertyValues(
            "ezkey.audit.integrity.max-window-days=7",
            "ezkey.audit.integrity.max-sync-window-days=8")
        .run(
            context -> {
              Throwable failure = context.getStartupFailure();
              assertNotNull(failure, "async < sync must fail startup");
              assertInstanceOf(ConfigurationPropertiesBindException.class, failure);
              assertTrue(rootMessage(failure).contains("max-window-days"));
            });
  }

  @Test
  @DisplayName("async == sync is accepted")
  void binding_asyncEqualsSyncAccepted() {
    contextRunner
        .withPropertyValues(
            "ezkey.audit.integrity.max-window-days=8",
            "ezkey.audit.integrity.max-sync-window-days=8")
        .run(
            context -> {
              assertNull(context.getStartupFailure());
              IntegrityWindowCapProperties bound =
                  context.getBean(IntegrityWindowCapProperties.class);
              assertEquals(8, bound.getMaxWindowDays());
              assertEquals(8, bound.getMaxSyncWindowDays());
            });
  }

  @Test
  @DisplayName("exactly 92×24h+1h async is accepted; +1s throws with maxWindowDays=92")
  void validateWindow_enforcesAsyncCapBoundary() {
    Duration max = properties.maxWindow();
    assertDoesNotThrow(() -> properties.validateWindow(FROM, FROM.plus(max)));
    IntegrityWindowOverCapException ex =
        assertThrows(
            IntegrityWindowOverCapException.class,
            () -> properties.validateWindow(FROM, FROM.plus(max).plusSeconds(1)));
    assertEquals(92, ex.getMaxWindowDays());
    assertTrue(ex.getMessage().contains("92"));
    assertTrue(ex.getMessage().contains("calendar days"));
  }

  @Test
  @DisplayName("exactly 8×24h+1h sync is accepted; +1s throws with maxWindowDays=8")
  void validateSyncWindow_enforcesSyncCapBoundary() {
    Duration max = properties.maxSyncWindow();
    assertEquals(Duration.ofHours(193), max);
    assertDoesNotThrow(() -> properties.validateSyncWindow(FROM, FROM.plus(max)));
    IntegrityWindowOverCapException ex =
        assertThrows(
            IntegrityWindowOverCapException.class,
            () -> properties.validateSyncWindow(FROM, FROM.plus(max).plusSeconds(1)));
    assertEquals(8, ex.getMaxWindowDays());
    assertTrue(ex.getMessage().contains("8"));
  }

  @Test
  @DisplayName("sync path rejects a 92-day Instant span that async would accept")
  void validateSyncWindow_rejectsAsyncSizedWindow() {
    IntegrityWindowOverCapException ex =
        assertThrows(
            IntegrityWindowOverCapException.class,
            () -> properties.validateSyncWindow(FROM, FROM.plus(properties.maxWindow())));
    assertEquals(8, ex.getMaxWindowDays());
  }

  @Test
  @DisplayName("92 calendar days across America/Toronto fall-back (2209h) is accepted for async")
  void validateWindow_acceptsNinetyTwoDaysAcrossFallBack() {
    OffsetDateTime from = OffsetDateTime.parse("2026-08-04T04:00:00Z");
    OffsetDateTime to = OffsetDateTime.parse("2026-11-04T05:00:00Z");
    assertEquals(2209, Duration.between(from, to).toHours());
    assertDoesNotThrow(() -> properties.validateWindow(from, to));
  }

  @Test
  @DisplayName("null from or to is rejected on both validators")
  void validateWindow_requiresBothEnds() {
    assertThrows(IllegalArgumentException.class, () -> properties.validateWindow(null, FROM));
    assertThrows(IllegalArgumentException.class, () -> properties.validateWindow(FROM, null));
    assertThrows(IllegalArgumentException.class, () -> properties.validateSyncWindow(null, FROM));
    assertThrows(IllegalArgumentException.class, () -> properties.validateSyncWindow(FROM, null));
  }

  @Test
  @DisplayName("inverted range is rejected")
  void validateWindow_rejectsInverted() {
    assertThrows(
        IllegalArgumentException.class, () -> properties.validateWindow(FROM, FROM.minusHours(1)));
    assertThrows(
        IllegalArgumentException.class,
        () -> properties.validateSyncWindow(FROM, FROM.minusHours(1)));
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
  @DisplayName("legacy operator-max-window-hours as compact env var fails startup")
  void legacyRetroactiveKeyAsCompactEnv_failsStartup() {
    assertLegacyEnvFails(
        "EZKEY_AUDIT_INTEGRITY_RETROACTIVE_OPERATORMAXWINDOWHOURS",
        "24",
        IntegrityWindowCapLegacyPropertyGuard.LEGACY_RETROACTIVE_OPERATOR_MAX_WINDOW_HOURS);
  }

  @Test
  @DisplayName("legacy verify-report key as compact EZKEY_…VERIFYREPORT_MAXWINDOWHOURS fails")
  void legacyVerifyReportKeyAsCompactEnv_failsStartup() {
    assertLegacyEnvFails(
        "EZKEY_AUDIT_INTEGRITY_VERIFYREPORT_MAXWINDOWHOURS",
        "96",
        IntegrityWindowCapLegacyPropertyGuard.LEGACY_VERIFY_REPORT_MAX_WINDOW_HOURS);
  }

  @Test
  @DisplayName("legacy verify-report key as underscore env var fails startup")
  void legacyVerifyReportKeyAsUnderscoreEnv_failsStartup() {
    assertLegacyEnvFails(
        "EZKEY_AUDIT_INTEGRITY_VERIFY_REPORT_MAX_WINDOW_HOURS",
        "72",
        IntegrityWindowCapLegacyPropertyGuard.LEGACY_VERIFY_REPORT_MAX_WINDOW_HOURS);
  }

  @Test
  @DisplayName("legacy operator-max-window-hours as underscore env var fails startup")
  void legacyRetroactiveKeyAsUnderscoreEnv_failsStartup() {
    assertLegacyEnvFails(
        "EZKEY_AUDIT_INTEGRITY_RETROACTIVE_OPERATOR_MAX_WINDOW_HOURS",
        "36",
        IntegrityWindowCapLegacyPropertyGuard.LEGACY_RETROACTIVE_OPERATOR_MAX_WINDOW_HOURS);
  }

  @Test
  @DisplayName("unrelated similar key (nightly.window-hours) does not trip the legacy guard")
  void unrelatedSimilarKey_doesNotFailStartup() {
    StandardEnvironment env = new StandardEnvironment();
    env.getPropertySources()
        .addFirst(
            new SystemEnvironmentPropertySource(
                "testEnv", Map.of("EZKEY_AUDIT_INTEGRITY_NIGHTLY_WINDOW_HOURS", "24")));

    assertDoesNotThrow(() -> new IntegrityWindowCapLegacyPropertyGuard(env));

    contextRunner
        .withPropertyValues("ezkey.audit.integrity.nightly.window-hours=24")
        .run(context -> assertNull(context.getStartupFailure()));
  }

  private static void assertLegacyEnvFails(
      String envKey, String value, String expectedCanonicalKey) {
    StandardEnvironment env = new StandardEnvironment();
    env.getPropertySources()
        .addFirst(new SystemEnvironmentPropertySource("testEnv", Map.of(envKey, value)));

    IllegalStateException ex =
        assertThrows(
            IllegalStateException.class, () -> new IntegrityWindowCapLegacyPropertyGuard(env));
    assertTrue(ex.getMessage().contains(expectedCanonicalKey));
    assertTrue(
        ex.getMessage()
            .contains(IntegrityWindowCapLegacyPropertyGuard.REPLACEMENT_MAX_WINDOW_DAYS));
    assertTrue(!ex.getMessage().contains(value));
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
