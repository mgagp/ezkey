/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: AdminPasswordChangeResponseDto
 * Description: Response DTO for administrator password change.
 */

package org.ezkey.admin.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Response DTO for administrator password change.
 * <p>
 * This DTO contains the result of a password change operation including
 * success status, message, and optional MFA enrollment information reminder.
 * </p>
 *
 * <p>
 * <b>MFA Enrollment Reminder:</b>
 * If the administrator has an unbound MFA enrollment, the response includes
 * enrollment credentials to remind the administrator to complete the binding
 * process for enhanced security.
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
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdminPasswordChangeResponseDto {

    /**
     * Indicates if the password change was successful.
     */
    private Boolean success;

    /**
     * Response message describing the result.
     */
    private String message;

    /**
     * Indicates if password change is still required.
     * Should be false after successful password change.
     */
    private Boolean passwordChangeRequired;

    /**
     * MFA enrollment information if applicable.
     * <p>
     * This field is included to remind administrators to bind their
     * MFA enrollment if not already done.
     * </p>
     */
    private MfaEnrollmentInfo mfaEnrollment;

    /**
     * Default constructor for JSON serialization.
     */
    public AdminPasswordChangeResponseDto() {
        // Default constructor
    }

    /**
     * Constructs a successful password change response.
     *
     * @param success true if password change succeeded
     * @param message descriptive message
     * @param passwordChangeRequired false after successful change
     */
    public AdminPasswordChangeResponseDto(Boolean success, String message, Boolean passwordChangeRequired) {
        this.success = success;
        this.message = message;
        this.passwordChangeRequired = passwordChangeRequired;
    }

    /**
     * Gets the success status.
     *
     * @return true if password change was successful
     */
    public Boolean getSuccess() {
        return success;
    }

    /**
     * Sets the success status.
     *
     * @param success the success status
     */
    public void setSuccess(Boolean success) {
        this.success = success;
    }

    /**
     * Gets the response message.
     *
     * @return the message
     */
    public String getMessage() {
        return message;
    }

    /**
     * Sets the response message.
     *
     * @param message the message
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * Gets the password change required status.
     *
     * @return true if password change is still required
     */
    public Boolean getPasswordChangeRequired() {
        return passwordChangeRequired;
    }

    /**
     * Sets the password change required status.
     *
     * @param passwordChangeRequired the password change required status
     */
    public void setPasswordChangeRequired(Boolean passwordChangeRequired) {
        this.passwordChangeRequired = passwordChangeRequired;
    }

    /**
     * Gets the MFA enrollment information.
     *
     * @return the MFA enrollment info
     */
    public MfaEnrollmentInfo getMfaEnrollment() {
        return mfaEnrollment;
    }

    /**
     * Sets the MFA enrollment information.
     *
     * @param mfaEnrollment the MFA enrollment info
     */
    public void setMfaEnrollment(MfaEnrollmentInfo mfaEnrollment) {
        this.mfaEnrollment = mfaEnrollment;
    }

    /**
     * Returns a string representation of the password change response.
     *
     * @return string representation
     */
    @Override
    public String toString() {
        return "AdminPasswordChangeResponseDto{" +
                "success=" + success +
                ", message='" + message + '\'' +
                ", passwordChangeRequired=" + passwordChangeRequired +
                ", mfaEnrollment=" + mfaEnrollment +
                '}';
    }

    /**
     * Nested class containing MFA enrollment information.
     * <p>
     * This class encapsulates enrollment details that are returned
     * to remind administrators to bind their MFA enrollment.
     * </p>
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MfaEnrollmentInfo {
        
        /**
         * Enrollment ID for binding.
         */
        private Integer enrollmentId;

        /**
         * Enrollment proof token for secure binding.
         */
        private String enrollmentProofToken;

        /**
         * Indicates if the enrollment is already bound.
         */
        private Boolean bound;

        /**
         * Reminder message for the administrator.
         */
        private String message;

        /**
         * Default constructor for JSON serialization.
         */
        public MfaEnrollmentInfo() {
            // Default constructor
        }

        /**
         * Constructs MFA enrollment information.
         *
         * @param enrollmentId the enrollment ID
         * @param enrollmentProofToken the enrollment proof token
         * @param bound true if enrollment is already bound
         * @param message reminder message
         */
        public MfaEnrollmentInfo(Integer enrollmentId, String enrollmentProofToken, 
                                Boolean bound, String message) {
            this.enrollmentId = enrollmentId;
            this.enrollmentProofToken = enrollmentProofToken;
            this.bound = bound;
            this.message = message;
        }

        /**
         * Gets the enrollment ID.
         *
         * @return the enrollment ID
         */
        public Integer getEnrollmentId() {
            return enrollmentId;
        }

        /**
         * Sets the enrollment ID.
         *
         * @param enrollmentId the enrollment ID
         */
        public void setEnrollmentId(Integer enrollmentId) {
            this.enrollmentId = enrollmentId;
        }

        /**
         * Gets the enrollment proof token.
         *
         * @return the enrollment proof token
         */
        public String getEnrollmentProofToken() {
            return enrollmentProofToken;
        }

        /**
         * Sets the enrollment proof token.
         *
         * @param enrollmentProofToken the enrollment proof token
         */
        public void setEnrollmentProofToken(String enrollmentProofToken) {
            this.enrollmentProofToken = enrollmentProofToken;
        }

        /**
         * Gets the bound status.
         *
         * @return true if enrollment is bound
         */
        public Boolean getBound() {
            return bound;
        }

        /**
         * Sets the bound status.
         *
         * @param bound the bound status
         */
        public void setBound(Boolean bound) {
            this.bound = bound;
        }

        /**
         * Gets the reminder message.
         *
         * @return the message
         */
        public String getMessage() {
            return message;
        }

        /**
         * Sets the reminder message.
         *
         * @param message the message
         */
        public void setMessage(String message) {
            this.message = message;
        }

        /**
         * Returns a string representation of the MFA enrollment info.
         *
         * @return string representation
         */
        @Override
        public String toString() {
            return "MfaEnrollmentInfo{" +
                    "enrollmentId=" + enrollmentId +
                    ", enrollmentProofToken='" + enrollmentProofToken + '\'' +
                    ", bound=" + bound +
                    ", message='" + message + '\'' +
                    '}';
        }
    }
}

