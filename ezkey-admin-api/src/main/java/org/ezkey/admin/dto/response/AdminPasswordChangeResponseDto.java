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

import java.time.LocalDateTime;

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
 * <b>MFA Flow Integration:</b>
 * If MFA is required after password change (enrollment bound + MFA enabled),
 * the response includes a temporary token to continue directly to the MFA flow
 * without requiring a re-login. This provides a seamless user experience.
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
     * Temporary token for MFA flow continuation.
     * <p>
     * When MFA is required after password change, this temp token
     * allows the administrator to proceed directly to the MFA flow
     * without re-login. The token is valid for 5 minutes.
     * </p>
     */
    private String tempToken;

    /**
     * Indicates if MFA verification is required.
     * <p>
     * When true, the administrator must complete MFA verification
     * using the provided temp token before receiving a bearer token.
     * </p>
     */
    private Boolean mfaRequired;

    /**
     * Expiration timestamp for the temp token.
     * <p>
     * The temp token expires after 5 minutes for security.
     * </p>
     */
    private LocalDateTime expiresAt;

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
     * Gets the temporary token for MFA flow.
     *
     * @return the temp token
     */
    public String getTempToken() {
        return tempToken;
    }

    /**
     * Sets the temporary token for MFA flow.
     *
     * @param tempToken the temp token
     */
    public void setTempToken(String tempToken) {
        this.tempToken = tempToken;
    }

    /**
     * Gets the MFA required flag.
     *
     * @return true if MFA is required
     */
    public Boolean getMfaRequired() {
        return mfaRequired;
    }

    /**
     * Sets the MFA required flag.
     *
     * @param mfaRequired the MFA required flag
     */
    public void setMfaRequired(Boolean mfaRequired) {
        this.mfaRequired = mfaRequired;
    }

    /**
     * Gets the temp token expiration time.
     *
     * @return the expiration time
     */
    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    /**
     * Sets the temp token expiration time.
     *
     * @param expiresAt the expiration time
     */
    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
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
                ", tempToken='" + (tempToken != null ? "[PROTECTED]" : "null") + '\'' +
                ", mfaRequired=" + mfaRequired +
                ", expiresAt=" + expiresAt +
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
         * Enrollment challenge code (6 digits) for verification.
         * <p>
         * This code must be provided during enrollment verification to prove
         * that the admin has access to the enrollment credentials shown during
         * the bootstrap process.
         * </p>
         */
        private Integer enrollmentChallenge;

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
         * @param enrollmentChallenge the enrollment challenge code (6 digits)
         * @param bound true if enrollment is already bound
         * @param message reminder message
         */
        public MfaEnrollmentInfo(Integer enrollmentId, String enrollmentProofToken, 
                                Integer enrollmentChallenge, Boolean bound, String message) {
            this.enrollmentId = enrollmentId;
            this.enrollmentProofToken = enrollmentProofToken;
            this.enrollmentChallenge = enrollmentChallenge;
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
         * Gets the enrollment challenge code.
         *
         * @return the challenge code (6 digits)
         */
        public Integer getEnrollmentChallenge() {
            return enrollmentChallenge;
        }

        /**
         * Sets the enrollment challenge code.
         *
         * @param enrollmentChallenge the challenge code (6 digits)
         */
        public void setEnrollmentChallenge(Integer enrollmentChallenge) {
            this.enrollmentChallenge = enrollmentChallenge;
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
                    ", enrollmentChallenge=" + enrollmentChallenge +
                    ", bound=" + bound +
                    ", message='" + message + '\'' +
                    '}';
        }
    }
}

