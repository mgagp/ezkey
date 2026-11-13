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
 * allowlist growth into “finish enrollment only.” Never GLOBAL; own profile and onboarding
 * (including QR) self-only; no passwordless {@code SESSION} mint while TEMP. Dashboard and lab
 * list/CRUD stay open unless explicitly denied.
 *
 * <p>Admin-by-id routes are the exception: anything other than the caller's own profile and
 * onboarding is denied, including recovery codes, activation-code reissue, and deactivate/activate.
 * That subtree is deny-by-default so a new admin credential route is closed until someone adds it
 * to the self-only set. The id segment must be the canonical decimal form of the caller's own id.
 * Any other spelling is denied, including hex, a leading sign, leading zeros, and a trailing slash,
 * because Spring parses {@code @PathVariable Integer} with {@code NumberUtils.parseNumber} while
 * this filter sees the raw URI. The rest of the API stays deny-list, because a full allowlist would
 * empty the navigable console.
 */
@Component
public class EvaluatorTempAccessFilter extends OncePerRequestFilter {

  /**
   * Admin-by-id path. Group 1 is the raw id segment. Group 2 is the suffix after the id, or null
   * when the path is the profile with no trailing slash.
   */
  private static final Pattern ADMIN_BY_ID = Pattern.compile("^/api/v1/admins/([^/]+)(?:/(.*))?$");

  /** Canonical decimal id: {@code 0} or a digit string with no sign and no leading zero. */
  private static final Pattern CANONICAL_DECIMAL = Pattern.compile("0|[1-9][0-9]*");

  /**
   * Any enrollment QR-delivery path. The id segment is not restricted to digits: a hex or signed
   * spelling still resolves to an enrollment id in the controller, so a miss would fail open.
   */
  private static final Pattern QR_DELIVERY =
      Pattern.compile("^/api/v1/enrollments/(?:qr-delivery(?:/.*)?|[^/]+/qr-delivery(?:/.*)?)$");

  /**
   * Deny-list seed (Patrick craft steer): GLOBAL/cross-tenant; mint SESSION without bind; integrity
   * raise/async write; dangerous export/bootstrap admin; destructive durable outside lab soft. Peer
   * admin credentials live under {@link #ADMIN_BY_ID} (deny-by-default there). Dashboard, lists,
   * and lab CRUD stay open unless listed.
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

    if (QR_DELIVERY.matcher(path).matches()) {
      return true;
    }

    return isDeniedAdminResource(path, principal);
  }

  /**
   * Deny-by-default for admin-by-id routes.
   *
   * <p>Own profile (GET/PATCH) and own onboarding, including the QR, stay reachable so the
   * evaluator can finish binding their device. Recovery-code regeneration and initial issuance,
   * activation-code reissue, deactivate/activate, and any future suffix are denied for every id,
   * including the caller's own: those responses are long-lived credentials or session revocation,
   * not bind material.
   *
   * @param path request path
   * @param principal temporary evaluator principal
   * @return true when the path must be rejected
   */
  private boolean isDeniedAdminResource(String path, AdminPrincipal principal) {
    Matcher matcher = ADMIN_BY_ID.matcher(path);
    if (!matcher.matches()) {
      return false;
    }
    if (!isCanonicalSelfAdminId(matcher.group(1), principal.adminId())) {
      return true;
    }
    return !isSelfOnlyAdminSuffix(matcher.group(2));
  }

  private static boolean isSelfOnlyAdminSuffix(String suffix) {
    return suffix == null || "onboarding".equals(suffix) || "onboarding/qrcode".equals(suffix);
  }

  /**
   * True only when the raw segment is the canonical decimal spelling of {@code adminId}.
   *
   * <p>{@code 099}, {@code +99}, and {@code 0x63} are rejected even when they parse to the same
   * number. A value that does not fit in an {@link Integer} is rejected.
   *
   * @param segment raw path segment
   * @param adminId caller admin id, or null when the principal has none
   * @return true when the segment is the canonical form of the caller's own id
   */
  private static boolean isCanonicalSelfAdminId(String segment, Integer adminId) {
    if (adminId == null || segment == null || !CANONICAL_DECIMAL.matcher(segment).matches()) {
      return false;
    }
    try {
      return Integer.valueOf(segment).equals(adminId);
    } catch (NumberFormatException _) {
      return false;
    }
  }
}
