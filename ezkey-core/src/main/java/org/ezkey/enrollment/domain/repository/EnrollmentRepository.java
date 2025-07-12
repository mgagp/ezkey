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

import org.ezkey.enrollment.domain.entity.Enrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for EzkeyEnrollment entity.
 * <p>
 * This repository provides data access methods for enrollment operations,
 * including CRUD operations and custom queries for enrollment management.
 * It extends JpaRepository to inherit standard database operations.
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
     * Finds an enrollment by its unique enrollment code.
     * <p>
     * This method is used to locate enrollments using their unique identifier
     * code, which is typically used in enrollment verification processes.
     * </p>
     *
     * @param enrollmentCode the unique enrollment code to search for
     * @return Optional containing the enrollment if found, empty otherwise
     */
    Optional<Enrollment> findByEnrollmentCode(String enrollmentCode);

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
     * Finds active enrollments for a specific integration.
     * <p>
     * This method retrieves only active enrollments for a particular
     * integration, filtering out inactive or disabled enrollments.
     * </p>
     *
     * @param integrationId the integration ID to search for
     * @return list of active enrollments for the specified integration
     */
    List<Enrollment> findByIntegrationIdAndEnrollmentActiveTrue(Integer integrationId);

    /**
     * Finds confirmed enrollments for a specific integration.
     * <p>
     * This method retrieves only confirmed enrollments for a particular
     * integration, useful for authentication operations.
     * </p>
     *
     * @param integrationId the integration ID to search for
     * @return list of confirmed enrollments for the specified integration
     */
    List<Enrollment> findByIntegrationIdAndEnrollmentConfirmedTrue(Integer integrationId);

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
    @Query("UPDATE EzkeyEnrollment e SET e.enrollmentRead = true WHERE e.enrollmentId = :enrollmentId")
    int setDeviceReadTrue(@Param("enrollmentId") Integer enrollmentId);

    /**
     * Checks if an enrollment exists by its enrollment code.
     * <p>
     * This method provides a quick way to check if an enrollment
     * with the specified code exists in the system.
     * </p>
     *
     * @param enrollmentCode the enrollment code to check
     * @return true if the enrollment exists, false otherwise
     */
    boolean existsByEnrollmentCode(String enrollmentCode);
}