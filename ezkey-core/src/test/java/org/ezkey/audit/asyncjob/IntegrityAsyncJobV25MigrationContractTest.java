/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.audit.asyncjob;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Contract: V25 CHECK clauses match Java enums and enforce Patrick's OQ4 A′ invariants.
 *
 * @since 2026
 */
class IntegrityAsyncJobV25MigrationContractTest {

  @Test
  @DisplayName("V25 trigger CHECK matches IntegrityAsyncJobTrigger enum")
  void v25TriggerCheckMatchesEnum() throws IOException {
    String sql = readV25();
    assertThat(sql)
        .contains("\"trigger\" VARCHAR(16) NOT NULL DEFAULT 'MANUAL'")
        .contains("chk_integrity_async_job_trigger")
        .contains("\"trigger\" IN ('MANUAL', 'AUTO', 'NIGHTLY')");
    Set<String> enumNames =
        Arrays.stream(IntegrityAsyncJobTrigger.values())
            .map(Enum::name)
            .collect(Collectors.toSet());
    assertThat(enumNames).containsExactlyInAnyOrder("MANUAL", "AUTO", "NIGHTLY");
  }

  @Test
  @DisplayName("V25 chain_status CHECK matches IntegrityChainStatus enum")
  void v25ChainStatusCheckMatchesEnum() throws IOException {
    String sql = readV25();
    assertThat(sql).contains("chk_integrity_async_job_chain_status");
    for (IntegrityChainStatus status : IntegrityChainStatus.values()) {
      assertThat(sql).contains("'" + status.name() + "'");
    }
  }

  @Test
  @DisplayName("V25 nightly never RUNNING and admin-null iff NIGHTLY")
  void v25NightlyInvariants() throws IOException {
    String sql = readV25();
    assertThat(sql)
        .contains("chk_integrity_async_job_nightly_terminal")
        .contains("\"trigger\" <> 'NIGHTLY' OR status IN ('SUCCEEDED', 'FAILED')")
        .contains("chk_integrity_async_job_nightly_admin")
        .contains("(\"trigger\" = 'NIGHTLY') = (started_by_admin_id IS NULL)")
        .contains("ALTER COLUMN started_by_admin_id DROP NOT NULL");
  }

  @Test
  @DisplayName("V25 one-shot heals INTERRUPTED without abandoned_at")
  void v25HealsInterruptedOrphans() throws IOException {
    String sql = readV25();
    assertThat(sql)
        .containsIgnoringCase("UPDATE ezkey_integrity_async_job")
        .contains("status = 'INTERRUPTED'")
        .contains("abandoned_at IS NULL")
        .contains("COALESCE(finished_at, CURRENT_TIMESTAMP)");
  }

  private static String readV25() throws IOException {
    try (InputStream in =
        IntegrityAsyncJobV25MigrationContractTest.class
            .getClassLoader()
            .getResourceAsStream("db/migration/V25__integrity_async_job_verdict_trigger.sql")) {
      assertThat(in).as("V25 migration on classpath").isNotNull();
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
