/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Utility: RecoveryAuditDetails
 * Description: Structured JSON for admin recovery audit {@code event_details} (see {@code
 * .cursor/plans/archived/2026-03/SPEC_ADMIN_RECOVERY_AUDIT_TRAIL.md}).
 */

package org.ezkey.admin.audit;

import org.ezkey.audit.util.AuditDetailsBuilder;
import org.ezkey.security.SensitiveDataHasher;

/**
 * Builds JSON {@code event_details} for the admin recovery funnel (recovery code + enrollment
 * reset). Never includes secrets (recovery codes, proof tokens, bearer tokens).
 */
public final class RecoveryAuditDetails {

  /** Length of the SHA-256 hex prefix used to correlate recover and reset audit rows. */
  public static final int RECOVERY_TOKEN_FINGERPRINT_HEX_LENGTH = 16;

  private RecoveryAuditDetails() {}

  /**
   * Returns the first {@code RECOVERY_TOKEN_FINGERPRINT_HEX_LENGTH} characters of the SHA-256 hex
   * digest of the recovery token (UTF-8).
   *
   * @param recoveryToken the temporary recovery token string (never logged in full)
   * @return lowercase hex prefix, or {@code null} if the token cannot be hashed
   */
  public static String recoveryTokenFingerprint(String recoveryToken) {
    if (recoveryToken == null || recoveryToken.isBlank()) {
      return null;
    }
    String full = SensitiveDataHasher.sha256Hex(recoveryToken);
    if (full == null || full.length() < RECOVERY_TOKEN_FINGERPRINT_HEX_LENGTH) {
      return null;
    }
    return full.substring(0, RECOVERY_TOKEN_FINGERPRINT_HEX_LENGTH);
  }

  /** Maps common failure messages from recovery code validation to a stable {@code reason_code}. */
  public static String recoveryRejectionReasonCode(String message) {
    if (message == null || message.isBlank()) {
      return "unknown";
    }
    String m = message;
    if (m.contains("Invalid credentials")) {
      return "unknown_user";
    }
    if (m.contains("Invalid recovery code")) {
      return "invalid_code";
    }
    if (m.contains("No recovery codes available")) {
      return "no_codes_remaining";
    }
    if (m.contains("inactive")) {
      return "account_inactive";
    }
    return "recovery_rejected";
  }

  public static String recoveryCodeValidatedSuccess(
      String username,
      Integer adminId,
      Integer tenantId,
      int recoveryCodesRemaining,
      Integer mfaEnrollmentId,
      String recoveryTokenFingerprint) {
    AuditDetailsBuilder b =
        AuditDetailsBuilder.builder()
            .custom("schema_version", 1)
            .custom("flow", "admin_recovery")
            .custom("step", "recovery_code_validated")
            .custom("username", username)
            .custom("recovery_codes_remaining", recoveryCodesRemaining)
            .custom("recovery_token_fingerprint", recoveryTokenFingerprint);
    if (adminId != null) {
      b.custom("admin_id", adminId);
    }
    b.custom("tenant_id", tenantId);
    if (mfaEnrollmentId != null) {
      b.custom("mfa_enrollment_id", mfaEnrollmentId);
    }
    return b.toJson();
  }

  public static String recoveryCodeRejected(
      String username, Integer tenantId, String reasonCode, String message) {
    AuditDetailsBuilder b =
        AuditDetailsBuilder.builder()
            .custom("schema_version", 1)
            .custom("flow", "admin_recovery")
            .custom("step", "recovery_code_rejected")
            .custom("username", username)
            .custom("reason_code", reasonCode)
            .custom("message", message);
    b.custom("tenant_id", tenantId);
    return b.toJson();
  }

  public static String recoveryUnexpectedError(
      String username, Integer tenantId, String exceptionClass) {
    AuditDetailsBuilder b =
        AuditDetailsBuilder.builder()
            .custom("schema_version", 1)
            .custom("flow", "admin_recovery")
            .custom("step", "recovery_error")
            .custom("username", username)
            .custom("tenant_id", tenantId);
    if (exceptionClass != null && !exceptionClass.isBlank()) {
      b.custom("exception_class", exceptionClass);
    }
    return b.toJson();
  }

  public static String enrollmentResetCompleted(
      Integer adminId,
      Integer tenantId,
      Integer enrollmentId,
      Integer integrationId,
      String recoveryTokenFingerprint,
      String enrollmentStatusAfter) {
    AuditDetailsBuilder b =
        AuditDetailsBuilder.builder()
            .custom("schema_version", 1)
            .custom("flow", "admin_recovery")
            .custom("step", "enrollment_reset_completed")
            .custom("enrollment_id", enrollmentId)
            .custom("integration_id", integrationId)
            .custom("recovery_token_fingerprint", recoveryTokenFingerprint)
            .custom("device_unbound", true)
            .custom("enrollment_status_after", enrollmentStatusAfter);
    if (adminId != null) {
      b.custom("admin_id", adminId);
    }
    b.custom("tenant_id", tenantId);
    return b.toJson();
  }

  public static String enrollmentResetFailed(
      Integer adminId,
      Integer tenantId,
      Integer enrollmentId,
      String recoveryTokenFingerprint,
      String reasonCode,
      String message) {
    AuditDetailsBuilder b =
        AuditDetailsBuilder.builder()
            .custom("schema_version", 1)
            .custom("flow", "admin_recovery")
            .custom("step", "enrollment_reset_failed")
            .custom("reason_code", reasonCode)
            .custom("message", message);
    if (adminId != null) {
      b.custom("admin_id", adminId);
    }
    b.custom("tenant_id", tenantId);
    if (enrollmentId != null) {
      b.custom("enrollment_id", enrollmentId);
    }
    if (recoveryTokenFingerprint != null && !recoveryTokenFingerprint.isBlank()) {
      b.custom("recovery_token_fingerprint", recoveryTokenFingerprint);
    }
    return b.toJson();
  }
}
