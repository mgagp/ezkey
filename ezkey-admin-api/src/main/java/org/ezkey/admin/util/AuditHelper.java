/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Utility: AuditHelper
 * Description: Helper utilities for audit logging in admin-api.
 */

package org.ezkey.admin.util;

import jakarta.servlet.http.HttpServletRequest;
import org.ezkey.audit.domain.ApiName;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.domain.entity.AuditLog;

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
   * Extract client IP address from HTTP request.
   *
   * <p>Checks headers in priority order:
   *
   * <ol>
   *   <li>CF-Connecting-IP (Cloudflare)
   *   <li>X-Forwarded-For (standard proxy header)
   *   <li>X-Real-IP (nginx proxy header)
   *   <li>Remote address (direct connection)
   * </ol>
   *
   * @param request the HTTP servlet request
   * @return client IP address or null if not available
   */
  public static String extractClientIp(HttpServletRequest request) {
    if (request == null) {
      return null;
    }

    // Priority 1: CF-Connecting-IP (Cloudflare)
    String cfConnectingIp = request.getHeader("CF-Connecting-IP");
    if (cfConnectingIp != null && !cfConnectingIp.isEmpty()) {
      return cfConnectingIp.trim();
    }

    // Priority 2: X-Forwarded-For (standard proxy header)
    String xForwardedFor = request.getHeader("X-Forwarded-For");
    if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
      return xForwardedFor.split(",")[0].trim();
    }

    // Priority 3: X-Real-IP (nginx proxy header)
    String xRealIP = request.getHeader("X-Real-IP");
    if (xRealIP != null && !xRealIP.isEmpty()) {
      return xRealIP.trim();
    }

    // Fallback: Direct connection IP
    return request.getRemoteAddr();
  }

  /**
   * Extract user agent from HTTP request.
   *
   * @param request the HTTP servlet request
   * @return user agent string or null if not available
   */
  public static String extractUserAgent(HttpServletRequest request) {
    if (request == null) {
      return null;
    }
    return request.getHeader("User-Agent");
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
   *   AuditHelper.createAdminAudit(context, EventType.ADMIN_LOGIN, AdminAuditConstants.LOGIN_SUCCESS)
   *     .eventStatus(EventStatus.SUCCESS)
   *     .eventDetails("Username: john.doe")
   *     .build()
   * );
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
   * Creates a SUCCESS audit log entry for admin API operations.
   *
   * <p>This is a convenience method for the most common audit pattern - logging successful
   * operations with optional details. It pre-populates the event status as SUCCESS.
   *
   * <p><b>Usage Example:</b>
   *
   * <pre>
   * auditLogService.log(
   *   AuditHelper.logSuccess(context, EventType.ADMIN_LOGIN, AdminAuditConstants.LOGIN_SUCCESS, "Username: john.doe")
   * );
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
   * Creates a FAILURE audit log entry for admin API operations.
   *
   * <p>This is a convenience method for logging failed operations (validation errors, business rule
   * violations, etc.) with an error message. It pre-populates the event status as FAILURE.
   *
   * <p><b>Usage Example:</b>
   *
   * <pre>
   * auditLogService.log(
   *   AuditHelper.logFailure(context, EventType.ADMIN_LOGIN, AdminAuditConstants.LOGIN_FAILURE, "Invalid credentials")
   * );
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
   * Creates an ERROR audit log entry for admin API operations.
   *
   * <p>This is a convenience method for logging unexpected errors (exceptions, system errors) with
   * an error message. It pre-populates the event status as ERROR.
   *
   * <p><b>Usage Example:</b>
   *
   * <pre>
   * auditLogService.log(
   *   AuditHelper.logError(context, EventType.ADMIN_LOGIN, "login_error", exception.getMessage())
   * );
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
}
