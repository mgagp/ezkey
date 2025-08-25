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

import org.ezkey.enrollment.domain.entity.Enrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for EzkeyEnrollment entity.
 * <p>
 * This repository provides data access methods for enrollment operations,
 * including CRUD operations and custom queries for enrollment management.
 * It extends JpaRepository to inherit standard database operations.
 * </p>
 *
 * <p>
 * <b>Supported Operations:</b>
 * <ul>
 * <li><b>CRUD Operations:</b> Standard JPA repository operations</li>
 * <li><b>Custom Queries:</b> Business-specific queries for enrollment management</li>
 * <li><b>Status Updates:</b> Atomic updates for read and verification status</li>
 * <li><b>Security:</b> Row-level locking for enrollment binding operations</li>
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
 * <b>Usage:</b> Enrollment data access layer
 * </p>
 *
 * @author Ezkey contributors
 * @since 2025
 * @see org.ezkey.enrollment.domain.entity.Enrollment
 */
@Repository
public interface EnrollmentRepository extends JpaRepository<Enrollment, Integer> {

    /**
     * Finds all enrollments for a specific integration.
     * <p>
     * This method retrieves all enrollments associated with a particular
     * integration, useful for integration management and reporting.
     * </p>
     *
     * @param integrationId the integration ID to search for
     * @return list of enrollments for the specified integration
     */
    List<Enrollment> findByIntegrationId(Integer integrationId);

    /**
     * Finds and locks an unread enrollment by ID for secure binding.
     * <p>
     * This method uses SELECT FOR NO KEY UPDATE to lock the row atomically,
     * preventing race conditions and ensuring exclusive access to the enrollment
     * during the binding process. The lock is acquired at the row level to minimize contention.
     * </p>
     *
     * @param enrollmentId the enrollment ID to find and lock
     * @return the unread enrollment with row lock, or empty if not found or already read
     */
    @Query(value = """
            SELECT * FROM ezkey_enrollment
            WHERE enrollment_id = :enrollmentId
              AND enrollment_read = false
            FOR NO KEY UPDATE
            """,nativeQuery = true)
    Optional<Enrollment> findAndLockUnreadById(@Param("enrollmentId") Integer enrollmentId);

    /**
     * Updates the enrollment read status to true.
     * <p>
     * This method marks an enrollment as read by the device, updating
     * the enrollment_read field to true for the specified enrollment.
     * </p>
     *
     * @param enrollmentId the ID of the enrollment to mark as read
     * @return number of rows affected (should be 1 if successful)
     */
    @Modifying
    @Query("UPDATE Enrollment e SET e.enrollmentRead = true WHERE e.enrollmentId = :enrollmentId")
    int setDeviceReadTrue(@Param("enrollmentId") Integer enrollmentId);

}