/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AuthAttemptAlertPersistedPostgresTest
 * Description: The real AlertService persists the enrollment alert after commit.
 */

package org.ezkey.authattempt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import org.ezkey.alert.domain.entity.Alert;
import org.ezkey.alert.repository.AlertRepository;
import org.ezkey.alert.service.AlertService;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.authattempt.domain.AuthAttemptRespondRequest;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.ezkey.authattempt.domain.repository.AuthAttemptRepository;
import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.signature.SignatureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Postgres proof that the enrollment alert is stored after the status commit, using the real {@link
 * AlertService}. Alert rows are re-read on a new JDBC connection, not the persistence context that
 * wrote them.
 *
 * @since 2026
 */
@SpringBootTest(classes = AuthAttemptAlertPersistedPostgresTest.TestConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
class AuthAttemptAlertPersistedPostgresTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer("postgres:18-alpine")
          .withDatabaseName("ezkey_auth_attempt_alert_persisted");

  @DynamicPropertySource
  static void datasourceProps(DynamicPropertyRegistry registry) {
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    registry.add("spring.flyway.enabled", () -> "false");
    registry.add("spring.sql.init.mode", () -> "always");
    registry.add(
        "spring.sql.init.schema-locations",
        () -> "classpath:auth-attempt-newer-pg-schema.sql,classpath:ezkey-alert-min-pg-schema.sql");
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @EntityScan(basePackageClasses = {AuthAttempt.class, Alert.class})
  @EnableJpaRepositories(basePackageClasses = {AuthAttemptRepository.class, AlertRepository.class})
  static class TestConfiguration {

    @Bean
    AuditLogService auditLogService() {
      return mock(AuditLogService.class);
    }

    @Bean
    AlertService alertService(AlertRepository alertRepository, AuditLogService auditLogService) {
      return new AlertService(alertRepository, auditLogService);
    }

    @Bean
    AuthAttemptEnrollmentAlert enrollmentAlert(
        AlertService alertService, PlatformTransactionManager transactionManager) {
      return new AuthAttemptEnrollmentAlert(alertService, transactionManager);
    }

    @Bean
    AuthAttemptTxHelper authAttemptTxHelper(
        AuthAttemptRepository authAttemptRepository, AuthAttemptEnrollmentAlert enrollmentAlert) {
      return new AuthAttemptTxHelper(authAttemptRepository, enrollmentAlert);
    }

    @Bean
    EnrollmentRepository enrollmentRepository() {
      Enrollment enrollment = new Enrollment();
      enrollment.setEnrollmentId(7);
      enrollment.setIntegrationId(3);
      enrollment.setActive(true);
      enrollment.setStatus(EnrollmentStatus.VERIFIED);
      enrollment.setAuthAttemptChallengeRequired(false);
      enrollment.setDevicePublicKey("device-key");
      enrollment.setIntegrationPrivateKey("integration-key");
      EnrollmentRepository repository = mock(EnrollmentRepository.class);
      when(repository.findById(7)).thenReturn(Optional.of(enrollment));
      return repository;
    }

    @Bean
    SignatureService signatureService() {
      SignatureService signatureService = mock(SignatureService.class);
      when(signatureService.validateSignature(any(), any(), any())).thenReturn(true);
      when(signatureService.signIntegrationPayload(any(), any())).thenReturn("signed");
      return signatureService;
    }

    @Bean
    @SuppressWarnings("unchecked")
    ObjectProvider<AuthRespondOwnershipRateLimiter> ownershipRateLimiter() {
      ObjectProvider<AuthRespondOwnershipRateLimiter> provider = mock(ObjectProvider.class);
      when(provider.getIfAvailable()).thenReturn(null);
      return provider;
    }

    @Bean
    AuthAttemptRespondService authAttemptRespondService(
        AuthAttemptRepository authAttemptRepository,
        EnrollmentRepository enrollmentRepository,
        SignatureService signatureService,
        AuthAttemptTxHelper authAttemptTxHelper,
        AuditLogService auditLogService,
        ObjectProvider<AuthRespondOwnershipRateLimiter> ownershipRateLimiter,
        AuthAttemptEnrollmentAlert enrollmentAlert) {
      return new AuthAttemptRespondService(
          authAttemptRepository,
          enrollmentRepository,
          signatureService,
          authAttemptTxHelper,
          auditLogService,
          ownershipRateLimiter,
          enrollmentAlert);
    }
  }

  @Autowired private AuthAttemptRespondService respondService;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void resetRows() {
    jdbc.execute("TRUNCATE TABLE ezkey_auth_attempt RESTART IDENTITY");
    jdbc.execute("TRUNCATE TABLE ezkey_alert RESTART IDENTITY");
  }

  @Test
  @DisplayName(
      "A fresh connection sees the alert after INVALID and after DENY, and the second touch"
          + " increments the count")
  void invalidThenRejected_persistsAlertAndIncrementsCount() {
    int invalidId = insertReadAttempt(12);
    AuthAttemptRespondRequest wrongCode = new AuthAttemptRespondRequest();
    wrongCode.setAuthAttemptId(invalidId);
    wrongCode.setAuthAttemptAccepted(true);
    wrongCode.setAuthAttemptChallengeResponse(99);
    wrongCode.setAuthAttemptProofTokenSignedByDevice("device-sig");
    respondService.respond(wrongCode, null);

    assertThat(statusOf(invalidId)).isEqualTo("INVALID");
    assertThat(alertRowsOnFreshConnection()).isEqualTo(1);
    assertThat(occurrenceCountOnFreshConnection()).isEqualTo(1);
    assertThat(alertTypeOnFreshConnection()).isEqualTo("AUTH_ATTEMPT_REJECTED");
    assertThat(alertPayloadOnFreshConnection()).isEqualTo("{\"enrollmentId\":7}");

    int rejectedId = insertReadAttempt(null);
    AuthAttemptRespondRequest deny = new AuthAttemptRespondRequest();
    deny.setAuthAttemptId(rejectedId);
    deny.setAuthAttemptAccepted(false);
    deny.setAuthAttemptProofTokenSignedByDevice("device-sig");
    respondService.respond(deny, null);

    assertThat(statusOf(rejectedId)).isEqualTo("REJECTED");
    assertThat(alertRowsOnFreshConnection()).isEqualTo(1);
    assertThat(occurrenceCountOnFreshConnection()).isEqualTo(2);
  }

  private int insertReadAttempt(Integer challenge) {
    Integer id =
        jdbc.queryForObject(
            "INSERT INTO ezkey_auth_attempt (enrollment_id, auth_attempt_status,"
                + " auth_attempt_challenge, auth_attempt_proof_token, created_at, expires_at)"
                + " VALUES (7, 'READ', ?, 'proof-token', now(), now() + interval '5 minutes')"
                + " RETURNING auth_attempt_id",
            Integer.class,
            challenge);
    return id;
  }

  private String statusOf(int attemptId) {
    return jdbc.queryForObject(
        "SELECT auth_attempt_status FROM ezkey_auth_attempt WHERE auth_attempt_id = ?",
        String.class,
        attemptId);
  }

  private int alertRowsOnFreshConnection() {
    Integer count = queryAlertOnFreshConnection("SELECT COUNT(*) FROM ezkey_alert", Integer.class);
    return count;
  }

  private int occurrenceCountOnFreshConnection() {
    return queryAlertOnFreshConnection(
        "SELECT occurrence_count FROM ezkey_alert WHERE dedupe_key = 'AUTH_ATTEMPT_REJECTED:7'",
        Integer.class);
  }

  private String alertTypeOnFreshConnection() {
    return queryAlertOnFreshConnection(
        "SELECT alert_type FROM ezkey_alert WHERE dedupe_key = 'AUTH_ATTEMPT_REJECTED:7'",
        String.class);
  }

  private String alertPayloadOnFreshConnection() {
    return queryAlertOnFreshConnection(
        "SELECT payload FROM ezkey_alert WHERE dedupe_key = 'AUTH_ATTEMPT_REJECTED:7'",
        String.class);
  }

  /**
   * Reads {@code ezkey_alert} on a connection opened for this query. The Spring persistence context
   * is not reused, so an uncommitted or rolled-back raise cannot satisfy the assertion.
   *
   * @param sql single-column query
   * @param type column type, {@link Integer} or {@link String}
   * @param <T> column type
   * @return the first column, or null when the query returns no row
   */
  private <T> T queryAlertOnFreshConnection(String sql, Class<T> type) {
    try (Connection connection =
            DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        PreparedStatement statement = connection.prepareStatement(sql);
        ResultSet rows = statement.executeQuery()) {
      if (!rows.next()) {
        return null;
      }
      if (type == Integer.class) {
        return type.cast(rows.getInt(1));
      }
      return type.cast(rows.getString(1));
    } catch (SQLException ex) {
      throw new IllegalStateException("fresh alert read failed", ex);
    }
  }
}
