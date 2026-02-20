/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: M2mSecurityConfig
 * Description: Spring Security configuration for M2M API — API key only.
 */

package org.ezkey.m2m.config;

import org.ezkey.m2m.security.ApiKeyAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Minimalist Spring Security configuration for the M2M API.
 *
 * <p>The M2M API supports only one authentication method: API key via HTTP Basic Auth ({@link
 * ApiKeyAuthenticationFilter}). There is no admin token filter, no form login, and no CSRF
 * protection (stateless REST API).
 *
 * <p><b>Filter chain:</b> {@code ApiKeyAuthenticationFilter} → default Spring Security filters.
 *
 * <p><b>Authorization rules:</b>
 *
 * <ul>
 *   <li>Actuator endpoints — public
 *   <li>OpenAPI / Swagger UI — public
 *   <li>All other requests — require {@code ROLE_API_KEY}
 * </ul>
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
@EnableMethodSecurity
public class M2mSecurityConfig {

  private final ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;

  /**
   * Constructs the security configuration.
   *
   * @param apiKeyAuthenticationFilter the API key authentication filter
   */
  public M2mSecurityConfig(ApiKeyAuthenticationFilter apiKeyAuthenticationFilter) {
    this.apiKeyAuthenticationFilter = apiKeyAuthenticationFilter;
  }

  /**
   * Configures the security filter chain for the M2M API.
   *
   * @param http the {@link HttpSecurity} builder
   * @return the configured {@link SecurityFilterChain}
   * @throws Exception if configuration fails
   */
  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(
            authz ->
                authz
                    .requestMatchers("/actuator/**")
                    .permitAll()
                    .requestMatchers("/swagger-ui/**", "/api-docs/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            exceptions ->
                exceptions.authenticationEntryPoint(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
        .httpBasic(httpBasic -> httpBasic.disable())
        .formLogin(formLogin -> formLogin.disable());

    http.addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }
}
