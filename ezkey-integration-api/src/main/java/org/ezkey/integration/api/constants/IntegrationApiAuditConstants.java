/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Constants: IntegrationApiAuditConstants
 * Description: Audit action constants for Integration API operations.
 */

package org.ezkey.integration.api.constants;

/**
 * Audit action constants for M2M (machine-to-machine) API operations.
 *
 * <p>Centralises all audit action strings used throughout the Integration API controller,
 * eliminating magic strings and providing a single point of change for audit action names.
 *
 * <p><b>Naming Convention:</b> Constants are grouped by feature area and follow the pattern {@code
 * {FEATURE}_{RESULT}}, e.g. {@code AUTH_ATTEMPT_CREATED}, {@code AUTH_ATTEMPT_CREATION_FAILED}.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
public final class IntegrationApiAuditConstants {

  private IntegrationApiAuditConstants() {
    // Prevent instantiation - constants class
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Authentication Attempt Actions
  // ═══════════════════════════════════════════════════════════════════════════

  /** Audit action for successful auth attempt creation. */
  public static final String AUTH_ATTEMPT_CREATED = "auth_attempt_created";

  /** Audit action for failed auth attempt creation (validation error). */
  public static final String AUTH_ATTEMPT_CREATION_FAILED = "auth_attempt_creation_failed";

  /** Audit action for unexpected error during auth attempt creation. */
  public static final String AUTH_ATTEMPT_CREATION_ERROR = "auth_attempt_creation_error";

  /** Audit action for successful auth attempt cancellation. */
  public static final String AUTH_ATTEMPT_CANCELLED = "auth_attempt_cancelled";

  /** Audit action for failed auth attempt cancellation (already completed or not found). */
  public static final String AUTH_ATTEMPT_CANCELLATION_FAILED = "auth_attempt_cancellation_failed";

  /** Audit action for unexpected error during auth attempt cancellation. */
  public static final String AUTH_ATTEMPT_CANCELLATION_ERROR = "auth_attempt_cancellation_error";
}
