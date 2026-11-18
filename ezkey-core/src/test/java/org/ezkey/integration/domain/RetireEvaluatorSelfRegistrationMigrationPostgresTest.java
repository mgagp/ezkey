/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: RetireEvaluatorSelfRegistrationMigrationPostgresTest
 * Description: V31 retires evaluator self-registration storage on real Postgres (#1368).
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
 * Applies the real V31 script to V30-shaped {@code ezkey_tenant} and {@code ezkey_admin_tokens}
 * tables (only the columns V31 touches).
 *
 * @since 2026
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("V31 retire evaluator self-registration on Postgres (#1368)")
class RetireEvaluatorSelfRegistrationMigrationPostgresTest {

  private static final String MIGRATION =
      "db/migration/V31__retire_evaluator_self_registration.sql";

  @Container
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer("postgres:18-alpine").withDatabaseName("ezkey_it");

  @BeforeEach
  void createV30ShapedTables() throws SQLException {
    execute(
        """
        DROP TABLE IF EXISTS ezkey_admin_tokens;
        DROP TABLE IF EXISTS ezkey_tenant;
        DROP TABLE IF EXISTS ezkey_shedlock;
        CREATE TABLE ezkey_shedlock (name VARCHAR(64) PRIMARY KEY);
        INSERT INTO ezkey_shedlock VALUES ('EVALUATOR_TEMP_SESSION_EXPIRY'), ('OTHER_JOB');
        CREATE TABLE ezkey_tenant (
            tenant_id INT PRIMARY KEY,
            tenant_name VARCHAR(100) NOT NULL,
            active BOOLEAN DEFAULT TRUE NOT NULL,
            self_registered BOOLEAN DEFAULT FALSE NOT NULL
        );
        CREATE TABLE ezkey_admin_tokens (
            token_id INT PRIMARY KEY,
            admin_id INT NOT NULL,
            token_purpose VARCHAR(20) NOT NULL,
            active BOOLEAN NOT NULL DEFAULT TRUE,
            CONSTRAINT chk_admin_tokens_token_purpose
              CHECK (token_purpose IN ('SESSION', 'RECOVERY', 'EVALUATOR_TEMP', 'ACTIVATION'))
        );
        CREATE UNIQUE INDEX uq_admin_tokens_one_evaluator_temp_per_admin
            ON ezkey_admin_tokens (admin_id) WHERE token_purpose = 'EVALUATOR_TEMP';
        INSERT INTO ezkey_tenant VALUES
          (1, 'Ezkey System', TRUE, FALSE),
          (2, 'acme', TRUE, FALSE),
          (3, 'eval-retired', FALSE, TRUE);
        INSERT INTO ezkey_admin_tokens VALUES
          (11, 1, 'SESSION', TRUE),
          (12, 1, 'RECOVERY', FALSE),
          (13, 2, 'ACTIVATION', TRUE),
          (21, 3, 'EVALUATOR_TEMP', TRUE),
          (22, 4, 'EVALUATOR_TEMP', FALSE);
        """);
  }

  @Test
  @DisplayName("EVALUATOR_TEMP rows, index and self_registered go; other tokens stay")
  void v31RemovesRetiredStorage() throws Exception {
    execute(readMigration());

    assertThat(queryInt("SELECT COUNT(*) FROM ezkey_admin_tokens")).isEqualTo(3);
    assertThat(
            queryInt(
                "SELECT COUNT(*) FROM ezkey_admin_tokens WHERE token_purpose = 'EVALUATOR_TEMP'"))
        .isZero();
    assertThat(
            queryInt(
                "SELECT COUNT(*) FROM pg_indexes"
                    + " WHERE indexname = 'uq_admin_tokens_one_evaluator_temp_per_admin'"))
        .isZero();
    assertThat(
            queryInt(
                "SELECT COUNT(*) FROM information_schema.columns"
                    + " WHERE table_name = 'ezkey_tenant' AND column_name = 'self_registered'"))
        .isZero();
    assertThat(queryInt("SELECT COUNT(*) FROM ezkey_shedlock")).isEqualTo(1);
    // A deactivated self-registered tenant does not block and is kept as a row.
    assertThat(queryInt("SELECT COUNT(*) FROM ezkey_tenant")).isEqualTo(3);
  }

  @Test
  @DisplayName("The CHECK refuses EVALUATOR_TEMP after V31 and still accepts ACTIVATION")
  void v31CheckRefusesEvaluatorTemp() throws Exception {
    execute(readMigration());

    assertThatThrownBy(
            () -> execute("INSERT INTO ezkey_admin_tokens VALUES (31, 5, 'EVALUATOR_TEMP', TRUE)"))
        .isInstanceOf(SQLException.class)
        .hasMessageContaining("chk_admin_tokens_token_purpose");
    execute("INSERT INTO ezkey_admin_tokens VALUES (32, 5, 'ACTIVATION', TRUE)");
  }

  @Test
  @DisplayName("An active self-registered tenant stops the migration and nothing changes")
  void v31StopsOnActiveSelfRegisteredTenant() throws Exception {
    execute("UPDATE ezkey_tenant SET active = TRUE WHERE tenant_id = 3");

    assertThatThrownBy(() -> executeInTransaction(readMigration()))
        .isInstanceOf(SQLException.class)
        .hasMessageContaining("still marked self_registered");

    assertThat(
            queryInt(
                "SELECT COUNT(*) FROM ezkey_admin_tokens WHERE token_purpose = 'EVALUATOR_TEMP'"))
        .isEqualTo(2);
    assertThat(queryInt("SELECT COUNT(*) FROM ezkey_tenant WHERE self_registered = TRUE"))
        .isEqualTo(1);
  }

  @Test
  @DisplayName("Clearing the marker on a kept tenant lets the migration run")
  void v31RunsOnceTheOperatorClearsTheMarker() throws Exception {
    execute("UPDATE ezkey_tenant SET active = TRUE WHERE tenant_id = 3");
    execute("UPDATE ezkey_tenant SET self_registered = FALSE WHERE tenant_id = 3");

    execute(readMigration());

    assertThat(queryInt("SELECT COUNT(*) FROM ezkey_tenant WHERE active = TRUE")).isEqualTo(3);
  }

  private static int queryInt(String sql) throws SQLException {
    try (Connection connection = connect();
        Statement statement = connection.createStatement();
        ResultSet rs = statement.executeQuery(sql)) {
      assertThat(rs.next()).isTrue();
      return rs.getInt(1);
    }
  }

  private static void execute(String sql) throws SQLException {
    try (Connection connection = connect();
        Statement statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }

  /** Flyway runs a Postgres migration in one transaction; mirror that for the failure case. */
  private static void executeInTransaction(String sql) throws SQLException {
    try (Connection connection = connect()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        statement.execute(sql);
        connection.commit();
      } catch (SQLException e) {
        connection.rollback();
        throw e;
      }
    }
  }

  private static Connection connect() throws SQLException {
    return DriverManager.getConnection(
        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
  }

  private static String readMigration() throws IOException {
    try (InputStream in =
        RetireEvaluatorSelfRegistrationMigrationPostgresTest.class
            .getClassLoader()
            .getResourceAsStream(MIGRATION)) {
      assertThat(in).isNotNull();
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
