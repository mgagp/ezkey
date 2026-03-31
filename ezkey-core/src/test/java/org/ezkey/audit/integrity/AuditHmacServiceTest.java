/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.audit.integrity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import org.ezkey.audit.domain.ApiName;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.domain.entity.AuditLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link AuditHmacService}.
 *
 * <p>Verifies the cryptographic correctness of HMAC-SHA256 signing, canonical form construction,
 * tamper detection, and lifecycle (active/inactive) behaviour.
 *
 * <p>A real 256-bit key is generated in-memory for each test run using a {@link TempDir} to avoid
 * any filesystem dependency on the CI environment. The {@code init()} method is called explicitly
 * because {@code @PostConstruct} is not triggered outside a Spring context.
 *
 * @since 2026
 */
class AuditHmacServiceTest {

  @TempDir Path tempDir;

  private AuditHmacService service;
  private AuditHmacProperties properties;

  @BeforeEach
  void setUp() throws Exception {
    byte[] keyBytes = new byte[32];
    new SecureRandom().nextBytes(keyBytes);
    String keyBase64 = Base64.getEncoder().encodeToString(keyBytes);

    Path keyFile = tempDir.resolve("audit-hmac.key");
    Files.writeString(keyFile, keyBase64, StandardCharsets.UTF_8);

    properties = new AuditHmacProperties();
    properties.setEnabled(true);
    properties.setHmacKeyFile(keyFile.toString());
    properties.setInstanceId("test-instance");

    service = new AuditHmacService(properties);
    service.init();
  }

  // -----------------------------------------------------------------------
  // isActive / getInstanceId
  // -----------------------------------------------------------------------

  @Test
  void isActive_whenEnabledAndKeyLoaded_returnsTrue() {
    assertTrue(service.isActive());
  }

  @Test
  void isActive_whenDisabled_returnsFalse() {
    AuditHmacProperties disabled = new AuditHmacProperties();
    disabled.setEnabled(false);
    AuditHmacService disabledService = new AuditHmacService(disabled);
    disabledService.init();

    assertFalse(disabledService.isActive());
  }

  @Test
  void isActive_whenEnabledButNoKeyFile_returnsFalse() {
    AuditHmacProperties noKey = new AuditHmacProperties();
    noKey.setEnabled(true);
    noKey.setHmacKeyFile(null);
    AuditHmacService noKeyService = new AuditHmacService(noKey);
    noKeyService.init();

    assertFalse(noKeyService.isActive());
  }

  @Test
  void getInstanceId_whenSet_returnsValue() {
    assertEquals("test-instance", service.getInstanceId());
  }

  @Test
  void getInstanceId_whenBlank_returnsNull() {
    properties.setInstanceId("   ");
    assertEquals(null, service.getInstanceId());
  }

  @Test
  void getInstanceId_whenNull_returnsNull() {
    properties.setInstanceId(null);
    assertNull(service.getInstanceId());
  }

  // -----------------------------------------------------------------------
  // buildCanonicalForm
  // -----------------------------------------------------------------------

  @Test
  void buildCanonicalForm_deterministicForSameEntry() {
    AuditLog entry = buildEntry(1L, "127.0.0.1", "admin-api");
    String c1 = service.buildCanonicalForm(entry);
    String c2 = service.buildCanonicalForm(entry);
    assertEquals(c1, c2, "Same entry must always produce the same canonical string");
  }

  @Test
  void buildCanonicalForm_nullFieldsRepresentedAsEmpty() {
    AuditLog entry =
        new AuditLog.Builder()
            .eventType(EventType.ADMIN_LOGIN)
            .eventAction("login")
            .eventStatus(EventStatus.SUCCESS)
            .apiName(ApiName.ADMIN_API)
            .build();
    // Explicit nulls: auditLogId, ipAddress, adminId, etc.
    String canonical = service.buildCanonicalForm(entry);
    assertNotNull(canonical);
    // The first field (auditLogId) is null → empty string → starts with "|"
    assertTrue(canonical.startsWith("|"), "Null auditLogId must yield empty leading field");
    assertFalse(canonical.contains("null"), "Canonical form must never contain the word 'null'");
  }

  @Test
  void buildCanonicalForm_timestampNormalisedToUtc() {
    OffsetDateTime plusTwo = OffsetDateTime.of(2026, 1, 15, 12, 0, 0, 0, ZoneOffset.ofHours(2));
    OffsetDateTime utcEquiv = OffsetDateTime.of(2026, 1, 15, 10, 0, 0, 0, ZoneOffset.UTC);

    AuditLog entryPlusTwo =
        new AuditLog.Builder()
            .eventType(EventType.ADMIN_LOGIN)
            .eventAction("login")
            .eventStatus(EventStatus.SUCCESS)
            .apiName(ApiName.ADMIN_API)
            .build();
    entryPlusTwo.setCreatedAt(plusTwo);

    AuditLog entryUtc =
        new AuditLog.Builder()
            .eventType(EventType.ADMIN_LOGIN)
            .eventAction("login")
            .eventStatus(EventStatus.SUCCESS)
            .apiName(ApiName.ADMIN_API)
            .build();
    entryUtc.setCreatedAt(utcEquiv);

    assertEquals(
        service.buildCanonicalForm(entryPlusTwo),
        service.buildCanonicalForm(entryUtc),
        "Same instant in different zones must produce the same canonical timestamp");
  }

  @Test
  void buildCanonicalForm_differentFieldValuesProduceDifferentStrings() {
    AuditLog e1 = buildEntry(1L, "10.0.0.1", "admin-api");
    AuditLog e2 = buildEntry(1L, "10.0.0.2", "admin-api");
    assertNotEquals(
        service.buildCanonicalForm(e1),
        service.buildCanonicalForm(e2),
        "Different IP addresses must produce different canonical forms");
  }

  // -----------------------------------------------------------------------
  // computeHmac(AuditLog)
  // -----------------------------------------------------------------------

  @Test
  void computeHmac_whenActive_returnsNonNullBase64() {
    AuditLog entry = buildEntry(1L, "127.0.0.1", "admin-api");
    String hmac = service.computeHmac(entry);
    assertNotNull(hmac);
    // Should decode without exception
    byte[] decoded = Base64.getDecoder().decode(hmac);
    assertEquals(32, decoded.length, "HMAC-SHA256 output must be 32 bytes");
  }

  @Test
  void computeHmac_isDeterministicForSameEntry() {
    AuditLog entry = buildEntry(42L, "192.168.1.1", "auth-api");
    assertEquals(service.computeHmac(entry), service.computeHmac(entry));
  }

  @Test
  void computeHmac_producesDistinctHmacsForDifferentEntries() {
    AuditLog e1 = buildEntry(1L, "10.0.0.1", "admin-api");
    AuditLog e2 = buildEntry(2L, "10.0.0.1", "admin-api");
    assertNotEquals(service.computeHmac(e1), service.computeHmac(e2));
  }

  @Test
  void computeHmac_whenNotActive_returnsNull() {
    AuditHmacProperties disabled = new AuditHmacProperties();
    disabled.setEnabled(false);
    AuditHmacService disabledService = new AuditHmacService(disabled);
    disabledService.init();

    assertNull(disabledService.computeHmac(buildEntry(1L, "127.0.0.1", "admin-api")));
  }

  // -----------------------------------------------------------------------
  // computeHmac(String) -- used by chain checkpoint service
  // -----------------------------------------------------------------------

  @Test
  void computeHmacString_whenActive_returnsBase64() {
    String hmac = service.computeHmac("some|chain|data");
    assertNotNull(hmac);
    assertEquals(32, Base64.getDecoder().decode(hmac).length);
  }

  @Test
  void computeHmacString_whenNotActive_returnsNull() {
    AuditHmacProperties disabled = new AuditHmacProperties();
    disabled.setEnabled(false);
    AuditHmacService disabledService = new AuditHmacService(disabled);
    disabledService.init();

    assertNull(disabledService.computeHmac("data"));
  }

  // -----------------------------------------------------------------------
  // verifyHmac -- tamper detection (the most critical tests)
  // -----------------------------------------------------------------------

  @Test
  void verifyHmac_signThenVerify_returnsTrue() {
    AuditLog entry = buildEntry(10L, "10.0.0.1", "admin-api");
    entry.setEntryHmac(service.computeHmac(entry));
    assertTrue(service.verifyHmac(entry), "Entry signed with the active key must verify as valid");
  }

  @Test
  void verifyHmac_whenIpAddressTampered_returnsFalse() {
    AuditLog entry = buildEntry(10L, "10.0.0.1", "admin-api");
    entry.setEntryHmac(service.computeHmac(entry));

    entry.setIpAddress("10.0.0.99"); // attacker changes the IP

    assertFalse(
        service.verifyHmac(entry),
        "A modified field must invalidate the HMAC -- tamper must be detected");
  }

  @Test
  void verifyHmac_whenEventTypeTampered_returnsFalse() {
    AuditLog entry = buildEntry(10L, "10.0.0.1", "admin-api");
    entry.setEntryHmac(service.computeHmac(entry));

    entry.setEventType(EventType.AUTH_ATTEMPT_CANCELLED); // attacker upgrades/downgrades event type

    assertFalse(service.verifyHmac(entry), "Changing event type must be detected as tampering");
  }

  @Test
  void verifyHmac_whenEntryHmacIsNull_returnsFalse() {
    AuditLog entry = buildEntry(10L, "10.0.0.1", "admin-api");
    // No HMAC set (unsigned entry)
    assertFalse(service.verifyHmac(entry), "Unsigned entry must not pass verification");
  }

  @Test
  void verifyHmac_whenHmacIsCorrupted_returnsFalse() {
    AuditLog entry = buildEntry(10L, "10.0.0.1", "admin-api");
    entry.setEntryHmac("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="); // garbage HMAC

    assertFalse(service.verifyHmac(entry), "Corrupted HMAC must fail verification");
  }

  @Test
  void verifyHmac_whenNotActive_returnsFalse() {
    AuditHmacProperties disabled = new AuditHmacProperties();
    disabled.setEnabled(false);
    AuditHmacService disabledService = new AuditHmacService(disabled);
    disabledService.init();

    AuditLog entry = buildEntry(10L, "10.0.0.1", "admin-api");
    entry.setEntryHmac("some-hmac");

    assertFalse(disabledService.verifyHmac(entry));
  }

  @Test
  void verifyHmac_subMicrosecondNanosecondsPrecision_roundTripPasses() {
    // Java's OffsetDateTime.now() can carry nanoseconds beyond microsecond precision.
    // PostgreSQL TIMESTAMPTZ silently truncates those extra nanoseconds.
    // This test ensures the HMAC round-trip is stable across that truncation.
    AuditLog entry = buildEntry(10L, "10.0.0.1", "admin-api");
    entry.setCreatedAt(
        OffsetDateTime.of(2026, 2, 19, 10, 0, 0, 123_456_789, ZoneOffset.UTC)); // nanos: 789 extra
    entry.setEntryHmac(service.computeHmac(entry));

    // Simulate PostgreSQL read-back: microsecond precision only (789 nanoseconds stripped)
    entry.setCreatedAt(OffsetDateTime.of(2026, 2, 19, 10, 0, 0, 123_456_000, ZoneOffset.UTC));

    assertTrue(
        service.verifyHmac(entry),
        "HMAC must survive PostgreSQL TIMESTAMPTZ nanosecond truncation (sub-microsecond digits)");
  }

  @Test
  void verifyHmac_whenPostgresRoundsNanos_verificationFails() {
    // If PostgreSQL ROUNDS (not truncates) sub-microsecond nanos, sign-time and verify-time
    // canonical forms differ. E.g. 123456789 nanos: we truncate to 123456, PG rounds to 123457.
    // This test proves that rounding would cause verification failure - root cause of ~50%
    // failures.
    AuditLog entry = buildEntry(10L, "10.0.0.1", "admin-api");
    entry.setCreatedAt(
        OffsetDateTime.of(
            2026, 2, 19, 10, 0, 0, 123_456_789, ZoneOffset.UTC)); // 789 would round up
    entry.setEntryHmac(service.computeHmac(entry));

    // Simulate PostgreSQL ROUNDING: 123456789 -> 123457000 (next microsecond)
    entry.setCreatedAt(OffsetDateTime.of(2026, 2, 19, 10, 0, 0, 123_457_000, ZoneOffset.UTC));

    assertFalse(
        service.verifyHmac(entry),
        "If PostgreSQL rounds sub-microsecond nanos, verification fails - this explains ~50% HMAC"
            + " failures when sign uses in-memory (truncated) and verify uses DB (rounded) value");
  }

  @Test
  void verifyHmac_keyMismatch_returnsFalse() throws Exception {
    // Sign with one key, verify with a different key
    AuditLog entry = buildEntry(10L, "10.0.0.1", "admin-api");
    entry.setEntryHmac(service.computeHmac(entry));

    // Build a second service with a different key
    byte[] otherKey = new byte[32];
    new SecureRandom().nextBytes(otherKey);
    Path otherKeyFile = tempDir.resolve("other-hmac.key");
    Files.writeString(
        otherKeyFile, Base64.getEncoder().encodeToString(otherKey), StandardCharsets.UTF_8);

    AuditHmacProperties otherProps = new AuditHmacProperties();
    otherProps.setEnabled(true);
    otherProps.setHmacKeyFile(otherKeyFile.toString());
    AuditHmacService otherService = new AuditHmacService(otherProps);
    otherService.init();

    assertFalse(
        otherService.verifyHmac(entry), "Entry signed with key A must not verify against key B");
  }

  // -----------------------------------------------------------------------
  // Helper
  // -----------------------------------------------------------------------

  private AuditLog buildEntry(Long id, String ip, String apiNameStr) {
    AuditLog entry =
        new AuditLog.Builder()
            .eventType(EventType.ADMIN_LOGIN)
            .eventAction("login")
            .eventStatus(EventStatus.SUCCESS)
            .apiName("auth-api".equalsIgnoreCase(apiNameStr) ? ApiName.AUTH_API : ApiName.ADMIN_API)
            .ipAddress(ip)
            .tenantId(1)
            .instanceId("test-instance")
            .build();
    entry.setAuditLogId(id);
    entry.setCreatedAt(OffsetDateTime.of(2026, 2, 19, 10, 0, 0, 0, ZoneOffset.UTC));
    return entry;
  }
}
