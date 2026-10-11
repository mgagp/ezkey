/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AuthApiDtoToStringMaskingTest
 * Description: Reflection guard — secret-bearing auth-api DTO toString() must mask values (#752).
 */

package org.ezkey.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.RegexPatternTypeFilter;

/**
 * Fails when a secret-bearing auth-api record/DTO exposes its value via {@code toString()}.
 *
 * <p>Scans {@code org.ezkey.authattempt.dto} and {@code org.ezkey.enrollment.dto}. Secret field
 * names match proof tokens, signatures, challenges, API keys, secrets, passwords, and private keys
 * (public keys and {@code *ChallengeRequired} flags are excluded).
 *
 * @since 2026
 */
class AuthApiDtoToStringMaskingTest {

  private static final String[] DTO_PACKAGES = {
    "org.ezkey.authattempt.dto", "org.ezkey.enrollment.dto"
  };

  /** Minimum secret-bearing DTOs expected from the #752 inventory (guards empty scans). */
  private static final int MIN_SECRET_BEARING_DTOS = 10;

  private static final Set<String> SECRET_NAME_MARKERS =
      Set.of(
          "prooftoken",
          "signed",
          "signature",
          "challengeresponse",
          "challenge",
          "apikey",
          "secret",
          "password",
          "privatekey");

  @Test
  @DisplayName("Secret-bearing auth-api DTO toString() masks values and overrides Object/Record")
  void secretBearingDtoToStringMasksValues() throws Exception {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new RegexPatternTypeFilter(Pattern.compile(".*")));

    List<String> secretBearingTypes = new ArrayList<>();

    for (String pkg : DTO_PACKAGES) {
      for (BeanDefinition def : scanner.findCandidateComponents(pkg)) {
        Class<?> clazz = Class.forName(def.getBeanClassName());
        if (!clazz.isRecord() || !isAuthApiModuleClass(clazz)) {
          // Shared core DTOs (same package names) are out of #752 auth-api scope
          continue;
        }
        List<RecordComponent> secretComponents = secretComponents(clazz);
        if (secretComponents.isEmpty()) {
          continue;
        }
        secretBearingTypes.add(clazz.getName());
        assertDeclaresToString(clazz);
        assertMaskedToString(clazz, secretComponents);
      }
    }

    assertThat(secretBearingTypes)
        .as("expected secret-bearing auth-api DTOs from inventory")
        .hasSizeGreaterThanOrEqualTo(MIN_SECRET_BEARING_DTOS);
  }

  /**
   * Keeps only classes compiled into this module (excludes {@code ezkey-core} DTOs that share
   * package names on the test classpath).
   *
   * @param clazz candidate type
   * @return true when the class was loaded from the auth-api module
   */
  private static boolean isAuthApiModuleClass(Class<?> clazz) {
    if (clazz.getProtectionDomain() == null
        || clazz.getProtectionDomain().getCodeSource() == null
        || clazz.getProtectionDomain().getCodeSource().getLocation() == null) {
      return false;
    }
    String location = clazz.getProtectionDomain().getCodeSource().getLocation().toString();
    return location.contains("ezkey-auth-api");
  }

  private static List<RecordComponent> secretComponents(Class<?> recordClass) {
    List<RecordComponent> secrets = new ArrayList<>();
    for (RecordComponent component : recordClass.getRecordComponents()) {
      if (isSecretFieldName(component.getName())) {
        secrets.add(component);
      }
    }
    return secrets;
  }

  private static boolean isSecretFieldName(String name) {
    String lower = name.toLowerCase(Locale.ROOT);
    // Flags / public material / storage-tier enums are not secrets
    if (lower.contains("challengerequired")
        || lower.contains("publickey")
        || lower.contains("storagetier")) {
      return false;
    }
    for (String marker : SECRET_NAME_MARKERS) {
      if (lower.contains(marker)) {
        return true;
      }
    }
    return false;
  }

  private static void assertDeclaresToString(Class<?> clazz) {
    try {
      Method toString = clazz.getDeclaredMethod("toString");
      assertThat(toString.getDeclaringClass())
          .as("%s must declare its own toString()", clazz.getSimpleName())
          .isEqualTo(clazz);
    } catch (NoSuchMethodException e) {
      fail(clazz.getSimpleName() + " must override toString() to mask secrets");
    }
  }

  private static void assertMaskedToString(Class<?> clazz, List<RecordComponent> secretComponents)
      throws Exception {
    RecordComponent[] all = clazz.getRecordComponents();
    Object[] args = new Object[all.length];
    List<String> markers = new ArrayList<>();

    for (int i = 0; i < all.length; i++) {
      RecordComponent component = all[i];
      boolean secret = secretComponents.contains(component);
      if (secret) {
        Object markerValue = secretMarkerValue(component);
        args[i] = markerValue;
        markers.add(String.valueOf(markerValue));
      } else {
        args[i] = safeSampleValue(component);
      }
    }

    Constructor<?> ctor = clazz.getDeclaredConstructors()[0];
    ctor.setAccessible(true);
    Object instance = ctor.newInstance(args);
    String rendered = instance.toString();

    for (String marker : markers) {
      assertThat(rendered)
          .as("%s toString must not contain secret marker %s", clazz.getSimpleName(), marker)
          .doesNotContain(marker);
    }
    assertThat(rendered)
        .as("%s toString must show [PROTECTED] for masked secrets", clazz.getSimpleName())
        .contains("[PROTECTED]");
  }

  private static Object secretMarkerValue(RecordComponent component) {
    Class<?> type = component.getType();
    if (type == String.class) {
      return "SECRET_MARKER_" + component.getName() + "_XYZ";
    }
    if (type == Integer.class || type == int.class) {
      return 900_000_000 + Math.floorMod(component.getName().hashCode(), 99_999);
    }
    throw new IllegalStateException(
        "Unsupported secret component type "
            + type.getName()
            + " for "
            + component.getName()
            + " — extend the masking test");
  }

  private static Object safeSampleValue(RecordComponent component) {
    Class<?> type = component.getType();
    if (type == String.class) {
      return "safe-" + component.getName();
    }
    if (type == Integer.class || type == int.class) {
      return 1;
    }
    if (type == Long.class || type == long.class) {
      return 1L;
    }
    if (type == Boolean.class || type == boolean.class) {
      return Boolean.TRUE;
    }
    if (type.isEnum()) {
      Object[] constants = type.getEnumConstants();
      if (constants != null && constants.length > 0) {
        return constants[0];
      }
    }
    return null;
  }
}
