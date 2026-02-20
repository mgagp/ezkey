/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Utility: AuditHelper
 * Description: Helper utilities for audit logging in the M2M API.
 */

package org.ezkey.m2m.util;

import org.ezkey.audit.domain.ApiName;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.util.ClientContext;

/**
 * Helper utilities for audit logging in the M2M API.
 *
 * <p>Provides common functionality for extracting audit-relevant information from HTTP requests —
 * IP address extraction with proxy header support, user agent extraction — and for building
 * consistent audit log entries tagged with {@link ApiName#M2M_API}.
 *
 * <p><b>Usage Context:</b> Used by {@code M2mAuthAttemptController} to ensure consistent audit
 * logging with proper client context extraction and uniform log structure.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
public final class AuditHelper {

  private AuditHelper() {
    // Utility class - no instantiation
  }

  /**
   * Creates a base audit log builder for M2M API operations.
   *
   * <p>Pre-populates {@link ApiName#M2M_API} plus the client IP and user agent from the provided
   * context object. Callers chain additional fields before calling {@code build()}.
   *
   * <pre>
   * auditLogService.log(
   *   AuditHelper.createM2mAudit(context, EventType.AUTH_ATTEMPT_CREATED,
   *       M2mAuditConstants.AUTH_ATTEMPT_CREATED)
   *     .eventStatus(EventStatus.SUCCESS)
   *     .authAttemptId(attemptId)
   *     .build()
   * );
   * </pre>
   *
   * @param context the client context containing IP and user agent
   * @param eventType the event type for categorisation
   * @param action the specific audit action string
   * @return pre-populated audit log builder
   */
  public static AuditLog.Builder createM2mAudit(
      ClientContext context, EventType eventType, String action) {
    return AuditLog.builder()
        .eventType(eventType)
        .eventAction(action)
        .apiName(ApiName.M2M_API)
        .ipAddress(context.clientIp())
        .userAgent(context.userAgent());
  }

  /**
   * Creates a base audit log builder for M2M API operations with tenant association.
   *
   * <p>Variant of {@link #createM2mAudit(ClientContext, EventType, String)} that also sets the
   * tenant ID for proper tenant-scoped visibility in the audit log.
   *
   * @param context the client context containing IP and user agent
   * @param eventType the event type for categorisation
   * @param action the specific audit action string
   * @param tenantId the tenant ID to associate; {@code null} for system-level events
   * @return pre-populated audit log builder with tenant ID set
   */
  public static AuditLog.Builder createM2mAudit(
      ClientContext context, EventType eventType, String action, Integer tenantId) {
    return createM2mAudit(context, eventType, action).tenantId(tenantId);
  }

  /**
   * Creates a SUCCESS audit log for M2M API operations.
   *
   * @param context the client context
   * @param eventType the event type
   * @param action the audit action string
   * @param details optional details (may be {@code null})
   * @param tenantId tenant ID; {@code null} for system-level events
   * @return complete audit log ready to be persisted
   */
  public static AuditLog logSuccess(
      ClientContext context, EventType eventType, String action, String details, Integer tenantId) {
    AuditLog.Builder builder =
        createM2mAudit(context, eventType, action, tenantId).eventStatus(EventStatus.SUCCESS);
    if (details != null && !details.isEmpty()) {
      builder.eventDetails(details);
    }
    return builder.build();
  }

  /**
   * Creates a FAILURE audit log for M2M API operations.
   *
   * @param context the client context
   * @param eventType the event type
   * @param action the audit action string
   * @param errorMessage the error message describing why the operation failed
   * @param tenantId tenant ID; {@code null} for system-level events
   * @return complete audit log ready to be persisted
   */
  public static AuditLog logFailure(
      ClientContext context,
      EventType eventType,
      String action,
      String errorMessage,
      Integer tenantId) {
    return createM2mAudit(context, eventType, action, tenantId)
        .eventStatus(EventStatus.FAILURE)
        .errorMessage(errorMessage)
        .build();
  }

  /**
   * Creates an ERROR audit log for M2M API operations.
   *
   * @param context the client context
   * @param eventType the event type
   * @param action the audit action string
   * @param errorMessage the error message from the exception
   * @param tenantId tenant ID; {@code null} for system-level events
   * @return complete audit log ready to be persisted
   */
  public static AuditLog logError(
      ClientContext context,
      EventType eventType,
      String action,
      String errorMessage,
      Integer tenantId) {
    return createM2mAudit(context, eventType, action, tenantId)
        .eventStatus(EventStatus.ERROR)
        .errorMessage(errorMessage)
        .build();
  }
}
