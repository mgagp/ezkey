/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: ManagementPortSeparationIT
 * Description: Actuator health on management port only (404 on business port) for demo-app-acme.
 */

package org.ezkey.demo.acme.managementport;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.ezkey.demo.acme.DemoAcmeApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Environment;
import org.springframework.test.context.TestPropertySource;

/**
 * Confirms Actuator health is served only on the management port for demo-app-acme.
 *
 * <p>JavaMelody is not on the Acme classpath; monitoring 403/200 coverage lives on the three APIs.
 */
@SpringBootTest(
    classes = DemoAcmeApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    properties = {
      "server.port=0",
      "management.server.port=0",
      "ezkey.rate-limit.enabled=false",
      "javamelody.enabled=false",
      "javamelody.management-endpoint-monitoring-enabled=false"
    })
class ManagementPortSeparationIT {

  @LocalServerPort private int businessPort;

  @LocalManagementPort private int managementPort;

  @Autowired private Environment environment;

  private final HttpClient http = HttpClient.newHttpClient();

  @Test
  void healthIs200OnManagementPortAnd404OnBusinessPort() throws Exception {
    assertThat(managementPort).isNotEqualTo(businessPort);
    assertThat(environment.getProperty("local.management.port", Integer.class))
        .isEqualTo(managementPort);

    assertThat(status("http://127.0.0.1:" + managementPort + "/actuator/health")).isEqualTo(200);
    assertThat(status("http://127.0.0.1:" + businessPort + "/actuator/health")).isEqualTo(404);
  }

  private int status(String url) throws Exception {
    HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
    HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
    return response.statusCode();
  }
}
