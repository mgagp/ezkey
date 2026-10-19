/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: ManagementPortSeparationTest
 * Description: Dual-port Actuator + JavaMelody separation for Admin API (minimal security chain).
 */

package org.ezkey.admin.managementport;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.ezkey.admin.config.JavaMelodyStartupGuardConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.test.context.TestPropertySource;

/**
 * Minimal Admin-shaped security chain: {@code /actuator/**} permitAll, everything else
 * authenticated — mirrors production {@link SecurityConfig} actuator rule without loading filters
 * that need the full Admin application.
 *
 * <p><b>Documented result:</b> {@code permitAll} for {@code /actuator/**} applies on the management
 * port, so {@code /actuator/health} and {@code /actuator/monitoring} return 200 without auth. Bare
 * {@code /monitoring} on the business port stays 403 under JavaMelody management-endpoint mode
 * (filter), not Spring Security.
 */
@SpringBootTest(
    classes = ManagementPortSeparationTest.TestApp.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    properties = {
      "server.port=0",
      "management.server.port=0",
      "management.endpoints.web.base-path=/actuator",
      "management.endpoints.web.exposure.include=health,monitoring",
      "management.endpoint.health.show-details=never",
      "management.endpoint.monitoring.access=unrestricted",
      "javamelody.enabled=true",
      "javamelody.management-endpoint-monitoring-enabled=true",
      "spring.main.banner-mode=off",
      "spring.autoconfigure.exclude="
          + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
          + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
          + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"
    })
class ManagementPortSeparationTest {

  @LocalServerPort private int businessPort;

  @LocalManagementPort private int managementPort;

  private final HttpClient http = HttpClient.newHttpClient();

  @Test
  void healthAndMonitoringRespectManagementPortSeparationAndActuatorPermitAll() throws Exception {
    assertThat(managementPort).isNotEqualTo(businessPort);

    assertThat(status("http://127.0.0.1:" + managementPort + "/actuator/health")).isEqualTo(200);
    // Target: native 404 — Actuator is not mounted on the business port (LIVE + verify script).
    assertThat(status("http://127.0.0.1:" + businessPort + "/actuator/health")).isEqualTo(404);

    // JavaMelody management-endpoint mode refuses the business-port report with 403.
    assertThat(status("http://127.0.0.1:" + businessPort + "/monitoring")).isEqualTo(403);

    // Proves /actuator/** permitAll on the management port (no 401).
    assertThat(status("http://127.0.0.1:" + managementPort + "/actuator/monitoring"))
        .isEqualTo(200);
  }

  private int status(String url) throws Exception {
    HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
    HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
    return response.statusCode();
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration(exclude = UserDetailsServiceAutoConfiguration.class)
  @Import({ActuatorPermitAllSecurityConfig.class, JavaMelodyStartupGuardConfig.class})
  static class TestApp {}

  /**
   * Mirrors production Admin actuator permitAll. Uses {@link AntPathRequestMatcher} so {@code
   * /actuator/**} still matches when no MVC handler exists on the business port (otherwise
   * MvcRequestMatcher misses and {@code anyRequest().authenticated()} returns 401 instead of the
   * target native 404). {@code /monitoring} is permitted so the JavaMelody filter can return 403.
   */
  @Configuration
  @EnableWebSecurity
  static class ActuatorPermitAllSecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
      http.csrf(csrf -> csrf.disable())
          .sessionManagement(
              session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
          .requestCache(cache -> cache.requestCache(new NullRequestCache()))
          .authorizeHttpRequests(
              authz ->
                  authz
                      .requestMatchers(AntPathRequestMatcher.antMatcher("/actuator/**"))
                      .permitAll()
                      .requestMatchers(AntPathRequestMatcher.antMatcher("/monitoring"))
                      .permitAll()
                      .anyRequest()
                      .authenticated())
          .exceptionHandling(
              exceptions ->
                  exceptions.authenticationEntryPoint(
                      new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
          .httpBasic(httpBasic -> httpBasic.disable())
          .formLogin(formLogin -> formLogin.disable());
      return http.build();
    }
  }
}
