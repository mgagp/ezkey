/*
 * Ezkey - Open Source MFA/Passkey Alternative
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
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
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

  // Enrollment events
  ENROLLMENT_CREATED,
  ENROLLMENT_DELETED,
  ENROLLMENT_BIND,
  ENROLLMENT_VERIFY,

  // Authentication attempt events
  AUTH_ATTEMPT_CREATED,
  AUTH_ATTEMPT_PENDING,
  AUTH_ATTEMPT_RESPOND,

  // System events
  SYSTEM_ERROR
}
