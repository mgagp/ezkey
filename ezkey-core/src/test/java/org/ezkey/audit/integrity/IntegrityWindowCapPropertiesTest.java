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
import org.ezkey.audit.exception.IntegrityWindowOverCapException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationPropertiesBindException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

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
      new ApplicationContextRunner().withUserConfiguration(WindowCapConfig.class);

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
    contextRunner.run(context -> {
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
        .run(context -> {
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
        .run(context -> {
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
        .run(context -> {
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
        .run(context -> {
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
        .run(context -> {
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
        .run(context -> {
          assertNull(context.getStartupFailure());
          IntegrityWindowCapProperties bound = context.getBean(IntegrityWindowCapProperties.class);
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
        .run(context -> {
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
        .run(context -> {
          assertNull(context.getStartupFailure());
          IntegrityWindowCapProperties bound = context.getBean(IntegrityWindowCapProperties.class);
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
  @DisplayName(
      "exactly 8×24h+1h sync is accepted; +1s throws with maxWindowDays=8 + asyncAlternative")
  void validateSyncWindow_enforcesSyncCapBoundary() {
    Duration max = properties.maxSyncWindow();
    assertEquals(Duration.ofHours(193), max);
    assertDoesNotThrow(() -> properties.validateSyncWindow(FROM, FROM.plus(max)));
    IntegrityWindowOverCapException ex =
        assertThrows(
            IntegrityWindowOverCapException.class,
            () -> properties.validateSyncWindow(FROM, FROM.plus(max).plusSeconds(1)));
    assertEquals(8, ex.getMaxWindowDays());
    assertEquals(IntegrityWindowOverCapException.CAP_KIND_SYNC, ex.getCapKind());
    assertEquals(IntegrityWindowOverCapException.ASYNC_ALTERNATIVE, ex.getAsyncAlternative());
    assertTrue(ex.getMessage().contains("8"));
  }

  @Test
  @DisplayName("async over-cap carries capKind=ASYNC and no asyncAlternative")
  void validateWindow_asyncOverCapHasCapKindAsyncAndNoAsyncAlternative() {
    IntegrityWindowOverCapException ex =
        assertThrows(
            IntegrityWindowOverCapException.class,
            () ->
                properties.validateWindow(FROM, FROM.plus(properties.maxWindow()).plusSeconds(1)));
    assertEquals(92, ex.getMaxWindowDays());
    assertEquals(IntegrityWindowOverCapException.CAP_KIND_ASYNC, ex.getCapKind());
    assertEquals(null, ex.getAsyncAlternative());
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
