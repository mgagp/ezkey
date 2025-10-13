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
 * <p>This configuration provides basic security settings for the admin API, including password
 * encoding and endpoint access control.
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

  @Autowired(required = false)
  private AdminRateLimitFilter adminRateLimitFilter;

  public SecurityConfig(AdminTokenAuthenticationFilter adminTokenAuthenticationFilter) {
    this.adminTokenAuthenticationFilter = adminTokenAuthenticationFilter;
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
   * <p>This configuration supports both HTTP Basic authentication and Bearer token authentication.
   * It uses our custom UserDetailsService for database authentication and our custom filter for
   * bearer token validation.
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
        .httpBasic(
            httpBasic -> httpBasic.realmName("Ezkey Admin API")); // HTTP Basic authentication

    // Add rate limiting filter before authentication filter (if enabled)
    if (adminRateLimitFilter != null) {
      http.addFilterBefore(adminRateLimitFilter, UsernamePasswordAuthenticationFilter.class);
    }
    // Add bearer token authentication filter
    http.addFilterBefore(
        adminTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }
}
