/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AdminCorsConfigurationSourceTest
 * Description: Unit tests for CORS configuration binding and {@link CorsConfigurationSource} behavior.
 */

package org.ezkey.admin.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

/** Tests {@link AdminCorsConfig#corsConfigurationSource(AdminCorsProperties)}. */
class AdminCorsConfigurationSourceTest {

  private final AdminCorsConfig adminCorsConfig = new AdminCorsConfig();

  @Test
  @DisplayName("When allowed-origins is empty, CorsConfigurationSource returns null")
  void whenAllowedOriginsEmpty_returnsNull() {
    AdminCorsProperties props = new AdminCorsProperties();
    CorsConfigurationSource source = adminCorsConfig.corsConfigurationSource(props);
    assertNull(source.getCorsConfiguration(new MockHttpServletRequest()));
  }

  @Test
  @DisplayName(
      "When allowed-origins is set, CorsConfiguration reflects methods, headers, credentials")
  void whenAllowedOriginsSet_returnsConfiguration() {
    AdminCorsProperties props = new AdminCorsProperties();
    props.setAllowedOrigins(List.of("https://ui.example"));
    CorsConfigurationSource source = adminCorsConfig.corsConfigurationSource(props);
    CorsConfiguration cors = source.getCorsConfiguration(new MockHttpServletRequest());
    assertNotNull(cors);
    assertEquals(List.of("https://ui.example"), cors.getAllowedOrigins());
    assertTrue(cors.getAllowedMethods().contains("GET"));
    assertTrue(cors.getAllowedMethods().contains("OPTIONS"));
    assertTrue(cors.getAllowedHeaders().contains("Authorization"));
    assertFalse(cors.getAllowCredentials());
  }

  @Test
  @DisplayName("allow-credentials can be enabled when explicitly configured")
  void allowCredentialsTrue() {
    AdminCorsProperties props = new AdminCorsProperties();
    props.setAllowedOrigins(List.of("https://ui.example"));
    props.setAllowCredentials(true);
    CorsConfiguration cors =
        adminCorsConfig
            .corsConfigurationSource(props)
            .getCorsConfiguration(new MockHttpServletRequest());
    assertNotNull(cors);
    assertTrue(cors.getAllowCredentials());
  }
}
