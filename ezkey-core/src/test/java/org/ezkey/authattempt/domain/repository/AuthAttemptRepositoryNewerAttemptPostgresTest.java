/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AuthAttemptRepositoryNewerAttemptPostgresTest
 * Description: Real SQL coverage for findNewerAttemptByEnrollmentId page-size-1 + id tie-break (#727).
 */

package org.ezkey.authattempt.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Exercises the real JPQL/SQL for {@link AuthAttemptRepository#findNewerAttemptByEnrollmentId}
 * against Postgres (#727).
 *
 * @since 2026
 */
@SpringBootTest(classes = AuthAttemptRepositoryNewerAttemptPostgresTest.TestConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("AuthAttemptRepository findNewerAttemptByEnrollmentId Postgres (#727)")
class AuthAttemptRepositoryNewerAttemptPostgresTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer("postgres:18-alpine").withDatabaseName("ezkey_auth_attempt_it");

  @DynamicPropertySource
  static void datasourceProps(DynamicPropertyRegistry registry) {
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    registry.add("spring.flyway.enabled", () -> "false");
    registry.add("spring.sql.init.mode", () -> "always");
    registry.add(
        "spring.sql.init.schema-locations", () -> "classpath:auth-attempt-newer-pg-schema.sql");
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @EntityScan(basePackageClasses = AuthAttempt.class)
  @EnableJpaRepositories(basePackageClasses = AuthAttemptRepository.class)
  static class TestConfiguration {}

  @Autowired private AuthAttemptRepository authAttemptRepository;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void resetRows() {
    jdbc.execute("TRUNCATE TABLE ezkey_auth_attempt RESTART IDENTITY");
  }

  @Test
  @DisplayName("When several newer rows share createdAt, returns highest authAttemptId only")
  void findNewerAttempt_sameCreatedAt_returnsHighestIdViaPageSizeOne() {
    OffsetDateTime base = OffsetDateTime.of(2026, 10, 8, 12, 0, 0, 0, ZoneOffset.UTC);
    OffsetDateTime newer = base.plusNanos(500_000_000);

    insertAttempt(10, 42, base, "proof-base");
    insertAttempt(11, 42, newer, "proof-a");
    insertAttempt(12, 42, newer, "proof-b");

    Optional<AuthAttempt> result = authAttemptRepository.findNewerAttemptByEnrollmentId(42, base);

    assertThat(result).isPresent();
    assertThat(result.get().getAuthAttemptId()).isEqualTo(12);
  }

  private void insertAttempt(
      int authAttemptId, int enrollmentId, OffsetDateTime createdAt, String proofToken) {
    jdbc.update(
        """
        INSERT INTO ezkey_auth_attempt (
          auth_attempt_id, enrollment_id, auth_attempt_status, auth_attempt_proof_token,
          created_at, expires_at)
        VALUES (?, ?, 'PENDING', ?, ?, ?)
        """,
        authAttemptId,
        enrollmentId,
        proofToken,
        createdAt,
        createdAt.plusMinutes(5));
  }
}
