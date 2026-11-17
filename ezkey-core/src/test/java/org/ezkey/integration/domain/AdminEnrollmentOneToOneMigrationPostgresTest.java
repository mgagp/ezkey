/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
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
 * V30 on a real PostgreSQL (#1313): one administrator, one enrollment, and no contact email on
 * administrator enrollments (tests T7 and T10 of the issue).
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("V30 admin enrollment 1:1 and contact email cleanup on Postgres (#1313)")
class AdminEnrollmentOneToOneMigrationPostgresTest {

  @Container
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer("postgres:18-alpine").withDatabaseName("ezkey_it");

  @BeforeEach
  void createV29ShapedTables() throws SQLException {
    execute(
        """
        DROP TABLE IF EXISTS ezkey_admin;
        DROP TABLE IF EXISTS ezkey_enrollment;
        DROP TABLE IF EXISTS ezkey_integration;
        CREATE TABLE ezkey_integration (
            integration_id INT PRIMARY KEY,
            is_system_integration BOOLEAN DEFAULT FALSE
        );
        CREATE TABLE ezkey_enrollment (
            enrollment_id INT PRIMARY KEY,
            integration_id INT NOT NULL REFERENCES ezkey_integration(integration_id),
            contact_email VARCHAR(255)
        );
        CREATE TABLE ezkey_admin (
            admin_id INT PRIMARY KEY,
            enrollment_id INT REFERENCES ezkey_enrollment(enrollment_id)
        );
        INSERT INTO ezkey_integration VALUES (1, TRUE), (2, FALSE);
        INSERT INTO ezkey_enrollment VALUES
          (10, 1, 'admin-copy@example.com'),
          (11, 1, NULL),
          (20, 2, 'end-user@example.com');
        INSERT INTO ezkey_admin VALUES (100, 10), (101, 11), (102, NULL), (103, NULL);
        """);
  }

  @Test
  @DisplayName("Contact email is cleared on system-integration enrollments only")
  void clearsContactEmailOnAdminEnrollmentsOnly() throws Exception {
    execute(readMigration());

    assertThat(contactEmail(10)).isNull();
    assertThat(contactEmail(11)).isNull();
    assertThat(contactEmail(20)).isEqualTo("end-user@example.com");
  }

  @Test
  @DisplayName("Running the cleanup again changes nothing")
  void cleanupIsIdempotent() throws Exception {
    execute(readMigration());
    execute(
        "UPDATE ezkey_enrollment e SET contact_email = NULL FROM ezkey_integration i"
            + " WHERE e.integration_id = i.integration_id AND i.is_system_integration = TRUE"
            + " AND e.contact_email IS NOT NULL");

    assertThat(contactEmail(20)).isEqualTo("end-user@example.com");
    assertThat(contactEmail(10)).isNull();
  }

  @Test
  @DisplayName("A second administrator on a linked enrollment is refused after V30")
  void uniqueIndexRefusesSharedEnrollment() throws Exception {
    execute(readMigration());

    assertThatThrownBy(() -> execute("INSERT INTO ezkey_admin VALUES (104, 10)"))
        .isInstanceOf(SQLException.class)
        .hasMessageContaining("uq_admin_enrollment_id");
  }

  @Test
  @DisplayName("Several administrators pending activation (no enrollment) are accepted")
  void pendingAdministratorsWithoutEnrollmentAreAccepted() throws Exception {
    execute(readMigration());
    execute("INSERT INTO ezkey_admin VALUES (105, NULL)");

    assertThat(count("SELECT COUNT(*) FROM ezkey_admin WHERE enrollment_id IS NULL")).isEqualTo(3);
  }

  @Test
  @DisplayName("The migration fails, and changes nothing, when two administrators share one")
  void failsOnExistingDuplicate() throws Exception {
    execute("INSERT INTO ezkey_admin VALUES (104, 10)");

    assertThatThrownBy(() -> execute(readMigration()))
        .isInstanceOf(SQLException.class)
        .hasMessageContaining("two administrators share an enrollment");
    assertThat(contactEmail(10)).isEqualTo("admin-copy@example.com");
    assertThat(count("SELECT COUNT(*) FROM pg_indexes WHERE indexname = 'uq_admin_enrollment_id'"))
        .isZero();
  }

  private static String contactEmail(int enrollmentId) throws SQLException {
    try (Connection connection = connect();
        Statement statement = connection.createStatement();
        ResultSet rs =
            statement.executeQuery(
                "SELECT contact_email FROM ezkey_enrollment WHERE enrollment_id = "
                    + enrollmentId)) {
      assertThat(rs.next()).as("enrollment %s exists", enrollmentId).isTrue();
      return rs.getString(1);
    }
  }

  private static long count(String sql) throws SQLException {
    try (Connection connection = connect();
        Statement statement = connection.createStatement();
        ResultSet rs = statement.executeQuery(sql)) {
      rs.next();
      return rs.getLong(1);
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
        AdminEnrollmentOneToOneMigrationPostgresTest.class
            .getClassLoader()
            .getResourceAsStream(
                "db/migration/V30__admin_enrollment_one_to_one_and_contact_email.sql")) {
      assertThat(in).isNotNull();
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
