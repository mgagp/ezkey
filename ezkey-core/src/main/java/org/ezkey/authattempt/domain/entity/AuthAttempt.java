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

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.ezkey.authattempt.domain.AuthAttemptStatus;

/**
 * JPA entity representing an authorization attempt within the Ezkey system.
 *
 * <p>This entity contains details about the authorization attempt, including its status, associated
 * enrollment, challenge information, and metadata. It represents the core authorization process
 * state and interacts with enrollment entities.
 *
 * <p><b>Entity Relationships:</b>
 *
 * <ul>
 *   <li><b>Many-to-One:</b> EzkeyAuthAttempt → EzkeyEnrollment (via enrollmentId)
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * <p><b>Usage:</b> JPA entity for authorization attempt persistence
 *
 * @author Ezkey contributors
 * @since 2025
 * @see org.ezkey.enrollment.domain.entity.Enrollment
 */
@Entity
@Table(name = "ezkey_auth_attempt")
public class AuthAttempt {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "auth_attempt_id")
  private Integer authAttemptId;

  @Column(name = "enrollment_id", nullable = false)
  private Integer enrollmentId;

  @Enumerated(EnumType.STRING)
  @Column(name = "auth_attempt_status", nullable = false)
  private AuthAttemptStatus authAttemptStatus = AuthAttemptStatus.PENDING;

  @Column(name = "auth_attempt_challenge")
  private Integer authAttemptChallenge;

  @Column(name = "auth_attempt_proof_token", nullable = false)
  private String authAttemptProofToken;

  @Column(name = "device_proof_token")
  private String deviceProofToken;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "expires_at", nullable = false)
  private LocalDateTime expiresAt;

  /** Default constructor for JPA. */
  public AuthAttempt() {
    this.createdAt = LocalDateTime.now();
    this.authAttemptStatus = AuthAttemptStatus.PENDING;
  }

  /**
   * Constructs an authorization attempt with required fields.
   *
   * @param enrollmentId the enrollment ID associated with this attempt
   * @param authAttemptProofToken the integration proof token for verification
   */
  public AuthAttempt(Integer enrollmentId, String authAttemptProofToken) {
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
   * Gets the authentication attempt status.
   *
   * @return the authentication attempt status
   */
  public AuthAttemptStatus getAuthAttemptStatus() {
    return authAttemptStatus;
  }

  /**
   * Sets the authentication attempt status.
   *
   * @param authAttemptStatus the authentication attempt status to set
   */
  public void setAuthAttemptStatus(AuthAttemptStatus authAttemptStatus) {
    this.authAttemptStatus = authAttemptStatus;
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
   * Sets the challenge code for device verification.
   *
   * @param authAttemptChallenge the challenge code to set
   */
  public void setAuthAttemptChallenge(Integer authAttemptChallenge) {
    this.authAttemptChallenge = authAttemptChallenge;
  }

  /**
   * Gets the proof token for this authorization attempt.
   *
   * @return the proof token
   */
  public String getAuthAttemptProofToken() {
    return authAttemptProofToken;
  }

  /**
   * Sets the proof token for this authorization attempt.
   *
   * @param authAttemptProofToken the proof token to set
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
   * Gets the creation timestamp.
   *
   * @return the creation timestamp
   */
  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  /**
   * Sets the creation timestamp.
   *
   * @param createdAt the creation timestamp to set
   */
  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  /**
   * Gets the expiration timestamp.
   *
   * @return the expiration timestamp
   */
  public LocalDateTime getExpiresAt() {
    return expiresAt;
  }

  /**
   * Sets the expiration timestamp.
   *
   * @param expiresAt the expiration timestamp to set
   */
  public void setExpiresAt(LocalDateTime expiresAt) {
    this.expiresAt = expiresAt;
  }

  /**
   * Checks if this authorization attempt has expired.
   *
   * @return true if the attempt has expired, false otherwise
   */
  public boolean isExpired() {
    return LocalDateTime.now().isAfter(expiresAt);
  }

  /**
   * Checks if this authorization attempt is still valid (not expired).
   *
   * @return true if the attempt is still valid, false otherwise
   */
  public boolean isValid() {
    return !isExpired();
  }

  @Override
  public String toString() {
    return "AuthAttempt{"
        + "authAttemptId="
        + authAttemptId
        + ", enrollmentId="
        + enrollmentId
        + ", authAttemptStatus="
        + authAttemptStatus
        + ", authAttemptChallenge="
        + authAttemptChallenge
        + ", createdAt="
        + createdAt
        + ", expiresAt="
        + expiresAt
        + '}';
  }
}
