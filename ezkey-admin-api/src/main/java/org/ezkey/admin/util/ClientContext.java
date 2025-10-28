/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Record: ClientContext
 * Description: Client context for audit logging in admin API.
 */

package org.ezkey.admin.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Client context for audit logging in admin API.
 *
 * <p>This record encapsulates client IP address and User-Agent extraction from HTTP requests to
 * provide consistent audit trail logging across all admin endpoints. It eliminates code duplication
 * and ensures uniform extraction logic throughout the admin API.
 *
 * <p><b>Usage Context:</b> Used by admin API controllers to capture client information for audit
 * logging. Provides a single, consistent way to extract and carry client context through the
 * request processing chain.
 *
 * <p><b>Extraction Logic:</b> Delegates to {@link AuditHelper} for actual IP and User-Agent
 * extraction, which handles X-Forwarded-For headers and other proxy scenarios correctly.
 *
 * <p><b>Example Usage:</b>
 *
 * <pre>
 * ClientContext context = ClientContext.from(httpRequest);
 *
 * auditLogService.log(
 *   AuditHelper.createAdminAudit(context, EventType.LOGIN, AdminAuditConstants.LOGIN_SUCCESS)
 *     .eventStatus(EventStatus.SUCCESS)
 *     .eventDetails("Username: " + username)
 *     .build()
 * );
 * </pre>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @param clientIp the client IP address extracted from the request (handles X-Forwarded-For)
 * @param userAgent the client user agent string from request headers
 * @author Ezkey contributors
 * @since 2025
 * @see AuditHelper#extractClientIp(HttpServletRequest)
 * @see AuditHelper#extractUserAgent(HttpServletRequest)
 */
public record ClientContext(String clientIp, String userAgent) {

  /**
   * Extracts client context from HTTP request.
   *
   * <p>This factory method creates a ClientContext by extracting the client IP address and
   * User-Agent from the provided HTTP request. It delegates to {@link AuditHelper} for proper
   * handling of proxy headers and edge cases.
   *
   * <p><b>Thread Safety:</b> This method is thread-safe as it only reads from the immutable HTTP
   * request object.
   *
   * @param request the HTTP servlet request to extract context from (must not be null)
   * @return client context with IP and user agent populated
   * @throws NullPointerException if request is null
   * @see AuditHelper#extractClientIp(HttpServletRequest)
   * @see AuditHelper#extractUserAgent(HttpServletRequest)
   */
  public static ClientContext from(HttpServletRequest request) {
    return new ClientContext(
        AuditHelper.extractClientIp(request), AuditHelper.extractUserAgent(request));
  }
}
