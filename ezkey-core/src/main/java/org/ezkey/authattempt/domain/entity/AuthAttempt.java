/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Entity: EzkeyAuthAttempt
 * Description: JPA entity representing an authorization attempt within the Ezkey system.
 */

package org.ezkey.authattempt.domain.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity representing an authorization attempt within the Ezkey system.
 * <p>
 * This entity contains details about the authorization attempt, including its status,
 * associated enrollment, challenge information, and metadata. It represents the
 * core authorization process state and interacts with enrollment entities.
 * </p>
 *
 * <p>
 * <b>Entity Relationships:</b>
 * <ul>
 * <li><b>Many-to-One:</b> EzkeyAuthAttempt → EzkeyEnrollment (via enrollmentId)</li>
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
 * <b>Usage:</b> JPA entity for authorization attempt persistence
 * </p>
 *
 * @author Ezkey contributors
 * @since 2025
 * @see org.ezkey.enrollment.domain.entity.Enrollment
 */
@Entity
@Table(name = "ezkey_auth_attempt")
public class AuthAttempt {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "auth_attempt_id")
    private Integer authAttemptId;

    @Column(name = "enrollment_id",nullable = false)
    private Integer enrollmentId;

    @Column(name = "auth_attempt_read",nullable = false)
    private Boolean authAttemptRead = false;

    @Column(name = "auth_attempt_responded",nullable = false)
    private Boolean authAttemptResponded = false;

    @Column(name = "auth_attempt_valid",nullable = false)
    private Boolean authAttemptValid = false;

    @Column(name = "auth_attempt_accepted",nullable = false)
    private Boolean authAttemptAccepted = false;

    @Column(name = "auth_attempt_challenge")
    private Integer authAttemptChallenge;

    @Column(name = "auth_attempt_proof_token",nullable = false)
    private String authAttemptProofToken;

    @Column(name = "device_proof_token")
    private String deviceProofToken;

    @Column(name = "created_at",nullable = false)
    private LocalDateTime createdAt;

    /**
     * Default constructor for JPA.
     */
    public AuthAttempt(){
        this.createdAt = LocalDateTime.now();
    }

    /**
     * Constructs an authorization attempt with required fields.
     *
     * @param enrollmentId the enrollment ID associated with this attempt
     * @param integrationProofToken the integration proof token for verification
     */
    public AuthAttempt(Integer enrollmentId,String authAttemptProofToken){
        this();
        this.enrollmentId = enrollmentId;
        this.setAuthAttemptProofToken(authAttemptProofToken);
    }

    // Getters and Setters

    /**
     * Gets the authorization attempt ID.
     *
     * @return the authorization attempt ID
     */
    public Integer getAuthAttemptId() {
        return authAttemptId;
    }

    /**
     * Sets the authorization attempt ID.
     *
     * @param authAttemptId the authorization attempt ID to set
     */
    public void setAuthAttemptId(Integer authAttemptId) {
        this.authAttemptId = authAttemptId;
    }

    /**
     * Gets the enrollment ID associated with this authorization attempt.
     *
     * @return the enrollment ID
     */
    public Integer getEnrollmentId() {
        return enrollmentId;
    }

    /**
     * Sets the enrollment ID for this authorization attempt.
     *
     * @param enrollmentId the enrollment ID to set
     */
    public void setEnrollmentId(Integer enrollmentId) {
        this.enrollmentId = enrollmentId;
    }

    /**
     * Gets whether the device has read the authorization attempt.
     *
     * @return true if the device has read the attempt, false otherwise
     */
    public Boolean getAuthAttemptRead() {
        return authAttemptRead;
    }

    /**
     * Sets whether the device has read the authorization attempt.
     *
     * @param authAttemptRead true if the device has read the attempt, false otherwise
     */
    public void setAuthAttemptRead(Boolean authAttemptRead) {
        this.authAttemptRead = authAttemptRead;
    }

    /**
     * Gets whether the device has responded the authorization attempt.
     *
     * @return true if the device has responded, false otherwise
     */
    public Boolean getAuthAttemptResponded() {
        return authAttemptResponded;
    }

    /**
     * Sets whether the device has responded the authorization attempt.
     *
     * @param authAttemptAccepted true if the device has responded, false otherwise
     */
    public void setAuthAttemptResponded(Boolean authAttemptResponded) {
        this.authAttemptResponded = authAttemptResponded;
    }

    /**
     * Gets whether the authorization attempt is valid.
     *
     * @return true if the attempt is valid, false otherwise
     */
    public Boolean getAuthAttemptValid() {
        return authAttemptValid;
    }

    /**
     * Sets whether the authorization attempt is valid.
     *
     * @param authAttemptValid true if the attempt is valid, false otherwise
     */
    public void setAuthAttemptValid(Boolean authAttemptValid) {
        this.authAttemptValid = authAttemptValid;
    }

    /**
     * Gets whether the device has accepted the authorization attempt.
     *
     * @return true if the device has accepted, false otherwise
     */
    public Boolean getAuthAttemptAccepted() {
        return authAttemptAccepted;
    }

    /**
     * Sets whether the device has accepted the authorization attempt.
     *
     * @param authAttemptAccepted true if the device has accepted, false otherwise
     */
    public void setAuthAttemptAccepted(Boolean authAttemptAccepted) {
        this.authAttemptAccepted = authAttemptAccepted;
    }

    /**
     * Gets the challenge code sent to the device for verification.
     *
     * @return the challenge code
     */
    public Integer getAuthAttemptChallenge() {
        return authAttemptChallenge;
    }

    /**
     * Sets the challenge code sent to the device for verification.
     *
     * @param authAttemptChallenge the challenge code to set
     */
    public void setAuthAttemptChallenge(Integer authAttemptChallenge) {
        this.authAttemptChallenge = authAttemptChallenge;
    }

    /**
     * Gets the authorization proof token.
     *
     * @return the authorization proof token
     */
    public String getAuthAttemptProofToken() {
        return authAttemptProofToken;
    }

    /**
     * Sets the authorization proof token.
     *
     * @param authAttemptProofToken the authorization proof token to set
     */
    public void setAuthAttemptProofToken(String authAttemptProofToken) {
        this.authAttemptProofToken = authAttemptProofToken;
    }

    /**
     * Gets the device proof token.
     *
     * @return the device proof token
     */
    public String getDeviceProofToken() {
        return deviceProofToken;
    }

    /**
     * Sets the device proof token.
     *
     * @param deviceProofToken the device proof token to set
     */
    public void setDeviceProofToken(String deviceProofToken) {
        this.deviceProofToken = deviceProofToken;
    }

    /**
     * Gets the timestamp when the authorization attempt was created.
     *
     * @return the creation timestamp
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * Sets the timestamp when the authorization attempt was created.
     *
     * @param createdAt the creation timestamp to set
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

}