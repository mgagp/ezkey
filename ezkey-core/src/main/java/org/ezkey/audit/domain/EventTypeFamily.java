/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.audit.domain;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * High-level grouping of {@link EventType} values for audit log filtering and operator UX.
 *
 * <p>Each {@link EventType} belongs to exactly one family. Families align with the comment sections
 * in {@link EventType}.
 */
public enum EventTypeFamily {
  ADMIN(
      EventType.ADMIN_LOGIN,
      EventType.ADMIN_LOGOUT,
      EventType.ADMIN_PASSWORD_CHANGE,
      EventType.ADMIN_RECOVERY_USE,
      EventType.ADMIN_RECOVERY_CODES_ISSUED,
      EventType.ADMIN_RECOVERY_CODES_REGENERATED,
      EventType.ADMIN_ACTIVATION,
      EventType.ADMIN_ACTIVATION_CODE_REISSUED,
      EventType.ADMIN_RECOVERY_ENROLLMENT_RESET,
      EventType.ADMIN_CREATED,
      EventType.ADMIN_PROFILE_UPDATED,
      EventType.ADMIN_DEACTIVATED,
      EventType.ADMIN_ACTIVATED),
  ENROLLMENT(
      EventType.ENROLLMENT_CREATED,
      EventType.ENROLLMENT_UPDATED,
      EventType.ENROLLMENT_DELETED,
      EventType.ENROLLMENT_BIND,
      EventType.ENROLLMENT_VERIFY,
      EventType.ENROLLMENT_REVOKED,
      EventType.ENROLLMENT_DEACTIVATED,
      EventType.ENROLLMENT_REACTIVATED,
      EventType.ENROLLMENT_AUTH_ATTEMPT_BLOCKED,
      EventType.ENROLLMENT_EXPIRED),
  AUTH_ATTEMPT(
      EventType.AUTH_ATTEMPT_CREATED,
      EventType.AUTH_ATTEMPT_PENDING,
      EventType.AUTH_ATTEMPT_RESPOND,
      EventType.AUTH_ATTEMPT_CANCELLED,
      EventType.AUTH_ATTEMPT_EXPIRED),
  API_KEY(
      EventType.API_KEY_CREATED,
      EventType.API_KEY_UPDATED,
      EventType.API_KEY_REVOKED,
      EventType.API_KEY_EXPIRED,
      EventType.API_KEY_AUTH_SUCCESS,
      EventType.API_KEY_AUTH_FAILED,
      EventType.API_KEY_IP_BLOCKED),
  SYSTEM(EventType.SYSTEM_ERROR),
  ENCRYPTION_KEY(
      EventType.KEY_INTRODUCED,
      EventType.KEY_PROMOTED_PRIMARY,
      EventType.KEY_DEMOTED,
      EventType.KEY_DISABLED,
      EventType.KEYSET_BACKUP_CREATED),
  REENCRYPTION(
      EventType.REENCRYPTION_STARTED,
      EventType.REENCRYPTION_BATCH_PROGRESS,
      EventType.REENCRYPTION_COMPLETED,
      EventType.REENCRYPTION_FAILED,
      EventType.REENCRYPTION_RESUMED,
      EventType.REENCRYPTION_PAUSED),
  INTEGRATION(
      EventType.INTEGRATION_CREATED,
      EventType.INTEGRATION_UPDATED,
      EventType.INTEGRATION_RETIRED,
      EventType.INTEGRATION_DELETED),
  TENANT(
      EventType.TENANT_CREATED,
      EventType.TENANT_UPDATED,
      EventType.TENANT_DEACTIVATED,
      EventType.TENANT_ACTIVATED,
      EventType.EVALUATOR_SELF_REGISTRATION),
  AUDIT_CHAIN(
      EventType.AUDIT_CHAIN_ARCHIVE_SEALED,
      EventType.AUDIT_CHAIN_ARCHIVE_EXPORTED,
      EventType.AUDIT_CHAIN_GAP_DECLARED,
      EventType.AUDIT_CHAIN_INCIDENT_DECLARED,
      EventType.AUDIT_INTEGRITY_RUPTURE_CONCILIATED,
      EventType.AUDIT_ENTRY_INTEGRITY_CONCILIATED,
      EventType.NIGHTLY_INTEGRITY_VALIDATION_COMPLETED,
      EventType.INTEGRITY_ASYNC_JOB_STARTED,
      EventType.INTEGRITY_ASYNC_JOB_COMPLETED,
      EventType.INTEGRITY_ASYNC_JOB_ABANDONED),
  ALERT(EventType.ALERT_RAISED, EventType.ALERT_RESOLVED);

  private final Set<EventType> members;

  EventTypeFamily(EventType... members) {
    this.members = Collections.unmodifiableSet(EnumSet.copyOf(Arrays.asList(members)));
  }

  /**
   * Returns the event types in this family for API filtering ({@code WHERE event_type IN (...)}).
   *
   * @return immutable set of member event types
   */
  public Set<EventType> getMemberEventTypes() {
    return members;
  }

  /**
   * Returns all enum constants in API wire order (enum declaration order).
   *
   * @return immutable list
   */
  public static List<EventTypeFamily> allInDeclarationOrder() {
    return List.of(values());
  }
}
