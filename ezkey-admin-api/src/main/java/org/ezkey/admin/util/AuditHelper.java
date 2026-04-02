/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Utility: AuditHelper
 * Description: Helper utilities for audit logging in admin-api.
 */

package org.ezkey.admin.util;

import java.util.LinkedHashMap;
import java.util.Map;
import org.ezkey.audit.domain.ApiName;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.util.ClientContext;
import org.ezkey.util.PhoneNumberUtils;

/**
 * Helper utilities for audit logging.
 *
 * <p>Provides common functionality for extracting audit-relevant information from HTTP requests
 * such as IP addresses and user agents, as well as building consistent audit log entries for the
 * admin API.
 *
 * <p><b>Usage Context:</b> Used by all admin API controllers to ensure consistent audit logging
 * with proper client context extraction and uniform log structure.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
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
   * Creates base audit log builder for admin API operations.
   *
   * <p>This method provides a centralized way to create audit log entries for the admin API with
   * consistent population of common fields. It automatically sets the API name to ADMIN_API and
   * includes client context (IP address and user agent).
   *
   * <p><b>Usage Example:</b>
   *
   * <pre>
   * ClientContext context = ClientContext.from(httpRequest);
   * auditLogService.log(
   *     AuditHelper.createAdminAudit(context, EventType.ADMIN_LOGIN, AdminAuditConstants.LOGIN_SUCCESS)
   *         .eventStatus(EventStatus.SUCCESS)
   *         .eventDetails("Username: john.doe")
   *         .build());
   * </pre>
   *
   * <p><b>Benefits:</b>
   *
   * <ul>
   *   <li>Ensures consistent audit log structure across all admin endpoints
   *   <li>Reduces code duplication in controllers
   *   <li>Guarantees API name is always set correctly
   *   <li>Single point of change for admin audit log format
   * </ul>
   *
   * @param context the client context containing IP and user agent
   * @param eventType the event type for categorization
   * @param action the specific action being audited
   * @return audit log builder with common fields pre-populated, ready for additional fields
   * @see ClientContext
   * @see AuditLog
   */
  public static AuditLog.Builder createAdminAudit(
      ClientContext context, EventType eventType, String action) {

    return AuditLog.builder()
        .eventType(eventType)
        .eventAction(action)
        .apiName(ApiName.ADMIN_API)
        .ipAddress(context.clientIp())
        .userAgent(context.userAgent());
  }

  /**
   * Creates base audit log builder for admin API operations with tenant association.
   *
   * <p>Variant of {@link #createAdminAudit(ClientContext, EventType, String)} that pre-populates
   * the {@code tenantId} field. Use this overload when the tenant context is known at audit
   * creation time to ensure proper tenant-scoped visibility of audit entries.
   *
   * @param context the client context containing IP and user agent
   * @param eventType the event type for categorization
   * @param action the specific action being audited
   * @param tenantId the tenant ID to associate with this audit entry; {@code null} for system-level
   *     events visible only to Global Admins
   * @return audit log builder with common fields and tenantId pre-populated
   * @see #createAdminAudit(ClientContext, EventType, String)
   */
  public static AuditLog.Builder createAdminAudit(
      ClientContext context, EventType eventType, String action, Integer tenantId) {

    return createAdminAudit(context, eventType, action).tenantId(tenantId);
  }

  /**
   * Creates a SUCCESS audit log entry for admin API operations.
   *
   * <p>This is a convenience method for the most common audit pattern - logging successful
   * operations with optional details. It pre-populates the event status as SUCCESS.
   *
   * <p><b>Usage Example:</b>
   *
   * <pre>
   * auditLogService.log(
   *     AuditHelper.logSuccess(context, EventType.ADMIN_LOGIN, AdminAuditConstants.LOGIN_SUCCESS,
   *         "Username: john.doe"));
   * </pre>
   *
   * @param context the client context containing IP and user agent
   * @param eventType the event type for categorization
   * @param action the specific action being audited
   * @param details optional details about the operation (can be null)
   * @return complete audit log ready to be logged
   */
  public static AuditLog logSuccess(
      ClientContext context, EventType eventType, String action, String details) {

    AuditLog.Builder builder =
        createAdminAudit(context, eventType, action).eventStatus(EventStatus.SUCCESS);

    if (details != null && !details.isEmpty()) {
      builder.eventDetails(details);
    }

    return builder.build();
  }

  /**
   * Creates a SUCCESS audit log entry for admin API operations with tenant association.
   *
   * <p>Variant of {@link #logSuccess(ClientContext, EventType, String, String)} that includes the
   * {@code tenantId} for proper tenant-scoped visibility.
   *
   * @param context the client context containing IP and user agent
   * @param eventType the event type for categorization
   * @param action the specific action being audited
   * @param details optional details about the operation (can be null)
   * @param tenantId the tenant ID to associate; {@code null} for system-level events
   * @return complete audit log ready to be logged
   */
  public static AuditLog logSuccess(
      ClientContext context, EventType eventType, String action, String details, Integer tenantId) {

    AuditLog.Builder builder =
        createAdminAudit(context, eventType, action, tenantId).eventStatus(EventStatus.SUCCESS);

    if (details != null && !details.isEmpty()) {
      builder.eventDetails(details);
    }

    return builder.build();
  }

  /**
   * Creates a FAILURE audit log entry for admin API operations.
   *
   * <p>This is a convenience method for logging failed operations (validation errors, business rule
   * violations, etc.) with an error message. It pre-populates the event status as FAILURE.
   *
   * <p><b>Usage Example:</b>
   *
   * <pre>
   * auditLogService.log(
   *     AuditHelper.logFailure(context, EventType.ADMIN_LOGIN, AdminAuditConstants.LOGIN_FAILURE,
   *         "Invalid credentials"));
   * </pre>
   *
   * @param context the client context containing IP and user agent
   * @param eventType the event type for categorization
   * @param action the specific action being audited
   * @param errorMessage the error message describing why the operation failed
   * @return complete audit log ready to be logged
   */
  public static AuditLog logFailure(
      ClientContext context, EventType eventType, String action, String errorMessage) {

    return createAdminAudit(context, eventType, action)
        .eventStatus(EventStatus.FAILURE)
        .errorMessage(errorMessage)
        .build();
  }

  /**
   * Creates a FAILURE audit log entry for admin API operations with tenant association.
   *
   * <p>Variant of {@link #logFailure(ClientContext, EventType, String, String)} that includes the
   * {@code tenantId} for proper tenant-scoped visibility.
   *
   * @param context the client context containing IP and user agent
   * @param eventType the event type for categorization
   * @param action the specific action being audited
   * @param errorMessage the error message describing why the operation failed
   * @param tenantId the tenant ID to associate; {@code null} for system-level events
   * @return complete audit log ready to be logged
   */
  public static AuditLog logFailure(
      ClientContext context,
      EventType eventType,
      String action,
      String errorMessage,
      Integer tenantId) {

    return createAdminAudit(context, eventType, action, tenantId)
        .eventStatus(EventStatus.FAILURE)
        .errorMessage(errorMessage)
        .build();
  }

  /**
   * Creates an ERROR audit log entry for admin API operations.
   *
   * <p>This is a convenience method for logging unexpected errors (exceptions, system errors) with
   * an error message. It pre-populates the event status as ERROR.
   *
   * <p><b>Usage Example:</b>
   *
   * <pre>
   * auditLogService.log(
   *     AuditHelper.logError(context, EventType.ADMIN_LOGIN, "login_error", exception.getMessage()));
   * </pre>
   *
   * @param context the client context containing IP and user agent
   * @param eventType the event type for categorization
   * @param action the specific action being audited
   * @param errorMessage the error message from the exception
   * @return complete audit log ready to be logged
   */
  public static AuditLog logError(
      ClientContext context, EventType eventType, String action, String errorMessage) {

    return createAdminAudit(context, eventType, action)
        .eventStatus(EventStatus.ERROR)
        .errorMessage(errorMessage)
        .build();
  }

  /**
   * Creates an ERROR audit log entry for admin API operations with tenant association.
   *
   * <p>Variant of {@link #logError(ClientContext, EventType, String, String)} that includes the
   * {@code tenantId} for proper tenant-scoped visibility.
   *
   * @param context the client context containing IP and user agent
   * @param eventType the event type for categorization
   * @param action the specific action being audited
   * @param errorMessage the error message from the exception
   * @param tenantId the tenant ID to associate; {@code null} for system-level events
   * @return complete audit log ready to be logged
   */
  public static AuditLog logError(
      ClientContext context,
      EventType eventType,
      String action,
      String errorMessage,
      Integer tenantId) {

    return createAdminAudit(context, eventType, action, tenantId)
        .eventStatus(EventStatus.ERROR)
        .errorMessage(errorMessage)
        .build();
  }

  /**
   * Creates a standard audit change entry for structured {@code event_details}.
   *
   * @param field the field name
   * @param previous the previous value
   * @param newValue the new value
   * @return a map suitable for the {@code changes} array
   */
  public static Map<String, Object> changeEntry(String field, Object previous, Object newValue) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("field", field);
    row.put("previous", previous);
    row.put("new", newValue);
    return row;
  }

  /**
   * Creates a masked phone-number audit change entry.
   *
   * @param field the field name
   * @param previous the previous canonical phone number
   * @param newValue the new canonical phone number
   * @return a change row with masked values only
   */
  public static Map<String, Object> maskedPhoneChangeEntry(
      String field, String previous, String newValue) {
    return changeEntry(
        field, PhoneNumberUtils.maskForAudit(previous), PhoneNumberUtils.maskForAudit(newValue));
  }
}
