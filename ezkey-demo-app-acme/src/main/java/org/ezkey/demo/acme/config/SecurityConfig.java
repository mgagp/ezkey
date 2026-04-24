/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: SecurityConfig
 * Description: Spring Security configuration for the ACME demo application.
 */

package org.ezkey.demo.acme.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

/**
 * Explicit Spring Security configuration for the ACME demo application.
 *
 * <p>The demo app uses controller-managed HTTP sessions rather than Spring Security's
 * authentication model. This filter chain therefore permits requests while keeping CSRF protection
 * enabled for browser-originated state-changing requests such as login and runtime API-key apply.
 * The configuration removes Spring Boot's generated default-login posture and makes the CSRF token
 * exposure explicit and observable in the rendered page.
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  /**
   * Configures the security filter chain for the ACME demo app.
   *
   * @param http the HttpSecurity builder
   * @return the configured security filter chain
   * @throws Exception if the configuration fails
   */
  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
    csrfHandler.setCsrfRequestAttributeName("_csrf");

    HttpSessionCsrfTokenRepository csrfRepository = new HttpSessionCsrfTokenRepository();
    csrfRepository.setParameterName("_csrf");
    csrfRepository.setHeaderName("X-CSRF-TOKEN");

    http.authorizeHttpRequests(authz -> authz.anyRequest().permitAll())
        .csrf(
            csrf ->
                csrf.csrfTokenRequestHandler(csrfHandler)
                    .csrfTokenRepository(csrfRepository)
                    .ignoringRequestMatchers("/actuator/**"))
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable);

    return http.build();
  }
}
