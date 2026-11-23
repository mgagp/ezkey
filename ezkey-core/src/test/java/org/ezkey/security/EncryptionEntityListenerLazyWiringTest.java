/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: EncryptionEntityListenerLazyWiringTest
 * Description: Context starts without a bean cycle and at-rest encryption stays active (#1458).
 */

package org.ezkey.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Characterization of the {@link EncryptionEntityListener} wiring (Spring Boot 4.2, #1458).
 *
 * <p>The encryption bean here depends on a JPA repository at construction, as {@code
 * EncryptionService} → {@code TinkKeyManager} → {@code KeysetBlobRepository} does in the APIs.
 * Under Spring Boot 4.2 an eager lookup from the listener made the context fail with a dependency
 * cycle through {@code entityManagerFactory}. This test proves two things at once: the context
 * starts, and the lazily resolved operations still encrypt on write and decrypt on read (no silent
 * plaintext fallback because the operations were never resolved).
 *
 * @since 2026
 */
@SpringBootTest(
    classes = EncryptionEntityListenerLazyWiringTest.TestConfiguration.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:listener_lazy_wiring;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.flyway.enabled=false",
      "spring.sql.init.mode=never"
    })
class EncryptionEntityListenerLazyWiringTest {

  private static final String PRIVATE_KEY = "MIIplain-integration-private-key";
  private static final String PROOF_TOKEN = "proof-token-lazy-wiring";

  @Autowired private EnrollmentRepository enrollmentRepository;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private RepositoryBackedEncryptionOperations encryptionOperations;

  @AfterAll
  static void clearBridge() {
    EncryptionOperationsHolder.clear();
  }

  @Test
  @DisplayName("Encrypted fields are written as ENC and read back as plaintext")
  void encryptsOnWriteAndDecryptsOnRead() {
    Integer id = enrollmentRepository.saveAndFlush(newEnrollment()).getEnrollmentId();

    String storedKey =
        jdbc.queryForObject(
            "SELECT integration_private_key FROM ezkey_enrollment WHERE enrollment_id = ?",
            String.class,
            id);
    String storedToken =
        jdbc.queryForObject(
            "SELECT enrollment_proof_token FROM ezkey_enrollment WHERE enrollment_id = ?",
            String.class,
            id);
    assertThat(storedKey).startsWith("ENC:7:").doesNotContain(PRIVATE_KEY);
    assertThat(storedToken).startsWith("ENC:7:").doesNotContain(PROOF_TOKEN);
    assertThat(encryptionOperations.encryptCalls.get()).isGreaterThanOrEqualTo(2);

    Enrollment reloaded = enrollmentRepository.findById(id).orElseThrow();
    assertThat(reloaded.getIntegrationPrivateKey()).isEqualTo(PRIVATE_KEY);
    assertThat(EncryptionOperationsHolder.get()).isSameAs(encryptionOperations);
  }

  private static Enrollment newEnrollment() {
    Enrollment enrollment = new Enrollment();
    enrollment.setIntegrationId(7);
    enrollment.setEnrollmentName("lazy-wiring");
    enrollment.setStatus(EnrollmentStatus.BOUND);
    enrollment.setActive(false);
    enrollment.setEnrollmentProofToken(PROOF_TOKEN);
    enrollment.setEnrollmentChallenge(111);
    enrollment.setIntegrationPrivateKey(PRIVATE_KEY);
    enrollment.setIntegrationPublicKey("integration-public");
    enrollment.setAuthAttemptChallengeRequired(false);
    enrollment.setCreatedAt(OffsetDateTime.now());
    enrollment.setExpiresAt(OffsetDateTime.now().plusHours(1));
    return enrollment;
  }

  /**
   * Reversible fake cipher whose construction needs a JPA repository, like {@code
   * EncryptionService}. Required mode is on, so an unencrypted read would throw.
   *
   * @since 2026
   */
  @Component
  static class RepositoryBackedEncryptionOperations implements EncryptionOperations {

    private static final String PREFIX = "ENC:7:";

    private final AtomicInteger encryptCalls = new AtomicInteger();

    RepositoryBackedEncryptionOperations(EnrollmentRepository repository) {
      // Constructor dependency on a repository reproduces the production bean graph.
      assertThat(repository).isNotNull();
    }

    @Override
    public boolean isEncryptionAvailable() {
      return true;
    }

    @Override
    public boolean isEncryptionRequired() {
      return true;
    }

    @Override
    public boolean isEncrypted(String value) {
      return value != null && value.startsWith(PREFIX);
    }

    @Override
    public String encrypt(String plaintext) {
      encryptCalls.incrementAndGet();
      return PREFIX
          + Base64.getEncoder().encodeToString(plaintext.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String decrypt(String encryptedValue) {
      return new String(
          Base64.getDecoder().decode(encryptedValue.substring(PREFIX.length())),
          StandardCharsets.UTF_8);
    }

    @Override
    public Long parseKeyIdFromPrefix(String encryptedValue) {
      return isEncrypted(encryptedValue) ? 7L : null;
    }
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @EntityScan(basePackageClasses = Enrollment.class)
  @EnableJpaRepositories(basePackageClasses = EnrollmentRepository.class)
  @Import({EncryptionEntityListener.class, RepositoryBackedEncryptionOperations.class})
  static class TestConfiguration {}
}
