/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Filter: ApiKeyAuthenticationFilter
 * Description: Custom authentication filter for validating API keys via HTTP Basic Auth.
 */

package org.ezkey.admin.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.Optional;
import org.ezkey.integration.domain.entity.Integration;
import org.ezkey.integration.service.ApiKeyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Custom authentication filter for validating API keys via HTTP Basic Auth.
 *
 * <p>This filter intercepts requests with "Authorization: Basic" headers where the username starts
 * with "ezkey_ikey_", validates the API key credentials, and sets up the security context if valid.
 *
 * <p><b>Authentication Flow:</b>
 *
 * <ol>
 *   <li>Parse HTTP Basic Auth header
 *   <li>Check if username starts with ezkey_ikey_ (API key indicator)
 *   <li>Extract integration key (username) and secret key (password)
 *   <li>Validate credentials via ApiKeyService
 *   <li>Check IP whitelist and expiration
 *   <li>Setup security context with ROLE_API_KEY
 * </ol>
 *
 * <p><b>Header Format:</b>
 *
 * <pre>
 * Authorization: Basic base64(ezkey_ikey_xxx:ezkey_skey_xxx)
 * </pre>
 *
 * <p><b>Security Features:</b>
 *
 * <ul>
 *   <li>BCrypt validation (slow by design to prevent brute force)
 *   <li>IP whitelist enforcement
 *   <li>Expiration checking
 *   <li>Comprehensive audit logging
 *   <li>Graceful failure (no authentication if invalid)
 * </ul>
 *
 * <p><b>Filter Ordering:</b> This filter should be placed before AdminTokenAuthenticationFilter in
 * the security chain to handle API key authentication first.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see ApiKeyService
 * @see org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
 */
@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

  private static final Logger logger = LoggerFactory.getLogger(ApiKeyAuthenticationFilter.class);

  // HTTP Basic Auth constants
  private static final String BASIC_AUTH_PREFIX = "Basic ";
  private static final String INTEGRATION_KEY_PREFIX = "ezkey_ikey_";
  private static final String AUTHORIZATION_HEADER = "Authorization";

  // Security role for API key authentication
  // Use ROLE_API_KEY to restrict API keys to auth attempt operations only
  private static final String ROLE_API_KEY = "ROLE_API_KEY";

  private final ApiKeyService apiKeyService;

  /**
   * Constructs a new ApiKeyAuthenticationFilter.
   *
   * @param apiKeyService the API key service for validation
   */
  public ApiKeyAuthenticationFilter(ApiKeyService apiKeyService) {
    this.apiKeyService = apiKeyService;
  }

  /**
   * Filters incoming requests to extract and validate API key credentials.
   *
   * <p>This method checks for HTTP Basic Auth headers with API key credentials, validates them, and
   * sets up the security context if authentication is successful.
   *
   * <p><b>Processing Flow:</b>
   *
   * <ol>
   *   <li>Check for Authorization header
   *   <li>Parse HTTP Basic Auth credentials
   *   <li>Verify username starts with ezkey_ikey_
   *   <li>Validate integration key and secret key
   *   <li>Extract client IP for whitelist check
   *   <li>Setup security context on success
   * </ol>
   *
   * <p><b>Error Handling:</b> Failures are logged but don't block the request - the filter chain
   * continues, and the request will be handled as unauthenticated.
   *
   * @param request the HTTP request
   * @param response the HTTP response
   * @param filterChain the filter chain
   * @throws ServletException if servlet error occurs
   * @throws IOException if I/O error occurs
   */
  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {

    // Extract Authorization header
    String authHeader = request.getHeader(AUTHORIZATION_HEADER);

    // Check if this is HTTP Basic Auth
    if (authHeader != null && authHeader.startsWith(BASIC_AUTH_PREFIX)) {
      try {
        // Extract Base64 credentials
        String base64Credentials = authHeader.substring(BASIC_AUTH_PREFIX.length());
        byte[] decodedBytes = Base64.getDecoder().decode(base64Credentials);
        String credentials = new String(decodedBytes, StandardCharsets.UTF_8);

        // Split into username:password
        String[] parts = credentials.split(":", 2);

        if (parts.length == 2) {
          String username = parts[0];
          String password = parts[1];

          // Check if this is an API key (username starts with ezkey_ikey_)
          if (username.startsWith(INTEGRATION_KEY_PREFIX)) {
            String integrationKey = username;
            String secretKey = password;

            // Extract client IP
            String clientIp = extractClientIp(request);

            logger.debug(
                "API key authentication attempt - Integration Key: {}..., Client IP: {}",
                integrationKey.substring(0, Math.min(15, integrationKey.length())),
                clientIp);

            // Validate API key
            Optional<Integration> integrationOpt =
                apiKeyService.validateApiKey(integrationKey, secretKey, clientIp);

            if (integrationOpt.isPresent()) {
              Integration integration = integrationOpt.get();

              // Setup authentication context
              setupApiKeyAuthentication(integration, request);

              logger.info(
                  "✅ API key authentication successful - Integration ID: {}, IP: {}",
                  integration.getId(),
                  clientIp);
            } else {
              logger.warn(
                  "❌ API key authentication failed - Integration Key: {}..., IP: {}",
                  integrationKey.substring(0, Math.min(15, integrationKey.length())),
                  clientIp);
            }
          }
        }
      } catch (IllegalArgumentException e) {
        // Invalid Base64 or malformed credentials
        logger.warn("❌ Malformed HTTP Basic Auth header: {}", e.getMessage());
      } catch (Exception e) {
        // Catch-all for unexpected errors
        logger.error("❌ Error processing API key authentication: {}", e.getMessage(), e);
      }
    }

    // Continue filter chain (whether authenticated or not)
    filterChain.doFilter(request, response);
  }

  /**
   * Sets up Spring Security authentication context for API key.
   *
   * <p>Creates an authentication token with ROLE_API_KEY and stores the integration object as
   * the principal for ownership checks in AccessControlService.
   *
   * @param integration the authenticated integration
   * @param request the HTTP request for additional details
   */
  private void setupApiKeyAuthentication(Integration integration, HttpServletRequest request) {
    // Create authentication token
    // Principal: Integration ID (for ownership checks in AccessControlService)
    // Credentials: null (API key already validated)
    // Authorities: ROLE_API_KEY (to distinguish from admin tokens)
    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(
            integration.getId(), // Principal: Integration ID for ownership checks (avoid lazy loading issues)
            null, // Credentials (not stored after validation)
            Collections.singletonList(new SimpleGrantedAuthority(ROLE_API_KEY)));

    // Add request details
    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

    // Set in security context
    SecurityContextHolder.getContext().setAuthentication(authentication);

    logger.debug(
        "Security context established for integration: {} with role: {}",
        integration.getId(),
        ROLE_API_KEY);
  }

  /**
   * Extracts the client IP address from the request.
   *
   * <p>Checks standard headers for proxied requests (X-Forwarded-For, X-Real-IP) before falling
   * back to the direct remote address.
   *
   * <p><b>Header Priority:</b>
   *
   * <ol>
   *   <li>X-Forwarded-For (first IP in chain)
   *   <li>X-Real-IP
   *   <li>Remote address
   * </ol>
   *
   * @param request the HTTP request
   * @return the client IP address
   */
  private String extractClientIp(HttpServletRequest request) {
    // Check X-Forwarded-For header (standard for proxied requests)
    String xForwardedFor = request.getHeader("X-Forwarded-For");
    if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
      // X-Forwarded-For can contain multiple IPs, take the first one (client)
      String[] ips = xForwardedFor.split(",");
      String clientIp = ips[0].trim();
      logger.debug("Client IP from X-Forwarded-For: {}", clientIp);
      return clientIp;
    }

    // Check X-Real-IP header (nginx standard)
    String xRealIp = request.getHeader("X-Real-IP");
    if (xRealIp != null && !xRealIp.isEmpty()) {
      logger.debug("Client IP from X-Real-IP: {}", xRealIp);
      return xRealIp;
    }

    // Fall back to direct remote address
    String remoteAddr = request.getRemoteAddr();
    logger.debug("Client IP from remote address: {}", remoteAddr);
    return remoteAddr;
  }
}
