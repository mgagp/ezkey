/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 *
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.admin.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.ezkey.admin.config.AdminBrowserSessionCookieProperties;
import org.ezkey.admin.security.AdminAuthRequestAttributes.AuthSource;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Validates CSRF headers for unsafe requests authenticated by the browser session cookie.
 *
 * <p>Bearer-authenticated requests are intentionally excluded because browsers do not attach Bearer
 * tokens automatically cross-site.
 */
@Component
public class AdminCookieCsrfFilter extends OncePerRequestFilter {

  private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

  private final AdminBrowserSessionCookieProperties properties;
  private final AdminCsrfTokenService csrfTokenService;

  public AdminCookieCsrfFilter(
      AdminBrowserSessionCookieProperties properties, AdminCsrfTokenService csrfTokenService) {
    this.properties = properties;
    this.csrfTokenService = csrfTokenService;
  }

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {
    if (!requiresCsrfValidation(request)) {
      filterChain.doFilter(request, response);
      return;
    }

    Object plainToken = request.getAttribute(AdminAuthRequestAttributes.PLAIN_TOKEN);
    String submittedToken = request.getHeader(properties.getBrowserCsrfHeaderName());
    if (!(plainToken instanceof String sessionToken)
        || !csrfTokenService.isValid(sessionToken, submittedToken)) {
      response.sendError(HttpStatus.FORBIDDEN.value(), "Invalid or missing CSRF token");
      return;
    }

    filterChain.doFilter(request, response);
  }

  private boolean requiresCsrfValidation(HttpServletRequest request) {
    if (!properties.isBrowserSessionCookieEnabled() || SAFE_METHODS.contains(request.getMethod())) {
      return false;
    }
    if (isPublicAuthBootstrapPath(request.getRequestURI())) {
      return false;
    }
    return request.getAttribute(AdminAuthRequestAttributes.AUTH_SOURCE) == AuthSource.COOKIE;
  }

  private boolean isPublicAuthBootstrapPath(String path) {
    return "/api/v1/admin/auth/activate".equals(path)
        || "/api/v1/admin/auth/login".equals(path)
        || "/api/v1/admin/auth/passwordless-wait".equals(path)
        || "/api/v1/admin/auth/recover".equals(path);
  }
}
