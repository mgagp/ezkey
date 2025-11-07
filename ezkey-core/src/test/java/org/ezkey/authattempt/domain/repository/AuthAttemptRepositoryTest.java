/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AuthAttemptRepositoryTest
 * Description: Critical unit tests for AuthAttemptRepository complex queries and locking mechanisms.
 */

package org.ezkey.authattempt.domain.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.ezkey.PostgreSQLTestBase;
import org.ezkey.authattempt.domain.AuthAttemptStatus;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.ezkey.security.SensitiveDataHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Critical unit tests for {@link AuthAttemptRepository}.
 *
 * <p>This test class provides comprehensive coverage of the AuthAttemptRepository focusing on
 * complex queries, locking mechanisms, and atomic operations. Tests cover critical security
 * operations like locking, status updates, and supersession logic.
 *
 * <p><b>Critical Test Coverage:</b>
 *
 * <ul>
 *   <li>findAndLockMostRecentByEnrollmentIdAndStatus() - Atomic locking operations
 *   <li>updateStatusIfCurrent() - Conditional status updates
 *   <li>findByEnrollmentIdAndStatusIn() - Supersession logic queries
 *   <li>updateStatusForMultipleAttempts() - Batch status updates
 *   <li>existsByDeviceProofTokenHash() - Replay attack prevention
 *   <li>findNewerAttemptByEnrollmentId() - Supersession detection
 * </ul>
 *
 * <p><b>Security Focus:</b> These tests validate critical security aspects including atomic
 * locking, race condition prevention, supersession logic, and replay attack prevention mechanisms.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see AuthAttemptRepository
 * @see AuthAttempt
 * @see AuthAttemptStatus
 */
@DisplayName("AuthAttempt Repository Critical Tests")
class AuthAttemptRepositoryTest extends PostgreSQLTestBase {

  @Autowired private AuthAttemptRepository authAttemptRepository;

  @Autowired
  private org.ezkey.enrollment.domain.repository.EnrollmentRepository enrollmentRepository;

  @Autowired
  private org.ezkey.integration.domain.repository.IntegrationRepository integrationRepository;

  @Autowired private jakarta.persistence.EntityManager entityManager;

  private AuthAttempt authAttempt1;
  private AuthAttempt authAttempt2;
  private AuthAttempt authAttempt3;
  private Integer enrollmentId;
  private final OffsetDateTime now = OffsetDateTime.now();

  @BeforeEach
  void setUp() {
    // Create test integration first (required by enrollment foreign key)
    org.ezkey.integration.domain.entity.Integration integration =
        new org.ezkey.integration.domain.entity.Integration();
    integration.setLogo("test-logo.png");
    integration.setActive(true);
    integration.setCreatedAt(now);
    integration = integrationRepository.save(integration);

    // Create test enrollment (required by auth_attempt foreign key)
    org.ezkey.enrollment.domain.entity.Enrollment enrollment =
        new org.ezkey.enrollment.domain.entity.Enrollment();
    enrollment.setIntegrationId(integration.getId());
    enrollment.setEnrollmentName("Test Enrollment");
    enrollment.setEnrollmentProofToken("test-enrollment-proof-" + System.currentTimeMillis());
    enrollment.setStatus(org.ezkey.enrollment.domain.EnrollmentStatus.VERIFIED);
    enrollment.setActive(true);
    enrollment.setAuthAttemptChallengeRequired(false);
    enrollment.setIntegrationPublicKey("integration-public-key");
    enrollment.setIntegrationPrivateKey("integration-private-key");
    enrollment.setDevicePublicKey("device-public-key");
    enrollment.setCreatedAt(now);
    enrollment = enrollmentRepository.save(enrollment);
    enrollmentId = enrollment.getEnrollmentId();

    // Create test auth attempts with unique tokens to avoid hash collisions
    String uniqueSuffix = System.nanoTime() + "-" + Thread.currentThread().getId();

    authAttempt1 = new AuthAttempt();
    authAttempt1.setEnrollmentId(enrollmentId);
    authAttempt1.setAuthAttemptStatus(AuthAttemptStatus.PENDING);
    authAttempt1.setAuthAttemptProofToken("proof-1-" + uniqueSuffix);
    authAttempt1.setDeviceProofToken("token-1-" + uniqueSuffix);
    authAttempt1.setCreatedAt(now.minusMinutes(10));
    authAttempt1.setExpiresAt(now.plusMinutes(5));

    authAttempt2 = new AuthAttempt();
    authAttempt2.setEnrollmentId(enrollmentId);
    authAttempt2.setAuthAttemptStatus(AuthAttemptStatus.READ);
    authAttempt2.setAuthAttemptProofToken("proof-2-" + uniqueSuffix);
    authAttempt2.setDeviceProofToken("token-2-" + uniqueSuffix);
    authAttempt2.setCreatedAt(now.minusMinutes(5));
    authAttempt2.setExpiresAt(now.plusMinutes(10));

    authAttempt3 = new AuthAttempt();
    authAttempt3.setEnrollmentId(enrollmentId);
    authAttempt3.setAuthAttemptStatus(AuthAttemptStatus.ACCEPTED);
    authAttempt3.setAuthAttemptProofToken("proof-3-" + uniqueSuffix);
    authAttempt3.setDeviceProofToken("token-3-" + uniqueSuffix);
    authAttempt3.setCreatedAt(now);
    authAttempt3.setExpiresAt(now.plusMinutes(15));

    // Save to database
    authAttempt1 = authAttemptRepository.save(authAttempt1);
    authAttempt2 = authAttemptRepository.save(authAttempt2);
    authAttempt3 = authAttemptRepository.save(authAttempt3);
  }

  // ===== LOCKING OPERATIONS TESTS =====

  @Test
  @DisplayName(
      "findAndLockMostRecentByEnrollmentIdAndStatus() - Should find and lock most recent pending"
          + " attempt")
  void findAndLockMostRecentByEnrollmentIdAndStatus_WhenPendingExists_ShouldReturnMostRecent() {
    // Act
    Optional<AuthAttempt> result =
        authAttemptRepository.findAndLockMostRecentByEnrollmentIdAndStatus(enrollmentId, "PENDING");

    // Assert
    assertTrue(result.isPresent());
    assertEquals(authAttempt1.getAuthAttemptId(), result.get().getAuthAttemptId());
    assertEquals(AuthAttemptStatus.PENDING, result.get().getAuthAttemptStatus());
  }

  @Test
  @DisplayName(
      "findAndLockMostRecentByEnrollmentIdAndStatus() - Should return empty when no pending"
          + " attempts")
  void findAndLockMostRecentByEnrollmentIdAndStatus_WhenNoPendingExists_ShouldReturnEmpty() {
    // Act
    Optional<AuthAttempt> result =
        authAttemptRepository.findAndLockMostRecentByEnrollmentIdAndStatus(enrollmentId, "INVALID");

    // Assert
    assertFalse(result.isPresent());
  }

  @Test
  @DisplayName(
      "findAndLockMostRecentValidByEnrollmentIdAndStatus() - Should find valid non-expired attempt")
  void findAndLockMostRecentValidByEnrollmentIdAndStatus_WhenValidExists_ShouldReturnValid() {
    // Act
    Optional<AuthAttempt> result =
        authAttemptRepository.findAndLockMostRecentValidByEnrollmentIdAndStatus(
            enrollmentId, "PENDING", now.plusMinutes(1));

    // Assert
    assertTrue(result.isPresent());
    assertEquals(authAttempt1.getAuthAttemptId(), result.get().getAuthAttemptId());
    assertEquals(AuthAttemptStatus.PENDING, result.get().getAuthAttemptStatus());
  }

  @Test
  @DisplayName(
      "findAndLockMostRecentValidByEnrollmentIdAndStatus() - Should return empty when expired")
  void findAndLockMostRecentValidByEnrollmentIdAndStatus_WhenExpired_ShouldReturnEmpty() {
    // Act
    Optional<AuthAttempt> result =
        authAttemptRepository.findAndLockMostRecentValidByEnrollmentIdAndStatus(
            enrollmentId, "PENDING", now.plusMinutes(10));

    // Assert
    assertFalse(result.isPresent());
  }

  // ===== STATUS UPDATE TESTS =====

  @Test
  @org.springframework.transaction.annotation.Transactional
  @DisplayName("updateStatusIfCurrent() - Should update status when current status matches")
  void updateStatusIfCurrent_WhenCurrentStatusMatches_ShouldUpdateStatus() {
    // Act
    int updatedRows =
        authAttemptRepository.updateStatusIfCurrent(
            authAttempt1.getAuthAttemptId(), AuthAttemptStatus.PENDING, AuthAttemptStatus.READ);

    // Assert
    assertEquals(1, updatedRows);

    // Verify the update
    entityManager.flush();
    entityManager.clear();
    AuthAttempt updated =
        authAttemptRepository.findById(authAttempt1.getAuthAttemptId()).orElseThrow();
    assertEquals(AuthAttemptStatus.READ, updated.getAuthAttemptStatus());
  }

  @Test
  @org.springframework.transaction.annotation.Transactional
  @DisplayName("updateStatusIfCurrent() - Should not update when current status doesn't match")
  void updateStatusIfCurrent_WhenCurrentStatusDoesNotMatch_ShouldNotUpdate() {
    // Act
    int updatedRows =
        authAttemptRepository.updateStatusIfCurrent(
            authAttempt1.getAuthAttemptId(),
            AuthAttemptStatus.READ, // Wrong current status
            AuthAttemptStatus.ACCEPTED);

    // Assert
    assertEquals(0, updatedRows);

    // Verify no update occurred
    entityManager.flush();
    entityManager.clear();
    AuthAttempt unchanged =
        authAttemptRepository.findById(authAttempt1.getAuthAttemptId()).orElseThrow();
    assertEquals(AuthAttemptStatus.PENDING, unchanged.getAuthAttemptStatus());
  }

  @Test
  @org.springframework.transaction.annotation.Transactional
  @DisplayName("updateStatusForMultipleAttempts() - Should update multiple attempts to expired")
  void updateStatusForMultipleAttempts_WhenMultipleIds_ShouldUpdateAll() {
    // Arrange
    List<Integer> ids =
        Arrays.asList(authAttempt1.getAuthAttemptId(), authAttempt2.getAuthAttemptId());

    // Act
    int updatedRows =
        authAttemptRepository.updateStatusForMultipleAttempts(ids, AuthAttemptStatus.EXPIRED);

    // Assert
    assertEquals(2, updatedRows);

    // Verify the updates
    entityManager.flush();
    entityManager.clear();
    AuthAttempt updated1 =
        authAttemptRepository.findById(authAttempt1.getAuthAttemptId()).orElseThrow();
    AuthAttempt updated2 =
        authAttemptRepository.findById(authAttempt2.getAuthAttemptId()).orElseThrow();
    assertEquals(AuthAttemptStatus.EXPIRED, updated1.getAuthAttemptStatus());
    assertEquals(AuthAttemptStatus.EXPIRED, updated2.getAuthAttemptStatus());
  }

  // ===== SUPERSESSION LOGIC TESTS =====

  @Test
  @DisplayName("findByEnrollmentIdAndStatusIn() - Should find non-final attempts for supersession")
  void findByEnrollmentIdAndStatusIn_WhenNonFinalStatuses_ShouldReturnMatchingAttempts() {
    // Arrange
    List<AuthAttemptStatus> nonFinalStatuses =
        Arrays.asList(AuthAttemptStatus.PENDING, AuthAttemptStatus.READ);

    // Act
    List<AuthAttempt> result =
        authAttemptRepository.findByEnrollmentIdAndStatusIn(enrollmentId, nonFinalStatuses);

    // Assert
    assertEquals(2, result.size());
    assertTrue(
        result.stream()
            .anyMatch(a -> a.getAuthAttemptId().equals(authAttempt1.getAuthAttemptId())));
    assertTrue(
        result.stream()
            .anyMatch(a -> a.getAuthAttemptId().equals(authAttempt2.getAuthAttemptId())));
  }

  @Test
  @DisplayName(
      "findNewerAttemptByEnrollmentId() - Should find newer attempt for supersession detection")
  void findNewerAttemptByEnrollmentId_WhenNewerExists_ShouldReturnNewerAttempt() {
    // Act
    Optional<AuthAttempt> result =
        authAttemptRepository.findNewerAttemptByEnrollmentId(enrollmentId, now.minusMinutes(4));

    // Assert
    assertTrue(result.isPresent());
    // With threshold now-4m, the newer attempt is authAttempt3 (created at now)
    assertEquals(authAttempt3.getAuthAttemptId(), result.get().getAuthAttemptId());
  }

  @Test
  @DisplayName("findNewerAttemptByEnrollmentId() - Should return empty when no newer attempt")
  void findNewerAttemptByEnrollmentId_WhenNoNewerExists_ShouldReturnEmpty() {
    // Act
    Optional<AuthAttempt> result =
        authAttemptRepository.findNewerAttemptByEnrollmentId(enrollmentId, now.plusMinutes(1));

    // Assert
    assertFalse(result.isPresent());
  }

  // ===== REPLAY ATTACK PREVENTION TESTS =====

  @Test
  @DisplayName("existsByDeviceProofTokenHash() - Should return true when token exists")
  void existsByDeviceProofTokenHash_WhenTokenExists_ShouldReturnTrue() {
    // Act
    boolean exists =
        authAttemptRepository.existsByDeviceProofTokenHash(
            SensitiveDataHasher.sha256Hex(authAttempt1.getDeviceProofToken()));

    // Assert
    assertTrue(exists);
  }

  @Test
  @DisplayName("existsByDeviceProofTokenHash() - Should return false when token doesn't exist")
  void existsByDeviceProofTokenHash_WhenTokenDoesNotExist_ShouldReturnFalse() {
    // Act
    boolean exists =
        authAttemptRepository.existsByDeviceProofTokenHash(
            SensitiveDataHasher.sha256Hex("non-existent-token"));

    // Assert
    assertFalse(exists);
  }

  // ===== STANDARD QUERY TESTS =====

  @Test
  @DisplayName(
      "findByEnrollmentIdOrderByAuthAttemptIdDesc() - Should return attempts ordered by ID desc")
  void findByEnrollmentIdOrderByAuthAttemptIdDesc_WhenMultipleAttempts_ShouldReturnOrdered() {
    // Act
    List<AuthAttempt> result =
        authAttemptRepository.findByEnrollmentIdOrderByAuthAttemptIdDesc(enrollmentId);

    // Assert
    assertEquals(3, result.size());
    // Should be ordered by ID descending (newest first)
    assertTrue(result.get(0).getAuthAttemptId() > result.get(1).getAuthAttemptId());
    assertTrue(result.get(1).getAuthAttemptId() > result.get(2).getAuthAttemptId());
  }

  @Test
  @DisplayName("findMostRecentByEnrollmentIdAndStatus() - Should return most recent by status")
  void findMostRecentByEnrollmentIdAndStatus_WhenStatusExists_ShouldReturnMostRecent() {
    // Act
    Optional<AuthAttempt> result =
        authAttemptRepository.findMostRecentByEnrollmentIdAndStatus(
            enrollmentId, AuthAttemptStatus.PENDING);

    // Assert
    assertTrue(result.isPresent());
    assertEquals(authAttempt1.getAuthAttemptId(), result.get().getAuthAttemptId());
    assertEquals(AuthAttemptStatus.PENDING, result.get().getAuthAttemptStatus());
  }

  @Test
  @DisplayName(
      "findMostRecentValidByEnrollmentIdAndStatus() - Should return most recent valid attempt")
  void findMostRecentValidByEnrollmentIdAndStatus_WhenValidExists_ShouldReturnMostRecent() {
    // Act
    Optional<AuthAttempt> result =
        authAttemptRepository.findMostRecentValidByEnrollmentIdAndStatus(
            enrollmentId, AuthAttemptStatus.PENDING, now.plusMinutes(1));

    // Assert
    assertTrue(result.isPresent());
    assertEquals(authAttempt1.getAuthAttemptId(), result.get().getAuthAttemptId());
    assertEquals(AuthAttemptStatus.PENDING, result.get().getAuthAttemptStatus());
  }

  @Test
  @DisplayName("findAllByEnrollmentId() - Should return all attempts for enrollment")
  void findAllByEnrollmentId_WhenMultipleAttempts_ShouldReturnAll() {
    // Act
    List<AuthAttempt> result = authAttemptRepository.findAllByEnrollmentId(enrollmentId);

    // Assert
    assertEquals(3, result.size());
    assertTrue(result.stream().allMatch(a -> a.getEnrollmentId().equals(enrollmentId)));
  }
}
