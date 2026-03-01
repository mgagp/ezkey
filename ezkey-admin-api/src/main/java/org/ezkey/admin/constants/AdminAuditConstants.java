/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Constants: AdminAuditConstants
 * Description: Audit action constants for admin API operations.
 */

package org.ezkey.admin.constants;

/**
 * Audit action constants for admin API operations.
 *
 * <p>This class centralizes all audit action strings used throughout the admin API controllers.
 * Centralizing these constants provides several benefits:
 *
 * <ul>
 *   <li>Eliminates magic strings scattered throughout the codebase
 *   <li>Prevents typos in audit action strings
 *   <li>Makes refactoring audit actions easier (single point of change)
 *   <li>Improves code readability and maintainability
 *   <li>Facilitates audit log analysis and querying
 * </ul>
 *
 * <p><b>Naming Convention:</b> Constants are grouped by feature area (LOGIN, LOGOUT, RECOVERY,
 * ENROLLMENT, AUTH_ATTEMPT) and follow the pattern: {FEATURE}_{STATUS}
 *
 * <p><b>Usage Example:</b>
 *
 * <pre>
 * auditLogService.log(
 *     AuditHelper.logSuccess(context, EventType.ADMIN_LOGIN,
 *         AdminAuditConstants.LOGIN_SUCCESS, "Username: john.doe"));
 * </pre>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
public final class AdminAuditConstants {

  private AdminAuditConstants() {
    // Prevent instantiation - this is a constants class
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Admin Authentication Actions
  // ═══════════════════════════════════════════════════════════════════════════

  /** Audit action for successful admin login. */
  public static final String LOGIN_SUCCESS = "login_success";

  /** Audit action for pending admin login (challenge required, waiting for device). */
  public static final String LOGIN_PENDING = "login_pending";

  /** Audit action for failed admin login (invalid credentials, etc.). */
  public static final String LOGIN_FAILURE = "login_failure";

  /** Audit action for successful admin logout. */
  public static final String LOGOUT_SUCCESS = "logout_success";

  // ═══════════════════════════════════════════════════════════════════════════
  // Admin Recovery Actions
  // ═══════════════════════════════════════════════════════════════════════════

  /** Audit action for successful use of recovery code. */
  public static final String RECOVERY_CODE_USED = "recovery_code_used";

  /** Audit action for failed recovery code validation. */
  public static final String RECOVERY_CODE_FAILED = "recovery_code_failed";

  /** Audit action for unexpected error during recovery. */
  public static final String RECOVERY_ERROR = "recovery_error";

  // ═══════════════════════════════════════════════════════════════════════════
  // Enrollment Management Actions
  // ═══════════════════════════════════════════════════════════════════════════

  /** Audit action for successful enrollment creation. */
  public static final String ENROLLMENT_CREATED = "enrollment_created";

  /** Audit action for failed enrollment creation (validation error). */
  public static final String ENROLLMENT_CREATION_FAILED = "enrollment_creation_failed";

  /** Audit action for unexpected error during enrollment creation. */
  public static final String ENROLLMENT_CREATION_ERROR = "enrollment_creation_error";

  /** Audit action for successful enrollment deletion. */
  public static final String ENROLLMENT_DELETED = "enrollment_deleted";

  /** Audit action for failed enrollment deletion (not found). */
  public static final String ENROLLMENT_DELETION_FAILED = "enrollment_deletion_failed";

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

  // ═══════════════════════════════════════════════════════════════════════════
  // Token Prefixes
  // ═══════════════════════════════════════════════════════════════════════════

  /** Prefix for recovery tokens to distinguish them from bearer tokens. */
  public static final String RECOVERY_TOKEN_PREFIX = "ezkey_recovery_";

  /** Prefix for bearer tokens in Authorization header. */
  public static final String BEARER_PREFIX = "Bearer ";

  // ═══════════════════════════════════════════════════════════════════════════
  // API Key Actions
  // ═══════════════════════════════════════════════════════════════════════════

  /** Audit action for successful API key creation. */
  public static final String API_KEY_CREATED = "api_key_created";

  /** Audit action for failed API key creation. */
  public static final String API_KEY_CREATION_FAILED = "api_key_creation_failed";

  /** Audit action for successful API key revocation. */
  public static final String API_KEY_REVOKED = "api_key_revoked";

  /** Audit action for API key not found during revocation. */
  public static final String API_KEY_REVOCATION_NOT_FOUND = "api_key_revocation_not_found";

  // ═══════════════════════════════════════════════════════════════════════════
  // Integration Actions
  // ═══════════════════════════════════════════════════════════════════════════

  /** Audit action for successful integration creation. */
  public static final String INTEGRATION_CREATED = "integration_created";

  /** Audit action for failed integration creation. */
  public static final String INTEGRATION_CREATION_FAILED = "integration_creation_failed";

  /** Audit action for successful integration deletion. */
  public static final String INTEGRATION_DELETED = "integration_deleted";

  // ═══════════════════════════════════════════════════════════════════════════
  // Tenant Actions
  // ═══════════════════════════════════════════════════════════════════════════

  /** Audit action for successful tenant creation. */
  public static final String TENANT_CREATED = "tenant_created";

  /** Audit action for failed tenant creation. */
  public static final String TENANT_CREATION_FAILED = "tenant_creation_failed";

  /** Audit action for successful tenant update. */
  public static final String TENANT_UPDATED = "tenant_updated";

  /** Audit action for successful tenant deactivation. */
  public static final String TENANT_DEACTIVATED = "tenant_deactivated";

  // ═══════════════════════════════════════════════════════════════════════════════════════════════
  // Admin Provisioning Actions
  // ═══════════════════════════════════════════════════════════════════════════════════════════════

  /** Audit action for successful global administrator creation. */
  public static final String ADMIN_GLOBAL_CREATED = "admin_global_created";

  /** Audit action for failed global administrator creation. */
  public static final String ADMIN_GLOBAL_CREATION_FAILED = "admin_global_creation_failed";

  /** Audit action for successful tenant administrator creation. */
  public static final String ADMIN_TENANT_CREATED = "admin_tenant_created";

  /** Audit action for failed tenant administrator creation. */
  public static final String ADMIN_TENANT_CREATION_FAILED = "admin_tenant_creation_failed";

  /** Audit action for successful administrator deactivation. */
  public static final String ADMIN_DEACTIVATED = "admin_deactivated";

  /**
   * Audit action for failed administrator deactivation (not found, limit, or self-deactivation).
   */
  public static final String ADMIN_DEACTIVATION_FAILED = "admin_deactivation_failed";

  // ═══════════════════════════════════════════════════════════════════════════════════════════════
  // Encryption Key Actions
  // ═══════════════════════════════════════════════════════════════════════════════════════════════

  /** Audit action for a manually triggered encryption key rotation. */
  public static final String ENCRYPTION_KEY_ROTATION_MANUAL = "encryption_key_rotation_manual";
}
