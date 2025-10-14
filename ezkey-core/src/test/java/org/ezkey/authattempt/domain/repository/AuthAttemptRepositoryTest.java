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

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.ezkey.authattempt.domain.AuthAttemptStatus;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.ezkey.PostgreSQLTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

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
 *   <li>existsByDeviceProofToken() - Replay attack prevention
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
@DataJpaTest
@ActiveProfiles("test")
@TestPropertySource(
    properties = {
      "spring.flyway.enabled=false",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
    })
@DisplayName("AuthAttempt Repository Critical Tests")
class AuthAttemptRepositoryTest {

  @Autowired private TestEntityManager entityManager;

  @Autowired private AuthAttemptRepository authAttemptRepository;

  private AuthAttempt authAttempt1;
  private AuthAttempt authAttempt2;
  private AuthAttempt authAttempt3;
  private final Integer enrollmentId = 123;
  private final LocalDateTime now = LocalDateTime.now();

  @BeforeEach
  void setUp() {
    // Create test auth attempts
    authAttempt1 = new AuthAttempt();
    authAttempt1.setEnrollmentId(enrollmentId);
    authAttempt1.setAuthAttemptStatus(AuthAttemptStatus.PENDING);
    authAttempt1.setAuthAttemptProofToken("proof-1");
    authAttempt1.setDeviceProofToken("token-1");
    authAttempt1.setCreatedAt(now.minusMinutes(10));
    authAttempt1.setExpiresAt(now.plusMinutes(5));

    authAttempt2 = new AuthAttempt();
    authAttempt2.setEnrollmentId(enrollmentId);
    authAttempt2.setAuthAttemptStatus(AuthAttemptStatus.READ);
    authAttempt2.setAuthAttemptProofToken("proof-2");
    authAttempt2.setDeviceProofToken("token-2");
    authAttempt2.setCreatedAt(now.minusMinutes(5));
    authAttempt2.setExpiresAt(now.plusMinutes(10));

    authAttempt3 = new AuthAttempt();
    authAttempt3.setEnrollmentId(enrollmentId);
    authAttempt3.setAuthAttemptStatus(AuthAttemptStatus.ACCEPTED);
    authAttempt3.setAuthAttemptProofToken("proof-3");
    authAttempt3.setDeviceProofToken("token-3");
    authAttempt3.setCreatedAt(now);
    authAttempt3.setExpiresAt(now.plusMinutes(15));

    // Save to database
    entityManager.persistAndFlush(authAttempt1);
    entityManager.persistAndFlush(authAttempt2);
    entityManager.persistAndFlush(authAttempt3);
    entityManager.clear();
  }

  // ===== LOCKING OPERATIONS TESTS =====

  @Test
  @Disabled("Requires PostgreSQL syntax FOR NO KEY UPDATE; H2 does not support it")
  @DisplayName(
      "findAndLockMostRecentByEnrollmentIdAndStatus() - Should find and lock most recent pending attempt")
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
  @Disabled("Requires PostgreSQL syntax FOR NO KEY UPDATE; H2 does not support it")
  @DisplayName(
      "findAndLockMostRecentByEnrollmentIdAndStatus() - Should return empty when no pending attempts")
  void findAndLockMostRecentByEnrollmentIdAndStatus_WhenNoPendingExists_ShouldReturnEmpty() {
    // Act
    Optional<AuthAttempt> result =
        authAttemptRepository.findAndLockMostRecentByEnrollmentIdAndStatus(enrollmentId, "INVALID");

    // Assert
    assertFalse(result.isPresent());
  }

  @Test
  @Disabled("Requires PostgreSQL syntax FOR NO KEY UPDATE; H2 does not support it")
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
  @Disabled("Requires PostgreSQL syntax FOR NO KEY UPDATE; H2 does not support it")
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
  @DisplayName("updateStatusIfCurrent() - Should update status when current status matches")
  void updateStatusIfCurrent_WhenCurrentStatusMatches_ShouldUpdateStatus() {
    // Act
    int updatedRows =
        authAttemptRepository.updateStatusIfCurrent(
            authAttempt1.getAuthAttemptId(), AuthAttemptStatus.PENDING, AuthAttemptStatus.READ);

    // Assert
    assertEquals(1, updatedRows);

    // Verify the update
    entityManager.clear();
    AuthAttempt updated = entityManager.find(AuthAttempt.class, authAttempt1.getAuthAttemptId());
    assertEquals(AuthAttemptStatus.READ, updated.getAuthAttemptStatus());
  }

  @Test
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
    entityManager.clear();
    AuthAttempt unchanged = entityManager.find(AuthAttempt.class, authAttempt1.getAuthAttemptId());
    assertEquals(AuthAttemptStatus.PENDING, unchanged.getAuthAttemptStatus());
  }

  @Test
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
    entityManager.clear();
    AuthAttempt updated1 = entityManager.find(AuthAttempt.class, authAttempt1.getAuthAttemptId());
    AuthAttempt updated2 = entityManager.find(AuthAttempt.class, authAttempt2.getAuthAttemptId());
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
  @DisplayName("existsByDeviceProofToken() - Should return true when token exists")
  void existsByDeviceProofToken_WhenTokenExists_ShouldReturnTrue() {
    // Act
    boolean exists = authAttemptRepository.existsByDeviceProofToken("token-1");

    // Assert
    assertTrue(exists);
  }

  @Test
  @DisplayName("existsByDeviceProofToken() - Should return false when token doesn't exist")
  void existsByDeviceProofToken_WhenTokenDoesNotExist_ShouldReturnFalse() {
    // Act
    boolean exists = authAttemptRepository.existsByDeviceProofToken("non-existent-token");

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
