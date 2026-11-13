/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: PartitionSchedulerPostgresTest
 * Description: PostgreSQL proof that a missing month rejects inserts and that a function failure
 *              is recorded instead of being swallowed.
 */

package org.ezkey.database.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import net.javacrumbs.shedlock.core.LockProvider;
import org.ezkey.alert.domain.entity.Alert;
import org.ezkey.alert.repository.AlertRepository;
import org.ezkey.alert.service.AlertService;
import org.ezkey.audit.integrity.ScheduledJobLastRun;
import org.ezkey.audit.integrity.ScheduledJobLastRunRepository;
import org.ezkey.audit.integrity.ScheduledJobLastRunService;
import org.ezkey.audit.service.AuditLogService;
import org.junit.jupiter.api.BeforeAll;
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
import org.springframework.dao.DataAccessException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * PostgreSQL proof of the month-boundary outage and of the scheduler's failure record.
 *
 * <p>The schema has only {@code 2026_11}. The service clock is fixed to that month, so the next
 * month is uncovered until a successful run. A login role without EXECUTE on {@code
 * create_monthly_partition} gets SQLSTATE {@code 42501}. The scheduler itself runs as the table
 * owner, so the failure-handling path replaces the function with one that raises the same SQLSTATE
 * (what grants drift looks like to the caller) and checks that the last-run row and the alert are
 * written while the insert still fails.
 *
 * @since 2026
 */
@SpringBootTest(classes = PartitionSchedulerPostgresTest.TestConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
class PartitionSchedulerPostgresTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-11-13T15:00:00Z"), ZoneOffset.UTC);

  private static final String FUNCTION =
      "create_monthly_partition(TEXT, TEXT, TIMESTAMPTZ, TIMESTAMPTZ)";

  private static String functionSql;

  @Container @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer("postgres:18-alpine").withDatabaseName("ezkey_partition_scheduler");

  @DynamicPropertySource
  static void datasourceProps(DynamicPropertyRegistry registry) {
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    registry.add("spring.flyway.enabled", () -> "false");
    registry.add("spring.sql.init.mode", () -> "always");
    registry.add(
        "spring.sql.init.schema-locations", () -> "classpath:partition-scheduler-pg-schema.sql");
    registry.add("spring.jpa.properties.hibernate.jdbc.time_zone", () -> "UTC");
    registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET TIME ZONE 'UTC'");
  }

  @BeforeAll
  static void loadFunctionSql() throws IOException {
    try (InputStream in =
        PartitionSchedulerPostgresTest.class.getResourceAsStream(
            "/partition-scheduler-function.sql")) {
      if (in == null) {
        throw new IllegalStateException("missing partition-scheduler-function.sql");
      }
      functionSql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @EntityScan(basePackageClasses = {Alert.class, ScheduledJobLastRun.class})
  @EnableJpaRepositories(
      basePackageClasses = {AlertRepository.class, ScheduledJobLastRunRepository.class})
  static class TestConfiguration {

    @Bean
    Clock clock() {
      return CLOCK;
    }

    @Bean
    AuditLogService auditLogService() {
      return org.mockito.Mockito.mock(AuditLogService.class);
    }

    @Bean
    AlertService alertService(AlertRepository alertRepository, AuditLogService auditLogService) {
      return new AlertService(alertRepository, auditLogService);
    }

    @Bean
    ScheduledJobLastRunService scheduledJobLastRunService(
        ScheduledJobLastRunRepository repository) {
      return new ScheduledJobLastRunService(repository);
    }

    @Bean
    PartitionSchedulerService partitionSchedulerService(
        jakarta.persistence.EntityManager entityManager,
        ScheduledJobLastRunService jobLastRunService,
        AlertService alertService,
        PlatformTransactionManager transactionManager,
        ObjectProvider<LockProvider> lockProvider,
        Clock clock) {
      return new PartitionSchedulerService(
          entityManager,
          jobLastRunService,
          alertService,
          transactionManager,
          lockProvider,
          clock,
          PartitionSchedulerService.DEFAULT_MONTHS_AHEAD,
          false);
    }
  }

  @Autowired private PartitionSchedulerService scheduler;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void resetHorizon() {
    dropExtraPartitions();
    jdbc.execute("DROP FUNCTION IF EXISTS " + FUNCTION);
    jdbc.execute(
        "DROP FUNCTION IF EXISTS create_monthly_partition_real(TEXT, TEXT, TIMESTAMPTZ,"
            + " TIMESTAMPTZ)");
    jdbc.execute(functionSql);
    jdbc.execute("GRANT EXECUTE ON FUNCTION " + FUNCTION + " TO PUBLIC");
    jdbc.update(
        "UPDATE ezkey_scheduled_job_last_run SET last_execution_at = NULL, last_status ="
            + " 'NEVER_RUN', last_run_scope = NULL, last_error_summary = NULL, updated_at ="
            + " CURRENT_TIMESTAMP WHERE job_key = 'PARTITION_CREATION'");
    jdbc.execute("DELETE FROM ezkey_alert");
    jdbc.execute(
        "DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ezkey_no_ddl') THEN"
            + " CREATE ROLE ezkey_no_ddl LOGIN PASSWORD 'ezkey_no_ddl'; END IF; END $$");
  }

  @Test
  @DisplayName("current month accepts a row and the next month rejects it while uncovered")
  void uncoveredNextMonth_rejectsInsert() {
    insertAuth("2026-11-15 12:00:00+00");
    assertThat(countAuth("2026-11-01 00:00:00+00", "2026-12-01 00:00:00+00")).isEqualTo(1);

    assertThatThrownBy(() -> insertAuth("2026-12-15 12:00:00+00"))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("no partition");
    assertThatThrownBy(() -> insertAudit("2026-12-15 12:00:00+00"))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("no partition");
  }

  @Test
  @DisplayName(
      "missing EXECUTE is 42501, and a function failure is stored while the insert still fails")
  void grantsDrift_recordsFailureAndLeavesNextMonthUncovered() throws SQLException {
    jdbc.execute("REVOKE EXECUTE ON FUNCTION " + FUNCTION + " FROM PUBLIC");
    jdbc.execute("REVOKE EXECUTE ON FUNCTION " + FUNCTION + " FROM ezkey_no_ddl");

    try (Connection denied =
        DriverManager.getConnection(postgres.getJdbcUrl(), "ezkey_no_ddl", "ezkey_no_ddl")) {
      try (PreparedStatement statement =
          denied.prepareStatement(
              "SELECT create_monthly_partition('ezkey_auth_attempt', 'ezkey_auth_attempt_2026_12',"
                  + " '2026-12-01 00:00:00+00', '2027-01-01 00:00:00+00')")) {
        assertThatThrownBy(statement::executeQuery)
            .isInstanceOf(SQLException.class)
            .extracting(ex -> ((SQLException) ex).getSQLState())
            .isEqualTo("42501");
      }
    }
    assertThat(partitionExists("ezkey_auth_attempt_2026_12")).isFalse();

    jdbc.execute(failingFunction("permission denied for function create_monthly_partition"));

    scheduler.createNextMonthPartitions();

    JobRow job = jobRow();
    assertThat(job.status()).isEqualTo("FAILED");
    assertThat(job.scope()).isEqualTo("UTC 2026-11..2027-02 monthsAhead=3 failed=8/8");
    assertThat(job.errorSummary()).isNotBlank();
    assertThat(job.errorSummary()).doesNotContain(" ");
    assertThat(job.errorSummary()).doesNotContain("permission");
    assertThat(job.errorSummary()).doesNotContain("create_monthly_partition");

    AlertRow alert = alertRow();
    assertThat(alert.type()).isEqualTo("PARTITION_CREATION_FAILED");
    assertThat(alert.severity()).isEqualTo("CRITICAL");
    assertThat(alert.status()).isEqualTo("OPEN");
    assertThat(alert.payload()).contains("\"error\":\"" + job.errorSummary() + "\"");
    assertThat(alert.payload()).contains("\"from\":\"2026-11\"");
    assertThat(alert.payload()).contains("\"to\":\"2027-02\"");
    assertThat(alert.payload()).doesNotContain("permission denied");

    assertThat(partitionExists("ezkey_auth_attempt_2026_12")).isFalse();
    assertThatThrownBy(() -> insertAuth("2026-12-15 12:00:00+00"))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("no partition");
  }

  @Test
  @DisplayName("a second failure touches the open alert, then a clean run resolves it")
  void repeatedFailure_thenSuccess_resolvesAlert() {
    jdbc.execute(failingFunction("permission denied"));
    scheduler.createNextMonthPartitions();
    scheduler.createNextMonthPartitions();
    assertThat(alertRow().occurrences()).isEqualTo(2);
    assertThat(alertRow().status()).isEqualTo("OPEN");

    jdbc.execute(functionSql);
    jdbc.execute("GRANT EXECUTE ON FUNCTION " + FUNCTION + " TO PUBLIC");
    scheduler.createNextMonthPartitions();

    assertThat(jobRow().status()).isEqualTo("SUCCESS");
    assertThat(jobRow().scope()).isEqualTo("UTC 2026-11..2027-02 monthsAhead=3");
    assertThat(jobRow().errorSummary()).isNull();
    AlertRow alert = alertRow();
    assertThat(alert.status()).isEqualTo("RESOLVED");
    assertThat(alert.reason()).isEqualTo("PARTITION_HORIZON_RESTORED");
  }

  @Test
  @DisplayName(
      "success creates three months ahead, allows that horizon, and adds no DEFAULT partition")
  void success_createsHorizonWithoutDefaultPartition() {
    scheduler.createNextMonthPartitions();
    scheduler.createNextMonthPartitions();

    assertThat(partitionExists("ezkey_auth_attempt_2026_11")).isTrue();
    assertThat(partitionExists("ezkey_auth_attempt_2026_12")).isTrue();
    assertThat(partitionExists("ezkey_auth_attempt_2027_01")).isTrue();
    assertThat(partitionExists("ezkey_auth_attempt_2027_02")).isTrue();
    assertThat(partitionExists("ezkey_auth_attempt_2027_03")).isFalse();
    assertThat(partitionExists("ezkey_audit_log_2027_02")).isTrue();
    assertThat(partitionExists("ezkey_audit_log_2027_02_admin")).isTrue();
    assertThat(partitionExists("ezkey_audit_log_2027_02_auth")).isTrue();
    assertThat(partitionExists("ezkey_audit_log_2027_02_integration")).isTrue();
    assertThat(defaultPartitionCount()).isZero();

    insertAuth("2027-02-15 12:00:00+00");
    insertAudit("2027-02-15 12:00:00+00");
    assertThatThrownBy(() -> insertAuth("2027-03-15 12:00:00+00"))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("no partition");

    assertThat(jobRow().status()).isEqualTo("SUCCESS");
    assertThat(alertCount()).isZero();
  }

  @Test
  @DisplayName("one partition in the middle fails and the other seven still commit")
  void midHorizonFailure_commitsTheOtherTargetsThenResolves() {
    jdbc.execute("ALTER FUNCTION " + FUNCTION + " RENAME TO create_monthly_partition_real");
    jdbc.execute(wrapperThatRejects("ezkey_auth_attempt_2026_12"));

    scheduler.createNextMonthPartitions();

    assertThat(partitionExists("ezkey_auth_attempt_2026_11")).isTrue();
    assertThat(partitionExists("ezkey_audit_log_2026_11")).isTrue();
    assertThat(partitionExists("ezkey_auth_attempt_2026_12")).isFalse();
    assertAuditMonth("ezkey_audit_log_2026_12");
    assertThat(partitionExists("ezkey_auth_attempt_2027_01")).isTrue();
    assertAuditMonth("ezkey_audit_log_2027_01");
    assertThat(partitionExists("ezkey_auth_attempt_2027_02")).isTrue();
    assertAuditMonth("ezkey_audit_log_2027_02");

    JobRow job = jobRow();
    assertThat(job.status()).isEqualTo("FAILED");
    assertThat(job.scope()).isEqualTo("UTC 2026-11..2027-02 monthsAhead=3 failed=1/8");
    assertThat(job.errorSummary()).isNotBlank();
    assertThat(job.errorSummary()).doesNotContain(" ");
    assertThat(alertCount()).isEqualTo(1);
    AlertRow alert = alertRow();
    assertThat(alert.type()).isEqualTo("PARTITION_CREATION_FAILED");
    assertThat(alert.severity()).isEqualTo("CRITICAL");
    assertThat(alert.status()).isEqualTo("OPEN");

    jdbc.execute(functionSql);
    jdbc.execute("GRANT EXECUTE ON FUNCTION " + FUNCTION + " TO PUBLIC");
    scheduler.createNextMonthPartitions();

    assertThat(partitionExists("ezkey_auth_attempt_2026_12")).isTrue();
    assertThat(jobRow().status()).isEqualTo("SUCCESS");
    assertThat(jobRow().scope()).isEqualTo("UTC 2026-11..2027-02 monthsAhead=3");
    assertThat(jobRow().errorSummary()).isNull();
    assertThat(alertCount()).isEqualTo(1);
    AlertRow resolved = alertRow();
    assertThat(resolved.status()).isEqualTo("RESOLVED");
    assertThat(resolved.reason()).isEqualTo("PARTITION_HORIZON_RESTORED");
  }

  private static String wrapperThatRejects(String partitionName) {
    return "CREATE FUNCTION create_monthly_partition("
        + " p_table_name TEXT, p_partition_name TEXT, p_start_date TIMESTAMPTZ,"
        + " p_end_date TIMESTAMPTZ) RETURNS BOOLEAN LANGUAGE plpgsql"
        + " SECURITY DEFINER SET search_path = public AS $$ BEGIN"
        + " IF p_partition_name = '"
        + partitionName
        + "' THEN RAISE EXCEPTION 'mid-horizon partition refused'"
        + " USING ERRCODE = '42501'; END IF;"
        + " RETURN create_monthly_partition_real(p_table_name, p_partition_name, p_start_date,"
        + " p_end_date); END $$";
  }

  private void assertAuditMonth(String parent) {
    assertThat(partitionExists(parent)).isTrue();
    assertThat(partitionExists(parent + "_admin")).isTrue();
    assertThat(partitionExists(parent + "_auth")).isTrue();
    assertThat(partitionExists(parent + "_integration")).isTrue();
  }

  private static String failingFunction(String message) {
    return "CREATE OR REPLACE FUNCTION create_monthly_partition("
        + " p_table_name TEXT, p_partition_name TEXT, p_start_date TIMESTAMPTZ,"
        + " p_end_date TIMESTAMPTZ) RETURNS BOOLEAN LANGUAGE plpgsql AS $$ BEGIN"
        + " RAISE EXCEPTION '"
        + message
        + "' USING ERRCODE = '42501'; END $$";
  }

  private void insertAuth(String createdAt) {
    jdbc.update(
        "INSERT INTO ezkey_auth_attempt (enrollment_id, auth_attempt_status, created_at)"
            + " VALUES (1, 'PENDING', ?::timestamptz)",
        createdAt);
  }

  private void insertAudit(String createdAt) {
    jdbc.update(
        "INSERT INTO ezkey_audit_log (event_type, api_name, created_at)"
            + " VALUES ('ALERT_RAISED', 'ADMIN_API', ?::timestamptz)",
        createdAt);
  }

  private int countAuth(String from, String to) {
    Integer count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM ezkey_auth_attempt WHERE created_at >= ?::timestamptz"
                + " AND created_at < ?::timestamptz",
            Integer.class,
            from,
            to);
    return count == null ? 0 : count;
  }

  private boolean partitionExists(String name) {
    Integer count =
        jdbc.queryForObject("SELECT COUNT(*) FROM pg_class WHERE relname = ?", Integer.class, name);
    return count != null && count > 0;
  }

  private int defaultPartitionCount() {
    Integer count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM pg_partitioned_table pt"
                + " JOIN pg_class parent ON parent.oid = pt.partrelid"
                + " WHERE parent.relname IN ('ezkey_auth_attempt', 'ezkey_audit_log')"
                + " AND pt.partdefid <> 0",
            Integer.class);
    return count == null ? 0 : count;
  }

  private int alertCount() {
    Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM ezkey_alert", Integer.class);
    return count == null ? 0 : count;
  }

  private JobRow jobRow() {
    return jdbc.queryForObject(
        "SELECT last_status, last_run_scope, last_error_summary FROM ezkey_scheduled_job_last_run"
            + " WHERE job_key = 'PARTITION_CREATION'",
        (rs, row) -> new JobRow(rs.getString(1), rs.getString(2), rs.getString(3)));
  }

  private AlertRow alertRow() {
    return jdbc.queryForObject(
        "SELECT alert_type, severity, status, payload, occurrence_count, resolution_reason"
            + " FROM ezkey_alert WHERE dedupe_key = 'PARTITION_CREATION_FAILED'",
        (rs, row) ->
            new AlertRow(
                rs.getString(1),
                rs.getString(2),
                rs.getString(3),
                rs.getString(4),
                rs.getInt(5),
                rs.getString(6)));
  }

  private void dropExtraPartitions() {
    List<String> names =
        jdbc.queryForList(
            "SELECT c.relname FROM pg_class c"
                + " WHERE c.relname ~ '^ezkey_(auth_attempt|audit_log)_[0-9]{4}_[0-9]{2}$'"
                + " AND c.relname NOT IN ('ezkey_auth_attempt_2026_11', 'ezkey_audit_log_2026_11')",
            String.class);
    for (String name : names) {
      if (name.matches("ezkey_(auth_attempt|audit_log)_\\d{4}_\\d{2}")) {
        jdbc.execute("DROP TABLE IF EXISTS " + name + " CASCADE");
      }
    }
  }

  private record JobRow(String status, String scope, String errorSummary) {}

  private record AlertRow(
      String type,
      String severity,
      String status,
      String payload,
      int occurrences,
      String reason) {}
}
