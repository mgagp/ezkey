/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Domain: EnrollmentStatus
 * Description: Enumeration representing the lifecycle status of an enrollment.
 */

package org.ezkey.enrollment.domain;

/**
 * Enumeration representing the lifecycle status of an enrollment.
 * <p>
 * This enum defines the possible states an enrollment can be in during its lifecycle,
 * from initial creation through device binding, verification, and final validation.
 * Each status represents a specific stage in the enrollment process and determines
 * what operations are allowed on the enrollment.
 * </p>
 *
 * <p>
 * <b>Enrollment Lifecycle:</b>
 * <ul>
 * <li><b>CREATED:</b> Initial state when enrollment is first created</li>
 * <li><b>BOUND:</b> Device has claimed the enrollment during binding process</li>
 * <li><b>VERIFIED:</b> Device has completed cryptographic verification successfully</li>
 * <li><b>INVALID:</b> Verification failed or enrollment is compromised</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Security Considerations:</b>
 * The status progression ensures that enrollments can only advance through
 * verified cryptographic steps, preventing unauthorized state changes and
 * maintaining the integrity of the enrollment process.
 * </p>
 *
 * <p>
 * <b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 * </p>
 * <p>
 * <b>License:</b> MIT
 * </p>
 *
 * @author Ezkey contributors
 * @since 2025
 */
public enum EnrollmentStatus {

    /**
     * Initial state when enrollment is first created.
     * <p>
     * The enrollment exists in the system but has not been claimed by any device.
     * This is the default state for new enrollments.
     * </p>
     */
    CREATED("CREATED"),

    /**
     * Device has claimed the enrollment during binding process.
     * <p>
     * The mobile device has successfully retrieved the enrollment information
     * and is ready to proceed with cryptographic verification.
     * </p>
     */
    BOUND("BOUND"),

    /**
     * Device has completed cryptographic verification successfully.
     * <p>
     * The enrollment has passed all verification steps and is cryptographically
     * sound. This enrollment can be used for authentication attempts when
     * combined with the active flag.
     * </p>
     */
    VERIFIED("VERIFIED"),

    /**
     * Verification failed or enrollment is compromised.
     * <p>
     * The enrollment failed verification or has been marked as invalid due to
     * security concerns. This enrollment cannot be used for authentication
     * and may require re-enrollment.
     * </p>
     */
    INVALID("INVALID");

    private final String value;

    /**
     * Constructs an enrollment status with the specified string value.
     *
     * @param value the string representation of this status
     */
    EnrollmentStatus(String value){
        this.value = value;
    }

    /**
     * Returns the string value of this enrollment status.
     * <p>
     * This value corresponds to the database representation and is used
     * for persistence and API serialization.
     * </p>
     *
     * @return the string value of this status
     */
    public String getValue() {
        return value;
    }

    /**
     * Returns the enrollment status corresponding to the specified string value.
     * <p>
     * This method is used for deserialization from database values and
     * API requests. It performs case-sensitive matching.
     * </p>
     *
     * @param value the string value to convert
     * @return the corresponding enrollment status
     * @throws IllegalArgumentException if the value does not correspond to any status
     */
    public static EnrollmentStatus fromValue(String value) {
        for (EnrollmentStatus status : values()){
            if (status.value.equals(value)){
                return status;
            }
        }
        throw new IllegalArgumentException("Invalid enrollment status value: " + value);
    }
}
