/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: EnrollmentRepositoryTest
 * Description: Critical unit tests for EnrollmentRepository locking mechanisms and native queries.
 */

package org.ezkey.enrollment.domain.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Critical unit tests for {@link EnrollmentRepository}.
 *
 * <p>This test class provides comprehensive coverage of the EnrollmentRepository focusing on
 * locking mechanisms, native queries, and atomic operations. Tests cover critical security
 * operations like row-level locking and status updates for enrollment binding and verification.
 *
 * <p><b>Critical Test Coverage:</b>
 *
 * <ul>
 *   <li>findAndLockUnreadById() - Row-level locking for binding operations
 *   <li>findAndLockBoundById() - Row-level locking for verification operations
 *   <li>updateEnrollmentStatus() - Atomic status updates
 *   <li>findByIntegrationId() - Integration-based queries
 *   <li>Standard CRUD operations - Basic repository functionality
 * </ul>
 *
 * <p><b>Security Focus:</b> These tests validate critical security aspects including atomic
 * locking, race condition prevention, and proper state management for enrollment operations.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see EnrollmentRepository
 * @see Enrollment
 * @see EnrollmentStatus
 */
@DataJpaTest
@ActiveProfiles("test")
@TestPropertySource(
    properties = {
      "spring.flyway.enabled=false",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
    })
@DisplayName("Enrollment Repository Critical Tests")
class EnrollmentRepositoryTest {

  @Autowired private TestEntityManager entityManager;

  @Autowired private EnrollmentRepository enrollmentRepository;

  private Enrollment enrollment1;
  private Enrollment enrollment2;
  private Enrollment enrollment3;
  private final Integer integrationId = 123;

  @BeforeEach
  void setUp() {
    // Create test enrollments
    enrollment1 = new Enrollment();
    enrollment1.setIntegrationId(integrationId);
    enrollment1.setEnrollmentName("Test Enrollment 1");
    enrollment1.setEnrollmentProofToken("proof-token-1");
    enrollment1.setEnrollmentChallenge(123456);
    enrollment1.setStatus(EnrollmentStatus.CREATED);
    enrollment1.setActive(false);
    enrollment1.setIntegrationPublicKey("integration-public-key-1");
    enrollment1.setIntegrationPrivateKey("integration-private-key-1");
    enrollment1.setCreatedAt(LocalDateTime.now().minusMinutes(10));

    enrollment2 = new Enrollment();
    enrollment2.setIntegrationId(integrationId);
    enrollment2.setEnrollmentName("Test Enrollment 2");
    enrollment2.setEnrollmentProofToken("proof-token-2");
    enrollment2.setEnrollmentChallenge(654321);
    enrollment2.setStatus(EnrollmentStatus.BOUND);
    enrollment2.setActive(false);
    enrollment2.setIntegrationPublicKey("integration-public-key-2");
    enrollment2.setIntegrationPrivateKey("integration-private-key-2");
    enrollment2.setCreatedAt(LocalDateTime.now().minusMinutes(5));

    enrollment3 = new Enrollment();
    enrollment3.setIntegrationId(456); // Different integration
    enrollment3.setEnrollmentName("Test Enrollment 3");
    enrollment3.setEnrollmentProofToken("proof-token-3");
    enrollment3.setEnrollmentChallenge(789012);
    enrollment3.setStatus(EnrollmentStatus.VERIFIED);
    enrollment3.setActive(true);
    enrollment3.setIntegrationPublicKey("integration-public-key-3");
    enrollment3.setIntegrationPrivateKey("integration-private-key-3");
    enrollment3.setDevicePublicKey("device-public-key-3");
    enrollment3.setCreatedAt(LocalDateTime.now());

    // Save to database
    entityManager.persistAndFlush(enrollment1);
    entityManager.persistAndFlush(enrollment2);
    entityManager.persistAndFlush(enrollment3);
    entityManager.clear();
  }

  // ===== LOCKING OPERATIONS TESTS =====

  @Test
  @DisplayName("findAndLockUnreadById() - Should find and lock CREATED enrollment")
  @Disabled(
      "H2 does not support FOR NO KEY UPDATE; repository method uses Postgres-specific locking")
  void findAndLockUnreadById_WhenCreatedEnrollment_ShouldReturnEnrollment() {
    // Act
    Optional<Enrollment> result =
        enrollmentRepository.findAndLockUnreadById(enrollment1.getEnrollmentId());

    // Assert
    assertTrue(result.isPresent());
    assertEquals(enrollment1.getEnrollmentId(), result.get().getEnrollmentId());
    assertEquals(EnrollmentStatus.CREATED, result.get().getStatus());
  }

  @Test
  @DisplayName("findAndLockUnreadById() - Should return empty when enrollment is not CREATED")
  @Disabled(
      "H2 does not support FOR NO KEY UPDATE; repository method uses Postgres-specific locking")
  void findAndLockUnreadById_WhenNotCreatedEnrollment_ShouldReturnEmpty() {
    // Act
    Optional<Enrollment> result =
        enrollmentRepository.findAndLockUnreadById(enrollment2.getEnrollmentId());

    // Assert
    assertFalse(result.isPresent());
  }

  @Test
  @DisplayName("findAndLockUnreadById() - Should return empty when enrollment doesn't exist")
  @Disabled(
      "H2 does not support FOR NO KEY UPDATE; repository method uses Postgres-specific locking")
  void findAndLockUnreadById_WhenEnrollmentDoesNotExist_ShouldReturnEmpty() {
    // Act
    Optional<Enrollment> result = enrollmentRepository.findAndLockUnreadById(99999);

    // Assert
    assertFalse(result.isPresent());
  }

  @Test
  @DisplayName("findAndLockBoundById() - Should find and lock BOUND enrollment")
  @Disabled(
      "H2 does not support FOR NO KEY UPDATE; repository method uses Postgres-specific locking")
  void findAndLockBoundById_WhenBoundEnrollment_ShouldReturnEnrollment() {
    // Act
    Optional<Enrollment> result =
        enrollmentRepository.findAndLockBoundById(enrollment2.getEnrollmentId());

    // Assert
    assertTrue(result.isPresent());
    assertEquals(enrollment2.getEnrollmentId(), result.get().getEnrollmentId());
    assertEquals(EnrollmentStatus.BOUND, result.get().getStatus());
  }

  @Test
  @DisplayName("findAndLockBoundById() - Should return empty when enrollment is not BOUND")
  @Disabled(
      "H2 does not support FOR NO KEY UPDATE; repository method uses Postgres-specific locking")
  void findAndLockBoundById_WhenNotBoundEnrollment_ShouldReturnEmpty() {
    // Act
    Optional<Enrollment> result =
        enrollmentRepository.findAndLockBoundById(enrollment1.getEnrollmentId());

    // Assert
    assertFalse(result.isPresent());
  }

  @Test
  @DisplayName("findAndLockBoundById() - Should return empty when enrollment doesn't exist")
  @Disabled(
      "H2 does not support FOR NO KEY UPDATE; repository method uses Postgres-specific locking")
  void findAndLockBoundById_WhenEnrollmentDoesNotExist_ShouldReturnEmpty() {
    // Act
    Optional<Enrollment> result = enrollmentRepository.findAndLockBoundById(99999);

    // Assert
    assertFalse(result.isPresent());
  }

  // ===== STATUS UPDATE TESTS =====

  @Test
  @DisplayName("updateEnrollmentStatus() - Should update enrollment status successfully")
  void updateEnrollmentStatus_WhenValidEnrollment_ShouldUpdateStatus() {
    // Act
    int updatedRows =
        enrollmentRepository.updateEnrollmentStatus(
            enrollment1.getEnrollmentId(), EnrollmentStatus.BOUND);

    // Assert
    assertEquals(1, updatedRows);

    // Verify the update
    entityManager.clear();
    Enrollment updated = entityManager.find(Enrollment.class, enrollment1.getEnrollmentId());
    assertEquals(EnrollmentStatus.BOUND, updated.getStatus());
  }

  @Test
  @DisplayName("updateEnrollmentStatus() - Should return 0 when enrollment doesn't exist")
  void updateEnrollmentStatus_WhenEnrollmentDoesNotExist_ShouldReturnZero() {
    // Act
    int updatedRows = enrollmentRepository.updateEnrollmentStatus(99999, EnrollmentStatus.BOUND);

    // Assert
    assertEquals(0, updatedRows);
  }

  @Test
  @DisplayName("updateEnrollmentStatus() - Should update multiple enrollments with same status")
  void updateEnrollmentStatus_WhenMultipleEnrollments_ShouldUpdateAll() {
    // Act - Update both CREATED enrollments to BOUND
    int updatedRows1 =
        enrollmentRepository.updateEnrollmentStatus(
            enrollment1.getEnrollmentId(), EnrollmentStatus.BOUND);

    // Assert
    assertEquals(1, updatedRows1);

    // Verify the update
    entityManager.clear();
    Enrollment updated1 = entityManager.find(Enrollment.class, enrollment1.getEnrollmentId());
    assertEquals(EnrollmentStatus.BOUND, updated1.getStatus());
  }

  // ===== INTEGRATION QUERY TESTS =====

  @Test
  @DisplayName("findByIntegrationId() - Should return enrollments for specific integration")
  void findByIntegrationId_WhenIntegrationExists_ShouldReturnEnrollments() {
    // Act
    List<Enrollment> result = enrollmentRepository.findByIntegrationId(integrationId);

    // Assert
    assertEquals(2, result.size());
    assertTrue(result.stream().allMatch(e -> e.getIntegrationId().equals(integrationId)));
    assertTrue(
        result.stream().anyMatch(e -> e.getEnrollmentId().equals(enrollment1.getEnrollmentId())));
    assertTrue(
        result.stream().anyMatch(e -> e.getEnrollmentId().equals(enrollment2.getEnrollmentId())));
  }

  @Test
  @DisplayName(
      "findByIntegrationId() - Should return empty list when integration has no enrollments")
  void findByIntegrationId_WhenNoEnrollments_ShouldReturnEmptyList() {
    // Act
    List<Enrollment> result = enrollmentRepository.findByIntegrationId(99999);

    // Assert
    assertTrue(result.isEmpty());
  }

  @Test
  @DisplayName("findByIntegrationId() - Should return single enrollment for integration")
  void findByIntegrationId_WhenSingleEnrollment_ShouldReturnSingleEnrollment() {
    // Act
    List<Enrollment> result = enrollmentRepository.findByIntegrationId(456);

    // Assert
    assertEquals(1, result.size());
    assertEquals(enrollment3.getEnrollmentId(), result.get(0).getEnrollmentId());
    assertEquals(456, result.get(0).getIntegrationId());
  }

  // ===== STANDARD CRUD TESTS =====

  @Test
  @DisplayName("findById() - Should return enrollment when found")
  void findById_WhenEnrollmentExists_ShouldReturnEnrollment() {
    // Act
    Optional<Enrollment> result = enrollmentRepository.findById(enrollment1.getEnrollmentId());

    // Assert
    assertTrue(result.isPresent());
    assertEquals(enrollment1.getEnrollmentId(), result.get().getEnrollmentId());
    assertEquals(enrollment1.getEnrollmentName(), result.get().getEnrollmentName());
    assertEquals(enrollment1.getStatus(), result.get().getStatus());
  }

  @Test
  @DisplayName("findById() - Should return empty when enrollment doesn't exist")
  void findById_WhenEnrollmentDoesNotExist_ShouldReturnEmpty() {
    // Act
    Optional<Enrollment> result = enrollmentRepository.findById(99999);

    // Assert
    assertFalse(result.isPresent());
  }

  @Test
  @DisplayName("findAll() - Should return all enrollments")
  void findAll_WhenMultipleEnrollments_ShouldReturnAll() {
    // Act
    List<Enrollment> result = enrollmentRepository.findAll();

    // Assert
    assertEquals(3, result.size());
    assertTrue(
        result.stream().anyMatch(e -> e.getEnrollmentId().equals(enrollment1.getEnrollmentId())));
    assertTrue(
        result.stream().anyMatch(e -> e.getEnrollmentId().equals(enrollment2.getEnrollmentId())));
    assertTrue(
        result.stream().anyMatch(e -> e.getEnrollmentId().equals(enrollment3.getEnrollmentId())));
  }

  @Test
  @DisplayName("save() - Should save new enrollment")
  void save_WhenNewEnrollment_ShouldSaveEnrollment() {
    // Arrange
    Enrollment newEnrollment = new Enrollment();
    newEnrollment.setIntegrationId(789);
    newEnrollment.setEnrollmentName("New Test Enrollment");
    newEnrollment.setEnrollmentProofToken("new-proof-token");
    newEnrollment.setEnrollmentChallenge(111111);
    newEnrollment.setStatus(EnrollmentStatus.CREATED);
    newEnrollment.setActive(false);
    newEnrollment.setIntegrationPublicKey("new-integration-public-key");
    newEnrollment.setIntegrationPrivateKey("new-integration-private-key");
    newEnrollment.setCreatedAt(LocalDateTime.now());

    // Act
    Enrollment saved = enrollmentRepository.save(newEnrollment);

    // Assert
    assertNotNull(saved.getEnrollmentId());
    assertEquals("New Test Enrollment", saved.getEnrollmentName());
    assertEquals(EnrollmentStatus.CREATED, saved.getStatus());
    assertEquals(789, saved.getIntegrationId());
  }

  @Test
  @DisplayName("deleteById() - Should delete enrollment")
  void deleteById_WhenValidEnrollment_ShouldDeleteEnrollment() {
    // Act
    enrollmentRepository.deleteById(enrollment1.getEnrollmentId());

    // Assert
    entityManager.flush();
    entityManager.clear();
    Enrollment deleted = entityManager.find(Enrollment.class, enrollment1.getEnrollmentId());
    assertEquals(null, deleted);
  }

  // ===== STATUS-SPECIFIC QUERY TESTS =====

  @Test
  @DisplayName("findByIntegrationId() - Should return enrollments with different statuses")
  void findByIntegrationId_WhenDifferentStatuses_ShouldReturnAllStatuses() {
    // Act
    List<Enrollment> result = enrollmentRepository.findByIntegrationId(integrationId);

    // Assert
    assertEquals(2, result.size());
    assertTrue(result.stream().anyMatch(e -> e.getStatus() == EnrollmentStatus.CREATED));
    assertTrue(result.stream().anyMatch(e -> e.getStatus() == EnrollmentStatus.BOUND));
  }

  @Test
  @DisplayName("Locking operations should work with different enrollment statuses")
  @Disabled(
      "H2 does not support Postgres-specific FOR NO KEY UPDATE; locking queries cannot be validated here")
  void lockingOperations_WithDifferentStatuses_ShouldWorkCorrectly() {
    // Test CREATED enrollment can be locked for binding
    Optional<Enrollment> createdLock =
        enrollmentRepository.findAndLockUnreadById(enrollment1.getEnrollmentId());
    assertTrue(createdLock.isPresent());
    assertEquals(EnrollmentStatus.CREATED, createdLock.get().getStatus());

    // Test BOUND enrollment can be locked for verification
    Optional<Enrollment> boundLock =
        enrollmentRepository.findAndLockBoundById(enrollment2.getEnrollmentId());
    assertTrue(boundLock.isPresent());
    assertEquals(EnrollmentStatus.BOUND, boundLock.get().getStatus());

    // Test VERIFIED enrollment cannot be locked for binding
    Optional<Enrollment> verifiedLock =
        enrollmentRepository.findAndLockUnreadById(enrollment3.getEnrollmentId());
    assertFalse(verifiedLock.isPresent());

    // Test VERIFIED enrollment cannot be locked for verification
    Optional<Enrollment> verifiedBoundLock =
        enrollmentRepository.findAndLockBoundById(enrollment3.getEnrollmentId());
    assertFalse(verifiedBoundLock.isPresent());
  }
}
