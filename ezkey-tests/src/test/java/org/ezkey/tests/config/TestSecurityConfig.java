/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: TestSecurityConfig
 * Description: Security configuration for functional tests - disables authentication
 */

package org.ezkey.tests.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Test security configuration that disables authentication for functional tests.
 *
 * <p>This configuration is only active when the property `ezkey.test.security.disabled=true` is
 * set, which should only happen during functional testing with embedded servers.
 *
 * @since 2025
 */
@Configuration
@ConditionalOnProperty(name = "ezkey.test.security.disabled", havingValue = "true")
public class TestSecurityConfig {

  /**
   * Creates a permissive security filter chain for testing.
   *
   * <p>This configuration disables CSRF protection and permits all requests without authentication.
   * It should only be used in test environments.
   *
   * @param http the HttpSecurity configuration
   * @return SecurityFilterChain that permits all requests
   * @throws Exception if configuration fails
   */
  @Bean
  @Primary
  public SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(authz -> authz.anyRequest().permitAll());

    return http.build();
  }
}
