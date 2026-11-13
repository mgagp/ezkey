/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AuthApiDtoToStringMaskingTest
 * Description: Reflection guard — secret-bearing auth-api types' toString() must mask values (#752).
 */

package org.ezkey.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.ezkey.auth.AuthApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.RegexPatternTypeFilter;

/**
 * Fails when a secret-bearing auth-api type exposes its value via {@code toString()}.
 *
 * <p>Scans every {@code org.ezkey.*} record and class loaded from the auth-api module (identified
 * by code-source origin equality with {@link AuthApplication}). Secret field names match tokens,
 * signatures, challenges, codes, keys, and related markers; exclusions are exact names or exact
 * suffixes applied only after a marker match.
 *
 * @since 2026
 */
class AuthApiDtoToStringMaskingTest {

  private static final String SCAN_BASE_PACKAGE = "org.ezkey";

  /** Minimum secret-bearing types expected from the #752 inventory (guards empty scans). */
  private static final int MIN_SECRET_BEARING_TYPES = 10;

  private static final URL AUTH_API_ORIGIN = resolveOrigin(AuthApplication.class);

  private static final Set<String> SECRET_NAME_MARKERS =
      Set.of(
          "prooftoken",
          "token",
          "signed",
          "signature",
          "challengeresponse",
          "challenge",
          "apikey",
          "secret",
          "password",
          "privatekey",
          "code",
          "otp",
          "hmac",
          "recovery",
          "nonce",
          "pin",
          "key");

  /**
   * Exact lower-case field names that are never secrets (applied only after a marker match).
   *
   * <p>Keep this list intentional — if a new false positive appears, add a named exclusion here (do
   * not weaken the secret markers).
   */
  private static final Set<String> EXCLUDED_EXACT_NAMES =
      Set.of("signedat", "challengelength", "keystrategy");

  /**
   * Exact lower-case suffixes that mark non-secret fields (applied only after a marker match).
   *
   * <p>Substring containment is intentionally forbidden: {@code publicKeyProofToken} must stay
   * secret even though it contains {@code publicKey}.
   */
  private static final Set<String> EXCLUDED_SUFFIXES =
      Set.of("publickey", "challengerequired", "storagetier", "keyalgorithm");

  @Test
  @DisplayName("Secret-bearing auth-api types mask toString() and override Object/Record")
  void secretBearingTypesMaskToString() throws Exception {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new RegexPatternTypeFilter(Pattern.compile(".*")));

    List<String> secretBearingTypes = new ArrayList<>();

    for (BeanDefinition def : scanner.findCandidateComponents(SCAN_BASE_PACKAGE)) {
      Class<?> clazz = Class.forName(def.getBeanClassName());
      if (!isAuthApiModuleClass(clazz)
          || clazz.isInterface()
          || clazz.isEnum()
          || clazz.isAnnotation()) {
        continue;
      }
      List<SecretMember> secrets = secretMembers(clazz);
      if (secrets.isEmpty()) {
        continue;
      }
      secretBearingTypes.add(clazz.getName());
      assertDeclaresToString(clazz);
      assertMaskedToString(clazz, secrets);
    }

    assertThat(secretBearingTypes)
        .as("expected secret-bearing auth-api types from inventory")
        .hasSizeGreaterThanOrEqualTo(MIN_SECRET_BEARING_TYPES);
  }

  @Test
  @DisplayName("publicKeyProofToken is secret — publicKey substring must not exclude it")
  void publicKeyProofTokenIsNotExcluded() {
    assertThat(isSecretFieldName("publicKeyProofToken")).isTrue();
  }

  @Test
  @DisplayName("signedAttestation is secret — signedAt exact exclusion must not match as substring")
  void signedAttestationIsNotExcluded() {
    assertThat(isSecretFieldName("signedAttestation")).isTrue();
  }

  @Test
  @DisplayName("Known non-secret names/suffixes are excluded after marker match")
  void knownNonSecretsAreExcluded() {
    assertThat(isSecretFieldName("integrationPublicKey")).isFalse();
    assertThat(isSecretFieldName("authAttemptChallengeRequired")).isFalse();
    assertThat(isSecretFieldName("devicePrivateKeyStorageTier")).isFalse();
    assertThat(isSecretFieldName("integrationKeyAlgorithm")).isFalse();
    assertThat(isSecretFieldName("signedAt")).isFalse();
    assertThat(isSecretFieldName("challengeLength")).isFalse();
    assertThat(isSecretFieldName("keyStrategy")).isFalse();
  }

  @Test
  @DisplayName("Unsupported secret field types fail loudly (no null fallback)")
  void unsupportedSecretTypesFailLoudly() {
    assertThatThrownBy(() -> sampleForType(List.class, "tokenList", true))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Unsupported")
        .hasMessageContaining("tokenList");
    assertThatThrownBy(() -> sampleForType(char[].class, "pinChars", true))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Unsupported");
    assertThatThrownBy(() -> sampleForType(UUID.class, "nonce", true))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Unsupported");
  }

  /**
   * Keeps only types compiled into the auth-api module by comparing code-source origin with {@link
   * AuthApplication}.
   *
   * @param clazz candidate type
   * @return true when the class shares AuthApplication's code-source location
   */
  private static boolean isAuthApiModuleClass(Class<?> clazz) {
    URL origin = resolveOrigin(clazz);
    return AUTH_API_ORIGIN != null && AUTH_API_ORIGIN.equals(origin);
  }

  private static URL resolveOrigin(Class<?> clazz) {
    if (clazz.getProtectionDomain() == null
        || clazz.getProtectionDomain().getCodeSource() == null) {
      return null;
    }
    return clazz.getProtectionDomain().getCodeSource().getLocation();
  }

  private static List<SecretMember> secretMembers(Class<?> clazz) {
    List<SecretMember> secrets = new ArrayList<>();
    if (clazz.isRecord()) {
      for (RecordComponent component : clazz.getRecordComponents()) {
        if (isSecretFieldName(component.getName())) {
          secrets.add(new SecretMember(component.getName(), component.getType()));
        }
      }
      return secrets;
    }
    for (Field field : clazz.getDeclaredFields()) {
      int mods = field.getModifiers();
      if (Modifier.isStatic(mods) || field.isSynthetic()) {
        continue;
      }
      if (isSecretFieldName(field.getName())) {
        secrets.add(new SecretMember(field.getName(), field.getType()));
      }
    }
    return secrets;
  }

  /**
   * Markers first (substring), then exact-name / exact-suffix exclusions.
   *
   * @param name field or record-component name
   * @return true when the member must be masked in {@code toString()}
   */
  static boolean isSecretFieldName(String name) {
    String lower = name.toLowerCase(Locale.ROOT);
    boolean markerMatched = false;
    for (String marker : SECRET_NAME_MARKERS) {
      if (lower.contains(marker)) {
        markerMatched = true;
        break;
      }
    }
    if (!markerMatched) {
      return false;
    }
    if (EXCLUDED_EXACT_NAMES.contains(lower)) {
      return false;
    }
    for (String suffix : EXCLUDED_SUFFIXES) {
      if (lower.endsWith(suffix)) {
        return false;
      }
    }
    return true;
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

  private static void assertMaskedToString(Class<?> clazz, List<SecretMember> secrets)
      throws Exception {
    Object instance;
    List<String> markers = new ArrayList<>();
    if (clazz.isRecord()) {
      instance = newRecordInstance(clazz, secrets, markers);
    } else {
      instance = newClassInstance(clazz, secrets, markers);
    }

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

  private static Object newRecordInstance(
      Class<?> clazz, List<SecretMember> secrets, List<String> markers) throws Exception {
    RecordComponent[] components = clazz.getRecordComponents();
    Class<?>[] paramTypes = new Class<?>[components.length];
    Object[] args = new Object[components.length];
    for (int i = 0; i < components.length; i++) {
      RecordComponent component = components[i];
      paramTypes[i] = component.getType();
      boolean secret = secrets.stream().anyMatch(s -> s.name().equals(component.getName()));
      Object value = sampleForType(component.getType(), component.getName(), secret);
      args[i] = value;
      if (secret) {
        addSecretMarkers(markers, value);
      }
    }
    Constructor<?> canonical = clazz.getDeclaredConstructor(paramTypes);
    canonical.setAccessible(true);
    return canonical.newInstance(args);
  }

  private static Object newClassInstance(
      Class<?> clazz, List<SecretMember> secrets, List<String> markers) throws Exception {
    Constructor<?> noArg;
    try {
      noArg = clazz.getDeclaredConstructor();
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException(
          clazz.getSimpleName()
              + " has secret fields but no no-arg constructor — extend the masking test",
          e);
    }
    noArg.setAccessible(true);
    Object instance = noArg.newInstance();
    for (SecretMember secret : secrets) {
      Field field = clazz.getDeclaredField(secret.name());
      field.setAccessible(true);
      Object markerValue = sampleForType(secret.type(), secret.name(), true);
      field.set(instance, markerValue);
      addSecretMarkers(markers, markerValue);
    }
    return instance;
  }

  private static void addSecretMarkers(List<String> markers, Object value) {
    markers.add(String.valueOf(value));
  }

  /**
   * Sample values for types used by auth-api DTOs. Unsupported types fail loudly (no null).
   *
   * @param type member type
   * @param name member name (for distinctive markers)
   * @param secret whether this member is secret-bearing
   * @return sample value
   */
  static Object sampleForType(Class<?> type, String name, boolean secret) {
    if (type == String.class) {
      return secret ? "SECRET_MARKER_" + name + "_XYZ9q" : "safe-" + name;
    }
    if (type == Integer.class || type == int.class) {
      // Distinctive odd magnitude unlikely to collide with ids / enums
      return secret ? 771_334_209 + Math.floorMod(name.hashCode(), 97) : 1;
    }
    if (type == Boolean.class || type == boolean.class) {
      return secret ? Boolean.TRUE : Boolean.FALSE;
    }
    if (type.isEnum()) {
      Object[] constants = type.getEnumConstants();
      if (constants != null && constants.length > 0) {
        return constants[0];
      }
    }
    throw new IllegalStateException(
        "Unsupported type "
            + type.getName()
            + " for "
            + name
            + " — extend sampleForType only for types auth-api DTOs actually use");
  }

  /**
   * Secret-bearing member (record component or class field).
   *
   * @param name member name
   * @param type member type
   */
  private record SecretMember(String name, Class<?> type) {}
}
