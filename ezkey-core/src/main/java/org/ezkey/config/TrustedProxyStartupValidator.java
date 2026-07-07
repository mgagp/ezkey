/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Utility: TrustedProxyStartupValidator
 * Description: Fail-fast validation for trusted proxy CIDR configuration (SEC-011).
 */

package org.ezkey.config;

import inet.ipaddr.IPAddressString;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Validates {@code ezkey.trusted-proxies} configuration at application startup.
 *
 * <p>When {@code ezkey.trusted-proxies.required=true}, at least one non-blank, parseable CIDR or
 * single-IP entry must be configured. This prevents audit logs and rate limits from silently using
 * only the reverse-proxy address when proxy headers are ignored.
 *
 * @since 2026
 */
public final class TrustedProxyStartupValidator {

  private TrustedProxyStartupValidator() {}

  /**
   * Enforces trusted proxy configuration when marked required.
   *
   * @param required whether startup must fail when configuration is missing or invalid
   * @param cidrs configured trusted proxy CIDR or single-IP strings
   * @throws IllegalStateException when required is true and cidrs are missing or invalid
   */
  public static void enforceRequired(boolean required, Collection<String> cidrs) {
    if (!required) {
      return;
    }
    List<String> normalized = normalize(cidrs);
    if (normalized.isEmpty()) {
      throw new IllegalStateException(
          "Trusted proxy CIDRs are required (ezkey.trusted-proxies.required=true) but"
              + " ezkey.trusted-proxies.cidrs is empty. Configure proxy network ranges so client"
              + " IP resolution for audit logs and rate limiting is accurate behind a reverse"
              + " proxy.");
    }
    List<String> invalid = findInvalidEntries(normalized);
    if (!invalid.isEmpty()) {
      throw new IllegalStateException(
          "Trusted proxy CIDRs are required (ezkey.trusted-proxies.required=true) but these"
              + " entries are invalid: "
              + String.join(", ", invalid));
    }
  }

  private static List<String> normalize(Collection<String> cidrs) {
    if (cidrs == null || cidrs.isEmpty()) {
      return List.of();
    }
    List<String> normalized = new ArrayList<>();
    for (String cidr : cidrs) {
      if (cidr != null && !cidr.isBlank()) {
        normalized.add(cidr.trim());
      }
    }
    return normalized;
  }

  private static List<String> findInvalidEntries(List<String> cidrs) {
    return cidrs.stream().filter(cidr -> !isValidCidrOrIp(cidr)).collect(Collectors.toList());
  }

  private static boolean isValidCidrOrIp(String value) {
    return new IPAddressString(value).isValid();
  }
}
