/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: SecurityConfig
 * Description: Spring Security configuration for admin API.
 */

package org.ezkey.admin.config;

import org.ezkey.admin.security.AdminRateLimitFilter;
import org.ezkey.admin.security.AdminTokenAuthenticationFilter;
import org.ezkey.admin.security.ApiKeyAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security configuration for admin API.
 *
 * <p>This configuration provides security settings for the admin API, supporting multiple
 * authentication methods:
 *
 * <ul>
 *   <li><b>API Keys:</b> HTTP Basic Auth for machine-to-machine (M2M) authentication
 *   <li><b>Bearer Tokens:</b> Token-based authentication for human administrators
 *   <li><b>Rate Limiting:</b> Protection against brute force attacks
 * </ul>
 *
 * <p><b>Filter Chain Order:</b>
 *
 * <ol>
 *   <li>Rate Limiting Filter (if enabled)
 *   <li>API Key Authentication Filter (HTTP Basic Auth)
 *   <li>Admin Token Authentication Filter (Bearer tokens)
 * </ol>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private final AdminTokenAuthenticationFilter adminTokenAuthenticationFilter;
  private final ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;

  @Autowired(required = false)
  private AdminRateLimitFilter adminRateLimitFilter;

  public SecurityConfig(
      AdminTokenAuthenticationFilter adminTokenAuthenticationFilter,
      ApiKeyAuthenticationFilter apiKeyAuthenticationFilter) {
    this.adminTokenAuthenticationFilter = adminTokenAuthenticationFilter;
    this.apiKeyAuthenticationFilter = apiKeyAuthenticationFilter;
  }

  /**
   * Password encoder bean for BCrypt hashing.
   *
   * @return BCryptPasswordEncoder instance
   */
  @Bean
  public BCryptPasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  /**
   * Security filter chain configuration.
   *
   * <p>This configuration supports multiple authentication methods:
   *
   * <ul>
   *   <li><b>API Keys:</b> HTTP Basic Auth for M2M authentication
   *   <li><b>Bearer Tokens:</b> Token-based authentication for admins
   * </ul>
   *
   * <p><b>Filter Order:</b> Rate Limiting → API Key Auth → Bearer Token Auth
   *
   * @param http the HttpSecurity configuration
   * @return SecurityFilterChain
   * @throws Exception if configuration fails
   */
  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(
            authz ->
                authz
                    // Allow public access to authentication endpoints
                    .requestMatchers("/api/v1/admin/auth/**")
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
                    // Require authentication for all other endpoints
                    .anyRequest()
                    .authenticated())
        .httpBasic(httpBasic -> httpBasic.disable()) // Disable default HTTP Basic
        .formLogin(formLogin -> formLogin.disable()); // Disable form login

    // Add rate limiting filter before all authentication filters (if enabled)
    if (adminRateLimitFilter != null) {
      http.addFilterBefore(adminRateLimitFilter, UsernamePasswordAuthenticationFilter.class);
    }

    // Add bearer token authentication filter
    http.addFilterBefore(
        adminTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

    // Add API key authentication filter BEFORE bearer token filter
    // This ensures API keys (HTTP Basic) are checked before bearer tokens
    http.addFilterBefore(
        apiKeyAuthenticationFilter, AdminTokenAuthenticationFilter.class);

    return http.build();
  }
}
