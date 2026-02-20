/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Record: ClientContext
 * Description: Immutable carrier for client context (IP and User-Agent) used in audit logging.
 */

package org.ezkey.audit.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Immutable carrier for client context used in audit logging.
 *
 * <p>
 * Encapsulates the client IP address and User-Agent extracted from an HTTP
 * request, providing a
 * single, consistent way to carry this information through the request
 * processing chain for audit
 * trail purposes across all API modules.
 *
 * <p>
 * <b>IP Extraction Priority:</b>
 *
 * <ol>
 * <li>CF-Connecting-IP (Cloudflare)
 * <li>X-Forwarded-For (standard proxy header — first entry)
 * <li>X-Real-IP (nginx proxy header)
 * <li>Remote address (direct connection)
 * </ol>
 *
 * <p>
 * <b>Example Usage:</b>
 *
 * <pre>
 * ClientContext context = ClientContext.from(httpRequest);
 * auditLogService.log(
 *     AuditHelper.createAdminAudit(context, EventType.AUTH_ATTEMPT_CREATED, "auth_attempt_created")
 *         .eventStatus(EventStatus.SUCCESS)
 *         .build());
 * </pre>
 *
 * <p>
 * <b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p>
 * <b>License:</b> MIT
 *
 * @param clientIp  client IP address extracted from the request (handles proxy
 *                  headers)
 * @param userAgent client User-Agent string from the request headers
 * @author Ezkey contributors
 * @since 2025
 */
public record ClientContext(String clientIp, String userAgent) {

  /**
   * Extracts client context from an HTTP servlet request.
   *
   * <p>
   * Inspects proxy headers in priority order before falling back to the direct
   * remote address.
   * User-Agent is extracted unconditionally from the {@code User-Agent} header.
   *
   * @param request the HTTP servlet request (must not be null)
   * @return a ClientContext populated with the resolved IP and user agent
   */
  public static ClientContext from(HttpServletRequest request) {
    // Null guard required: unit tests that invoke controllers directly (without
    // MockMvc)
    // pass null as HttpServletRequest. Returning an empty context keeps audit
    // logging
    // gracefully degraded rather than throwing NPE before the actual assertion
    // under test.
    if (request == null) {
      return new ClientContext(null, null);
    }
    return new ClientContext(extractIp(request), request.getHeader("User-Agent"));
  }

  private static String extractIp(HttpServletRequest request) {
    // Priority 1: CF-Connecting-IP (Cloudflare)
    String cf = request.getHeader("CF-Connecting-IP");
    if (cf != null && !cf.isEmpty()) {
      return cf.trim();
    }

    // Priority 2: X-Forwarded-For (standard proxy header — take first entry)
    String xff = request.getHeader("X-Forwarded-For");
    if (xff != null && !xff.isEmpty()) {
      return xff.split(",")[0].trim();
    }

    // Priority 3: X-Real-IP (nginx)
    String xri = request.getHeader("X-Real-IP");
    if (xri != null && !xri.isEmpty()) {
      return xri.trim();
    }

    // Fallback: direct connection
    return request.getRemoteAddr();
  }
}
