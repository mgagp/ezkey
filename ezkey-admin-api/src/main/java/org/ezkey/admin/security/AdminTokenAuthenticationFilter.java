/*
 * Ezkey - Open Source MFA/Passkey Alternative
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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.ezkey.admin.service.AdminTokenValidationService;
import org.ezkey.integration.domain.entity.AdminToken;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
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
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
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

  public AdminTokenAuthenticationFilter(AdminTokenValidationService tokenValidationService) {
    this.tokenValidationService = tokenValidationService;
  }

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {

    String authHeader = request.getHeader("Authorization");

    if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
      String token = authHeader.substring(BEARER_PREFIX.length());

      try {
        // Use the service for transaction-aware validation with relations loaded
        Optional<AdminToken> tokenOptional =
            tokenValidationService.validateTokenWithRelations(token);

        if (tokenOptional.isPresent()) {
          AdminToken adminToken = tokenOptional.get();
          var admin = adminToken.getAdmin();

          // Extract scope information from token
          // GlobalAdmin should have null tenantId for full cross-tenant visibility
          Integer tenantId = null;
          if (admin.getAdminType() == AdminType.TENANT_ADMIN) {
            tenantId = adminToken.getTenant() != null ? adminToken.getTenant().getTenantId() : null;
          }
          Integer integrationId =
              adminToken.getIntegration() != null ? adminToken.getIntegration().getId() : null;

          // Create AdminPrincipal with scope information
          AdminPrincipal principal =
              new AdminPrincipal(admin.getAdminId(), admin.getAdminType(), tenantId, integrationId);

          // Build authorities: always ROLE_ADMIN, plus specific role based on admin type
          List<SimpleGrantedAuthority> authorities = new ArrayList<>();
          authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));

          AdminType adminType = admin.getAdminType();
          if (adminType == AdminType.GLOBAL_ADMIN) {
            authorities.add(new SimpleGrantedAuthority("ROLE_GLOBAL_ADMIN"));
          } else if (adminType == AdminType.TENANT_ADMIN) {
            authorities.add(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN"));
          }
          // Note: INTEGRATION_ADMIN is not activated in Phase 1, but we don't add a role for it

          // Create authentication object with AdminPrincipal
          UsernamePasswordAuthenticationToken authentication =
              new UsernamePasswordAuthenticationToken(principal, null, authorities);

          // Set authentication in security context
          SecurityContextHolder.getContext().setAuthentication(authentication);

          // Update last used timestamp in a separate transaction
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

    filterChain.doFilter(request, response);
  }
}
