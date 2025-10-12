/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Utility: AuditHelper
 * Description: Helper methods for audit logging in Auth API
 */

package org.ezkey.auth.audit;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Helper utility for extracting audit information from HTTP requests.
 * <p>
 * This class provides methods for extracting client IP addresses and other
 * request information needed for comprehensive audit logging.
 * </p>
 *
 * @author Ezkey contributors
 * @since 2025
 */
public class AuditHelper {

    /**
     * Extract client IP address from HTTP request headers.
     * <p>
     * Prioritizes trusted headers in the following order:
     * 1. CF-Connecting-IP (Cloudflare - most trusted)
     * 2. X-Forwarded-For (proxies - can be spoofed)
     * 3. X-Real-IP (Nginx/HAProxy - can be spoofed)
     * 4. getRemoteAddr() (direct connection - fallback)
     * </p>
     *
     * @param request the HTTP request
     * @return the client IP address or null if request is null
     */
    public static String extractClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        // Try Cloudflare header first (most trusted)
        String ip = request.getHeader("CF-Connecting-IP");
        if (ip != null && !ip.isEmpty()) {
            return ip;
        }

        // Try X-Forwarded-For
        ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty()) {
            // X-Forwarded-For can contain multiple IPs, take the first one
            return ip.split(",")[0].trim();
        }

        // Try X-Real-IP
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty()) {
            return ip;
        }

        // Fallback to remote address
        return request.getRemoteAddr();
    }

    /**
     * Extract user agent string from HTTP request.
     *
     * @param request the HTTP request
     * @return the user agent string or null if not present
     */
    public static String extractUserAgent(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        return request.getHeader("User-Agent");
    }
}
