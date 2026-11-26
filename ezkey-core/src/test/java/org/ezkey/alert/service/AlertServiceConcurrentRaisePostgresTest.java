/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AlertServiceConcurrentRaisePostgresTest
 * Description: raiseOrTouch survives a concurrent first raise of the same dedupe key (#1267).
 */

package org.ezkey.alert.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.ezkey.alert.domain.AlertSeverity;
import org.ezkey.alert.domain.AlertType;
import org.ezkey.alert.domain.entity.Alert;
import org.ezkey.alert.repository.AlertRepository;
import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Real-Postgres proof for the first-raise race on {@code uq_alert_dedupe_key_open}.
 *
 * <p>A second connection inserts the OPEN row and holds its transaction open. The service call
 * cannot see that row, so it inserts too and blocks on the unique index. When the other transaction
 * commits, the service must touch the winner's row instead of failing.
 *
 * @since 2026
 */
@SpringBootTest(classes = AlertServiceConcurrentRaisePostgresTest.TestConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
class AlertServiceConcurrentRaisePostgresTest {

  private static final String DEDUPE_KEY = "AUDIT_CHAIN_GAP_PENDING:race";

  @Container @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer("postgres:19-alpine").withDatabaseName("ezkey_alert_race");

  @DynamicPropertySource
  static void datasourceProps(DynamicPropertyRegistry registry) {
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    registry.add("spring.flyway.enabled", () -> "false");
    registry.add("spring.sql.init.mode", () -> "always");
    registry.add(
        "spring.sql.init.schema-locations", () -> "classpath:ezkey-alert-min-pg-schema.sql");
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @EntityScan(basePackageClasses = Alert.class)
  @EnableJpaRepositories(basePackageClasses = AlertRepository.class)
  static class TestConfiguration {

    @Bean
    AuditLogService auditLogService() {
      return mock(AuditLogService.class);
    }

    @Bean
    AlertService alertService(AlertRepository alertRepository, AuditLogService auditLogService) {
      return new AlertService(alertRepository, auditLogService);
    }
  }

  @Autowired private DataSource dataSource;
  @Autowired private AlertService alertService;
  @Autowired private AuditLogService auditLogService;

  private JdbcTemplate jdbc;

  @BeforeEach
  void cleanTable() {
    jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("DELETE FROM ezkey_alert");
    reset(auditLogService);
  }

  @Test
  @DisplayName("first raise blocked by a concurrent insert touches the winner's row")
  void concurrentFirstRaise_touchesWinnerRow() throws Exception {
    try (Connection winner = dataSource.getConnection()) {
      winner.setAutoCommit(false);
      try (PreparedStatement insert =
          winner.prepareStatement(
              "INSERT INTO ezkey_alert (alert_type, severity, status, dedupe_key, payload)"
                  + " VALUES ('AUDIT_CHAIN_GAP_PENDING', 'WARNING', 'OPEN', ?, '{\"n\":1}')")) {
        insert.setString(1, DEDUPE_KEY);
        insert.executeUpdate();
      }

      CompletableFuture<Alert> loser =
          CompletableFuture.supplyAsync(() ->
              alertService.raiseOrTouch(
                  AlertType.AUDIT_CHAIN_GAP_PENDING,
                  AlertSeverity.WARNING,
                  DEDUPE_KEY,
                  "{\"n\":2}"));

      awaitBlockedOnLock();
      winner.commit();

      Alert touched = loser.get(30, TimeUnit.SECONDS);
      assertThat(touched.getOccurrenceCount()).isEqualTo(2);
    }

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM ezkey_alert WHERE dedupe_key = ?", Integer.class, DEDUPE_KEY))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT occurrence_count FROM ezkey_alert WHERE dedupe_key = ?",
                Integer.class,
                DEDUPE_KEY))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT payload FROM ezkey_alert WHERE dedupe_key = ?", String.class, DEDUPE_KEY))
        .isEqualTo("{\"n\":2}");
    // The winner announced the alert; the touch path must not announce it again.
    verify(auditLogService, never()).log(ArgumentMatchers.any(AuditLog.class));
  }

  @Test
  @DisplayName("first raise without contention inserts once and announces once")
  void firstRaise_insertsAndAnnounces() {
    Alert raised =
        alertService.raiseOrTouch(
            AlertType.AUDIT_CHAIN_GAP_PENDING, AlertSeverity.WARNING, DEDUPE_KEY, null);
    Alert touched =
        alertService.raiseOrTouch(
            AlertType.AUDIT_CHAIN_GAP_PENDING, AlertSeverity.WARNING, DEDUPE_KEY, "{\"n\":3}");

    assertThat(raised.getAlertId()).isNotNull();
    assertThat(touched.getAlertId()).isEqualTo(raised.getAlertId());
    assertThat(touched.getOccurrenceCount()).isEqualTo(2);
    verify(auditLogService).log(ArgumentMatchers.any(AuditLog.class));
  }

  /** Waits until some backend waits on a lock: the service insert is parked on the index. */
  private void awaitBlockedOnLock() throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
    while (System.nanoTime() < deadline) {
      Integer waiting =
          jdbc.queryForObject(
              "SELECT count(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock'",
              Integer.class);
      if (waiting != null && waiting > 0) {
        return;
      }
      Thread.sleep(50);
    }
    throw new AssertionError("service insert never blocked on the unique index");
  }
}
