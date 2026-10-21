/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: TenantAliasHotReloadTest
 * Description: External mapping file takes effect on URL lookup without restart; corrupt keeps last.
 */

package org.ezkey.demo.acme.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.ezkey.demo.acme.config.AcmeProperties;
import org.ezkey.demo.acme.config.TenantAliasProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TenantAliasHotReloadTest {

  private static final String ALIAS = "0123456789abcdef";
  private static final String ALIAS_2 = "aabbccddeeff0011";

  @TempDir Path tempDir;

  private Path mappingFile;
  private TenantAliasProperties aliasProperties;
  private AtomicReference<Instant> now;
  private AccessCodeService service;

  @BeforeEach
  void setUp() throws Exception {
    mappingFile = tempDir.resolve("tenant-aliases.properties");
    Files.writeString(
        mappingFile, ALIAS + "|ikey-a|skey-a|Northwind Portal\n", StandardCharsets.UTF_8);

    aliasProperties = new TenantAliasProperties();
    aliasProperties.setFile(mappingFile.toString());
    aliasProperties.setReloadThrottleSeconds(5);

    now = new AtomicReference<>(Instant.parse("2026-10-21T12:00:00Z"));
    Clock clock =
        new Clock() {
          @Override
          public ZoneOffset getZone() {
            return ZoneOffset.UTC;
          }

          @Override
          public Clock withZone(java.time.ZoneId zone) {
            return this;
          }

          @Override
          public Instant instant() {
            return now.get();
          }
        };

    service = AccessCodeService.forTests(new AcmeProperties(), aliasProperties, clock);
    service.initialize();
  }

  @Test
  void shouldResolveAliasFromExternalFileAfterInit() {
    assertThat(service.findSlotIdByCode(ALIAS)).isPresent();
    String slotId = service.findSlotIdByCode(ALIAS).orElseThrow();
    assertThat(slotId).startsWith("ext:");
    assertThat(service.getLabel(slotId)).isEqualTo("Northwind Portal");
    assertThat(service.resolveCredentials(slotId).integrationKey()).isEqualTo("ikey-a");
  }

  @Test
  void shouldPickUpAppendedLineOnNextAccessAfterThrottle() throws Exception {
    assertThat(service.findSlotIdByCode(ALIAS_2)).isEmpty();

    // Advance past throttle, then rewrite file with a second alias (mtime changes).
    now.set(now.get().plusSeconds(6));
    Files.writeString(
        mappingFile,
        ALIAS + "|ikey-a|skey-a|Northwind Portal\n" + ALIAS_2 + "|ikey-b|skey-b|Partner Demo\n",
        StandardCharsets.UTF_8);
    bumpMtime(mappingFile);

    now.set(now.get().plusSeconds(6));
    assertThat(service.findSlotIdByCode(ALIAS_2)).isPresent();
    assertThat(service.getLabel(service.findSlotIdByCode(ALIAS_2).orElseThrow()))
        .isEqualTo("Partner Demo");
  }

  @Test
  void shouldKeepLastValidMappingsWhenFileBecomesUnusable() throws Exception {
    assertThat(service.findSlotIdByCode(ALIAS)).isPresent();

    now.set(now.get().plusSeconds(6));
    Files.writeString(mappingFile, "totally-invalid|line\nbad\n", StandardCharsets.UTF_8);
    bumpMtime(mappingFile);
    now.set(now.get().plusSeconds(6));

    assertThat(service.findSlotIdByCode(ALIAS)).isPresent();
    assertThat(service.getLabel(service.findSlotIdByCode(ALIAS).orElseThrow()))
        .isEqualTo("Northwind Portal");
  }

  private static void bumpMtime(Path path) throws Exception {
    FileTime current = Files.getLastModifiedTime(path);
    Files.setLastModifiedTime(path, FileTime.fromMillis(current.toMillis() + 2_000L));
  }

  @Test
  void shouldNotRereadWithinThrottleWindowEvenIfFileChanged() throws Exception {
    assertThat(service.findSlotIdByCode(ALIAS)).isPresent();

    // Change file but stay inside throttle window (mtime check skipped entirely).
    Files.writeString(
        mappingFile, ALIAS_2 + "|ikey-b|skey-b|Partner Demo\n", StandardCharsets.UTF_8);
    now.set(now.get().plusSeconds(1));

    assertThat(service.findSlotIdByCode(ALIAS_2)).isEmpty();
    assertThat(service.findSlotIdByCode(ALIAS)).isPresent();
  }

  @Test
  void shouldTreatUnknownAndMalformedAliasAsEmpty() {
    assertThat(service.findSlotIdByCode("ffffffffffffffff")).isEmpty();
    assertThat(service.findSlotIdByCode("NOT-HEX!!!")).isEmpty();
    assertThat(service.findSlotIdByCode("")).isEmpty();
  }
}
