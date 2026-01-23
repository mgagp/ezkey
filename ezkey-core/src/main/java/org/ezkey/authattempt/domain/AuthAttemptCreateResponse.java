/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Domain: AuthAttemptCreateResponse
 * Description: Domain response object for creating authentication attempts.
 */

package org.ezkey.authattempt.domain;

import java.time.OffsetDateTime;

/**
 * Domain response object for creating authentication attempts.
 *
 * <p>This domain object represents the response data returned internally by the service layer after
 * successfully creating an authentication attempt. It contains the created attempt's ID enabling
 * tracking and monitoring of the authentication process.
 *
 * <p><b>Usage Context:</b> Used by the AuthAttemptService to return authentication attempt creation
 * results to both admin and auth API controllers. The service layer transforms this domain object
 * into appropriate API DTOs for external consumption.
 *
 * <p><b>Authentication Flow:</b> This response confirms that an authentication attempt has been
 * successfully created and is now pending user approval through their enrolled mobile device. The
 * authentication attempt ID can be used for tracking and monitoring the authentication process.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see org.ezkey.authattempt.service.AuthAttemptService
 * @see org.ezkey.authattempt.domain.AuthAttemptCreateRequest
 * @see org.ezkey.authattempt.domain.entity.AuthAttempt
 */
public class AuthAttemptCreateResponse {

  /**
   * Unique identifier of the created authentication attempt.
   *
   * <p>Used to reference this specific authentication attempt in subsequent operations and
   * tracking. This ID links the authentication request to the user's approval or denial decision
   * and enables monitoring of the authentication flow status.
   */
  private Integer authAttemptId;

  /**
   * Optional challenge code generated for this authentication attempt.
   *
   * <p>When challenge verification is required, this field contains the numeric challenge code (4-6
   * digits) that the user must enter on their device during approval. Null if no challenge was
   * requested.
   */
  private Integer authAttemptChallenge;

  /**
   * Timestamp when the authentication attempt was created.
   *
   * <p>Required for foreign key reference to partitioned table. Used in audit logs to maintain
   * referential integrity with the partitioned ezkey_auth_attempt table.
   */
  private OffsetDateTime createdAt;

  /**
   * Timeout duration in seconds for this authentication attempt.
   *
   * <p>Indicates the maximum time (in seconds) that the user has to respond to the authentication
   * request on their mobile device. This value is returned to client applications so they can
   * display appropriate timeout information to users and manage their wait operations accordingly.
   */
  private Integer timeoutSeconds;

  /**
   * Absolute expiration timestamp for this authentication attempt.
   *
   * <p>Indicates the exact moment when this authentication attempt will expire. This timestamp is
   * calculated based on the creation time plus the timeout duration. Client applications can use
   * this to display countdown timers or determine if an attempt has expired without needing to
   * query the server.
   */
  private OffsetDateTime expiresAt;

  /**
   * Gets the authentication attempt ID.
   *
   * @return the unique identifier of the created authentication attempt
   */
  public Integer getAuthAttemptId() {
    return authAttemptId;
  }

  /**
   * Sets the authentication attempt ID.
   *
   * @param authAttemptId the authentication attempt ID to set
   */
  public void setAuthAttemptId(Integer authAttemptId) {
    this.authAttemptId = authAttemptId;
  }

  /**
   * Gets the authentication attempt challenge code.
   *
   * @return the challenge code, or null if no challenge
   */
  public Integer getAuthAttemptChallenge() {
    return authAttemptChallenge;
  }

  /**
   * Sets the authentication attempt challenge code.
   *
   * @param authAttemptChallenge the challenge code to set
   */
  public void setAuthAttemptChallenge(Integer authAttemptChallenge) {
    this.authAttemptChallenge = authAttemptChallenge;
  }

  /**
   * Gets the creation timestamp of the authentication attempt.
   *
   * @return the creation timestamp
   */
  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  /**
   * Sets the creation timestamp of the authentication attempt.
   *
   * @param createdAt the creation timestamp to set
   */
  public void setCreatedAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
  }

  /**
   * Gets the timeout duration in seconds.
   *
   * @return the timeout duration in seconds
   */
  public Integer getTimeoutSeconds() {
    return timeoutSeconds;
  }

  /**
   * Sets the timeout duration in seconds.
   *
   * @param timeoutSeconds the timeout duration in seconds to set
   */
  public void setTimeoutSeconds(Integer timeoutSeconds) {
    this.timeoutSeconds = timeoutSeconds;
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
}
