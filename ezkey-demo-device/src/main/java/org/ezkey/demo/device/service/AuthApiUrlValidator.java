/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: AuthApiUrlValidator
 * Description: Validates/normalizes Auth API base URLs; host allowlist + ReDoS-safe path trim.
 */

package org.ezkey.demo.device.service;

import java.net.URI;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Validates and normalizes Auth API base URLs from enrollment QR payloads. Rules mirror {@code
 * ezkey_mobile/app/utils/urlValidation.ts} and {@code enrollment-qr-import.js}, plus a host
 * allowlist so enrollment tokens are not sent to arbitrary hosts (lab SSRF hardening). Userinfo is
 * rejected; the normalized form is rebuilt from scheme, host, port, and path only.
 *
 * @since 2025
 */
public final class AuthApiUrlValidator {

  /** Constant user-facing message when the host is outside the allowlist (never echoes the URL). */
  public static final String HOST_NOT_ALLOWED_MESSAGE =
      "Auth API host is not allowed on this demo device.";

  /**
   * Constant user-facing message when the URL fails format / scheme rules (never echoes the URL).
   */
  public static final String INVALID_URL_MESSAGE =
      "Invalid Auth API URL in enrollment QR. Use a valid HTTPS URL or HTTP on localhost.";

  private static final Set<String> DEV_HTTP_HOSTS = Set.of("localhost", "127.0.0.1", "10.0.2.2");

  private AuthApiUrlValidator() {}

  /**
   * Builds the effective host allowlist.
   *
   * <p>When {@code configuredHosts} is non-empty, those hostnames are used as-is (lowercased).
   * Otherwise the default is the host of {@code configuredAuthApiBaseUrl} plus {@code localhost}
   * and {@code 127.0.0.1}.
   *
   * @param configuredAuthApiBaseUrl {@code ezkey.auth.api.url}
   * @param configuredHosts optional explicit {@code ezkey.demo-device.allowed-auth-api-hosts}
   * @return lowercase hostnames allowed as Auth API targets
   */
  public static Set<String> effectiveAllowlist(
      String configuredAuthApiBaseUrl, Collection<String> configuredHosts) {
    Set<String> hosts = new LinkedHashSet<>();
    if (configuredHosts != null) {
      for (String host : configuredHosts) {
        if (host != null && !host.isBlank()) {
          hosts.add(host.trim().toLowerCase(Locale.ROOT));
        }
      }
    }
    if (!hosts.isEmpty()) {
      return Set.copyOf(hosts);
    }
    hosts.add("localhost");
    hosts.add("127.0.0.1");
    String fromConfig = hostOf(configuredAuthApiBaseUrl);
    if (fromConfig != null) {
      hosts.add(fromConfig);
    }
    return Set.copyOf(hosts);
  }

  /**
   * Validates an Auth API base URL against format rules and the host allowlist.
   *
   * <p>HTTPS is required in production. HTTP is allowed only for development loopback hosts. The
   * hostname must appear in {@code allowedHosts}.
   *
   * @param value raw URL from QR or form
   * @param allowedHosts lowercase hostnames permitted as Auth API targets
   * @return normalized URL without trailing slash on path, or {@code null} when invalid
   */
  public static String validate(String value, Set<String> allowedHosts) {
    String normalized = normalize(value);
    if (normalized == null) {
      return null;
    }
    String host = hostOf(normalized);
    if (host == null) {
      return null;
    }
    if (allowedHosts == null || !allowedHosts.contains(host)) {
      return null;
    }
    return normalized;
  }

  /**
   * Validates format/scheme only (no allowlist). Prefer {@link #validate(String, Set)} for bind/QR.
   *
   * @param value raw URL from QR or form
   * @return normalized URL, or {@code null} when invalid
   */
  public static String validate(String value) {
    return normalize(value);
  }

  /**
   * Whether failure of {@link #validate(String, Set)} was an allowlist rejection (format OK, host
   * not listed) versus a format/scheme failure.
   *
   * @param value raw URL
   * @param allowedHosts effective allowlist
   * @return {@code true} when format is valid but host is outside the allowlist
   */
  public static boolean isHostNotAllowed(String value, Set<String> allowedHosts) {
    String normalized = normalize(value);
    if (normalized == null) {
      return false;
    }
    String host = hostOf(normalized);
    return host != null && (allowedHosts == null || !allowedHosts.contains(host));
  }

  /**
   * Extracts a lowercase hostname from a URL string.
   *
   * @param url raw or normalized URL
   * @return lowercase host, or {@code null}
   */
  public static String hostOf(String url) {
    if (url == null || url.isBlank()) {
      return null;
    }
    try {
      URI uri = URI.create(url.trim());
      String host = uri.getHost();
      if (host == null || host.isBlank()) {
        return null;
      }
      return host.toLowerCase(Locale.ROOT);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static String normalize(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String trimmed = value.trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    URI uri;
    try {
      uri = URI.create(trimmed);
    } catch (IllegalArgumentException e) {
      return null;
    }
    String scheme = uri.getScheme();
    if (scheme == null) {
      return null;
    }
    scheme = scheme.toLowerCase(Locale.ROOT);
    if (!"http".equals(scheme) && !"https".equals(scheme)) {
      return null;
    }
    // Reject userinfo so normalization never keeps credentials or @-authority tricks
    // (e.g. https://evil.com@allowed). Fail closed with a constant message path — no URL echo.
    if (uri.getUserInfo() != null) {
      return null;
    }
    String host = uri.getHost();
    if (host == null || host.isBlank()) {
      return null;
    }
    String hostname = host.toLowerCase(Locale.ROOT);
    boolean isHttps = "https".equals(scheme);
    boolean isDevHttp = "http".equals(scheme) && DEV_HTTP_HOSTS.contains(hostname);
    if (!isHttps && !isDevHttp) {
      return null;
    }
    String path = uri.getPath();
    if (path != null && path.length() > 1) {
      path = stripTrailingSlashes(path);
    }
    // Rebuild from scheme + host + port + path only (never getAuthority(), which embeds userinfo).
    StringBuilder normalized = new StringBuilder();
    normalized.append(scheme).append("://").append(hostname);
    int port = uri.getPort();
    if (port != -1) {
      normalized.append(':').append(port);
    }
    if (path != null && !path.isEmpty() && !"/".equals(path)) {
      normalized.append(path);
    }
    return normalized.toString();
  }

  /**
   * Strips trailing {@code /} characters in linear time (avoids {@code replaceAll("/+$")} ReDoS).
   *
   * @param path non-null path with length &gt; 1
   * @return path without trailing slashes; root {@code /} is preserved when that is all that
   *     remains
   */
  static String stripTrailingSlashes(String path) {
    int end = path.length();
    while (end > 1 && path.charAt(end - 1) == '/') {
      end--;
    }
    return end == path.length() ? path : path.substring(0, end);
  }
}
