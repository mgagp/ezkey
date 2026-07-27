/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 *
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: AdminCorsConfig
 *
 * Description: Registers CORS for the Admin API when {@code ezkey.admin.cors.allowed-origins} is
 * non-empty.
 */

package org.ezkey.admin.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Supplies a {@link CorsConfigurationSource} for Spring Security when cross-origin browser access
 * is configured.
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Configuration
@EnableConfigurationProperties(AdminCorsProperties.class)
public class AdminCorsConfig {

  /**
   * Returns {@code null} when CORS is not configured so behavior matches deployments that do not
   * need cross-origin headers (e.g. same-origin Admin UI behind Caddy).
   *
   * @param props bound {@link AdminCorsProperties}
   * @return request-based CORS configuration or {@code null}
   */
  @Bean
  public CorsConfigurationSource corsConfigurationSource(AdminCorsProperties props) {
    return _ -> {
      List<String> origins = props.getAllowedOrigins();
      if (origins == null || origins.isEmpty()) {
        return null;
      }
      CorsConfiguration config = new CorsConfiguration();
      config.setAllowedOrigins(new ArrayList<>(origins));
      config.setAllowedMethods(new ArrayList<>(props.getAllowedMethods()));
      config.setAllowedHeaders(new ArrayList<>(props.getAllowedHeaders()));
      config.setAllowCredentials(props.isAllowCredentials());
      return config;
    };
  }
}
