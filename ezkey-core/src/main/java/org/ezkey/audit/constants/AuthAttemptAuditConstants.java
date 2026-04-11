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
}
