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
import java.util.Collection;

/**
 * Immutable carrier for client context used in audit logging.
 *
 * <p>Encapsulates the client IP address and User-Agent extracted from an HTTP request, providing a
 * single, consistent way to carry this information through the request processing chain for audit
 * trail purposes across all API modules.
 *
 * <p><b>IP Extraction (when trusted proxies are configured):</b> Uses {@link ClientIpResolver} so
 * that proxy headers (CF-Connecting-IP, X-Forwarded-For, X-Real-IP) are only trusted when the
 * direct connection comes from a configured CIDR. Otherwise only the remote address is used.
 *
 * <p><b>Example Usage:</b>
 *
 * <pre>
 * ClientContext context = ClientContext.from(httpRequest, trustedProxyCidrs);
 * auditLogService.log(
 *     AuditHelper.createAdminAudit(context, EventType.AUTH_ATTEMPT_CREATED, "auth_attempt_created")
 *         .eventStatus(EventStatus.SUCCESS)
 *         .build());
 * </pre>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @param clientIp client IP address extracted from the request (handles proxy headers)
 * @param userAgent client User-Agent string from the request headers
 * @author Ezkey contributors
 * @since 2025
 */
public record ClientContext(String clientIp, String userAgent) {

  /**
   * Request attribute name for pre-resolved client IP. When set by a filter (e.g. in admin-api),
   * {@link #from(HttpServletRequest)} uses this value instead of resolving again.
   */
  public static final String CLIENT_IP_REQUEST_ATTRIBUTE = "org.ezkey.audit.resolvedClientIp";

  /**
   * Extracts client context from an HTTP servlet request without trusted-proxy list.
   *
   * <p>Equivalent to {@link #from(HttpServletRequest, Collection)} with null trusted proxies: only
   * the direct remote address is used for IP (no proxy headers trusted). Use this when
   * trusted-proxy configuration is not available or when backward compatibility is required.
   *
   * @param request the HTTP servlet request (may be null; returns empty context)
   * @return a ClientContext populated with the resolved IP and user agent
   */
  public static ClientContext from(HttpServletRequest request) {
    if (request == null) {
      return new ClientContext(null, null);
    }
    Object preResolved = request.getAttribute(CLIENT_IP_REQUEST_ATTRIBUTE);
    if (preResolved instanceof String) {
      return new ClientContext((String) preResolved, request.getHeader("User-Agent"));
    }
    return from(request, null);
  }

  /**
   * Extracts client context from an HTTP servlet request with optional trusted-proxy CIDR list.
   *
   * <p>Uses {@link ClientIpResolver#resolve} so that proxy headers are only trusted when the direct
   * connection comes from one of the given CIDRs. User-Agent is extracted unconditionally from the
   * {@code User-Agent} header.
   *
   * @param request the HTTP servlet request (may be null; returns empty context)
   * @param trustedProxyCidrs optional list of CIDR or single-IP strings; null or empty means only
   *     remoteAddr is used
   * @return a ClientContext populated with the resolved IP and user agent
   */
  public static ClientContext from(
      HttpServletRequest request, Collection<String> trustedProxyCidrs) {
    if (request == null) {
      return new ClientContext(null, null);
    }
    String clientIp = ClientIpResolver.resolve(request, trustedProxyCidrs);
    return new ClientContext(clientIp, request.getHeader("User-Agent"));
  }
}
