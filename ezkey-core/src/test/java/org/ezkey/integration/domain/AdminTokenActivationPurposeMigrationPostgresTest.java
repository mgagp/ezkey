/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AdminTokenActivationPurposeMigrationPostgresTest
 * Description: V26 reclassifies legacy activation-code rows on real Postgres (#1122).
 */

package org.ezkey.integration.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Applies the real V26 script to V25-shaped {@code ezkey_admin} / {@code ezkey_admin_tokens} tables
 * and checks which rows are converted to {@code ACTIVATION}, revoked, or left alone.
 *
 * @since 2026
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("V26 activation purpose migration on Postgres (#1122)")
class AdminTokenActivationPurposeMigrationPostgresTest {

  @Container
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer("postgres:18-alpine").withDatabaseName("ezkey_it");

  @BeforeEach
  void createV25ShapedTables() throws SQLException {
    execute(
        """
        DROP TABLE IF EXISTS ezkey_admin_tokens;
        DROP TABLE IF EXISTS ezkey_admin;
        CREATE TABLE ezkey_admin (
            admin_id INT PRIMARY KEY,
            enrollment_id INT,
            lifecycle_status VARCHAR(32) NOT NULL
        );
        CREATE TABLE ezkey_admin_tokens (
            token_id INT PRIMARY KEY,
            admin_id INT NOT NULL REFERENCES ezkey_admin(admin_id),
            token_purpose VARCHAR(20) NOT NULL DEFAULT 'SESSION',
            last_used_at TIMESTAMPTZ,
            active BOOLEAN NOT NULL DEFAULT TRUE,
            CONSTRAINT chk_admin_tokens_token_purpose
              CHECK (token_purpose IN ('SESSION', 'RECOVERY', 'EVALUATOR_TEMP'))
        );
        INSERT INTO ezkey_admin VALUES
          (1, NULL, 'PENDING_ACTIVATION'),
          (2, NULL, 'PENDING_ACTIVATION'),
          (3, 30, 'ACTIVE'),
          (4, 40, 'ACTIVE');
        INSERT INTO ezkey_admin_tokens (token_id, admin_id, token_purpose, last_used_at, active)
        VALUES
          (11, 1, 'SESSION', NULL, TRUE),
          (12, 1, 'SESSION', NULL, FALSE),
          (21, 2, 'SESSION', now(), TRUE),
          (31, 3, 'SESSION', now(), TRUE),
          (32, 3, 'RECOVERY', NULL, TRUE),
          (41, 4, 'EVALUATOR_TEMP', now(), TRUE);
        """);
  }

  @Test
  @DisplayName("Unused pending invite becomes ACTIVATION; bearer-used invite is revoked")
  void v26ReclassifiesLegacyActivationRows() throws Exception {
    execute(readMigration());

    assertRow(11, "ACTIVATION", true);
    assertRow(12, "SESSION", false);
    assertRow(21, "SESSION", false);
    assertRow(31, "SESSION", true);
    assertRow(32, "RECOVERY", true);
    assertRow(41, "EVALUATOR_TEMP", true);
  }

  @Test
  @DisplayName("CHECK accepts ACTIVATION after V26 and still rejects unknown purposes")
  void v26CheckAcceptsActivation() throws Exception {
    execute(readMigration());

    execute(
        "INSERT INTO ezkey_admin_tokens (token_id, admin_id, token_purpose) VALUES (99, 1,"
            + " 'ACTIVATION')");
    assertRow(99, "ACTIVATION", true);
    assertThatThrownBy(() ->
            execute(
                "INSERT INTO ezkey_admin_tokens (token_id, admin_id, token_purpose) VALUES"
                    + " (100, 1, 'INVITE')"))
        .isInstanceOf(SQLException.class)
        .hasMessageContaining("chk_admin_tokens_token_purpose");
  }

  private static void assertRow(int tokenId, String purpose, boolean active) throws SQLException {
    try (Connection connection = connect();
        Statement statement = connection.createStatement();
        ResultSet rs =
            statement.executeQuery(
                "SELECT token_purpose, active FROM ezkey_admin_tokens WHERE token_id = "
                    + tokenId)) {
      assertThat(rs.next()).as("token %s exists", tokenId).isTrue();
      assertThat(rs.getString(1)).as("token %s purpose", tokenId).isEqualTo(purpose);
      assertThat(rs.getBoolean(2)).as("token %s active", tokenId).isEqualTo(active);
    }
  }

  private static void execute(String sql) throws SQLException {
    try (Connection connection = connect();
        Statement statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }

  private static Connection connect() throws SQLException {
    return DriverManager.getConnection(
        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
  }

  private static String readMigration() throws IOException {
    try (InputStream in =
        AdminTokenActivationPurposeMigrationPostgresTest.class
            .getClassLoader()
            .getResourceAsStream("db/migration/V26__admin_token_activation_purpose.sql")) {
      assertThat(in).isNotNull();
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
