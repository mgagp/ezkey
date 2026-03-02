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
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.NativeQuery;
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
public interface EnrollmentRepository
    extends JpaRepository<Enrollment, Integer>, JpaSpecificationExecutor<Enrollment> {

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
  @NativeQuery(
      """
      SELECT * FROM ezkey_enrollment
      WHERE enrollment_id = :enrollmentId
        AND enrollment_status = 'CREATED'
      FOR NO KEY UPDATE
      """)
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
  @NativeQuery(
      """
      SELECT * FROM ezkey_enrollment
      WHERE enrollment_id = :enrollmentId
        AND enrollment_status = 'BOUND'
      FOR NO KEY UPDATE
      """)
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
  Optional<Enrollment> findByEnrollmentIdAndEnrollmentProofTokenHash(
      Integer enrollmentId, String enrollmentProofTokenHash);

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
  Optional<Enrollment> findByEnrollmentProofTokenHashAndActive(
      String enrollmentProofTokenHash, Boolean active);

  /**
   * Counts enrollments with encrypted integration private key matching the prefix pattern.
   *
   * <p>Used for re-encryption batch operations to identify records encrypted with a specific key.
   *
   * @param prefix the encryption prefix pattern (e.g., "ENC:1:%")
   * @return count of matching records
   */
  @NativeQuery("SELECT COUNT(*) FROM ezkey_enrollment WHERE integration_private_key LIKE :prefix")
  int countByEncryptedIntegrationPrivateKeyLike(@Param("prefix") String prefix);

  /**
   * Counts enrollments with encrypted enrollment proof token matching the prefix pattern.
   *
   * <p>Used for re-encryption batch operations to identify records encrypted with a specific key.
   *
   * @param prefix the encryption prefix pattern (e.g., "ENC:1:%")
   * @return count of matching records
   */
  @NativeQuery("SELECT COUNT(*) FROM ezkey_enrollment WHERE enrollment_proof_token LIKE :prefix")
  int countByEncryptedEnrollmentProofTokenLike(@Param("prefix") String prefix);

  /**
   * Finds enrollments with encrypted integration private key matching the prefix pattern.
   *
   * <p>Used for re-encryption batch operations to fetch records for processing. Results are ordered
   * by enrollment_id for resumable batch processing.
   *
   * @param prefix the encryption prefix pattern (e.g., "ENC:1:%")
   * @param lastId the last processed enrollment ID (for resumability), or null to start from
   *     beginning
   * @param limit maximum number of records to return
   * @return list of matching enrollments
   */
  @NativeQuery(
      """
      SELECT * FROM ezkey_enrollment
      WHERE integration_private_key LIKE :prefix
        AND (:lastId IS NULL OR enrollment_id > :lastId)
      ORDER BY enrollment_id ASC
      LIMIT :limit
      """)
  List<Enrollment> findEncryptedIntegrationPrivateKeyLike(
      @Param("prefix") String prefix, @Param("lastId") Integer lastId, @Param("limit") int limit);

  /**
   * Finds enrollments with encrypted enrollment proof token matching the prefix pattern.
   *
   * <p>Used for re-encryption batch operations to fetch records for processing. Results are ordered
   * by enrollment_id for resumable batch processing.
   *
   * @param prefix the encryption prefix pattern (e.g., "ENC:1:%")
   * @param lastId the last processed enrollment ID (for resumability), or null to start from
   *     beginning
   * @param limit maximum number of records to return
   * @return list of matching enrollments
   */
  @NativeQuery(
      """
      SELECT * FROM ezkey_enrollment
      WHERE enrollment_proof_token LIKE :prefix
        AND (:lastId IS NULL OR enrollment_id > :lastId)
      ORDER BY enrollment_id ASC
      LIMIT :limit
      """)
  List<Enrollment> findEncryptedEnrollmentProofTokenLike(
      @Param("prefix") String prefix, @Param("lastId") Integer lastId, @Param("limit") int limit);

  /**
   * Finds enrollments with the same integration, name, and status.
   *
   * <p>This method is used for validation during enrollment creation to check if a VERIFIED
   * enrollment already exists with the same integration and name. It allows the system to enforce
   * uniqueness constraints at the application level before database constraint violations occur.
   *
   * <p><b>Usage Context:</b> Called during enrollment creation to validate that no active VERIFIED
   * enrollment exists with the same name for the same integration. This provides early feedback to
   * users and prevents unnecessary enrollment creation attempts.
   *
   * @param integrationId the integration ID to search for
   * @param enrollmentName the enrollment name to search for
   * @param status the enrollment status to filter by
   * @return list of enrollments matching the criteria
   * @since 2025
   */
  List<Enrollment> findByIntegrationIdAndEnrollmentNameAndStatus(
      Integer integrationId, String enrollmentName, EnrollmentStatus status);

  /**
   * Finds VERIFIED enrollments with the same integration and name, excluding a specific enrollment.
   *
   * <p>This method is used for validation during enrollment verification to check if another
   * VERIFIED enrollment already exists with the same integration and name. It excludes the current
   * enrollment being verified to allow checking for duplicates.
   *
   * <p><b>Usage Context:</b> Called during enrollment verification to ensure that only one VERIFIED
   * enrollment can exist per (integration_id, enrollment_name) combination. This enforces the
   * uniqueness constraint at the application level and provides clear error messages before
   * database constraint violations occur.
   *
   * @param integrationId the integration ID to search for
   * @param enrollmentName the enrollment name to search for
   * @param status the enrollment status to filter by (typically VERIFIED)
   * @param excludeEnrollmentId the enrollment ID to exclude from results (current enrollment being
   *     verified)
   * @return list of VERIFIED enrollments matching the criteria
   * @since 2025
   */
  List<Enrollment> findByIntegrationIdAndEnrollmentNameAndStatusAndEnrollmentIdNot(
      Integer integrationId,
      String enrollmentName,
      EnrollmentStatus status,
      Integer excludeEnrollmentId);

  /**
   * Finds active VERIFIED enrollments by integration and user identifier.
   *
   * <p>Used for auth attempt creation when the client provides userIdentifier instead of
   * enrollmentId. Returns all matching enrollments to support multi-device scenarios (user may have
   * multiple devices). Caller must handle: 0 matches (not found), 1 match (use it), 2+ matches
   * (disambiguation required).
   *
   * @param integrationId the integration ID to search for
   * @param userIdentifier the user identifier (username, user_id) to search for
   * @param status the enrollment status to filter by (typically VERIFIED)
   * @param active whether the enrollment is active (typically true)
   * @return list of matching enrollments
   * @since 2025
   */
  List<Enrollment> findByIntegrationIdAndUserIdentifierAndStatusAndActive(
      Integer integrationId, String userIdentifier, EnrollmentStatus status, Boolean active);

  /**
   * Finds all enrollments for an integration matching the given status and active flag.
   *
   * <p>Used by bulk revocation to retrieve all active VERIFIED enrollments for a given integration.
   *
   * @param integrationId the integration ID
   * @param status the enrollment status to filter by (typically VERIFIED)
   * @param active whether the enrollment is active (typically true)
   * @return list of matching enrollments
   * @since 2025
   */
  List<Enrollment> findByIntegrationIdAndStatusAndActive(
      Integer integrationId, EnrollmentStatus status, Boolean active);
}
