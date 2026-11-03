/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 *
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Utility: AuditHelper
 *
 * Description: Helper utilities for audit logging in auth-api.
 */

package org.ezkey.auth.util;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Collection;
import org.ezkey.audit.util.ClientIpResolver;

/**
 * Helper utilities for audit logging.
 *
 * <p>Provides common functionality for extracting audit-relevant information from HTTP requests
 * such as IP addresses and user agents. Uses {@link ClientIpResolver} when a trusted-proxy list is
 * provided so that proxy headers are only trusted when the connection is from a configured proxy.
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
   * Extract client IP address from HTTP request without trusted-proxy list.
   *
   * <p>Equivalent to {@link #extractClientIp(HttpServletRequest, Collection)} with null list: only
   * the direct remote address is used (no proxy headers trusted).
   *
   * @param request the HTTP servlet request
   * @return client IP address or null if request is null
   */
  public static String extractClientIp(HttpServletRequest request) {
    return extractClientIp(request, null, false);
  }

  /**
   * Extract client IP address from HTTP request with optional trusted-proxy CIDR list.
   *
   * <p>Does not trust {@code CF-Connecting-IP}. Prefer {@link #extractClientIp(HttpServletRequest,
   * Collection, boolean)} when the Cloudflare edge setting is available.
   *
   * @param request the HTTP servlet request
   * @param trustedProxyCidrs optional list of CIDR or single-IP strings; null or empty means only
   *     remoteAddr is used
   * @return client IP address or null if request is null
   */
  public static String extractClientIp(
      HttpServletRequest request, Collection<String> trustedProxyCidrs) {
    return extractClientIp(request, trustedProxyCidrs, false);
  }

  /**
   * Extract client IP address from HTTP request with trusted-proxy CIDRs and optional Cloudflare
   * edge trust.
   *
   * @param request the HTTP servlet request
   * @param trustedProxyCidrs optional list of CIDR or single-IP strings; null or empty means only
   *     remoteAddr is used
   * @param trustCloudflareConnectingIp when true and remoteAddr is trusted, prefer CF-Connecting-IP
   * @return client IP address or null if request is null
   */
  public static String extractClientIp(
      HttpServletRequest request,
      Collection<String> trustedProxyCidrs,
      boolean trustCloudflareConnectingIp) {
    if (request == null) {
      return null;
    }
    return ClientIpResolver.resolve(request, trustedProxyCidrs, trustCloudflareConnectingIp);
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
