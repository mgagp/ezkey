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
 * <p>Admin API authenticates human operators (Bearer token or browser session cookie). API-key
 * machine-to-machine traffic belongs on Integration API. HTTP Basic is disabled; credentials of
 * that form do not authenticate here.
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
 *   <li>Admin Token Authentication Filter (Bearer tokens)
 *   <li>Cookie CSRF Filter (browser session cookie only)
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
  private final AdminRateLimitFilter adminRateLimitFilter;
  private final AdminCookieCsrfFilter adminCookieCsrfFilter;

  /**
   * Creates the Admin API security configuration.
   *
   * @param adminTokenAuthenticationFilter Bearer / session token filter
   * @param adminRateLimitFilter login and admin-ops rate limit filter
   * @param adminCookieCsrfFilter CSRF check for cookie-authenticated unsafe methods
   */
  public SecurityConfig(
      AdminTokenAuthenticationFilter adminTokenAuthenticationFilter,
      AdminRateLimitFilter adminRateLimitFilter,
      AdminCookieCsrfFilter adminCookieCsrfFilter) {
    this.adminTokenAuthenticationFilter = adminTokenAuthenticationFilter;
    this.adminRateLimitFilter = adminRateLimitFilter;
    this.adminCookieCsrfFilter = adminCookieCsrfFilter;
  }

  /**
   * Security filter chain configuration.
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
        .httpBasic(httpBasic -> httpBasic.disable())
        .formLogin(formLogin -> formLogin.disable());

    http.addFilterBefore(adminRateLimitFilter, UsernamePasswordAuthenticationFilter.class);

    http.addFilterBefore(
        adminTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

    http.addFilterAfter(adminCookieCsrfFilter, AdminTokenAuthenticationFilter.class);

    return http.build();
  }
}
