/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AuthAttemptAlertAfterCommitPostgresTest
 * Description: A throwing enrollment alert does not roll back INVALID or REJECTED.
 */

package org.ezkey.authattempt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.ezkey.alert.service.AlertService;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.authattempt.domain.AuthAttemptCreateRequest;
import org.ezkey.authattempt.domain.AuthAttemptRespondRequest;
import org.ezkey.authattempt.domain.AuthAttemptRespondResponse;
import org.ezkey.authattempt.domain.AuthenticationResult;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.ezkey.authattempt.domain.repository.AuthAttemptRepository;
import org.ezkey.config.EzkeyCoreProperties;
import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.exception.auth.AuthAttemptCreationCooldownException;
import org.ezkey.integration.domain.repository.IntegrationRepository;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Postgres proof that a failing enrollment alert cannot undo the status change or escape respond.
 *
 * <p>The alert double throws {@link DataIntegrityViolationException}, the failure mode of {@code
 * uq_alert_dedupe_key_open}. The status row is read back after the service method returns.
 *
 * @since 2026
 */
@SpringBootTest(classes = AuthAttemptAlertAfterCommitPostgresTest.TestConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
class AuthAttemptAlertAfterCommitPostgresTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer("postgres:18-alpine")
          .withDatabaseName("ezkey_auth_attempt_alert_after_commit");

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
  static class TestConfiguration {

    @Bean
    AlertService alertService() {
      AlertService alertService = mock(AlertService.class);
      doThrow(new DataIntegrityViolationException("uq_alert_dedupe_key_open"))
          .when(alertService)
          .raiseOrTouch(any(), any(), any(), any());
      return alertService;
    }

    @Bean
    AuthAttemptEnrollmentAlert enrollmentAlert(AlertService alertService) {
      return new AuthAttemptEnrollmentAlert(alertService);
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
      when(signatureService.generateProofToken()).thenReturn("proof-created");
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
        ObjectProvider<AuthRespondOwnershipRateLimiter> ownershipRateLimiter,
        AuthAttemptEnrollmentAlert enrollmentAlert) {
      return new AuthAttemptRespondService(
          authAttemptRepository,
          enrollmentRepository,
          signatureService,
          authAttemptTxHelper,
          mock(AuditLogService.class),
          ownershipRateLimiter,
          enrollmentAlert);
    }

    @Bean
    AuthAttemptCreateLock authAttemptCreateLock() {
      return new AuthAttemptCreateLock();
    }

    @Bean
    AuthAttemptService authAttemptService(
        AuthAttemptRepository authAttemptRepository,
        EnrollmentRepository enrollmentRepository,
        SignatureService signatureService,
        AuthAttemptCreateLock createLock) {
      EzkeyCoreProperties properties = new EzkeyCoreProperties();
      properties.getAuthAttempt().setCooldownThreshold(1);
      return new AuthAttemptService(
          authAttemptRepository,
          enrollmentRepository,
          signatureService,
          properties,
          mock(AuthAttemptPendingService.class),
          mock(AuthAttemptRespondService.class),
          mock(AuthAttemptWaitService.class),
          mock(IntegrationRepository.class),
          createLock,
          java.time.Clock.systemUTC());
    }
  }

  @Autowired private AuthAttemptRespondService respondService;
  @Autowired private AuthAttemptService authAttemptService;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void resetRows() {
    jdbc.execute("TRUNCATE TABLE ezkey_auth_attempt RESTART IDENTITY");
  }

  @Test
  @DisplayName("Wrong challenge stays INVALID when the alert throws, and cooldown counts it")
  void wrongChallenge_alertThrows_staysInvalidAndArmsCooldown() {
    int attemptId = insertReadAttempt(12);
    AuthAttemptRespondRequest request = new AuthAttemptRespondRequest();
    request.setAuthAttemptId(attemptId);
    request.setAuthAttemptAccepted(true);
    request.setAuthAttemptChallengeResponse(99);
    request.setAuthAttemptProofTokenSignedByDevice("device-sig");

    AuthAttemptRespondResponse response = respondService.respond(request, null);

    assertThat(response.getResult()).isEqualTo(AuthenticationResult.FAILED);
    assertThat(statusOf(attemptId)).isEqualTo("INVALID");
    assertThatThrownBy(() -> authAttemptService.create(createRequest()))
        .isInstanceOf(AuthAttemptCreationCooldownException.class);
  }

  @Test
  @DisplayName("Device deny stays REJECTED when the alert throws, and respond returns")
  void deny_alertThrows_staysRejected() {
    int attemptId = insertReadAttempt(null);
    AuthAttemptRespondRequest request = new AuthAttemptRespondRequest();
    request.setAuthAttemptId(attemptId);
    request.setAuthAttemptAccepted(false);
    request.setAuthAttemptProofTokenSignedByDevice("device-sig");

    AuthAttemptRespondResponse response = respondService.respond(request, null);

    assertThat(response.getResult()).isEqualTo(AuthenticationResult.DENIED);
    assertThat(statusOf(attemptId)).isEqualTo("REJECTED");
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

  private static AuthAttemptCreateRequest createRequest() {
    AuthAttemptCreateRequest request = new AuthAttemptCreateRequest();
    request.setEnrollmentId(7);
    request.setChallengeRequested(false);
    return request;
  }
}
