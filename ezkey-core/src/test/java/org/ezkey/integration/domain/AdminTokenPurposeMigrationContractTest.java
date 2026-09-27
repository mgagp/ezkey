/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AdminTokenPurposeMigrationContractTest
 * Description: Ensures Flyway CHECK for token_purpose matches AdminTokenPurpose enum.
 */

package org.ezkey.integration.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Contract: Java {@link AdminTokenPurpose} values must be allowed by the latest Flyway CHECK on
 * {@code ezkey_admin_tokens.token_purpose}. Catches the V18→mint gap (EVALUATOR_TEMP missing).
 *
 * @since 2026
 */
class AdminTokenPurposeMigrationContractTest {

  private static final Pattern CHECK_IN_CLAUSE =
      Pattern.compile(
          "chk_admin_tokens_token_purpose\\s+CHECK\\s*\\(\\s*token_purpose\\s+IN\\s*\\(([^)]+)\\)",
          Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

  @Test
  @DisplayName("V23 CHECK allows every AdminTokenPurpose enum constant including EVALUATOR_TEMP")
  void v23CheckAllowsAllEnumPurposes() throws IOException {
    String sql =
        readClasspathResource("db/migration/V23__widen_admin_token_purpose_for_evaluator_temp.sql");

    Matcher matcher = CHECK_IN_CLAUSE.matcher(sql);
    assertThat(matcher.find())
        .as("V23 must redefine chk_admin_tokens_token_purpose with an IN (...) list")
        .isTrue();

    Set<String> allowed =
        Arrays.stream(matcher.group(1).split(","))
            .map(String::trim)
            .map(s -> s.replace("'", ""))
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toSet());

    Set<String> enumNames =
        Arrays.stream(AdminTokenPurpose.values()).map(Enum::name).collect(Collectors.toSet());

    assertThat(allowed).containsExactlyInAnyOrderElementsOf(enumNames);
    assertThat(allowed).contains("EVALUATOR_TEMP", "SESSION", "RECOVERY");
    assertThat(sql)
        .containsIgnoringCase("DROP CONSTRAINT IF EXISTS chk_admin_tokens_token_purpose");
  }

  @Test
  @DisplayName(
      "V24 partial unique index enforces one EVALUATOR_TEMP per admin_id without active filter")
  void v24PartialUniqueIndexOneEvaluatorTempPerAdmin() throws IOException {
    String sql =
        readClasspathResource("db/migration/V24__uq_admin_tokens_one_evaluator_temp_per_admin.sql");

    assertThat(sql)
        .containsIgnoringCase("uq_admin_tokens_one_evaluator_temp_per_admin")
        .containsIgnoringCase("CREATE UNIQUE INDEX")
        .contains("ON ezkey_admin_tokens (admin_id)")
        .containsIgnoringCase("WHERE token_purpose = 'EVALUATOR_TEMP'");

    // One-shot includes deactivated rows — must not restrict to active = true.
    assertThat(sql.toLowerCase()).doesNotContain("active = true");
    assertThat(sql.toLowerCase()).doesNotContain("active=true");
  }

  private static String readClasspathResource(String path) throws IOException {
    try (InputStream in =
        AdminTokenPurposeMigrationContractTest.class.getClassLoader().getResourceAsStream(path)) {
      assertThat(in).as("classpath resource %s", path).isNotNull();
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
