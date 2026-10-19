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

import jakarta.servlet.DispatcherType;
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
import org.springframework.test.context.TestPropertySource;

/**
 * Minimal Admin-shaped security chain: {@code /actuator/**} permitAll, everything else
 * authenticated — mirrors production {@link org.ezkey.admin.config.SecurityConfig} actuator rule
 * without loading filters that need the full Admin application.
 *
 * <p><b>Documented result:</b> management-port {@code /actuator/health} and {@code
 * /actuator/monitoring} return 200. Business-port {@code /actuator/health} is native <b>404</b>
 * (Actuator not mounted). Business {@code /monitoring} is <b>401</b> under this chain (same as
 * production LIVE — Security answers before JavaMelody's 403).
 *
 * <p>Test-only: {@code DispatcherType.ERROR} is permitAll so Boot's {@code /error} dispatch after a
 * missing handler is not turned into 401. Production returns 404 via the exception handler without
 * that auth failure (LIVE confirmed).
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
    // Exact 404 — Actuator is not mounted on the business port (LIVE + verify script).
    assertThat(status("http://127.0.0.1:" + businessPort + "/actuator/health")).isEqualTo(404);

    // Production LIVE: Security returns 401 on business /monitoring (not JM's 403).
    assertThat(status("http://127.0.0.1:" + businessPort + "/monitoring")).isEqualTo(401);

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
   * Mirrors production Admin actuator permitAll. String patterns use Security 7 {@code
   * PathPatternRequestMatcher}. {@code DispatcherType.ERROR} permitAll is test-only (see class
   * Javadoc) so a missing business-port Actuator handler yields native 404 instead of 401 on {@code
   * /error}.
   */
  @Configuration
  @EnableWebSecurity
  static class ActuatorPermitAllSecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
      // CSRF left enabled: this test only issues GET (no state-changing calls).
      http.sessionManagement(
              session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
          .requestCache(cache -> cache.requestCache(new NullRequestCache()))
          .authorizeHttpRequests(
              authz ->
                  authz
                      .dispatcherTypeMatchers(DispatcherType.ERROR)
                      .permitAll()
                      .requestMatchers("/actuator/**")
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
