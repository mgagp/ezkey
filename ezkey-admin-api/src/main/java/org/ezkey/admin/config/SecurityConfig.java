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

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security configuration for admin API.
 * <p>
 * This configuration provides basic security settings for the admin API,
 * including password encoding and endpoint access control.
 * </p>
 *
 * <p>
 * <b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 * </p>
 * <p>
 * <b>License:</b> MIT
 * </p>
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

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
     * <p>
     * This configuration uses our custom UserDetailsService for database authentication
     * and allows public access to authentication endpoints while requiring authentication
     * for all other endpoints.
     * </p>
     *
     * @param http the HttpSecurity configuration
     * @return SecurityFilterChain
     * @throws Exception if configuration fails
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(authz -> authz
                // Allow public access to authentication endpoints
                .requestMatchers("/api/v1/admin/auth/**").permitAll()
                // Allow public access to health check endpoints
                .requestMatchers("/actuator/**").permitAll()
                // Allow public access to API documentation
                .requestMatchers("/swagger-ui/**", "/api-docs/**").permitAll()
                // Require authentication for all other endpoints
                .anyRequest().authenticated()
            )
            .httpBasic(httpBasic -> httpBasic.realmName("Ezkey Admin API")); // Use HTTP Basic authentication with our UserDetailsService
        
        return http.build();
    }
}
