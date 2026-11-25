/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AdminApiDtoToStringMaskingTest
 * Description: Reflection guard — secret-bearing admin-api types' toString() must mask values (#756).
 */

package org.ezkey.admin.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.net.URL;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.ezkey.admin.AdminApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.RegexPatternTypeFilter;

/**
 * Fails when a secret-bearing admin-api type exposes its value via {@code toString()}.
 *
 * <p>Scans every {@code org.ezkey.*} record and class loaded from the admin-api module (identified
 * by code-source origin equality with {@link AdminApplication}). A member is secret-bearing when
 * its type can carry a value (see {@link #isValueCarrier}) and its name matches a marker (tokens,
 * signatures, challenges, codes, keys and related words); exclusions are exact names or exact
 * suffixes applied only after a marker match. Masking with {@code [PROTECTED]} or omitting the
 * member both pass. Same approach as the auth-api guard (#752); integration-api and core follow in
 * their own changes.
 *
 * @since 2026
 */
class AdminApiDtoToStringMaskingTest {

  private static final String SCAN_BASE_PACKAGE = "org.ezkey";

  /** Minimum secret-bearing types expected from the #756 inventory (guards empty scans). */
  private static final int MIN_SECRET_BEARING_TYPES = 25;

  private static final URL ADMIN_API_ORIGIN = resolveOrigin(AdminApplication.class);

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
      Set.of(
          "signedat", "challengelength", "keystrategy", "code", "apiname", "jobkey", "keystatus");

  /**
   * Exact lower-case suffixes that mark non-secret fields (applied only after a marker match).
   *
   * <p>Substring containment is intentionally forbidden: {@code publicKeyProofToken} must stay
   * secret even though it contains {@code publicKey}.
   */
  private static final Set<String> EXCLUDED_SUFFIXES =
      Set.of(
          "publickey",
          "challengerequired",
          "storagetier",
          "keyalgorithm",
          "integrationkey",
          "keyid",
          "countrycode",
          "count",
          "remaining",
          "minutes");

  @Test
  @DisplayName("Secret-bearing admin-api types mask toString() and override Object/Record")
  void secretBearingTypesMaskToString() throws Exception {
    ClassPathScanningCandidateComponentProvider scanner =
        new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new RegexPatternTypeFilter(Pattern.compile(".*")));

    List<String> secretBearingTypes = new ArrayList<>();
    List<String> violations = new ArrayList<>();

    for (BeanDefinition def : scanner.findCandidateComponents(SCAN_BASE_PACKAGE)) {
      Class<?> clazz =
          Class.forName(
              def.getBeanClassName(), false, AdminApiDtoToStringMaskingTest.class.getClassLoader());
      if (!isAdminApiModuleClass(clazz)
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
      String violation = violation(clazz, secrets);
      if (violation != null) {
        violations.add(violation);
      }
    }

    assertThat(violations).as("secret-bearing admin-api types leaking via toString()").isEmpty();
    assertThat(secretBearingTypes)
        .as("expected secret-bearing admin-api types from inventory")
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
    assertThat(isSecretFieldName("integrationKey")).isFalse();
    assertThat(isSecretFieldName("apiKeyId")).isFalse();
    assertThat(isSecretFieldName("countryCode")).isFalse();
    assertThat(isSecretFieldName("codesRemaining")).isFalse();
    assertThat(isSecretFieldName("previousCodesCount")).isFalse();
  }

  @Test
  @DisplayName("Admin secrets named in #756 are detected")
  void adminSecretsAreDetected() {
    assertThat(isSecretFieldName("activationCode")).isTrue();
    assertThat(isSecretFieldName("recoveryCodes")).isTrue();
    assertThat(isSecretFieldName("challengeCode")).isTrue();
    assertThat(isSecretFieldName("enrollmentProofToken")).isTrue();
    assertThat(isSecretFieldName("waiterSecret")).isTrue();
    assertThat(isSecretFieldName("secretKey")).isTrue();
  }

  @Test
  @DisplayName("Only value-carrying member types are checked")
  void onlyValueCarriersAreChecked() {
    assertThat(isValueCarrier(String.class)).isTrue();
    assertThat(isValueCarrier(Integer.class)).isTrue();
    assertThat(isValueCarrier(List.class)).isTrue();
    assertThat(isValueCarrier(Long.class)).isFalse();
    assertThat(isValueCarrier(Boolean.class)).isFalse();
    assertThat(isValueCarrier(OffsetDateTime.class)).isFalse();
  }

  @Test
  @DisplayName("Unsupported secret field types fail loudly (no null fallback)")
  void unsupportedSecretTypesFailLoudly() {
    assertThatThrownBy(() -> sampleForType(Set.class, "tokenSet", true))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Unsupported")
        .hasMessageContaining("tokenSet");
    assertThatThrownBy(() -> sampleForType(char[].class, "pinChars", true))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Unsupported");
    assertThatThrownBy(() -> sampleForType(UUID.class, "nonce", true))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Unsupported");
  }

  /**
   * Keeps only types compiled into the admin-api module by comparing code-source origin with {@link
   * AdminApplication}.
   *
   * @param clazz candidate type
   * @return true when the class shares AdminApplication's code-source location
   */
  private static boolean isAdminApiModuleClass(Class<?> clazz) {
    URL origin = resolveOrigin(clazz);
    return ADMIN_API_ORIGIN != null && ADMIN_API_ORIGIN.equals(origin);
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
        if (isValueCarrier(component.getType()) && isSecretFieldName(component.getName())) {
          secrets.add(new SecretMember(component.getName(), component.getType(), clazz));
        }
      }
      return secrets;
    }
    // Walk the superclass chain: the scanner skips abstract bases, so their fields count here.
    for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
      for (Field field : c.getDeclaredFields()) {
        int mods = field.getModifiers();
        if (Modifier.isStatic(mods) || field.isSynthetic()) {
          continue;
        }
        if (isValueCarrier(field.getType()) && isSecretFieldName(field.getName())) {
          secrets.add(new SecretMember(field.getName(), field.getType(), c));
        }
      }
    }
    return secrets;
  }

  /**
   * Member types that can carry a secret value. Service references, ids ({@code Long}), flags
   * ({@code Boolean}) and timestamps never do, which keeps Spring beans and expiry dates out of the
   * guard without per-class exclusions.
   *
   * @param type member type
   * @return true when the member can hold a secret value
   */
  static boolean isValueCarrier(Class<?> type) {
    return type == String.class
        || type == Integer.class
        || type == int.class
        || type == char[].class
        || type == byte[].class
        || type == String[].class
        || type == Optional.class
        || Collection.class.isAssignableFrom(type)
        || Map.class.isAssignableFrom(type);
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

  /**
   * Returns a description of the leak, or null when {@code clazz} declares its own {@code
   * toString()} and renders none of the secret sample values. Omitting a secret or showing {@code
   * [PROTECTED]} both pass.
   *
   * @param clazz secret-bearing type
   * @param secrets its secret members
   * @return violation message, or null
   */
  private static String violation(Class<?> clazz, List<SecretMember> secrets) throws Exception {
    try {
      clazz.getDeclaredMethod("toString");
    } catch (NoSuchMethodException e) {
      return clazz.getName() + " must override toString() to mask " + names(secrets);
    }
    List<String> markers = new ArrayList<>();
    Object instance =
        clazz.isRecord()
            ? newRecordInstance(clazz, secrets, markers)
            : newClassInstance(clazz, secrets, markers);
    String rendered = instance.toString();
    for (String marker : markers) {
      // The 8-character head catches a toString() that prints a prefix of the secret.
      String head = marker.length() > 8 ? marker.substring(0, 8) : marker;
      if (rendered.contains(head)) {
        return clazz.getName() + " toString() leaks " + head;
      }
    }
    return null;
  }

  private static List<String> names(List<SecretMember> secrets) {
    return secrets.stream().map(SecretMember::name).toList();
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
    Object instance = instantiate(clazz);
    for (SecretMember secret : secrets) {
      Field field = secret.declaringClass().getDeclaredField(secret.name());
      field.setAccessible(true);
      Object markerValue = sampleForType(secret.type(), secret.name(), true);
      field.set(instance, markerValue);
      addSecretMarkers(markers, markerValue);
    }
    return instance;
  }

  /**
   * Builds an instance through the no-arg constructor, else through the first declared constructor
   * with non-secret samples; secret fields are then set by reflection.
   *
   * @param clazz type to instantiate
   * @return new instance
   */
  private static Object instantiate(Class<?> clazz) throws Exception {
    Constructor<?>[] constructors = clazz.getDeclaredConstructors();
    Constructor<?> chosen = constructors[0];
    for (Constructor<?> candidate : constructors) {
      if (candidate.getParameterCount() == 0) {
        chosen = candidate;
        break;
      }
    }
    Class<?>[] paramTypes = chosen.getParameterTypes();
    Object[] args = new Object[paramTypes.length];
    for (int i = 0; i < paramTypes.length; i++) {
      args[i] = sampleForType(paramTypes[i], "arg" + i, false);
    }
    chosen.setAccessible(true);
    return chosen.newInstance(args);
  }

  private static void addSecretMarkers(List<String> markers, Object value) {
    if (value instanceof Object[] array) {
      for (Object element : array) {
        markers.add(String.valueOf(element));
      }
      return;
    }
    if (value instanceof Collection<?> values) {
      values.forEach(v -> markers.add(String.valueOf(v)));
      return;
    }
    markers.add(String.valueOf(value));
  }

  /**
   * Sample values for types used by admin-api DTOs. Unsupported types fail loudly (no null).
   *
   * @param type member type
   * @param name member name (for distinctive markers)
   * @param secret whether this member is secret-bearing
   * @return sample value
   */
  static Object sampleForType(Class<?> type, String name, boolean secret) {
    if (type == String[].class && secret) {
      return new String[] {"SECRET_MARKER_" + name + "_0_XYZ9q"};
    }
    if (type == List.class && secret) {
      return List.of("SECRET_MARKER_" + name + "_0_XYZ9q", "SECRET_MARKER_" + name + "_1_XYZ9q");
    }
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
    if (!secret) {
      return nonSecretSample(type);
    }
    throw new IllegalStateException(
        "Unsupported type "
            + type.getName()
            + " for "
            + name
            + " — extend sampleForType only for types admin-api DTOs actually use");
  }

  private static Object nonSecretSample(Class<?> type) {
    if (type == long.class || type == Long.class) {
      return 1L;
    }
    if (type == OffsetDateTime.class) {
      return OffsetDateTime.parse("2026-01-01T00:00:00Z");
    }
    if (type == UUID.class) {
      return UUID.fromString("00000000-0000-0000-0000-000000000001");
    }
    if (type.isPrimitive()) {
      throw new IllegalStateException("Unsupported primitive " + type.getName());
    }
    return null;
  }

  /**
   * Secret-bearing member (record component or class field).
   *
   * @param name member name
   * @param type member type
   * @param declaringClass class that declares the member
   */
  private record SecretMember(String name, Class<?> type, Class<?> declaringClass) {}
}
