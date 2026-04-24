/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Utility: ClientIpResolver
 * Description: Resolves client IP from request headers only when the direct proxy is trusted.
 */

package org.ezkey.demo.acme.security;

import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collection;

/**
 * Resolves the client IP address from an HTTP request with optional trusted-proxy CIDR checks.
 *
 * @author Ezkey contributors
 * @since 2025
 */
public final class ClientIpResolver {

  private static final String HEADER_CF_CONNECTING_IP = "CF-Connecting-IP";
  private static final String HEADER_X_FORWARDED_FOR = "X-Forwarded-For";
  private static final String HEADER_X_REAL_IP = "X-Real-IP";

  private ClientIpResolver() {}

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

    String fromHeaders = resolveFromHeaders(request);
    return fromHeaders != null ? fromHeaders : remoteAddr;
  }

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
      IPAddressString cidrString = new IPAddressString(cidr.trim());
      if (!cidrString.isValid()) {
        continue;
      }
      IPAddress cidrAddr = cidrString.getAddress();
      if (cidrAddr != null && cidrAddr.contains(remoteAddrObj)) {
        return true;
      }
    }
    return false;
  }

  private static String resolveFromHeaders(HttpServletRequest request) {
    String cfConnectingIp = request.getHeader(HEADER_CF_CONNECTING_IP);
    if (cfConnectingIp != null && !cfConnectingIp.isBlank() && isValidIp(cfConnectingIp.trim())) {
      return cfConnectingIp.trim();
    }

    String xForwardedFor = request.getHeader(HEADER_X_FORWARDED_FOR);
    if (xForwardedFor != null && !xForwardedFor.isBlank()) {
      String firstIp = xForwardedFor.split(",")[0].trim();
      if (isValidIp(firstIp)) {
        return firstIp;
      }
    }

    String xRealIp = request.getHeader(HEADER_X_REAL_IP);
    if (xRealIp != null && !xRealIp.isBlank() && isValidIp(xRealIp.trim())) {
      return xRealIp.trim();
    }

    return null;
  }

  private static boolean isValidIp(String ip) {
    if (ip == null || ip.isBlank()) {
      return false;
    }
    return new IPAddressString(ip.trim()).isValid();
  }
}
