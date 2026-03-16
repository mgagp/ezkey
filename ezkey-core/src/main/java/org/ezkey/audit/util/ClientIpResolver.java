/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Utility: ClientIpResolver
 * Description: Resolves client IP from HTTP request with optional trusted-proxy CIDR list.
 */

package org.ezkey.audit.util;

import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collection;

/**
 * Resolves the client IP address from an HTTP request, optionally trusting proxy headers only when
 * the direct connection comes from a configured trusted proxy (CIDR list).
 *
 * <p><b>Rules:</b>
 *
 * <ul>
 *   <li>If {@code trustedProxyCidrs} is null or empty: always return {@code
 *       request.getRemoteAddr()} (do not trust any header; prevents spoofing).
 *   <li>If non-empty and {@code remoteAddr} is contained in any CIDR: resolve client IP from
 *       headers in order: CF-Connecting-IP, then X-Forwarded-For (first IP), then X-Real-IP, then
 *       fallback to remoteAddr.
 *   <li>If non-empty but {@code remoteAddr} is not in any CIDR: return remoteAddr only (headers
 *       ignored).
 * </ul>
 *
 * <p>Uses {@code com.github.seancfoley.ipaddress} for CIDR containment checks (no DNS lookup).
 * Header values are validated as valid IP format before use.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
public final class ClientIpResolver {

  private static final String HEADER_CF_CONNECTING_IP = "CF-Connecting-IP";
  private static final String HEADER_X_FORWARDED_FOR = "X-Forwarded-For";
  private static final String HEADER_X_REAL_IP = "X-Real-IP";

  private ClientIpResolver() {
    // Utility class - no instantiation
  }

  /**
   * Resolves the client IP address from the request.
   *
   * <p>When {@code trustedProxyCidrs} is null or empty, only the direct connection address is used.
   * When non-empty, proxy headers are trusted only if {@code request.getRemoteAddr()} is contained
   * in one of the given CIDRs.
   *
   * @param request the HTTP servlet request (must not be null)
   * @param trustedProxyCidrs optional list of CIDR or single-IP strings (e.g. 10.0.0.0/8,
   *     172.16.0.0/12); null or empty means do not trust any proxy header
   * @return the resolved client IP address (never null; may be empty string if remoteAddr is
   *     unavailable)
   */
  public static String resolve(HttpServletRequest request, Collection<String> trustedProxyCidrs) {
    if (request == null) {
      return "";
    }
    String remoteAddr = request.getRemoteAddr();
    if (remoteAddr == null) {
      remoteAddr = "";
    }

    if (trustedProxyCidrs == null || trustedProxyCidrs.isEmpty()) {
      return remoteAddr;
    }

    if (!isRemoteAddrFromTrustedProxy(remoteAddr, trustedProxyCidrs)) {
      return remoteAddr;
    }

    // Trust proxy: resolve from headers in priority order
    String fromHeader = resolveFromHeaders(request);
    return fromHeader != null ? fromHeader : remoteAddr;
  }

  /** Returns true if the given remote address is contained in any of the trusted proxy CIDRs. */
  private static boolean isRemoteAddrFromTrustedProxy(String remoteAddr, Collection<String> cidrs) {
    if (remoteAddr == null || remoteAddr.isBlank()) {
      return false;
    }
    IPAddressString remote = new IPAddressString(remoteAddr.trim());
    if (!remote.isValid()) {
      return false;
    }
    IPAddress remoteAddrObj = remote.getAddress();

    for (String cidr : cidrs) {
      if (cidr == null || cidr.isBlank()) {
        continue;
      }
      IPAddressString cidrStr = new IPAddressString(cidr.trim());
      if (!cidrStr.isValid()) {
        continue;
      }
      IPAddress cidrAddr = cidrStr.getAddress();
      if (cidrAddr != null && cidrAddr.contains(remoteAddrObj)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Extracts client IP from proxy headers (CF-Connecting-IP, X-Forwarded-For, X-Real-IP). Returns
   * null if no valid IP is found in headers.
   */
  private static String resolveFromHeaders(HttpServletRequest request) {
    String cf = request.getHeader(HEADER_CF_CONNECTING_IP);
    if (cf != null && !cf.isEmpty() && isValidIp(cf.trim())) {
      return cf.trim();
    }

    String xff = request.getHeader(HEADER_X_FORWARDED_FOR);
    if (xff != null && !xff.isEmpty()) {
      String first = xff.split(",")[0].trim();
      if (isValidIp(first)) {
        return first;
      }
    }

    String xri = request.getHeader(HEADER_X_REAL_IP);
    if (xri != null && !xri.isEmpty() && isValidIp(xri.trim())) {
      return xri.trim();
    }

    return null;
  }

  /**
   * Validates that the string is a valid IP address (IPv4 or IPv6) without performing DNS lookup.
   */
  private static boolean isValidIp(String ip) {
    if (ip == null || ip.isBlank()) {
      return false;
    }
    return new IPAddressString(ip.trim()).isValid();
  }
}
