/*
 * Ezkey - Open Source Cryptographic MFA Platform
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
import java.util.Enumeration;

/**
 * Resolves the client IP address from an HTTP request, optionally trusting proxy headers only when
 * the direct connection comes from a configured trusted proxy (CIDR list).
 *
 * <p><b>Rules (fail-closed):</b>
 *
 * <ul>
 *   <li>If {@code trustedProxyCidrs} is null or empty: always return {@code
 *       request.getRemoteAddr()} (do not trust any header; prevents spoofing).
 *   <li>If {@code remoteAddr} is not in any trusted CIDR: return remoteAddr and ignore all headers.
 *   <li>If {@code remoteAddr} is trusted: collect every {@code X-Forwarded-For} header value (via
 *       {@link HttpServletRequest#getHeaders}), join them in declaration order, then walk the
 *       combined list from <b>right to left</b>, skip each hop that is itself a trusted proxy, and
 *       keep the first untrusted entry. Never take the leftmost entry by default.
 *   <li>If any XFF entry is invalid, or the entire chain is trusted: fall back to remoteAddr.
 *   <li>{@code CF-Connecting-IP} is used only when {@code trustCloudflareConnectingIp} is true
 *       (dedicated setting, OFF by default) and the immediate hop is trusted — meaning the operator
 *       has declared that hop as a Cloudflare edge. {@code X-Real-IP} is ignored and never
 *       preferred.
 * </ul>
 *
 * <p>Hop validation accepts <b>IPv4 or IPv6 literals only</b> (no CIDR prefixes, no {@code *}, no
 * hostnames). Forms such as {@code ip:port} or {@code [ipv6]:port} are <b>rejected</b>
 * (fail-closed) rather than normalized — a port suffix is not a client identity for rate-limit /
 * audit keys.
 *
 * <p>Uses {@code com.github.seancfoley.ipaddress} for CIDR containment checks (no DNS lookup).
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
public final class ClientIpResolver {

  private static final String HEADER_CF_CONNECTING_IP = "CF-Connecting-IP";
  private static final String HEADER_X_FORWARDED_FOR = "X-Forwarded-For";

  private ClientIpResolver() {
    // Utility class - no instantiation
  }

  /**
   * Resolves the client IP address from the request without trusting {@code CF-Connecting-IP}.
   *
   * <p>Equivalent to {@link #resolve(HttpServletRequest, Collection, boolean)} with {@code
   * trustCloudflareConnectingIp=false}.
   *
   * @param request the HTTP servlet request (must not be null)
   * @param trustedProxyCidrs optional list of CIDR or single-IP strings (e.g. 10.0.0.0/8,
   *     172.16.0.0/12); null or empty means do not trust any proxy header
   * @return the resolved client IP address (never null; may be empty string if remoteAddr is
   *     unavailable)
   */
  public static String resolve(HttpServletRequest request, Collection<String> trustedProxyCidrs) {
    return resolve(request, trustedProxyCidrs, false);
  }

  /**
   * Resolves the client IP address from the request.
   *
   * <p>When {@code trustedProxyCidrs} is null or empty, only the direct connection address is used.
   * When non-empty, proxy headers are trusted only if {@code request.getRemoteAddr()} is contained
   * in one of the given CIDRs. {@code CF-Connecting-IP} is consulted only when {@code
   * trustCloudflareConnectingIp} is true.
   *
   * @param request the HTTP servlet request (must not be null)
   * @param trustedProxyCidrs optional list of CIDR or single-IP strings; null or empty means do not
   *     trust any proxy header
   * @param trustCloudflareConnectingIp when true and remoteAddr is trusted, prefer a valid {@code
   *     CF-Connecting-IP} before walking X-Forwarded-For; OFF by default at configuration layer
   * @return the resolved client IP address (never null; may be empty string if remoteAddr is
   *     unavailable)
   */
  public static String resolve(
      HttpServletRequest request,
      Collection<String> trustedProxyCidrs,
      boolean trustCloudflareConnectingIp) {
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

    if (!isAddressInTrustedProxies(remoteAddr, trustedProxyCidrs)) {
      return remoteAddr;
    }

    if (trustCloudflareConnectingIp) {
      String cf = request.getHeader(HEADER_CF_CONNECTING_IP);
      if (cf != null && !cf.isBlank() && isValidIp(cf.trim())) {
        return cf.trim();
      }
    }

    String fromXff =
        resolveFromXForwardedFor(collectXForwardedFor(request), remoteAddr, trustedProxyCidrs);
    return fromXff != null ? fromXff : remoteAddr;
  }

  /**
   * Joins every {@code X-Forwarded-For} header value in declaration order (comma-separated).
   * Returns null when no non-blank values are present.
   */
  private static String collectXForwardedFor(HttpServletRequest request) {
    Enumeration<String> headers = request.getHeaders(HEADER_X_FORWARDED_FOR);
    if (headers == null || !headers.hasMoreElements()) {
      return null;
    }
    StringBuilder joined = new StringBuilder();
    while (headers.hasMoreElements()) {
      String value = headers.nextElement();
      if (value == null || value.isBlank()) {
        continue;
      }
      if (joined.length() > 0) {
        joined.append(',');
      }
      joined.append(value.trim());
    }
    return joined.length() == 0 ? null : joined.toString();
  }

  /**
   * Returns true if the given address is contained in any of the trusted proxy CIDRs.
   *
   * @param address IP address to check
   * @param cidrs trusted proxy CIDR or single-IP strings
   * @return true when address is a single IP literal and contained in at least one CIDR
   */
  static boolean isAddressInTrustedProxies(String address, Collection<String> cidrs) {
    if (!isValidIp(address)) {
      return false;
    }
    IPAddress remoteAddrObj = new IPAddressString(address.trim()).getAddress();
    if (remoteAddrObj == null) {
      return false;
    }

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
   * Walks X-Forwarded-For right-to-left, skipping trusted hops. Returns null when the header is
   * absent/blank (caller falls back to remoteAddr). Returns {@code remoteAddr} when any entry is
   * invalid or the entire chain is trusted.
   */
  private static String resolveFromXForwardedFor(
      String xff, String remoteAddr, Collection<String> trustedProxyCidrs) {
    if (xff == null || xff.isBlank()) {
      return null;
    }

    String[] parts = xff.split(",");
    for (String part : parts) {
      if (!isValidIp(part.trim())) {
        return remoteAddr;
      }
    }

    for (int i = parts.length - 1; i >= 0; i--) {
      String hop = parts[i].trim();
      if (!isAddressInTrustedProxies(hop, trustedProxyCidrs)) {
        return hop;
      }
    }
    return remoteAddr;
  }

  /**
   * Validates that the string is a single IPv4 or IPv6 <b>literal</b> (no DNS lookup).
   *
   * <p>Rejects CIDR prefixes ({@code 10.0.0.0/8}, {@code 203.0.113.1/32}), wildcard {@code *},
   * hostnames, and {@code ip:port} / {@code [ipv6]:port} forms. Port-bearing forms are rejected
   * rather than stripped so rate-limit and audit keys never silently absorb a non-identity suffix.
   *
   * @param ip candidate address string (leading/trailing whitespace allowed)
   * @return true only for a single IPv4 or IPv6 literal
   */
  static boolean isValidIp(String ip) {
    if (ip == null || ip.isBlank()) {
      return false;
    }
    IPAddressString parsed = new IPAddressString(ip.trim());
    if (!parsed.isValid() || !parsed.isIPAddress() || parsed.isPrefixed()) {
      return false;
    }
    IPAddress addr = parsed.getAddress();
    return addr != null && !addr.isMultiple();
  }
}
