/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: SecurityConfig
 * Description: Spring Security configuration for auth API (public access).
 */

package org.ezkey.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security configuration for auth API.
 *
 * <p>This configuration allows public access to all auth API endpoints without authentication. The
 * auth API is designed for mobile device access and does not require Spring Security
 * authentication.
 *
 * <p><b>Security Model:</b>
 *
 * <ul>
 *   <li>All endpoints are publicly accessible
 *   <li>Security is handled at the application level with cryptographic signatures
 *   <li>Rate limiting is configured separately in application properties
 * </ul>
 *
 * <p><b>Note:</b> Spring Security is included via ezkey-core dependency (for BCrypt in API keys)
 * but is not used for auth API authentication. This configuration disables the default security
 * behavior.
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
public class SecurityConfig {

  /**
   * Security filter chain configuration that allows public access to all endpoints.
   *
   * <p>This configuration:
   *
   * <ul>
   *   <li>Disables CSRF protection (not needed for mobile API)
   *   <li>Permits all requests without authentication
   *   <li>Disables HTTP Basic authentication
   *   <li>Disables form login
   * </ul>
   *
   * @param http the HttpSecurity configuration
   * @return SecurityFilterChain
   * @throws Exception if configuration fails
   */
  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(authz -> authz.anyRequest().permitAll())
        .httpBasic(httpBasic -> httpBasic.disable())
        .formLogin(formLogin -> formLogin.disable());

    return http.build();
  }
}
