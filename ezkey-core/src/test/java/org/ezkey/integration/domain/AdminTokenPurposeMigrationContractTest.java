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
 * {@code ezkey_admin_tokens.token_purpose}, and the CHECK must allow nothing else. Catches a
 * missing purpose (the V18→mint gap, ACTIVATION in V26 / #1122) and a retired purpose left in the
 * CHECK (EVALUATOR_TEMP, removed in V31 / #1368).
 *
 * @since 2026
 */
class AdminTokenPurposeMigrationContractTest {

  private static final String LATEST = "db/migration/V31__retire_evaluator_self_registration.sql";

  private static final Pattern CHECK_IN_CLAUSE =
      Pattern.compile(
          "chk_admin_tokens_token_purpose\\s+CHECK\\s*\\(\\s*token_purpose\\s+IN\\s*\\(([^)]+)\\)",
          Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

  @Test
  @DisplayName("V31 CHECK allows exactly the AdminTokenPurpose enum constants")
  void latestCheckAllowsExactlyEnumPurposes() throws IOException {
    String sql = readClasspathResource(LATEST);

    Matcher matcher = CHECK_IN_CLAUSE.matcher(sql);
    assertThat(matcher.find())
        .as("V31 must redefine chk_admin_tokens_token_purpose with an IN (...) list")
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
    assertThat(allowed).containsExactlyInAnyOrder("SESSION", "RECOVERY", "ACTIVATION");
    assertThat(sql)
        .containsIgnoringCase("DROP CONSTRAINT IF EXISTS chk_admin_tokens_token_purpose");
  }

  @Test
  @DisplayName("V31 deletes EVALUATOR_TEMP rows and drops the V24 one-per-admin index")
  void v31RetiresEvaluatorTempStorage() throws IOException {
    String sql = readClasspathResource(LATEST);

    assertThat(sql)
        .containsIgnoringCase("DELETE FROM ezkey_admin_tokens")
        .contains("token_purpose = 'EVALUATOR_TEMP'")
        .containsIgnoringCase("DROP INDEX IF EXISTS uq_admin_tokens_one_evaluator_temp_per_admin");
  }

  private static String readClasspathResource(String path) throws IOException {
    try (InputStream in =
        AdminTokenPurposeMigrationContractTest.class.getClassLoader().getResourceAsStream(path)) {
      assertThat(in).as("classpath resource %s", path).isNotNull();
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
