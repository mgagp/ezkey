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
     * Finds the most recent unread authorization attempt for a given enrollment ID.
     * <p>
     * This method returns the first (most recent) unread authorization attempt
     * for the specified enrollment. Only attempts that haven't been read by the device
     * are considered.
     * </p>
     *
     * @param enrollmentId the enrollment ID to search for
     * @return the most recent unread authorization attempt, or empty if none found
     */
    @Query("SELECT a FROM AuthAttempt a WHERE a.enrollmentId = :enrollmentId AND a.authAttemptRead = false ORDER BY a.authAttemptId DESC")
    Optional<AuthAttempt> findMostRecentUnreadByEnrollmentId(@Param("enrollmentId") Integer enrollmentId);

    /**
     * Finds and locks the most recent unread authorization attempt for a given enrollment ID.
     * <p>
     * This method uses SELECT FOR NO KEY UPDATE to lock the row atomically,
     * preventing race conditions and ensuring exclusive access to the auth attempt.
     * The lock is acquired at the row level to minimize contention.
     * </p>
     *
     * @param enrollmentId the enrollment ID to search for
     * @return the most recent unread authorization attempt with row lock, or empty if none found
     */
    @Query(value = """
        SELECT * FROM auth_attempt 
        WHERE enrollment_id = :enrollmentId 
          AND auth_attempt_read = false 
        ORDER BY auth_attempt_id DESC 
        LIMIT 1 
        FOR NO KEY UPDATE
        """, nativeQuery = true)
    Optional<AuthAttempt> findAndLockMostRecentUnreadByEnrollmentId(@Param("enrollmentId") Integer enrollmentId);

    /**
     * Updates the read status of an authorization attempt if it hasn't been read yet.
     * <p>
     * This method atomically updates the read status to true only if the current
     * status is false, preventing race conditions in concurrent scenarios.
     * </p>
     *
     * @param authAttemptId the authorization attempt ID to update
     * @return the number of rows affected (1 if updated, 0 if already read)
     */
    @Modifying
    @Query("UPDATE AuthAttempt a SET a.authAttemptRead = true WHERE a.authAttemptId = :authAttemptId AND a.authAttemptRead = false")
    int setDeviceReadTrueIfNotRead(@Param("authAttemptId") Integer authAttemptId);

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
}