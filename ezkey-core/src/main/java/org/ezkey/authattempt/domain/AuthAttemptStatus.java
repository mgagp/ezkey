/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Enum: AuthAttemptStatus
 * Description: Enumeration representing the lifecycle status of authentication attempts.
 */

package org.ezkey.authattempt.domain;

/**
 * Enumeration representing the lifecycle status of authentication attempts.
 *
 * <p>This enum defines the possible states that an authentication attempt can be in during its
 * lifecycle from creation through completion. It replaces the previous boolean flags approach with
 * a single, clear status field that ensures state consistency and simplifies the authentication
 * flow logic.
 *
 * <p><b>Status Lifecycle:</b>
 *
 * <ul>
 *   <li><b>PENDING:</b> Authentication attempt created, waiting for device to claim it
 *   <li><b>READ:</b> Device has claimed the authentication attempt
 *   <li><b>INVALID:</b> Cryptographic validation failed (invalid signature)
 *   <li><b>REJECTED:</b> User explicitly denied the authentication request
 *   <li><b>ACCEPTED:</b> User approved the authentication request
 *   <li><b>EXPIRED:</b> Attempt expired due to timeout or supersession by newer attempt
 * </ul>
 *
 * <p><b>Transitions:</b>
 *
 * <ul>
 *   <li>PENDING → READ → INVALID/REJECTED/ACCEPTED
 *   <li>Any status → EXPIRED (due to timeout or supersession)
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
public enum AuthAttemptStatus {

  /**
   * Authentication attempt created and waiting for device to claim it. This is the initial state
   * when an authentication attempt is first created.
   */
  PENDING,

  /**
   * Device has claimed the authentication attempt and is processing it. The device has successfully
   * read the authentication request.
   *
   * <p>This state is transient: if the user does not approve or deny before {@code expires_at}, a
   * scheduled job ({@link org.ezkey.authattempt.service.AuthAttemptExpiryScheduler}) persists
   * {@link #EXPIRED} so the row does not remain {@code READ} indefinitely.
   */
  READ,

  /** Cryptographic validation failed. The device signature could not be verified as authentic. */
  INVALID,

  /**
   * User explicitly denied the authentication request. The user chose to reject the authentication
   * on their device.
   */
  REJECTED,

  /**
   * User approved the authentication request. The user successfully authenticated and approved the
   * request.
   */
  ACCEPTED,

  /**
   * Authentication attempt expired. Either the timeout was reached or a newer attempt superseded
   * this one.
   */
  EXPIRED
}
