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

/**
 * Helper utilities for audit logging.
 *
 * <p>Provides common functionality for extracting audit-relevant information from HTTP requests
 * such as IP addresses and user agents.
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
}
