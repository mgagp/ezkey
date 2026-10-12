/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Class: IntegrityWindowCapLegacyPropertyGuard
 * Description: Fail-fast when provisional Integrity hour-cap keys are still configured.
 */

package org.ezkey.audit.integrity;

import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;

/**
 * Refuses startup when either provisional #743 hour-cap property is still set (any relaxed form,
 * including environment variables). Silently ignoring a lower operator cap would weaken a resource
 * bound (design-principles §17 fail-closed).
 *
 * <p>Detection uses Spring Boot {@link Binder} for the canonical key (covers properties and
 * underscore env forms via {@code SystemEnvironmentPropertySource}). Compact env forms such as
 * {@code EZKEY_…VERIFYREPORT_MAXWINDOWHOURS} are <em>not</em> bound by Binder alone when present
 * only on a secondary {@code SystemEnvironmentPropertySource} — see the exact-name scan below
 * (proven by {@code IntegrityWindowCapPropertiesTest} compact vs underscore cases).
 *
 * <p>Failure messages name the replacement key {@code ezkey.audit.integrity.max-window-days} and
 * never echo configured values.
 *
 * @since 2026
 */
@Configuration
public class IntegrityWindowCapLegacyPropertyGuard {

  /** Canonical key superseded by {@code max-window-days} (verify-report / VERIFY / report GETs). */
  public static final String LEGACY_VERIFY_REPORT_MAX_WINDOW_HOURS =
      "ezkey.audit.integrity.verify-report.max-window-hours";

  /**
   * Canonical key superseded by {@code max-window-days} (operator retroactive / RUN_VALIDATION).
   */
  public static final String LEGACY_RETROACTIVE_OPERATOR_MAX_WINDOW_HOURS =
      "ezkey.audit.integrity.retroactive.operator-max-window-hours";

  /** Replacement property operators must use. */
  public static final String REPLACEMENT_MAX_WINDOW_DAYS = "ezkey.audit.integrity.max-window-days";

  /**
   * Compact env form cited in the cut-3 brief (dashes/dots removed between segments).
   *
   * <p>{@code EZKEY_AUDIT_INTEGRITY_VERIFYREPORT_MAXWINDOWHOURS}
   */
  static final String LEGACY_VERIFY_REPORT_ENV_COMPACT =
      "EZKEY_AUDIT_INTEGRITY_VERIFYREPORT_MAXWINDOWHOURS";

  /**
   * Compact env form cited in the cut-3 brief.
   *
   * <p>{@code EZKEY_AUDIT_INTEGRITY_RETROACTIVE_OPERATORMAXWINDOWHOURS}
   */
  static final String LEGACY_RETROACTIVE_ENV_COMPACT =
      "EZKEY_AUDIT_INTEGRITY_RETROACTIVE_OPERATORMAXWINDOWHOURS";

  private final Environment environment;

  /**
   * Constructs the guard and fails immediately when a legacy hour key is bound.
   *
   * @param environment Spring environment (property sources + relaxed binding)
   */
  public IntegrityWindowCapLegacyPropertyGuard(Environment environment) {
    this.environment = environment;
    rejectIfPresent(LEGACY_VERIFY_REPORT_MAX_WINDOW_HOURS, LEGACY_VERIFY_REPORT_ENV_COMPACT);
    rejectIfPresent(LEGACY_RETROACTIVE_OPERATOR_MAX_WINDOW_HOURS, LEGACY_RETROACTIVE_ENV_COMPACT);
  }

  private void rejectIfPresent(String canonicalKey, String compactEnvKey) {
    if (Binder.get(environment).bind(canonicalKey, Bindable.of(String.class)).isBound()
        || environmentContainsExactName(compactEnvKey)) {
      throw new IllegalStateException(
          "Property '"
              + canonicalKey
              + "' is no longer supported. Use '"
              + REPLACEMENT_MAX_WINDOW_DAYS
              + "' instead.");
    }
  }

  /**
   * Detects an exact property-source key for compact {@code EZKEY_…} forms that Binder misses on a
   * secondary {@code SystemEnvironmentPropertySource} (underscore forms are Binder-covered).
   *
   * @param exactName exact key to look for in enumerable property sources
   * @return {@code true} when any source exposes that name
   */
  private boolean environmentContainsExactName(String exactName) {
    if (!(environment instanceof AbstractEnvironment abstractEnv)) {
      return environment.getProperty(exactName) != null;
    }
    for (PropertySource<?> source : abstractEnv.getPropertySources()) {
      if (source.containsProperty(exactName)) {
        return true;
      }
      if (source instanceof EnumerablePropertySource<?> enumerable) {
        for (String name : enumerable.getPropertyNames()) {
          if (exactName.equals(name)) {
            return true;
          }
        }
      }
    }
    return false;
  }
}
