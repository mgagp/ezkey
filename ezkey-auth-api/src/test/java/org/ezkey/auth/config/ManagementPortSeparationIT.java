/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: ManagementPortSeparationIT
 * Description: Dual-port Actuator + JavaMelody separation for Auth API (minimal context).
 */

package org.ezkey.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * Boots a minimal Auth API web surface (SecurityConfig + JavaMelody guard) to prove management-port
 * separation and JavaMelody report placement.
 *
 * <p>Auth SecurityConfig is {@code anyRequest().permitAll()}, so {@code /actuator/**} on the
 * management port is reachable without extra matchers (documented for operators).
 */
@SpringBootTest(
    classes = ManagementPortSeparationIT.TestApp.class,
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
      "spring.main.banner-mode=off"
    })
class ManagementPortSeparationIT {

  @LocalServerPort private int businessPort;

  @LocalManagementPort private int managementPort;

  private final HttpClient http = HttpClient.newHttpClient();

  @Test
  void healthAndMonitoringRespectManagementPortSeparation() throws Exception {
    assertThat(managementPort).isNotEqualTo(businessPort);

    assertThat(status("http://127.0.0.1:" + managementPort + "/actuator/health")).isEqualTo(200);
    assertThat(status("http://127.0.0.1:" + businessPort + "/actuator/health")).isEqualTo(404);

    // JavaMelody filter on the business port refuses the report with 403 when
    // management-endpoint-monitoring-enabled=true.
    assertThat(status("http://127.0.0.1:" + businessPort + "/monitoring")).isEqualTo(403);

    // Report is the Actuator endpoint on the management port (permitAll / anyRequest).
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
  @Import({SecurityConfig.class, JavaMelodyStartupGuardConfig.class})
  static class TestApp {}
}
