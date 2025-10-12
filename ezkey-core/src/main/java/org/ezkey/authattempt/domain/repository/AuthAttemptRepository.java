/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Repository: EzkeyAuthAttemptRepository
 * Description: Spring Data JPA repository for EzkeyAuthAttempt entity.
 */

package org.ezkey.authattempt.domain.repository;

import java.util.List;
import java.util.Optional;

import org.ezkey.authattempt.domain.AuthAttemptStatus;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for EzkeyAuthAttempt entity.
 * <p>
 * This repository provides data access operations for authorization attempts,
 * including custom queries for business-specific operations like finding
 * the most recent attempt by enrollment ID and updating read/reply status.
 * </p>
 *
 * <p>
 * <b>Supported Operations:</b>
 * <ul>
 * <li><b>CRUD Operations:</b> Standard JPA repository operations</li>
 * <li><b>Custom Queries:</b> Business-specific queries for auth attempt management</li>
 * <li><b>Status Updates:</b> Atomic updates for read and reply status</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 * </p>
 * <p>
 * <b>License:</b> MIT
 * </p>
 * <p>
 * <b>Usage:</b> Data access layer for authorization attempts
 * </p>
 *
 * @author Ezkey contributors
 * @since 2025
 * @see AuthAttempt
 */
@Repository
public interface AuthAttemptRepository extends JpaRepository<AuthAttempt, Integer> {

    /**
     * Finds the most recent authorization attempt for a given enrollment ID.
     * <p>
     * This method returns the authorization attempt with the highest ID
     * (most recent) for the specified enrollment.
     * </p>
     *
     * @param enrollmentId the enrollment ID to search for
     * @return the most recent authorization attempt, or empty if none found
     */
    @Query("SELECT a FROM AuthAttempt a WHERE a.enrollmentId = :enrollmentId ORDER BY a.authAttemptId DESC")
    List<AuthAttempt> findByEnrollmentIdOrderByAuthAttemptIdDesc(@Param("enrollmentId") Integer enrollmentId);

    /**
     * Finds the most recent pending authorization attempt for a given enrollment ID.
     * <p>
     * This method returns the first (most recent) pending authorization attempt
     * for the specified enrollment. Only attempts with PENDING status are considered.
     * </p>
     *
     * @param enrollmentId the enrollment ID to search for
     * @return the most recent pending authorization attempt, or empty if none found
     */
    @Query("SELECT a FROM AuthAttempt a WHERE a.enrollmentId = :enrollmentId AND a.authAttemptStatus = :status ORDER BY a.authAttemptId DESC")
    Optional<AuthAttempt> findMostRecentByEnrollmentIdAndStatus(@Param("enrollmentId") Integer enrollmentId, @Param("status") AuthAttemptStatus status);

    /**
     * Finds the most recent valid (non-expired) pending authorization attempt for a given enrollment ID.
     * <p>
     * This method returns the first (most recent) pending and non-expired authorization attempt
     * for the specified enrollment. Only attempts with PENDING status and haven't expired are considered.
     * </p>
     *
     * @param enrollmentId the enrollment ID to search for
     * @param now the current timestamp for expiration comparison
     * @return the most recent valid pending authorization attempt, or empty if none found
     */
    @Query("SELECT a FROM AuthAttempt a WHERE a.enrollmentId = :enrollmentId AND a.authAttemptStatus = :status AND a.expiresAt > :now ORDER BY a.authAttemptId DESC")
    Optional<AuthAttempt> findMostRecentValidByEnrollmentIdAndStatus(@Param("enrollmentId") Integer enrollmentId, @Param("status") AuthAttemptStatus status, @Param("now") java.time.LocalDateTime now);

    /**
     * Finds and locks the most recent pending authorization attempt for a given enrollment ID.
     * <p>
     * This method uses SELECT FOR NO KEY UPDATE to lock the row atomically,
     * preventing race conditions and ensuring exclusive access to the auth attempt.
     * The lock is acquired at the row level to minimize contention.
     * </p>
     *
     * @param enrollmentId the enrollment ID to search for
     * @return the most recent pending authorization attempt with row lock, or empty if none found
     */
    @Query(value = """
            SELECT * FROM ezkey_auth_attempt
            WHERE enrollment_id = :enrollmentId
              AND auth_attempt_status = :status
            ORDER BY auth_attempt_id DESC
            LIMIT 1
            FOR NO KEY UPDATE
            """,nativeQuery = true)
    Optional<AuthAttempt> findAndLockMostRecentByEnrollmentIdAndStatus(@Param("enrollmentId") Integer enrollmentId, @Param("status") String status);

    /**
     * Finds and locks the most recent valid (non-expired) pending authorization attempt for a given enrollment ID.
     * <p>
     * This method uses SELECT FOR NO KEY UPDATE to lock the row atomically,
     * preventing race conditions and ensuring exclusive access to the auth attempt.
     * Only attempts with PENDING status and haven't expired are considered.
     * The lock is acquired at the row level to minimize contention.
     * </p>
     *
     * @param enrollmentId the enrollment ID to search for
     * @param now the current timestamp for expiration comparison
     * @return the most recent valid pending authorization attempt with row lock, or empty if none found
     */
    @Query(value = """
            SELECT * FROM ezkey_auth_attempt
            WHERE enrollment_id = :enrollmentId
              AND auth_attempt_status = :status
              AND expires_at > :now
            ORDER BY auth_attempt_id DESC
            LIMIT 1
            FOR NO KEY UPDATE
            """,nativeQuery = true)
    Optional<AuthAttempt> findAndLockMostRecentValidByEnrollmentIdAndStatus(@Param("enrollmentId") Integer enrollmentId, @Param("status") String status, @Param("now") java.time.LocalDateTime now);

    /**
     * Updates the status of an authorization attempt if it's currently pending.
     * <p>
     * This method atomically updates the status from PENDING to the new status,
     * preventing race conditions in concurrent scenarios.
     * </p>
     *
     * @param authAttemptId the authorization attempt ID to update
     * @param newStatus the new status to set
     * @return the number of rows affected (1 if updated, 0 if not pending)
     */
    @Modifying
    @Query("UPDATE AuthAttempt a SET a.authAttemptStatus = :newStatus WHERE a.authAttemptId = :authAttemptId AND a.authAttemptStatus = :currentStatus")
    int updateStatusIfCurrent(@Param("authAttemptId") Integer authAttemptId, @Param("currentStatus") AuthAttemptStatus currentStatus, @Param("newStatus") AuthAttemptStatus newStatus);

    /**
     * Finds all authorization attempts for a given enrollment ID.
     * <p>
     * This method returns all authorization attempts associated with the
     * specified enrollment, ordered by creation time (newest first).
     * </p>
     *
     * @param enrollmentId the enrollment ID to search for
     * @return list of authorization attempts for the enrollment
     */
    @Query("SELECT a FROM AuthAttempt a WHERE a.enrollmentId = :enrollmentId ORDER BY a.createdAt DESC")
    List<AuthAttempt> findAllByEnrollmentId(@Param("enrollmentId") Integer enrollmentId);

    /**
     * Checks if a device proof token already exists in any authorization attempt.
     * <p>
     * This method is used to ensure device proof token uniqueness across all
     * authorization attempts to prevent replay attacks.
     * </p>
     *
     * @param deviceProofToken the device proof token to check
     * @return true if the device proof token already exists, false otherwise
     */
    @Query("SELECT COUNT(a) > 0 FROM AuthAttempt a WHERE a.deviceProofToken = :deviceProofToken")
    boolean existsByDeviceProofToken(@Param("deviceProofToken") String deviceProofToken);

    /**
     * Finds a newer authentication attempt for the same enrollment.
     * <p>
     * This method checks if there exists a more recent authentication attempt
     * for the same enrollment, which would make the current attempt conceptually
     * expired. This implements the business rule that only the most recent
     * authentication attempt for a person should be valid.
     * </p>
     *
     * @param enrollmentId the enrollment ID to check
     * @param createdAt the creation time of the current attempt
     * @return the newer authentication attempt if it exists, empty otherwise
     */
    @Query("SELECT a FROM AuthAttempt a WHERE a.enrollmentId = :enrollmentId AND a.createdAt > :createdAt ORDER BY a.createdAt DESC")
    Optional<AuthAttempt> findNewerAttemptByEnrollmentId(@Param("enrollmentId") Integer enrollmentId, @Param("createdAt") java.time.OffsetDateTime createdAt);

    /**
     * Finds all non-final authentication attempts for a given enrollment ID.
     * <p>
     * This method returns all authentication attempts that are not in final states
     * (PENDING, READ) for the specified enrollment. Used for supersession logic.
     * </p>
     *
     * @param enrollmentId the enrollment ID to search for
     * @return list of non-final authentication attempts
     */
    @Query("SELECT a FROM AuthAttempt a WHERE a.enrollmentId = :enrollmentId AND a.authAttemptStatus IN (:statuses)")
    List<AuthAttempt> findByEnrollmentIdAndStatusIn(@Param("enrollmentId") Integer enrollmentId, @Param("statuses") List<AuthAttemptStatus> statuses);

    /**
     * Updates multiple authentication attempts to EXPIRED status.
     * <p>
     * This method atomically updates multiple authentication attempts to EXPIRED
     * status, used for supersession when a new attempt is created.
     * </p>
     *
     * @param authAttemptIds list of authentication attempt IDs to update
     * @return the number of rows affected
     */
    @Modifying
    @Query("UPDATE AuthAttempt a SET a.authAttemptStatus = :newStatus WHERE a.authAttemptId IN (:authAttemptIds)")
    int updateStatusForMultipleAttempts(@Param("authAttemptIds") List<Integer> authAttemptIds, @Param("newStatus") AuthAttemptStatus newStatus);
}