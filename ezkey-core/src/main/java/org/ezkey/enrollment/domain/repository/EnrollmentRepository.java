/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Repository: EzkeyEnrollmentRepository
 * Description: Spring Data JPA repository for EzkeyEnrollment entity.
 */

package org.ezkey.enrollment.domain.repository;

import java.util.List;
import java.util.Optional;
import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for EzkeyEnrollment entity.
 *
 * <p>This repository provides data access methods for enrollment operations, including CRUD
 * operations and custom queries for enrollment management. It extends JpaRepository to inherit
 * standard database operations.
 *
 * <p><b>Supported Operations:</b>
 *
 * <ul>
 *   <li><b>CRUD Operations:</b> Standard JPA repository operations
 *   <li><b>Custom Queries:</b> Business-specific queries for enrollment management
 *   <li><b>Status Updates:</b> Atomic updates for read and verification status
 *   <li><b>Security:</b> Row-level locking for enrollment binding operations
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * <p><b>Usage:</b> Enrollment data access layer
 *
 * @author Ezkey contributors
 * @since 2025
 * @see org.ezkey.enrollment.domain.entity.Enrollment
 */
@Repository
public interface EnrollmentRepository extends JpaRepository<Enrollment, Integer> {

  /**
   * Finds all enrollments for a specific integration.
   *
   * <p>This method retrieves all enrollments associated with a particular integration, useful for
   * integration management and reporting.
   *
   * @param integrationId the integration ID to search for
   * @return list of enrollments for the specified integration
   */
  List<Enrollment> findByIntegrationId(Integer integrationId);

  /**
   * Finds and locks a CREATED enrollment by ID for secure binding.
   *
   * <p>This method uses SELECT FOR NO KEY UPDATE to lock the row atomically, preventing race
   * conditions and ensuring exclusive access to the enrollment during the binding process. The lock
   * is acquired at the row level to minimize contention.
   *
   * @param enrollmentId the enrollment ID to find and lock
   * @return the CREATED enrollment with row lock, or empty if not found or already bound
   */
  @Query(
      value =
          """
          SELECT * FROM ezkey_enrollment
          WHERE enrollment_id = :enrollmentId
            AND enrollment_status = 'CREATED'
          FOR NO KEY UPDATE
          """,
      nativeQuery = true)
  Optional<Enrollment> findAndLockUnreadById(@Param("enrollmentId") Integer enrollmentId);

  /**
   * Finds and locks a BOUND enrollment by ID for secure binding.
   *
   * <p>This method uses SELECT FOR NO KEY UPDATE to lock the row atomically, preventing race
   * conditions and ensuring exclusive access to the enrollment during the binding process. The lock
   * is acquired at the row level to minimize contention.
   *
   * @param enrollmentId the enrollment ID to find and lock
   * @return the BOUND enrollment with row lock, or empty if not found or already verified
   */
  @Query(
      value =
          """
          SELECT * FROM ezkey_enrollment
          WHERE enrollment_id = :enrollmentId
            AND enrollment_status = 'BOUND'
          FOR NO KEY UPDATE
          """,
      nativeQuery = true)
  Optional<Enrollment> findAndLockBoundById(@Param("enrollmentId") Integer enrollmentId);

  /**
   * Updates the enrollment status to BOUND.
   *
   * <p>This method marks an enrollment as bound by the device, updating the enrollment_status field
   * to BOUND for the specified enrollment.
   *
   * @param enrollmentId the ID of the enrollment to mark as bound
   * @return number of rows affected (should be 1 if successful)
   */
  @Modifying
  @Query("UPDATE Enrollment e SET e.status = :status WHERE e.enrollmentId = :enrollmentId")
  int updateEnrollmentStatus(
      @Param("enrollmentId") Integer enrollmentId, @Param("status") EnrollmentStatus status);

  /**
   * Checks if a device public key hash is already used by a verified enrollment.
   *
   * <p>This method provides security validation to prevent replay attacks and ensure that each
   * device public key can only be associated with one verified enrollment. It uses the SHA-256 hash
   * of the device public key for validation, allowing uniqueness checking independent of encryption
   * format.
   *
   * <p><b>Security Purpose:</b>
   *
   * <ul>
   *   <li>Prevents replay attacks using the same device public key
   *   <li>Ensures enrollment integrity by preventing key reuse
   *   <li>Maintains one-to-one relationship between device public keys and enrollments
   *   <li>Works independently of encryption format (hash computed from plaintext)
   *   <li>Aligns with AuthAttemptService security model for consistency
   * </ul>
   *
   * <p><b>Usage Context:</b> Called during enrollment verification to validate that the device
   * public key has not been previously used to complete another enrollment. This prevents
   * enrollment hijacking and ensures cryptographic identity uniqueness.
   *
   * @param devicePublicKeyHash the SHA-256 hash of the device public key (hexadecimal, 64 chars)
   * @return true if the device public key hash is already used by a verified enrollment, false
   *     otherwise
   * @see
   *     org.ezkey.authattempt.service.AuthAttemptService#pending(org.ezkey.authattempt.domain.AuthAttemptPendingRequest)
   * @since 2025
   */
  @Query(
      "SELECT COUNT(e) > 0 FROM Enrollment e WHERE e.devicePublicKeyHash = :devicePublicKeyHash AND"
          + " e.status = 'VERIFIED'")
  boolean existsByDevicePublicKeyHash(@Param("devicePublicKeyHash") String devicePublicKeyHash);

  /**
   * Checks if a device public key is already used by a verified enrollment (legacy method).
   *
   * <p>This method is deprecated in favor of {@link #existsByDevicePublicKeyHash(String)} which
   * uses hash-based validation independent of encryption format.
   *
   * @param devicePublicKey the Base64-encoded device public key to check for uniqueness
   * @return true if the device public key is already used by a verified enrollment, false otherwise
   * @deprecated Use {@link #existsByDevicePublicKeyHash(String)} instead for encryption-independent
   *     validation
   * @since 2025
   */
  @Deprecated
  @Query(
      "SELECT COUNT(e) > 0 FROM Enrollment e WHERE e.devicePublicKey = :devicePublicKey AND"
          + " e.status = 'VERIFIED'")
  boolean existsByDevicePublicKeyAndVerified(@Param("devicePublicKey") String devicePublicKey);

  /**
   * Find enrollment by ID and proof token for secure binding.
   *
   * <p>This method provides secure access to enrollment data by requiring both the enrollment ID
   * and the enrollment proof token. This prevents enumeration attacks where attackers could
   * systematically test enrollment IDs to discover valid enrollments and obtain sensitive
   * information.
   *
   * <p><b>Security Purpose:</b>
   *
   * <ul>
   *   <li>Prevents enumeration attacks on enrollment IDs
   *   <li>Ensures only parties with valid proof tokens can access enrollment data
   *   <li>Protects sensitive enrollment information from unauthorized access
   *   <li>Maintains enrollment proof token confidentiality
   * </ul>
   *
   * @param enrollmentId the enrollment ID
   * @param enrollmentProofToken the enrollment proof token
   * @return Optional enrollment if found and token matches
   */
  Optional<Enrollment> findByEnrollmentIdAndEnrollmentProofToken(
      Integer enrollmentId, String enrollmentProofToken);

  /**
   * Find active enrollment by proof token. Used for secure enrollment identification in PENDING
   * requests.
   *
   * <p>This method provides secure access to enrollment data using only the enrollment proof token.
   * This prevents enumeration attacks by removing the need to expose enrollment IDs in URLs while
   * maintaining security through cryptographic proof token validation.
   *
   * <p><b>Security Purpose:</b>
   *
   * <ul>
   *   <li>Prevents enumeration attacks on enrollment IDs
   *   <li>Ensures only parties with valid proof tokens can access enrollment data
   *   <li>Protects sensitive enrollment information from unauthorized access
   *   <li>Validates that enrollment is active and can be used for authentication
   * </ul>
   *
   * @param enrollmentProofToken the cryptographic proof token
   * @param active whether the enrollment is active
   * @return enrollment if found and active
   * @since 2025
   */
  Optional<Enrollment> findByEnrollmentProofTokenAndActive(
      String enrollmentProofToken, Boolean active);
}
