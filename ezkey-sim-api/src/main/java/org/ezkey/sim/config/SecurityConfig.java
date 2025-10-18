/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: SecurityConfig
 * Description: Spring Security configuration for simulation API.
 */

package org.ezkey.sim.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security configuration for simulation API.
 *
 * <p>This configuration permits all requests to simulation endpoints without authentication since
 * this API is intended for development and testing purposes only.
 *
 * <p><b>Warning:</b> This configuration disables all security. The simulation API should NEVER be
 * exposed in production environments.
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

  /**
   * Security filter chain that permits all requests.
   *
   * <p>This configuration is appropriate for the simulation API since it's a development tool that
   * should not have authentication barriers.
   *
   * @param http the HttpSecurity configuration
   * @return SecurityFilterChain that permits all requests
   * @throws Exception if configuration fails
   */
  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(authz -> authz.anyRequest().permitAll());
    return http.build();
  }
}

