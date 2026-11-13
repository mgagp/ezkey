/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: EnrollmentExpiredAuditRequiresNewPostgresTest
 * Description: EXPIRED and its audit commit when the caller transaction rolls back (#1024).
 */

package org.ezkey.enrollment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import org.ezkey.audit.domain.repository.AuditLogRepository;
import org.ezkey.audit.integrity.AuditChainCheckpointRepository;
import org.ezkey.audit.integrity.AuditHmacService;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.exception.auth.EnrollmentVerifyFailedException;
import org.ezkey.security.EncryptionEntityListener;
import org.ezkey.security.EncryptionOperationsHolder;
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
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Postgres proof that expiry after a proven verify survives the caller's rollback.
 *
 * <p>{@link EnrollmentVerifyService} is {@code @Transactional} and throws after {@link
 * EnrollmentTxHelper#markExpiredAndEmitAudit}. That helper, and the audit insert inside it, use a
 * separate transaction. This test rolls the caller back and reads the committed row and audit
 * event. The audit HMAC signer is inactive so the insert does not need a key file.
 *
 * @since 2026
 */
@SpringBootTest(classes = EnrollmentExpiredAuditRequiresNewPostgresTest.TestConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
class EnrollmentExpiredAuditRequiresNewPostgresTest {

  private static final String EVENT_ACTION = "enrollment_expired_verify_rejected";

  @Container @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer("postgres:18-alpine")
          .withDatabaseName("ezkey_enrollment_expired_audit");

  @DynamicPropertySource
  static void datasourceProps(DynamicPropertyRegistry registry) {
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
    registry.add("spring.flyway.enabled", () -> "false");
    registry.add("spring.sql.init.mode", () -> "never");
  }

  @Autowired private JdbcTemplate jdbc;
  @Autowired private EnrollmentRepository enrollmentRepository;
  @Autowired private ExpiringCaller expiringCaller;

  @BeforeEach
  void resetRows() throws Exception {
    clearEncryptionBridge();
    jdbc.execute("CREATE SEQUENCE IF NOT EXISTS ezkey_audit_log_id_seq");
    jdbc.update("DELETE FROM ezkey_audit_log");
    jdbc.update("DELETE FROM ezkey_enrollment");
  }

  @Test
  @DisplayName("EXPIRED status and audit commit when the caller transaction rolls back")
  void expiredAuditCommitsWhenCallerRollsBack() {
    Integer keptId = seed("kept", "token-kept");
    Integer expiredId = seed("expires", "token-expires");
    OffsetDateTime expiresAt = OffsetDateTime.now().minusMinutes(2);

    assertThatThrownBy(() -> expiringCaller.dirtyThenExpireAndFail(keptId, expiredId, 7, expiresAt))
        .isInstanceOf(EnrollmentVerifyFailedException.class);

    assertThat(
            jdbc.queryForObject(
                "SELECT enrollment_name FROM ezkey_enrollment WHERE enrollment_id = ?",
                String.class,
                keptId))
        .isEqualTo("kept");
    assertThat(
            jdbc.queryForObject(
                "SELECT enrollment_status FROM ezkey_enrollment WHERE enrollment_id = ?",
                String.class,
                expiredId))
        .isEqualTo(EnrollmentStatus.EXPIRED.name());
    Integer auditCount =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM ezkey_audit_log WHERE enrollment_id = ? AND event_action = ?"
                + " AND event_type = 'ENROLLMENT_EXPIRED' AND api_name = 'AUTH_API'",
            Integer.class,
            expiredId,
            EVENT_ACTION);
    assertThat(auditCount).isEqualTo(1);
  }

  private Integer seed(String name, String proofToken) {
    Enrollment enrollment = new Enrollment();
    enrollment.setIntegrationId(7);
    enrollment.setEnrollmentName(name);
    enrollment.setStatus(EnrollmentStatus.BOUND);
    enrollment.setActive(false);
    enrollment.setEnrollmentProofToken(proofToken);
    enrollment.setEnrollmentChallenge(111);
    enrollment.setIntegrationPrivateKey("integration-private");
    enrollment.setIntegrationPublicKey("integration-public");
    enrollment.setAuthAttemptChallengeRequired(false);
    enrollment.setCreatedAt(OffsetDateTime.now().minusHours(1));
    enrollment.setExpiresAt(OffsetDateTime.now().minusMinutes(2));
    return enrollmentRepository.saveAndFlush(enrollment).getEnrollmentId();
  }

  private static void clearEncryptionBridge() throws Exception {
    Field field = EncryptionEntityListener.class.getDeclaredField("encryptionOperations");
    field.setAccessible(true);
    field.set(null, null);
    EncryptionOperationsHolder.clear();
  }

  /**
   * Caller transaction that must roll back without undoing expiry.
   *
   * @since 2026
   */
  @Service
  static class ExpiringCaller {

    private final EnrollmentRepository enrollmentRepository;
    private final EnrollmentTxHelper enrollmentTxHelper;

    ExpiringCaller(
        EnrollmentRepository enrollmentRepository, EnrollmentTxHelper enrollmentTxHelper) {
      this.enrollmentRepository = enrollmentRepository;
      this.enrollmentTxHelper = enrollmentTxHelper;
    }

    /**
     * Renames another enrollment, expires the target, then fails.
     *
     * @param otherId enrollment whose rename must disappear
     * @param expiredId enrollment marked EXPIRED in the separate transaction
     * @param integrationId integration recorded on the audit row
     * @param expiresAt expiry timestamp recorded on the audit row
     */
    @Transactional
    public void dirtyThenExpireAndFail(
        Integer otherId, Integer expiredId, Integer integrationId, OffsetDateTime expiresAt) {
      Enrollment other = enrollmentRepository.findById(otherId).orElseThrow();
      other.setEnrollmentName("outer-uncommitted");
      enrollmentRepository.saveAndFlush(other);
      enrollmentTxHelper.markExpiredAndEmitAudit(expiredId, integrationId, expiresAt, EVENT_ACTION);
      throw new EnrollmentVerifyFailedException("Enrollment invitation has expired");
    }
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @EntityScan(basePackageClasses = {Enrollment.class, org.ezkey.audit.domain.entity.AuditLog.class})
  @EnableJpaRepositories(
      basePackageClasses = {EnrollmentRepository.class, AuditLogRepository.class})
  @Import({EnrollmentTxHelper.class, AuditLogService.class, ExpiringCaller.class})
  static class TestConfiguration {

    @Bean
    AuditChainCheckpointRepository auditChainCheckpointRepository() {
      return mock(AuditChainCheckpointRepository.class);
    }

    @Bean
    AuditHmacService auditHmacService() {
      return mock(AuditHmacService.class);
    }
  }
}
