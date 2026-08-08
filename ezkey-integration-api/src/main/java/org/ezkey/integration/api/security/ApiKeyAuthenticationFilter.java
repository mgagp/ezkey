/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Filter: ApiKeyAuthenticationFilter
 * Description: Custom authentication filter for validating API keys via HTTP Basic Auth.
 */

package org.ezkey.integration.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.Optional;
import org.ezkey.audit.util.ClientIpResolver;
import org.ezkey.integration.api.config.TrustedProxyProperties;
import org.ezkey.integration.domain.entity.Integration;
import org.ezkey.integration.service.ApiKeyService;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Custom authentication filter for validating API keys via HTTP Basic Auth.
 *
 * <p>Intercepts requests with {@code Authorization: Basic} headers whose username starts with
 * {@code ezkey_ikey_}, validates the credentials via {@link ApiKeyService}, and establishes a
 * {@code ROLE_API_KEY} security context on success.
 *
 * <p><b>Header format:</b>
 *
 * <pre>Authorization: Basic base64(ezkey_ikey_xxx:ezkey_skey_xxx)</pre>
 *
 * <p>This is a direct copy of the admin-api filter scoped to the Integration API module package.
 * Extraction to a shared module ({@code ezkey-security-common}) is deferred to a future phase.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

  private static final Logger logger = LoggerFactory.getLogger(ApiKeyAuthenticationFilter.class);

  private static final String BASIC_AUTH_PREFIX = "Basic ";
  private static final String INTEGRATION_KEY_PREFIX = "ezkey_ikey_";
  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String ROLE_API_KEY = "ROLE_API_KEY";

  private final ApiKeyService apiKeyService;

  private final TrustedProxyProperties trustedProxyProperties;

  /**
   * Constructs the filter with the API key validation service and trusted proxy configuration.
   *
   * @param apiKeyService the service used to validate API key credentials
   * @param trustedProxyProperties the trusted proxy CIDR list for client IP resolution (never null)
   */
  public ApiKeyAuthenticationFilter(
      ApiKeyService apiKeyService, TrustedProxyProperties trustedProxyProperties) {
    this.apiKeyService = apiKeyService;
    this.trustedProxyProperties = trustedProxyProperties;
  }

  /**
   * Inspects the {@code Authorization: Basic} header, validates the API key, and populates the
   * security context. The filter chain continues regardless of authentication outcome.
   *
   * @param request the incoming HTTP request
   * @param response the HTTP response
   * @param filterChain the remaining filter chain
   * @throws ServletException if a servlet processing error occurs
   * @throws IOException if an I/O error occurs
   */
  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {

    String authHeader = request.getHeader(AUTHORIZATION_HEADER);

    if (authHeader != null && authHeader.startsWith(BASIC_AUTH_PREFIX)) {
      try {
        String base64Credentials = authHeader.substring(BASIC_AUTH_PREFIX.length());
        byte[] decodedBytes = Base64.getDecoder().decode(base64Credentials);
        String credentials = new String(decodedBytes, StandardCharsets.UTF_8);
        String[] parts = credentials.split(":", 2);

        if (parts.length == 2) {
          String username = parts[0];
          String password = parts[1];

          if (username.startsWith(INTEGRATION_KEY_PREFIX)) {
            String clientIp = ClientIpResolver.resolve(request, trustedProxyProperties.getCidrs());

            logger.debug(
                "API key authentication attempt - Integration Key: {}..., Client IP: {}",
                username.substring(0, Math.min(15, username.length())),
                clientIp);

            Optional<Integration> integrationOpt =
                apiKeyService.validateApiKey(username, password, clientIp);

            if (integrationOpt.isPresent()) {
              Integration integration = integrationOpt.get();
              setupApiKeyAuthentication(integration, request);

              logger.info(
                  "✅ API key authentication successful - Integration ID: {}, IP: {}",
                  integration.getId(),
                  clientIp);
            } else {
              logger.warn(
                  "❌ API key authentication failed - Integration Key: {}..., IP: {}",
                  username.substring(0, Math.min(15, username.length())),
                  clientIp);
            }
          }
        }
      } catch (IllegalArgumentException e) {
        logger.warn("❌ Malformed HTTP Basic Auth header: {}", e.getMessage());
      } catch (Exception e) { // CHECKSTYLE IGNORE IllegalCatch
        logger.error("❌ Error processing API key authentication: {}", e.getMessage(), e);
      }
    }

    filterChain.doFilter(request, response);
  }

  /**
   * Establishes the Spring Security context for a successfully authenticated API key.
   *
   * <p>The principal is the integration ID (Integer) so that downstream access-control checks can
   * resolve integration ownership without triggering lazy-loading exceptions.
   *
   * @param integration the authenticated integration
   * @param request the current HTTP request
   */
  private void setupApiKeyAuthentication(Integration integration, HttpServletRequest request) {
    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(
            integration.getId(),
            null,
            Collections.singletonList(new SimpleGrantedAuthority(ROLE_API_KEY)));

    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
    SecurityContextHolder.getContext().setAuthentication(authentication);

    logger.debug(
        "Security context established for integration: {} with role: {}",
        integration.getId(),
        ROLE_API_KEY);
  }
}
