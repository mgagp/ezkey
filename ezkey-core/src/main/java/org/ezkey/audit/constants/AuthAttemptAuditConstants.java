/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Constants: AuthAttemptAuditConstants
 * Description: Stable audit action strings for authentication attempt lifecycle events shared across
 *     modules.
 */
package org.ezkey.audit.constants;

/**
 * Audit action constants for authentication attempts used outside Admin/Integration API controllers
 * (for example scheduled expiry in core).
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
public final class AuthAttemptAuditConstants {

  private AuthAttemptAuditConstants() {}

  /**
   * Persisted {@code PENDING} or {@code READ} auth attempt was marked {@code EXPIRED} by the
   * scheduled TTL alignment job (past {@code expires_at}).
   */
  public static final String AUTH_ATTEMPT_EXPIRED_SCHEDULER = "auth_attempt_expired_scheduler";

  /**
   * Respond rejected: device signature failed ownership verification. Attempt status unchanged;
   * client receives generic state-conflict (#912). Event details must never include the signature.
   */
  public static final String AUTH_ATTEMPT_RESPOND_SIGNATURE_REJECTED =
      "auth_attempt_respond_signature_rejected";

  /** Respond processed with result {@code APPROVED} (#1314). */
  public static final String AUTH_ATTEMPT_APPROVED = "auth_attempt_approved";

  /** Respond processed with result {@code DENIED} (#1314). */
  public static final String AUTH_ATTEMPT_DENIED = "auth_attempt_denied";

  /**
   * Respond processed with result {@code FAILED}: accept after a valid device signature, but the
   * challenge response was missing or did not match; the attempt is now {@code INVALID} (#1314).
   * Event details must never include the entered code.
   */
  public static final String AUTH_ATTEMPT_CHALLENGE_FAILED = "auth_attempt_challenge_failed";

  /**
   * Respond returned a result the audit mapping does not expect ({@code EXPIRED} or none). Not
   * reachable today: wall-clock expiry throws before a result is built (#895, #1314).
   */
  public static final String AUTH_ATTEMPT_RESPOND_UNEXPECTED_RESULT =
      "auth_attempt_respond_unexpected_result";
}
