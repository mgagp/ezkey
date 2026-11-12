/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AuthAttemptCreateConcurrencyPostgresTest
 * Description: Concurrent REFUSE creates leave exactly one live attempt.
 */

package org.ezkey.authattempt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.ezkey.authattempt.domain.AuthAttemptCreateRequest;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.ezkey.authattempt.domain.repository.AuthAttemptRepository;
import org.ezkey.config.EzkeyCoreProperties;
import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.exception.auth.AuthAttemptPendingExistsException;
import org.ezkey.integration.domain.PendingAttemptPolicy;
import org.ezkey.integration.domain.entity.Integration;
import org.ezkey.integration.domain.repository.IntegrationRepository;
import org.ezkey.signature.SignatureService;
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
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Concurrent creates under REFUSE: the advisory lock lets one transaction insert, and the others
 * observe that row.
 *
 * @since 2026
 */
@SpringBootTest(classes = AuthAttemptCreateConcurrencyPostgresTest.TestConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
class AuthAttemptCreateConcurrencyPostgresTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer("postgres:18-alpine")
          .withDatabaseName("ezkey_auth_attempt_concurrency");

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
    AuthAttemptCreateLock authAttemptCreateLock() {
      return new AuthAttemptCreateLock();
    }

    @Bean
    AuthAttemptService authAttemptService(
        AuthAttemptRepository authAttemptRepository, AuthAttemptCreateLock createLock) {
      Enrollment enrollment = new Enrollment();
      enrollment.setEnrollmentId(7);
      enrollment.setIntegrationId(3);
      enrollment.setActive(true);
      enrollment.setStatus(EnrollmentStatus.VERIFIED);
      enrollment.setAuthAttemptChallengeRequired(false);
      EnrollmentRepository enrollmentRepository = mock(EnrollmentRepository.class);
      when(enrollmentRepository.findById(7)).thenReturn(Optional.of(enrollment));

      Integration integration = new Integration();
      integration.setPendingAttemptPolicy(PendingAttemptPolicy.REFUSE);
      IntegrationRepository integrationRepository = mock(IntegrationRepository.class);
      when(integrationRepository.findById(3)).thenReturn(Optional.of(integration));

      AtomicInteger tokens = new AtomicInteger();
      SignatureService signatureService = mock(SignatureService.class);
      when(signatureService.generateProofToken())
          .thenAnswer(invocation -> "proof-" + tokens.incrementAndGet() + "-" + UUID.randomUUID());

      return new AuthAttemptService(
          authAttemptRepository,
          enrollmentRepository,
          signatureService,
          new EzkeyCoreProperties(),
          mock(AuthAttemptPendingService.class),
          mock(AuthAttemptRespondService.class),
          mock(AuthAttemptWaitService.class),
          integrationRepository,
          createLock,
          java.time.Clock.systemUTC());
    }
  }

  @Autowired private AuthAttemptService authAttemptService;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void resetRows() {
    jdbc.execute("TRUNCATE TABLE ezkey_auth_attempt RESTART IDENTITY");
  }

  @Test
  @DisplayName("Eight concurrent REFUSE creates leave exactly one PENDING attempt")
  void concurrentRefuse_oneWinner() throws Exception {
    int threads = 8;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    try {
      List<Callable<Object>> calls = new ArrayList<>();
      for (int i = 0; i < threads; i++) {
        calls.add(() -> authAttemptService.create(request()));
      }
      List<Future<Object>> futures = pool.invokeAll(calls);
      int wins = 0;
      int refused = 0;
      for (Future<Object> future : futures) {
        try {
          future.get();
          wins++;
        } catch (Exception ex) {
          Throwable cause = ex.getCause() == null ? ex : ex.getCause();
          assertThat(cause).isInstanceOf(AuthAttemptPendingExistsException.class);
          assertThat(((AuthAttemptPendingExistsException) cause).getRetryAfterSeconds())
              .isBetween(1L, 120L);
          refused++;
        }
      }
      assertThat(wins).isEqualTo(1);
      assertThat(refused).isEqualTo(threads - 1);
    } finally {
      pool.shutdownNow();
    }

    Integer pending =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM ezkey_auth_attempt WHERE auth_attempt_status = 'PENDING'",
            Integer.class);
    assertThat(pending).isEqualTo(1);
  }

  private static AuthAttemptCreateRequest request() {
    AuthAttemptCreateRequest request = new AuthAttemptCreateRequest();
    request.setEnrollmentId(7);
    request.setChallengeRequested(false);
    return request;
  }
}
