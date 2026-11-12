/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: TenantSelfRegisteredMigrationPostgresTest
 * Description: V28 marks existing self-registered tenants on real Postgres (#1206 step 7).
 */

package org.ezkey.integration.domain;

import static org.assertj.core.api.Assertions.assertThat;

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
 * Applies the real V28 script to a V27-shaped {@code ezkey_tenant} table and checks which tenants
 * are marked as self-registered.
 *
 * @since 2026
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("V28 self-registered tenant migration on Postgres (#1206 step 7)")
class TenantSelfRegisteredMigrationPostgresTest {

  @Container
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer("postgres:18-alpine").withDatabaseName("ezkey_it");

  @BeforeEach
  void createV27ShapedTable() throws SQLException {
    execute(
        """
        DROP TABLE IF EXISTS ezkey_tenant;
        CREATE TABLE ezkey_tenant (
            tenant_id INT PRIMARY KEY,
            tenant_name VARCHAR(100) NOT NULL,
            tenant_description TEXT,
            is_system_tenant BOOLEAN DEFAULT FALSE NOT NULL
        );
        INSERT INTO ezkey_tenant VALUES
          (1, 'Ezkey System', 'System tenant', TRUE),
          (2, 'eval-a1b2c3', 'Anonymous evaluator preview tenant', FALSE),
          (3, 'eval-d4e5f6', 'Anonymous evaluator preview tenant (My lab)', FALSE),
          (4, 'eval-named-by-hand', 'Customer pilot', FALSE),
          (5, 'acme', 'Anonymous evaluator preview tenant', FALSE);
        """);
  }

  @Test
  @DisplayName("Only tenants with both the eval- name and the default description are marked")
  void v28MarksSelfRegisteredTenants() throws Exception {
    execute(readMigration());

    assertMarked(1, false);
    assertMarked(2, true);
    assertMarked(3, true);
    assertMarked(4, false);
    assertMarked(5, false);
  }

  @Test
  @DisplayName("A tenant inserted after V28 defaults to not self-registered")
  void v28DefaultsToFalse() throws Exception {
    execute(readMigration());
    execute("INSERT INTO ezkey_tenant (tenant_id, tenant_name) VALUES (6, 'later')");

    assertMarked(6, false);
  }

  private static void assertMarked(int tenantId, boolean expected) throws SQLException {
    try (Connection connection = connect();
        Statement statement = connection.createStatement();
        ResultSet rs =
            statement.executeQuery(
                "SELECT self_registered FROM ezkey_tenant WHERE tenant_id = " + tenantId)) {
      assertThat(rs.next()).as("tenant %s exists", tenantId).isTrue();
      assertThat(rs.getBoolean(1)).as("tenant %s self_registered", tenantId).isEqualTo(expected);
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
        TenantSelfRegisteredMigrationPostgresTest.class
            .getClassLoader()
            .getResourceAsStream("db/migration/V28__tenant_self_registered.sql")) {
      assertThat(in).isNotNull();
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
