/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Filter: EvaluatorTempAccessFilter
 * Description: Deny-list for EVALUATOR_TEMP sessions (navigable TENANT_ADMIN, not bridled-only).
 */

package org.ezkey.admin.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.NonNull;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Fail-closed deny-list for {@code EVALUATOR_TEMP} authentications.
 *
 * <p>Mode C is navigable {@code TENANT_ADMIN} ACL pass-through plus a deny-list seed — not Path B
 * allowlist growth into “finish enrollment only.” Never GLOBAL; QR/onboarding self-only; no
 * passwordless {@code SESSION} mint while TEMP. Dashboard and lab list/CRUD stay open unless
 * explicitly denied.
 */
@Component
public class EvaluatorTempAccessFilter extends OncePerRequestFilter {

  private static final Pattern ADMIN_ONBOARDING =
      Pattern.compile("^/api/v1/admins/(\\d+)(/onboarding(/qrcode)?)?$");

  /**
   * Deny-list seed (Patrick craft steer): GLOBAL/cross-tenant; mint SESSION without bind; integrity
   * raise/async write; cross-admin QR/onboarding (self-only rule below); dangerous export/bootstrap
   * admin; destructive durable outside lab soft. Dashboard/lists/lab CRUD stay open unless listed.
   */
  private static final String[] DENIED_PREFIXES = {
    "/api/v1/tenants",
    "/api/v1/encryption-keys",
    "/api/v1/alerts",
    "/api/v1/integrity",
    "/api/v1/admins/global",
    "/api/v1/admins/tenant",
    "/api/v1/admin/auth/login",
    "/api/v1/admin/auth/passwordless-wait",
    "/api/v1/admin/auth/recover",
    "/api/v1/admin/enrollments/reset",
  };

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {

    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null
        && authentication.getPrincipal() instanceof AdminPrincipal principal
        && principal.isEvaluatorTemp()) {
      String path = request.getRequestURI();
      if (path != null && isDeniedForEvaluatorTemp(path, principal)) {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response
            .getWriter()
            .write(
                "{\"type\":\"https://ezkey.io/problems/authorization/access-denied\","
                    + "\"title\":\"Forbidden\",\"status\":403,"
                    + "\"detail\":\"This action is not available during a temporary console"
                    + " session.\"}");
        return;
      }
    }

    filterChain.doFilter(request, response);
  }

  private boolean isDeniedForEvaluatorTemp(String path, AdminPrincipal principal) {
    for (String prefix : DENIED_PREFIXES) {
      if (path.equals(prefix) || path.startsWith(prefix + "/")) {
        return true;
      }
    }

    if (path.startsWith("/api/v1/audit-logs/")
        && (path.contains("/seal")
            || path.contains("/purge")
            || path.contains("/export")
            || path.contains("/checkpoint")
            || path.contains("/gap")
            || path.contains("/integrity"))) {
      return true;
    }

    Matcher matcher = ADMIN_ONBOARDING.matcher(path);
    if (matcher.matches()) {
      Integer targetAdminId = Integer.valueOf(matcher.group(1));
      return principal.adminId() == null || !principal.adminId().equals(targetAdminId);
    }

    return false;
  }
}
