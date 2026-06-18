package org.ezkey.demo.device.service;

import java.net.URI;
import java.util.Set;

/**
 * Validates and normalizes Auth API base URLs from enrollment QR payloads. Rules mirror
 * {@code ezkey_mobile/app/utils/urlValidation.ts} and {@code enrollment-qr-import.js}.
 *
 * @since 2025
 */
public final class AuthApiUrlValidator {

  private static final Set<String> DEV_HTTP_HOSTS = Set.of("localhost", "127.0.0.1", "10.0.2.2");

  private AuthApiUrlValidator() {}

  /**
   * Validates an Auth API base URL.
   *
   * <p>HTTPS is required in production. HTTP is allowed only for development loopback hosts.
   *
   * @param value raw URL from QR or form
   * @return normalized URL without trailing slash on path, or {@code null} when invalid
   */
  public static String validate(String value) {
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
    scheme = scheme.toLowerCase();
    if (!"http".equals(scheme) && !"https".equals(scheme)) {
      return null;
    }
    String host = uri.getHost();
    if (host == null || host.isBlank()) {
      return null;
    }
    String hostname = host.toLowerCase();
    boolean isHttps = "https".equals(scheme);
    boolean isDevHttp = "http".equals(scheme) && DEV_HTTP_HOSTS.contains(hostname);
    if (!isHttps && !isDevHttp) {
      return null;
    }
    String path = uri.getPath();
    if (path != null && path.length() > 1) {
      path = path.replaceAll("/+$", "");
    }
    if (path == null || path.isEmpty() || "/".equals(path)) {
      return uri.getScheme() + "://" + uri.getAuthority();
    }
    return uri.getScheme() + "://" + uri.getAuthority() + path;
  }
}
