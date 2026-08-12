/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: SecurityConfig
 * Description: Spring Security configuration for admin API.
 */

package org.ezkey.admin.config;

import org.ezkey.admin.security.AdminCookieCsrfFilter;
import org.ezkey.admin.security.AdminRateLimitFilter;
import org.ezkey.admin.security.AdminTokenAuthenticationFilter;
import org.ezkey.admin.security.ApiKeyAuthAttemptsAcceptanceFilter;
import org.ezkey.admin.security.ApiKeyAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security configuration for admin API.
 *
 * <p>This configuration provides security settings for the admin API, supporting multiple
 * authentication methods:
 *
 * <ul>
 *   <li><b>API Keys:</b> HTTP Basic Auth for machine-to-machine (Integration API) authentication
 *   <li><b>Bearer Tokens:</b> Token-based authentication for human administrators
 *   <li><b>Rate Limiting:</b> Protection against brute force attacks
 * </ul>
 *
 * <p><b>CORS:</b> When {@code ezkey.admin.cors.allowed-origins} is non-empty, Spring Security
 * applies {@link org.springframework.web.cors.CorsConfigurationSource} for browser clients on a
 * different origin than the API (e.g. Admin UI on Cloudflare Pages). Empty origins leave CORS unset
 * (same as same-origin deployments behind a reverse proxy).
 *
 * <p><b>Filter Chain Order:</b>
 *
 * <ol>
 *   <li>Rate Limiting Filter (if enabled)
 *   <li>API Key Authentication Filter (HTTP Basic Auth)
 *   <li>API Key Auth-Attempts Acceptance Filter (deny-by-default gate)
 *   <li>Admin Token Authentication Filter (Bearer tokens)
 * </ol>
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  private final AdminTokenAuthenticationFilter adminTokenAuthenticationFilter;
  private final ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;
  private final ApiKeyAuthAttemptsAcceptanceFilter apiKeyAuthAttemptsAcceptanceFilter;
  private final AdminRateLimitFilter adminRateLimitFilter;
  private final AdminCookieCsrfFilter adminCookieCsrfFilter;

  public SecurityConfig(
      AdminTokenAuthenticationFilter adminTokenAuthenticationFilter,
      ApiKeyAuthenticationFilter apiKeyAuthenticationFilter,
      ApiKeyAuthAttemptsAcceptanceFilter apiKeyAuthAttemptsAcceptanceFilter,
      AdminRateLimitFilter adminRateLimitFilter,
      AdminCookieCsrfFilter adminCookieCsrfFilter) {
    this.adminTokenAuthenticationFilter = adminTokenAuthenticationFilter;
    this.apiKeyAuthenticationFilter = apiKeyAuthenticationFilter;
    this.apiKeyAuthAttemptsAcceptanceFilter = apiKeyAuthAttemptsAcceptanceFilter;
    this.adminRateLimitFilter = adminRateLimitFilter;
    this.adminCookieCsrfFilter = adminCookieCsrfFilter;
  }

  /**
   * Security filter chain configuration.
   *
   * <p>This configuration supports multiple authentication methods:
   *
   * <ul>
   *   <li><b>API Keys:</b> HTTP Basic Auth for Integration API (API key) authentication
   *   <li><b>Bearer Tokens:</b> Token-based authentication for admins
   * </ul>
   *
   * <p><b>Filter Order:</b> Rate Limiting → API Key Auth → API Key Acceptance Gate → Bearer Token
   * Auth
   *
   * @param http the HttpSecurity configuration
   * @return SecurityFilterChain
   * @throws Exception if configuration fails
   */
  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .cors(Customizer.withDefaults())
        .authorizeHttpRequests(
            authz ->
                authz
                    // Allow public access to authentication bootstrap endpoints
                    .requestMatchers(
                        "/api/v1/admin/auth/activate",
                        "/api/v1/admin/auth/login",
                        "/api/v1/admin/auth/passwordless-wait",
                        "/api/v1/admin/auth/recover")
                    .permitAll()
                    // Current session metadata requires an authenticated browser or bearer session
                    .requestMatchers("/api/v1/admin/auth/me")
                    .authenticated()
                    // Logout accepts the token from Authorization or cookie and handles missing
                    // credentials itself.
                    .requestMatchers("/api/v1/admin/auth/logout")
                    .permitAll()
                    // Allow public access to MFA endpoints (temp token in body)
                    .requestMatchers("/api/v1/admin/mfa/**")
                    .permitAll()
                    // Allow public access to enrollment reset (recovery token in header)
                    .requestMatchers("/api/v1/admin/enrollments/**")
                    .permitAll()
                    // Allow public access to health check endpoints
                    .requestMatchers("/actuator/**")
                    .permitAll()
                    // Allow public access to API documentation
                    .requestMatchers("/swagger-ui/**", "/api-docs/**")
                    .permitAll()
                    // Public instance metadata (login shell, operators)
                    .requestMatchers("/api/v1/public/**")
                    .permitAll()
                    // Require authentication for all other endpoints
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            exceptions ->
                exceptions.authenticationEntryPoint(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
        .httpBasic(httpBasic -> httpBasic.disable()) // Disable default HTTP Basic
        .formLogin(formLogin -> formLogin.disable()); // Disable form login

    // Add rate limiting filter before all authentication filters
    http.addFilterBefore(adminRateLimitFilter, UsernamePasswordAuthenticationFilter.class);

    // Add bearer token authentication filter
    http.addFilterBefore(
        adminTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

    // Validate CSRF only for unsafe requests authenticated via the browser session cookie.
    http.addFilterAfter(adminCookieCsrfFilter, AdminTokenAuthenticationFilter.class);

    // Add API key authentication filter BEFORE bearer token filter
    // This ensures API keys (HTTP Basic) are checked before bearer tokens
    http.addFilterBefore(apiKeyAuthenticationFilter, AdminTokenAuthenticationFilter.class);

    // Deny-by-default gate for ROLE_API_KEY after successful API-key authentication
    http.addFilterAfter(apiKeyAuthAttemptsAcceptanceFilter, ApiKeyAuthenticationFilter.class);

    return http.build();
  }
}
