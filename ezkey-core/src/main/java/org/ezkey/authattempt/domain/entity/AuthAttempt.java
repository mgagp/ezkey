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
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import org.ezkey.authattempt.domain.AuthAttemptStatus;
import org.ezkey.security.EncryptionEntityListener;
import org.ezkey.security.EncryptionService;
import org.ezkey.security.Reencryptable;
import org.ezkey.security.SensitiveDataHasher;
import org.slf4j.LoggerFactory;

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
@EntityListeners(EncryptionEntityListener.class)
@Table(name = "ezkey_auth_attempt")
public class AuthAttempt implements Reencryptable {

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

  @Column(name = "auth_attempt_proof_token", columnDefinition = "TEXT", nullable = false)
  private String encryptedAuthAttemptProofToken;

  @Column(name = "auth_attempt_proof_token_hash", length = 128, unique = true)
  private String authAttemptProofTokenHash;

  @Transient private String authAttemptProofToken;

  @Column(name = "device_proof_token", columnDefinition = "TEXT")
  private String encryptedDeviceProofToken;

  @Column(name = "device_proof_token_hash", length = 128, unique = true)
  private String deviceProofTokenHash;

  @Transient private String deviceProofToken;

  @Column(name = "context_title", length = 200)
  private String contextTitle;

  @Column(name = "context_message", length = 2000)
  private String contextMessage;

  /**
   * When true and Auth API {@code ezkey.demo.mitm-signature-enabled} is on, the Pending response
   * body is altered after signing (presentation-only simulated MITM).
   */
  @Column(name = "demo_mitm_signature_enabled", nullable = false)
  private boolean demoMitmSignatureEnabled = false;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "expires_at", nullable = false)
  private OffsetDateTime expiresAt;

  /** Default constructor for JPA. */
  public AuthAttempt() {
    this.createdAt = OffsetDateTime.now();
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
   * Gets the ID (alias for getAuthAttemptId for JPA compatibility).
   *
   * @return the authorization attempt ID
   */
  public Integer getId() {
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
    if (authAttemptProofToken != null) {
      return authAttemptProofToken;
    }

    if (encryptedAuthAttemptProofToken == null) {
      return null;
    }

    EncryptionService service = getEncryptionService();
    if (service != null && service.isEncryptionAvailable()) {
      if (service.isEncrypted(encryptedAuthAttemptProofToken)) {
        try {
          authAttemptProofToken = service.decrypt(encryptedAuthAttemptProofToken);
          return authAttemptProofToken;
        } catch (Exception exception) {
          LoggerFactory.getLogger(AuthAttempt.class)
              .warn(
                  "Failed to decrypt auth attempt proof token for authAttemptId {}. Returning"
                      + " as-is.",
                  authAttemptId,
                  exception);
          return encryptedAuthAttemptProofToken;
        }
      }
    }

    authAttemptProofToken = encryptedAuthAttemptProofToken;
    return authAttemptProofToken;
  }

  /**
   * Sets the proof token for this authorization attempt.
   *
   * @param authAttemptProofToken the proof token to set
   */
  public void setAuthAttemptProofToken(String authAttemptProofToken) {
    this.authAttemptProofToken = authAttemptProofToken;
    this.authAttemptProofTokenHash = SensitiveDataHasher.sha256Hex(authAttemptProofToken);
    this.encryptedAuthAttemptProofToken = authAttemptProofToken;
  }

  /**
   * Gets the device proof token.
   *
   * @return the device proof token
   */
  public String getDeviceProofToken() {
    if (deviceProofToken != null) {
      return deviceProofToken;
    }

    if (encryptedDeviceProofToken == null) {
      return null;
    }

    EncryptionService service = getEncryptionService();
    if (service != null && service.isEncryptionAvailable()) {
      if (service.isEncrypted(encryptedDeviceProofToken)) {
        try {
          deviceProofToken = service.decrypt(encryptedDeviceProofToken);
          return deviceProofToken;
        } catch (Exception exception) {
          LoggerFactory.getLogger(AuthAttempt.class)
              .warn(
                  "Failed to decrypt device proof token for authAttemptId {}. Returning as-is.",
                  authAttemptId,
                  exception);
          return encryptedDeviceProofToken;
        }
      }
    }

    deviceProofToken = encryptedDeviceProofToken;
    return deviceProofToken;
  }

  /**
   * Sets the device proof token.
   *
   * @param deviceProofToken the device proof token to set
   */
  public void setDeviceProofToken(String deviceProofToken) {
    this.deviceProofToken = deviceProofToken;
    this.deviceProofTokenHash = SensitiveDataHasher.sha256Hex(deviceProofToken);
    this.encryptedDeviceProofToken = deviceProofToken;
  }

  /**
   * Gets the creation timestamp.
   *
   * @return the creation timestamp
   */
  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  /**
   * Sets the creation timestamp.
   *
   * @param createdAt the creation timestamp to set
   */
  public void setCreatedAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
  }

  /**
   * Gets the expiration timestamp.
   *
   * @return the expiration timestamp
   */
  public OffsetDateTime getExpiresAt() {
    return expiresAt;
  }

  /**
   * Sets the expiration timestamp.
   *
   * @param expiresAt the expiration timestamp to set
   */
  public void setExpiresAt(OffsetDateTime expiresAt) {
    this.expiresAt = expiresAt;
  }

  /**
   * Gets the optional short title for contextual authentication (e.g. "Payment Approval").
   *
   * @return the context title, or null if not provided
   */
  public String getContextTitle() {
    return contextTitle;
  }

  /**
   * Sets the optional short title for contextual authentication.
   *
   * @param contextTitle the context title to set
   */
  public void setContextTitle(String contextTitle) {
    this.contextTitle = contextTitle;
  }

  /**
   * Gets the optional descriptive message for contextual authentication.
   *
   * @return the context message, or null if not provided
   */
  public String getContextMessage() {
    return contextMessage;
  }

  /**
   * Sets the optional descriptive message for contextual authentication.
   *
   * @param contextMessage the context message to set
   */
  public void setContextMessage(String contextMessage) {
    this.contextMessage = contextMessage;
  }

  /**
   * Whether this attempt is opted into demo MITM simulation (no effect unless Auth API demo flag is
   * enabled).
   *
   * @return true when the attempt should receive a tampered Pending body in demo mode
   */
  public boolean isDemoMitmSignatureEnabled() {
    return demoMitmSignatureEnabled;
  }

  /**
   * Sets the demo MITM simulation flag (typically set at creation from API request).
   *
   * @param demoMitmSignatureEnabled whether to enable tampered Pending in demo mode
   */
  public void setDemoMitmSignatureEnabled(boolean demoMitmSignatureEnabled) {
    this.demoMitmSignatureEnabled = demoMitmSignatureEnabled;
  }

  /**
   * Checks if this authorization attempt has expired.
   *
   * @return true if the attempt has expired, false otherwise
   */
  public boolean isExpired() {
    return OffsetDateTime.now().isAfter(expiresAt);
  }

  /**
   * Checks if this authorization attempt is still valid (not expired).
   *
   * @return true if the attempt is still valid, false otherwise
   */
  public boolean isValid() {
    return !isExpired();
  }

  public String getAuthAttemptProofTokenHash() {
    return authAttemptProofTokenHash;
  }

  public void setAuthAttemptProofTokenHash(String authAttemptProofTokenHash) {
    this.authAttemptProofTokenHash = authAttemptProofTokenHash;
  }

  public String getDeviceProofTokenHash() {
    return deviceProofTokenHash;
  }

  public void setDeviceProofTokenHash(String deviceProofTokenHash) {
    this.deviceProofTokenHash = deviceProofTokenHash;
  }

  private EncryptionService getEncryptionService() {
    try {
      java.lang.reflect.Field field =
          Class.forName("org.ezkey.security.EncryptionEntityListener")
              .getDeclaredField("encryptionService");
      field.setAccessible(true);
      return (EncryptionService) field.get(null);
    } catch (Exception exception) {
      return null;
    }
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
        + ", contextTitle='"
        + contextTitle
        + '\''
        + ", createdAt="
        + createdAt
        + ", expiresAt="
        + expiresAt
        + '}';
  }

  // ===== Reencryptable Interface Implementation =====

  @Override
  public Map<String, String> getEncryptedFields() {
    Map<String, String> fields = new HashMap<>();
    if (encryptedAuthAttemptProofToken != null) {
      fields.put("auth_attempt_proof_token", encryptedAuthAttemptProofToken);
    }
    if (encryptedDeviceProofToken != null) {
      fields.put("device_proof_token", encryptedDeviceProofToken);
    }
    return fields;
  }

  @Override
  public void setEncryptedField(String columnName, String encryptedValue) {
    switch (columnName) {
      case "auth_attempt_proof_token":
        this.encryptedAuthAttemptProofToken = encryptedValue;
        this.authAttemptProofToken = null; // Clear transient to force re-decryption
        break;
      case "device_proof_token":
        this.encryptedDeviceProofToken = encryptedValue;
        this.deviceProofToken = null; // Clear transient to force re-decryption
        break;
      default:
        throw new IllegalArgumentException("Unknown encrypted field: " + columnName);
    }
  }

  @Override
  public Long getEntityId() {
    return authAttemptId != null ? Long.valueOf(authAttemptId) : null;
  }

  @Override
  public String getTableName() {
    return "ezkey_auth_attempt";
  }
}
