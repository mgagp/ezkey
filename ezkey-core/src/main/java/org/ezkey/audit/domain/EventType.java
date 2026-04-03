/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Enum: EventType
 * Description: Enumeration of audit event types for security monitoring and compliance tracking.
 */

package org.ezkey.audit.domain;

/**
 * Enumeration of audit event types.
 *
 * <p>Defines all types of security-relevant events that are tracked in the audit log system for
 * monitoring, forensic analysis, and SOC2 compliance requirements.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
public enum EventType {
  // Admin authentication events
  ADMIN_LOGIN,
  ADMIN_LOGOUT,
  ADMIN_PASSWORD_CHANGE,
  ADMIN_RECOVERY_USE,
  /**
   * MFA enrollment reset completed using a recovery token (device unbound, new bind credentials
   * issued). Distinct from {@link #ADMIN_RECOVERY_USE} (recovery code / recovery token issuance).
   */
  ADMIN_RECOVERY_ENROLLMENT_RESET,
  ADMIN_CREATED, // New administrator provisioned (global or tenant)
  ADMIN_PROFILE_UPDATED, // Administrator profile fields updated (firstName, lastName, email,
  // challengeRequired)
  ADMIN_DEACTIVATED, // Administrator deactivated + tokens revoked (+ reason when provided)
  ADMIN_ACTIVATED, // Administrator reactivated (reversible after deactivation)

  // Enrollment events
  ENROLLMENT_CREATED,
  ENROLLMENT_UPDATED, // Enrollment metadata updated (enrollmentName, contactEmail, expiresAt,
  // authAttemptChallengeRequired, userIdentifier); event_details JSON with change list
  ENROLLMENT_DELETED,
  ENROLLMENT_BIND,
  ENROLLMENT_VERIFY,
  ENROLLMENT_REVOKED, // Admin permanently revoked an enrollment (SOC 2 CC6.3)
  ENROLLMENT_DEACTIVATED, // Admin deactivated an enrollment (reversible)
  ENROLLMENT_REACTIVATED, // Admin reactivated a previously deactivated enrollment
  ENROLLMENT_AUTH_ATTEMPT_BLOCKED, // Auth attempt rejected because enrollment is inactive/revoked
  ENROLLMENT_EXPIRED, // Pending enrollment expired (expires_at passed); status set to EXPIRED

  // Authentication attempt events
  AUTH_ATTEMPT_CREATED,
  AUTH_ATTEMPT_PENDING,
  AUTH_ATTEMPT_RESPOND,
  AUTH_ATTEMPT_CANCELLED,

  // API key events
  API_KEY_CREATED,
  API_KEY_UPDATED, // API key config updated (ipWhitelist, description)
  API_KEY_REVOKED,
  API_KEY_EXPIRED,
  API_KEY_AUTH_SUCCESS,
  API_KEY_AUTH_FAILED,
  API_KEY_IP_BLOCKED,

  // System events
  SYSTEM_ERROR,

  // Encryption key lifecycle events
  KEY_INTRODUCED, // New encryption key added to keyset
  KEY_PROMOTED_PRIMARY, // Key promoted to primary for new encryption
  KEY_DEMOTED, // Key demoted from PRIMARY to ENABLED
  KEY_DISABLED, // Key disabled (no longer for decryption)
  KEYSET_BACKUP_CREATED, // Backup created before rotation

  // Re-encryption batch events
  REENCRYPTION_STARTED, // Batch re-encryption initiated
  REENCRYPTION_BATCH_PROGRESS, // Periodic progress update (every 10%)
  REENCRYPTION_COMPLETED, // Batch re-encryption finished successfully
  REENCRYPTION_FAILED, // Batch re-encryption error
  REENCRYPTION_RESUMED, // Batch resumed after pause/failure
  REENCRYPTION_PAUSED, // Batch paused by admin

  // Integration lifecycle events
  INTEGRATION_CREATED, // New integration registered
  INTEGRATION_UPDATED, // Integration updated
  INTEGRATION_RETIRED, // Integration retired from day-to-day operations
  INTEGRATION_DELETED, // Integration removed (+ reason when provided)

  // Tenant lifecycle events
  TENANT_CREATED, // New tenant provisioned
  TENANT_UPDATED, // Tenant metadata updated
  TENANT_DEACTIVATED, // Tenant deactivated (+ reason when provided)
  TENANT_ACTIVATED, // Tenant reactivated (+ reason when provided)

  // Audit chain lifecycle events
  AUDIT_CHAIN_ARCHIVE_SEALED, // Audit partition sealed prior to archival and partition drop
  AUDIT_CHAIN_GAP_DECLARED, // Admin-declared downtime gap formally documented in the chain
  AUDIT_CHAIN_GAP_PENDING // Scheduler detected an undeclared gap before its lookback window
}
