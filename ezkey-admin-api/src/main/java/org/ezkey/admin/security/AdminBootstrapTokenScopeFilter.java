/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Filter: AdminBootstrapTokenScopeFilter
 * Description: Restricts BOOTSTRAP-purpose sessions to a narrow Admin API allowlist.
 */

package org.ezkey.admin.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Pattern;
import org.ezkey.integration.domain.AdminTokenPurpose;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Enforces the narrow path allowlist for {@link AdminTokenPurpose#BOOTSTRAP} sessions.
 *
 * <p>BOOTSTRAP tokens authenticate through {@link AdminTokenAuthenticationFilter} like SESSION
 * tokens, but must not grant the full Admin API surface while the evaluator completes activation
 * and device bind (V-2026-09-26). The SecurityConfig {@code permitAll} auth family remains
 * allowlisted so a leftover Mode B cookie cannot 403 passwordless login.
 *
 * @author Ezkey contributors
 * @since 2026
 */
@Component
public class AdminBootstrapTokenScopeFilter extends OncePerRequestFilter {

  private static final Logger logger =
      LoggerFactory.getLogger(AdminBootstrapTokenScopeFilter.class);

  private static final Pattern ADMIN_ONBOARDING =
      Pattern.compile("^/api/v1/admins/\\d+/onboarding(?:/qrcode)?$");
  private static final Pattern ENROLLMENT_GET = Pattern.compile("^/api/v1/enrollments/\\d+$");

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {

    Object purposeAttr = request.getAttribute(AdminAuthRequestAttributes.TOKEN_PURPOSE);
    if (!(purposeAttr instanceof AdminTokenPurpose purpose)
        || purpose != AdminTokenPurpose.BOOTSTRAP) {
      filterChain.doFilter(request, response);
      return;
    }

    String method = request.getMethod();
    String path = normalizePath(request.getRequestURI(), request.getContextPath());
    if (isAllowlisted(method, path)) {
      filterChain.doFilter(request, response);
      return;
    }

    logger.warn("BOOTSTRAP session denied for {} {}", method, path);
    response.sendError(
        HttpStatus.FORBIDDEN.value(),
        "Bootstrap session is limited to activation and enrollment onboarding");
  }

  /**
   * Returns whether a BOOTSTRAP session may call {@code method} {@code path}.
   *
   * <p>Includes the SecurityConfig {@code permitAll} auth family ({@code login}, {@code
   * passwordless-wait}, {@code recover}, {@code onboarding-resume}, {@code activate}, {@code
   * logout}) so a leftover Mode B HttpOnly BOOTSTRAP cookie does not 403 anonymous credential flows
   * that send {@code credentials: include} (Patrick craft / sticky-cookie RCA).
   *
   * @param method HTTP method
   * @param path normalized request path
   * @return true when the path is on the narrow BOOTSTRAP allowlist
   */
  static boolean isAllowlisted(String method, String path) {
    if (path == null || method == null) {
      return false;
    }
    String m = method.toUpperCase();
    if ("GET".equals(m) && "/api/v1/admin/auth/me".equals(path)) {
      return true;
    }
    if ("POST".equals(m) && isPermitAllAuthBootstrapPath(path)) {
      return true;
    }
    if ("GET".equals(m) && ADMIN_ONBOARDING.matcher(path).matches()) {
      return true;
    }
    if ("GET".equals(m) && ENROLLMENT_GET.matcher(path).matches()) {
      return true;
    }
    return false;
  }

  /**
   * Same permitAll auth-bootstrap family as {@link org.ezkey.admin.config.SecurityConfig} (sticky
   * BOOTSTRAP must not block these).
   *
   * @param path normalized request path
   * @return true for public auth bootstrap POST paths
   */
  private static boolean isPermitAllAuthBootstrapPath(String path) {
    return "/api/v1/admin/auth/activate".equals(path)
        || "/api/v1/admin/auth/login".equals(path)
        || "/api/v1/admin/auth/passwordless-wait".equals(path)
        || "/api/v1/admin/auth/recover".equals(path)
        || "/api/v1/admin/auth/onboarding-resume".equals(path)
        || "/api/v1/admin/auth/logout".equals(path);
  }

  private static String normalizePath(String requestUri, String contextPath) {
    if (requestUri == null) {
      return "";
    }
    String path = requestUri;
    if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
      path = path.substring(contextPath.length());
    }
    if (path.length() > 1 && path.endsWith("/")) {
      path = path.substring(0, path.length() - 1);
    }
    return path;
  }
}
