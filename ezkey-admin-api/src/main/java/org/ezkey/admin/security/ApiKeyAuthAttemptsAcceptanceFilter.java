/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Filter: ApiKeyAuthAttemptsAcceptanceFilter
 * Description: Rejects ROLE_API_KEY traffic on Admin API when the acceptance flag is false.
 */

package org.ezkey.admin.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.ezkey.admin.config.AdminApiKeyAuthAttemptsProperties;
import org.ezkey.exception.AdminApiProblemCatalog;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Second gate on Admin API for API-key M2M traffic.
 *
 * <p>Runs after {@link ApiKeyAuthenticationFilter}. When {@code
 * ezkey.admin.auth.api-key-auth-attempts-enabled} is {@code false} (default) and the current
 * authentication carries {@code ROLE_API_KEY}, the request is rejected with RFC 9457 {@code 403}
 * before role-based authorization. Bearer / session admin flows are unaffected.
 *
 * @author Ezkey contributors
 * @since 2026
 * @see AdminApiKeyAuthAttemptsProperties
 */
@Component
public class ApiKeyAuthAttemptsAcceptanceFilter extends OncePerRequestFilter {

  private static final Logger logger =
      LoggerFactory.getLogger(ApiKeyAuthAttemptsAcceptanceFilter.class);

  private static final String ROLE_API_KEY = "ROLE_API_KEY";

  private final AdminApiKeyAuthAttemptsProperties properties;
  private final ObjectMapper objectMapper;

  /**
   * Creates the acceptance gate.
   *
   * @param properties Admin API API-key acceptance settings
   */
  public ApiKeyAuthAttemptsAcceptanceFilter(AdminApiKeyAuthAttemptsProperties properties) {
    this.properties = properties;
    this.objectMapper = new ObjectMapper();
  }

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {
    if (properties.isApiKeyAuthAttemptsEnabled()) {
      filterChain.doFilter(request, response);
      return;
    }

    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !hasApiKeyRole(authentication)) {
      filterChain.doFilter(request, response);
      return;
    }

    logger.info(
        "Rejecting API-key request on Admin API (api-key-auth-attempts-enabled=false): {} {}",
        request.getMethod(),
        request.getRequestURI());
    writeDisabledProblem(request, response);
  }

  private static boolean hasApiKeyRole(Authentication authentication) {
    for (GrantedAuthority authority : authentication.getAuthorities()) {
      if (ROLE_API_KEY.equals(authority.getAuthority())) {
        return true;
      }
    }
    return false;
  }

  private void writeDisabledProblem(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.FORBIDDEN, AdminApiProblemCatalog.DETAIL_API_KEY_AUTH_ATTEMPTS_DISABLED);
    problem.setType(URI.create(AdminApiProblemCatalog.TYPE_API_KEY_AUTH_ATTEMPTS_DISABLED));
    problem.setTitle(AdminApiProblemCatalog.TITLE_API_KEY_AUTH_ATTEMPTS_DISABLED);
    problem.setInstance(URI.create(request.getRequestURI()));

    response.setStatus(HttpStatus.FORBIDDEN.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    objectMapper.writeValue(response.getOutputStream(), problem);
  }
}
