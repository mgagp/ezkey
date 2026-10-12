/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: IntegrityAsyncJobPostgresIT
 * Description: Patrick T1–T3 — real Postgres repository, V25 CHECKs/UPDATE, ShedLock probe.
 */

package org.ezkey.audit.asyncjob;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.ezkey.audit.exception.IntegrityAsyncJobBusyException;
import org.ezkey.audit.integrity.AuditChainVerificationService;
import org.ezkey.audit.integrity.AuditIntegrityService;
import org.ezkey.audit.integrity.IntegrityHeavyCryptoGate;
import org.ezkey.audit.integrity.IntegrityWindowCapProperties;
import org.ezkey.audit.integrity.NightlyIntegrityProperties;
import org.ezkey.audit.integrity.NightlyIntegrityShedLockProbe;
import org.ezkey.audit.integrity.RetroactiveIntegrityValidationService;
import org.ezkey.audit.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Real-Postgres coverage for Integrity cut 3 PR 1 review gates T1–T3.
 *
 * @since 2026
 */
@SpringBootTest(classes = IntegrityAsyncJobPostgresIT.TestConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class IntegrityAsyncJobPostgresIT {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer("postgres:18-alpine").withDatabaseName("ezkey_it");

  @DynamicPropertySource
  static void datasourceProps(DynamicPropertyRegistry registry) {
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    registry.add("spring.flyway.enabled", () -> "false");
    registry.add("spring.sql.init.mode", () -> "never");
    registry.add("ezkey.audit.integrity.async-job.enabled", () -> "true");
    registry.add("ezkey.audit.integrity.async-job.ttl", () -> "PT60M");
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @EntityScan(basePackageClasses = IntegrityAsyncJob.class)
  @EnableJpaRepositories(basePackageClasses = IntegrityAsyncJobRepository.class)
  @Import({IntegrityAsyncJobStateService.class, IntegrityNightlyTerminalService.class})
  static class TestConfiguration {

    @Bean
    IntegrityAsyncJobProperties integrityAsyncJobProperties() {
      IntegrityAsyncJobProperties p = new IntegrityAsyncJobProperties();
      p.setTtl(Duration.ofMinutes(60));
      return p;
    }

    @Bean
    AuditLogService auditLogService() {
      return mock(AuditLogService.class);
    }

    @Bean
    IntegrityHeavyCryptoGate heavyCryptoGate() {
      return new IntegrityHeavyCryptoGate();
    }

    @Bean
    NightlyIntegrityShedLockProbe nightlyIntegrityShedLockProbe(DataSource dataSource) {
      return new NightlyIntegrityShedLockProbe(dataSource);
    }

    @Bean(name = "integrityAsyncJobExecutor")
    ThreadPoolTaskExecutor integrityAsyncJobExecutor() {
      ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
      ex.setCorePoolSize(1);
      ex.setMaxPoolSize(1);
      ex.setQueueCapacity(1);
      ex.setThreadNamePrefix("it-integrity-async-");
      ex.initialize();
      return ex;
    }

    @Bean
    IntegrityAsyncJobService integrityAsyncJobService(
        IntegrityAsyncJobRepository jobRepository,
        IntegrityAsyncJobStateService stateService,
        IntegrityNightlyTerminalService nightlyTerminalService,
        IntegrityHeavyCryptoGate heavyCryptoGate,
        NightlyIntegrityShedLockProbe nightlyShedLockProbe,
        AuditLogService auditLogService,
        ThreadPoolTaskExecutor integrityAsyncJobExecutor) {
      NightlyIntegrityProperties nightly = mock(NightlyIntegrityProperties.class);
      when(nightly.isEnabled()).thenReturn(true);
      return new IntegrityAsyncJobService(
          jobRepository,
          stateService,
          nightlyTerminalService,
          heavyCryptoGate,
          mock(AuditIntegrityService.class),
          mock(AuditChainVerificationService.class),
          mock(RetroactiveIntegrityValidationService.class),
          nightly,
          new IntegrityWindowCapProperties(),
          nightlyShedLockProbe,
          auditLogService,
          integrityAsyncJobExecutor);
    }
  }

  @Autowired private DataSource dataSource;
  @Autowired private IntegrityAsyncJobRepository jobRepository;
  @Autowired private IntegrityAsyncJobService jobService;
  @Autowired private NightlyIntegrityShedLockProbe shedLockProbe;

  private JdbcTemplate jdbc;

  @BeforeEach
  void resetSchema() {
    jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("DROP TABLE IF EXISTS ezkey_integrity_async_job CASCADE");
    jdbc.execute("DROP TABLE IF EXISTS ezkey_shedlock CASCADE");
    ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
    populator.addScript(new ClassPathResource("integrity-async-job-pg-schema.sql"));
    populator.execute(dataSource);
  }

  @Test
  @DisplayName("T1: newer NIGHTLY row ignored by getCurrent / abandon / escape-sticky (real repo)")
  void t1_newerNightlyIgnoredByOperatorSlotReaders() {
    OffsetDateTime older = OffsetDateTime.parse("2026-10-01T10:00:00Z");
    OffsetDateTime newer = OffsetDateTime.parse("2026-10-01T12:00:00Z");

    IntegrityAsyncJob expiredOperator = baseOperator(older);
    expiredOperator.setStatus(IntegrityAsyncJobStatus.EXPIRED);
    expiredOperator.setFinishedAt(older.plusMinutes(30));
    expiredOperator.setErrorSummary("No heartbeat within TTL");
    jobRepository.saveAndFlush(expiredOperator);

    IntegrityAsyncJob nightly = baseNightly(newer);
    nightly.setStatus(IntegrityAsyncJobStatus.SUCCEEDED);
    nightly.setFinishedAt(newer);
    nightly.setChainStatus(IntegrityChainStatus.OK);
    nightly.setTotalCheckpoints(10);
    nightly.setValidCheckpoints(10);
    nightly.setInvalidCheckpoints(0);
    nightly.setArchivedCheckpoints(0);
    nightly.setUndeclaredGaps(0);
    nightly.setEntryHmacViolations(0);
    nightly.setResultIntact(true);
    jobRepository.saveAndFlush(nightly);

    var current = jobService.getCurrent();
    assertThat(current).isPresent();
    assertThat(current.get().jobId()).isEqualTo(expiredOperator.getJobId());
    assertThat(current.get().trigger()).isEqualTo(IntegrityAsyncJobTrigger.MANUAL);

    // Escape-sticky Start refuse must cite the operator EXPIRED, not the newer nightly.
    assertThatThrownBy(
            () ->
                jobService.start(
                    new org.ezkey.audit.dto.IntegrityAsyncJobStartRequest(
                        IntegrityAsyncJobType.VERIFY_CHAIN_RANGE, older, older.plusHours(1), null),
                    1,
                    "admin.docker"))
        .isInstanceOf(IntegrityAsyncJobBusyException.class)
        .satisfies(
            ex -> {
              IntegrityAsyncJobBusyException busy = (IntegrityAsyncJobBusyException) ex;
              assertThat(busy.getCurrentJob().getJobId()).isEqualTo(expiredOperator.getJobId());
            });

    var abandoned = jobService.abandon(7);
    assertThat(abandoned.jobId()).isEqualTo(expiredOperator.getJobId());
    assertThat(abandoned.trigger()).isEqualTo(IntegrityAsyncJobTrigger.MANUAL);
    assertThat(jobRepository.findById(nightly.getJobId()).orElseThrow().getAbandonedAt()).isNull();
  }

  @Test
  @DisplayName("mixed last-verdict: chain OK with entry-HMAC violations is verdict-bearing")
  void mixedLastVerdict_chainOkWithEntryHmacViolations() {
    OffsetDateTime t = OffsetDateTime.parse("2026-10-02T08:00:00Z");
    IntegrityAsyncJob mixed = baseNightly(t);
    mixed.setStatus(IntegrityAsyncJobStatus.SUCCEEDED);
    mixed.setFinishedAt(t);
    mixed.setChainStatus(IntegrityChainStatus.OK);
    mixed.setTotalCheckpoints(24);
    mixed.setValidCheckpoints(24);
    mixed.setInvalidCheckpoints(0);
    mixed.setArchivedCheckpoints(0);
    mixed.setUndeclaredGaps(0);
    mixed.setEntryHmacViolations(3);
    mixed.setResultIntact(false);
    jobRepository.saveAndFlush(mixed);

    Optional<IntegrityAsyncJob> verdict = jobRepository.findLatestVerdict();
    assertThat(verdict).isPresent();
    assertThat(verdict.get().getChainStatus()).isEqualTo(IntegrityChainStatus.OK);
    assertThat(verdict.get().getEntryHmacViolations()).isEqualTo(3);

    var dto = jobService.getLastVerdict();
    assertThat(dto).isPresent();
    assertThat(dto.get().entryHmacViolations()).isEqualTo(3);
    assertThat(dto.get().chainStatus()).isEqualTo(IntegrityChainStatus.OK);
  }

  @Test
  @DisplayName("T2: V25 CHECKs + INTERRUPTED heal UPDATE execute on Postgres")
  void t2_v25ChecksAndHealUpdateAgainstPostgres() throws Exception {
    // Rebuild from V22-shaped table, then apply the real V25 script.
    jdbc.execute("DROP TABLE IF EXISTS ezkey_integrity_async_job CASCADE");
    jdbc.execute(
        """
        CREATE TABLE ezkey_integrity_async_job (
            job_id UUID PRIMARY KEY,
            slot_key VARCHAR(32) NOT NULL DEFAULT 'GLOBAL',
            job_type VARCHAR(64) NOT NULL,
            status VARCHAR(32) NOT NULL,
            started_by_admin_id INT NOT NULL,
            started_by_username VARCHAR(255) NOT NULL,
            started_at TIMESTAMPTZ NOT NULL,
            heartbeat_at TIMESTAMPTZ NOT NULL,
            finished_at TIMESTAMPTZ,
            scope_from TIMESTAMPTZ,
            scope_to TIMESTAMPTZ,
            raise_alert BOOLEAN,
            result_summary VARCHAR(1024),
            error_summary VARCHAR(512),
            result_intact BOOLEAN,
            result_alert_id BIGINT,
            abandoned_at TIMESTAMPTZ,
            abandoned_by_admin_id INT,
            created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
            CONSTRAINT chk_integrity_async_job_type CHECK (job_type IN (
              'VERIFY_CHAIN_RANGE','VERIFY_ENTRY_HMAC_RANGE','RUN_VALIDATION')),
            CONSTRAINT chk_integrity_async_job_status CHECK (status IN (
              'RUNNING','SUCCEEDED','FAILED','CANCELLED','EXPIRED','INTERRUPTED'))
        )
        """);

    // Pre-V25 orphan INTERRUPTED without abandoned_at.
    UUID orphanId = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO ezkey_integrity_async_job (
          job_id, slot_key, job_type, status, started_by_admin_id, started_by_username,
          started_at, heartbeat_at, finished_at)
        VALUES (?, 'GLOBAL', 'VERIFY_CHAIN_RANGE', 'INTERRUPTED', 1, 'admin.docker',
          TIMESTAMPTZ '2026-09-01 00:00:00Z', TIMESTAMPTZ '2026-09-01 00:10:00Z',
          TIMESTAMPTZ '2026-09-01 00:10:00Z')
        """,
        orphanId);

    ResourceDatabasePopulator v25 = new ResourceDatabasePopulator();
    v25.setSeparator(";");
    v25.addScript(
        new ClassPathResource("db/migration/V25__integrity_async_job_verdict_trigger.sql"));
    v25.execute(dataSource);

    OffsetDateTime abandonedAt =
        jdbc.queryForObject(
            "SELECT abandoned_at FROM ezkey_integrity_async_job WHERE job_id = ?",
            OffsetDateTime.class,
            orphanId);
    assertThat(abandonedAt).isNotNull();

    // Nightly RUNNING rejected.
    assertThatThrownBy(
            () ->
                jdbc.update(
                    """
                    INSERT INTO ezkey_integrity_async_job (
                      job_id, slot_key, job_type, status, "trigger", started_by_admin_id,
                      started_by_username, started_at, heartbeat_at)
                    VALUES (?, 'GLOBAL', 'RUN_VALIDATION', 'RUNNING', 'NIGHTLY', NULL,
                      'system', NOW(), NOW())
                    """,
                    UUID.randomUUID()))
        .hasMessageContaining("chk_integrity_async_job_nightly_terminal");

    // Nightly with admin id rejected.
    assertThatThrownBy(
            () ->
                jdbc.update(
                    """
                    INSERT INTO ezkey_integrity_async_job (
                      job_id, slot_key, job_type, status, "trigger", started_by_admin_id,
                      started_by_username, started_at, heartbeat_at, finished_at)
                    VALUES (?, 'GLOBAL', 'RUN_VALIDATION', 'SUCCEEDED', 'NIGHTLY', 1,
                      'system', NOW(), NOW(), NOW())
                    """,
                    UUID.randomUUID()))
        .hasMessageContaining("chk_integrity_async_job_nightly_admin");

    // Bad chain_status rejected.
    assertThatThrownBy(
            () ->
                jdbc.update(
                    """
                    INSERT INTO ezkey_integrity_async_job (
                      job_id, slot_key, job_type, status, "trigger", started_by_admin_id,
                      started_by_username, started_at, heartbeat_at, finished_at, chain_status)
                    VALUES (?, 'GLOBAL', 'VERIFY_CHAIN_RANGE', 'SUCCEEDED', 'MANUAL', 1,
                      'admin.docker', NOW(), NOW(), NOW(), 'BOGUS')
                    """,
                    UUID.randomUUID()))
        .hasMessageContaining("chk_integrity_async_job_chain_status");
  }

  @Test
  @DisplayName("T3: ShedLock probe — active, expired, and post-crash lock_until")
  void t3_shedLockProbeActiveExpiredAndCrashWindow() {
    assertThat(shedLockProbe.isNightlyLockActive()).isFalse();

    jdbc.update(
        """
        INSERT INTO ezkey_shedlock (name, lock_until, locked_at, locked_by)
        VALUES (?, NOW() + INTERVAL '30 minutes', NOW(), 'it-node')
        """,
        NightlyIntegrityShedLockProbe.NIGHTLY_LOCK_NAME);
    assertThat(shedLockProbe.isNightlyLockActive()).isTrue();

    jdbc.update(
        "UPDATE ezkey_shedlock SET lock_until = NOW() - INTERVAL '1 minute' WHERE name = ?",
        NightlyIntegrityShedLockProbe.NIGHTLY_LOCK_NAME);
    assertThat(shedLockProbe.isNightlyLockActive()).isFalse();

    // Crash residual: lock_until still in the future (≤ lockAtMostFor PT2H) → fail-closed active.
    jdbc.update(
        "UPDATE ezkey_shedlock SET lock_until = NOW() + INTERVAL '90 minutes' WHERE name = ?",
        NightlyIntegrityShedLockProbe.NIGHTLY_LOCK_NAME);
    assertThat(shedLockProbe.isNightlyLockActive()).isTrue();
  }

  private static IntegrityAsyncJob baseOperator(OffsetDateTime startedAt) {
    IntegrityAsyncJob job = new IntegrityAsyncJob();
    job.setJobId(UUID.randomUUID());
    job.setSlotKey(IntegrityAsyncJob.GLOBAL_SLOT_KEY);
    job.setJobType(IntegrityAsyncJobType.VERIFY_CHAIN_RANGE);
    job.setTrigger(IntegrityAsyncJobTrigger.MANUAL);
    job.setStartedByAdminId(1);
    job.setStartedByUsername("admin.docker");
    job.setStartedAt(startedAt);
    job.setHeartbeatAt(startedAt);
    job.setScopeFrom(startedAt.minusHours(1));
    job.setScopeTo(startedAt);
    return job;
  }

  private static IntegrityAsyncJob baseNightly(OffsetDateTime startedAt) {
    IntegrityAsyncJob job = new IntegrityAsyncJob();
    job.setJobId(UUID.randomUUID());
    job.setSlotKey(IntegrityAsyncJob.GLOBAL_SLOT_KEY);
    job.setJobType(IntegrityAsyncJobType.RUN_VALIDATION);
    job.setTrigger(IntegrityAsyncJobTrigger.NIGHTLY);
    job.setStartedByAdminId(null);
    job.setStartedByUsername("system");
    job.setStartedAt(startedAt);
    job.setHeartbeatAt(startedAt);
    job.setScopeFrom(startedAt.minusHours(24));
    job.setScopeTo(startedAt);
    job.setRaiseAlert(true);
    return job;
  }
}
