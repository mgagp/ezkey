/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AccessCodeServiceTest
 * Description: Startup validation and SHA-256 constant-time slot lookup for access codes.
 */

package org.ezkey.demo.acme.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.ezkey.demo.acme.config.AcmeProperties;
import org.ezkey.demo.acme.config.AcmeProperties.AccessCodeSlotProperties;
import org.junit.jupiter.api.Test;

class AccessCodeServiceTest {

  private static final String CODE_A = "0123456789abcdef0123456789abcdef";
  private static final String CODE_B = "fedcba9876543210fedcba9876543210";

  @Test
  void shouldFailFastWhenCodeTooShort() {
    AccessCodeService service = new AccessCodeService(new AcmeProperties());
    Map<String, AccessCodeSlotProperties> slots = new LinkedHashMap<>();
    slots.put("short", slot("abcd", "ikey", "skey", "Short"));

    assertThatThrownBy(() -> service.loadSlots(slots))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("invalid code");
  }

  @Test
  void shouldFailFastWhenCodesDuplicate() {
    AccessCodeService service = new AccessCodeService(new AcmeProperties());
    Map<String, AccessCodeSlotProperties> slots = new LinkedHashMap<>();
    slots.put("a", slot(CODE_A, "ikey-a", "skey-a", "Northwind Portal"));
    slots.put("b", slot(CODE_A, "ikey-b", "skey-b", "Partner Demo"));

    assertThatThrownBy(() -> service.loadSlots(slots))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Duplicate");
  }

  @Test
  void shouldFailFastWhenLabelBlank() {
    AccessCodeService service = new AccessCodeService(new AcmeProperties());
    Map<String, AccessCodeSlotProperties> slots = new LinkedHashMap<>();
    slots.put("a", slot(CODE_A, "ikey", "skey", " "));

    assertThatThrownBy(() -> service.loadSlots(slots))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("non-blank");
  }

  @Test
  void shouldResolveKnownCodeAndRejectUnknownWithoutExposingCode() {
    AccessCodeService service = loadTwoSlots();

    Optional<String> found = service.findSlotIdByCode(CODE_A);
    assertThat(found).contains("northwind");
    assertThat(service.getLabel("northwind")).isEqualTo("Northwind Portal");
    assertThat(service.resolveCredentials("northwind").integrationKey()).isEqualTo("ikey-a");

    assertThat(service.findSlotIdByCode(CODE_B)).contains("partner");
    assertThat(service.findSlotIdByCode("ffffffffffffffffffffffffffffffff")).isEmpty();
  }

  @Test
  void shouldNotKeepPlaintextCodeEqualityPath() {
    AccessCodeService service = loadTwoSlots();
    // Uppercase hex is a different digest than the stored lowercase code.
    assertThat(service.findSlotIdByCode(CODE_A.toUpperCase())).isEmpty();
  }

  private static AccessCodeService loadTwoSlots() {
    AccessCodeService service = new AccessCodeService(new AcmeProperties());
    Map<String, AccessCodeSlotProperties> slots = new LinkedHashMap<>();
    slots.put("northwind", slot(CODE_A, "ikey-a", "skey-a", "Northwind Portal"));
    slots.put("partner", slot(CODE_B, "ikey-b", "skey-b", "Partner Demo"));
    service.loadSlots(slots);
    return service;
  }

  private static AccessCodeSlotProperties slot(
      String code, String integrationKey, String secretKey, String label) {
    AccessCodeSlotProperties props = new AccessCodeSlotProperties();
    props.setCode(code);
    props.setIntegrationKey(integrationKey);
    props.setSecretKey(secretKey);
    props.setLabel(label);
    return props;
  }
}
