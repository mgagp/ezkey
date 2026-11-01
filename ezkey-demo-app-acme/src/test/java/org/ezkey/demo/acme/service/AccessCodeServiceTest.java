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

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.ezkey.demo.acme.config.AcmeProperties;
import org.ezkey.demo.acme.config.AcmeProperties.AccessCodeSlotProperties;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class AccessCodeServiceTest {

  private static final String CODE_A = "0123456789abcdef0123456789abcdef";
  private static final String CODE_B = "fedcba9876543210fedcba9876543210";
  private static final String SECRET = "ezkey_skey_must_never_appear_in_logs_958";

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
  void shouldFailFastWhenCodeIs31HexChars() {
    AccessCodeService service = new AccessCodeService(new AcmeProperties());
    Map<String, AccessCodeSlotProperties> slots = new LinkedHashMap<>();
    slots.put("almost", slot("0123456789abcdef0123456789abcde", "ikey", "skey", "Almost"));

    assertThatThrownBy(() -> service.loadSlots(slots))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("invalid code");
  }

  @Test
  void shouldFailFastWhenCodeHasUppercaseHex() {
    AccessCodeService service = new AccessCodeService(new AcmeProperties());
    Map<String, AccessCodeSlotProperties> slots = new LinkedHashMap<>();
    slots.put("upper", slot("0123456789ABCDEF0123456789ABCDEF", "ikey", "skey", "Upper"));

    assertThatThrownBy(() -> service.loadSlots(slots))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("invalid code");
  }

  @Test
  void shouldFailFastWhenCodeExceeds64HexChars() {
    AccessCodeService service = new AccessCodeService(new AcmeProperties());
    Map<String, AccessCodeSlotProperties> slots = new LinkedHashMap<>();
    slots.put(
        "long",
        slot(
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0",
            "ikey",
            "skey",
            "TooLong"));

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

  @Test
  void shouldNotLogCodeOrSecretWhenLoadingSlots() {
    Logger logger = (Logger) LoggerFactory.getLogger(AccessCodeService.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      AccessCodeService service = new AccessCodeService(new AcmeProperties());
      Map<String, AccessCodeSlotProperties> slots = new LinkedHashMap<>();
      slots.put("northwind", slot(CODE_A, "ezkey_ikey_test", SECRET, "Northwind Portal"));
      service.loadSlots(slots);

      String joined =
          appender.list.stream()
              .map(ILoggingEvent::getFormattedMessage)
              .reduce("", (a, b) -> a + "\n" + b);
      assertThat(joined).doesNotContain(CODE_A);
      assertThat(joined).doesNotContain(SECRET);
      assertThat(joined).contains("Northwind Portal");
    } finally {
      logger.detachAppender(appender);
    }
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
