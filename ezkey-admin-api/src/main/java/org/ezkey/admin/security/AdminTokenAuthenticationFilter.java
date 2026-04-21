/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Filter: AdminTokenAuthenticationFilter
 * Description: Custom authentication filter for validating admin bearer tokens.
 */

package org.ezkey.admin.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.ezkey.admin.config.AdminBrowserSessionCookieProperties;
import org.ezkey.admin.service.AdminTokenValidationService;
import org.ezkey.integration.domain.entity.AdminToken;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminType;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Custom authentication filter for validating admin bearer tokens.
 *
 * <p>This filter intercepts requests with "Authorization: Bearer" headers, validates the token
 * against the database, and sets up the security context if the token is valid and not expired.
 *
 * <p>When {@link AdminBrowserSessionCookieProperties#isBrowserSessionCookieEnabled()} is true and
 * there is no Bearer header, the same opaque token may be read from the configured HttpOnly cookie.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Component
public class AdminTokenAuthenticationFilter extends OncePerRequestFilter {

  private static final Logger logger =
      LoggerFactory.getLogger(AdminTokenAuthenticationFilter.class);
  private static final String BEARER_PREFIX = "Bearer ";

  private final AdminTokenValidationService tokenValidationService;
  private final AdminBrowserSessionCookieProperties browserSessionCookieProperties;

  public AdminTokenAuthenticationFilter(
      AdminTokenValidationService tokenValidationService,
      AdminBrowserSessionCookieProperties browserSessionCookieProperties) {
    this.tokenValidationService = tokenValidationService;
    this.browserSessionCookieProperties = browserSessionCookieProperties;
  }

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {

    String authHeader = request.getHeader("Authorization");
    Optional<String> tokenFromHeader =
        authHeader != null && authHeader.startsWith(BEARER_PREFIX)
            ? Optional.of(authHeader.substring(BEARER_PREFIX.length()))
            : Optional.empty();

    Optional<String> tokenToValidate =
        tokenFromHeader.or(() -> readTokenFromSessionCookie(request));

    if (tokenToValidate.isPresent()) {
      tryAuthenticateWithPlainToken(tokenToValidate.get());
    }

    filterChain.doFilter(request, response);
  }

  private Optional<String> readTokenFromSessionCookie(HttpServletRequest request) {
    if (!browserSessionCookieProperties.isBrowserSessionCookieEnabled()) {
      return Optional.empty();
    }
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return Optional.empty();
    }
    String name = browserSessionCookieProperties.getBrowserSessionCookieName();
    for (Cookie c : cookies) {
      if (name.equals(c.getName())) {
        String v = c.getValue();
        if (v != null && !v.isBlank()) {
          return Optional.of(v);
        }
        return Optional.empty();
      }
    }
    return Optional.empty();
  }

  private void tryAuthenticateWithPlainToken(String token) {
    try {
      Optional<AdminToken> tokenOptional = tokenValidationService.validateTokenWithRelations(token);

      if (tokenOptional.isPresent()) {
        AdminToken adminToken = tokenOptional.get();
        var admin = adminToken.getAdmin();

        Integer tenantId = null;
        if (admin.getAdminType() == AdminType.TENANT_ADMIN) {
          tenantId = adminToken.getTenant() != null ? adminToken.getTenant().getTenantId() : null;
        }
        Integer integrationId =
            adminToken.getIntegration() != null ? adminToken.getIntegration().getId() : null;

        AdminPrincipal principal =
            new AdminPrincipal(admin.getAdminId(), admin.getAdminType(), tenantId, integrationId);

        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));

        AdminType adminType = admin.getAdminType();
        if (adminType == AdminType.GLOBAL_ADMIN) {
          authorities.add(new SimpleGrantedAuthority("ROLE_GLOBAL_ADMIN"));
        } else if (adminType == AdminType.TENANT_ADMIN) {
          authorities.add(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN"));
        }

        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(principal, null, authorities);

        SecurityContextHolder.getContext().setAuthentication(authentication);

        tokenValidationService.updateTokenLastUsed(token);

        logger.debug(
            "✅ Token validated successfully for admin: {} (type: {}, tenant: {}, integration:"
                + " {})",
            admin.getUsername(),
            adminType,
            tenantId,
            integrationId);
      }
    } catch (Exception e) {
      logger.error("❌ Error validating token: {}", e.getMessage(), e);
    }
  }
}
