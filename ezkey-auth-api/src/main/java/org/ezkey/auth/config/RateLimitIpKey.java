/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Class: RateLimitIpKey
 * Description: Normalizes client IPs into rate-limit bucket keys (#918).
 */

package org.ezkey.auth.config;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Bucket key for an IP rate-limit ceiling.
 *
 * <p>IPv4 addresses stay whole: carrier NAT already shares one address, and aggregating further
 * would merge unrelated subscribers. IPv6 privacy extensions (RFC 4941) rotate only the interface
 * identifier, so addresses in one subscriber prefix share a key. The prefix is a /64, the usual
 * on-link subnet (RFC 4291). A /56 would merge up to 256 distinct /64s and can join unrelated
 * customers. IPv4-mapped IPv6 is stored as the embedded IPv4 address so the two spellings cannot
 * split a budget.
 *
 * @author Ezkey contributors
 * @since 2026
 */
public final class RateLimitIpKey {

  private RateLimitIpKey() {}

  /**
   * Returns the rate-limit key for a resolved client IP.
   *
   * @param clientIp resolved client address; null or blank becomes {@code unknown}
   * @return stable bucket key (never null)
   */
  public static String normalize(String clientIp) {
    if (clientIp == null) {
      return "unknown";
    }
    String trimmed = stripDecorations(clientIp.trim());
    if (trimmed.isEmpty()) {
      return "unknown";
    }
    try {
      InetAddress address = InetAddress.ofLiteral(trimmed);
      if (address instanceof Inet4Address) {
        return address.getHostAddress();
      }
      byte[] bytes = address.getAddress();
      if (isIpv4Mapped(bytes)) {
        return ipv4FromMapped(bytes);
      }
      for (int i = 8; i < 16; i++) {
        bytes[i] = 0;
      }
      return InetAddress.getByAddress(bytes).getHostAddress();
    } catch (IllegalArgumentException | UnknownHostException ex) {
      return "invalid";
    }
  }

  private static String stripDecorations(String value) {
    String stripped = value;
    if (stripped.startsWith("[") && stripped.endsWith("]") && stripped.length() > 2) {
      stripped = stripped.substring(1, stripped.length() - 1);
    }
    int zone = stripped.indexOf('%');
    if (zone >= 0) {
      stripped = stripped.substring(0, zone);
    }
    return stripped;
  }

  private static boolean isIpv4Mapped(byte[] bytes) {
    if (bytes.length != 16) {
      return false;
    }
    for (int i = 0; i < 10; i++) {
      if (bytes[i] != 0) {
        return false;
      }
    }
    return bytes[10] == (byte) 0xff && bytes[11] == (byte) 0xff;
  }

  private static String ipv4FromMapped(byte[] bytes) {
    return (bytes[12] & 0xff)
        + "."
        + (bytes[13] & 0xff)
        + "."
        + (bytes[14] & 0xff)
        + "."
        + (bytes[15] & 0xff);
  }
}
