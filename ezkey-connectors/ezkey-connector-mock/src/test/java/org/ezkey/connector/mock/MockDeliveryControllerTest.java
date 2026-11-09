/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 *
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.connector.mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;

@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "ezkey.connector.mock.test-mode=true",
      "ezkey.connector.mock.token=current-token",
      "ezkey.connector.mock.next-token=next-token",
      "ezkey.connector.mock.slow-seconds=1"
    })
@DisplayName("Mock delivery connector")
class MockDeliveryControllerTest {

  private static final String PNG_BASE64 =
      "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==";
  private static final String SEND_ID = "3f0e2f6a-9b1c-4d7e-8a55-2c1d9e7b6a10";

  @LocalServerPort private int port;

  private final HttpClient client =
      HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

  private static String job(String recipient, String qr, String extra) {
    return """
    {"sendId":"%s","trigger":"operator","tenantRef":"42","template":"enrollment-qr",
     "locale":"fr","recipient":"%s","organisationName":"Acme",
     "expectedServer":"auth.example.com","qrPngBase64":"%s"%s}\
    """
        .formatted(SEND_ID, recipient, qr, extra);
  }

  private HttpResponse<String> send(String token, String body) throws Exception {
    return client.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v0/deliveries"))
            .header("Authorization", "Bearer " + token)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> scenario(String localPart) throws Exception {
    return send("current-token", job(localPart + "@example.com", PNG_BASE64, ""));
  }

  private HttpResponse<String> health(String token) throws Exception {
    return client.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v0/health"))
            .header("Authorization", "Bearer " + token)
            .GET()
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  @Test
  @DisplayName("refuses to start outside explicit test mode or without a token")
  void startupGuard() {
    assertThatThrownBy(() -> MockConnectorApplication.check(false, "token"))
        .hasMessageContaining("never delivers");
    assertThatThrownBy(() -> MockConnectorApplication.check(true, " "))
        .hasMessageContaining("TOKEN");
  }

  @Test
  @DisplayName("identifies itself in its responses")
  void identifiesItself() throws Exception {
    HttpResponse<String> health = health("current-token");
    assertThat(health.statusCode()).isEqualTo(200);
    assertThat(health.body())
        .contains("\"testMode\":true", "\"contractVersion\":\"v0\"", "ezkey-connector-mock");
    assertThat(health.body()).contains("\"connectorVersion\":\"" + buildVersion() + "\"");

    HttpResponse<String> accepted = scenario("anyone");
    assertThat(accepted.statusCode()).isEqualTo(200);
    assertThat(accepted.body()).contains("\"outcome\":\"accepted\"", "\"testMode\":true");
  }

  @Test
  @DisplayName("protects every route, and accepts both tokens during a rotation")
  void tokens() throws Exception {
    assertThat(health("wrong").statusCode()).isEqualTo(401);
    assertThat(send("wrong", job("a@example.com", PNG_BASE64, "")).statusCode()).isEqualTo(401);
    assertThat(health("next-token").statusCode()).isEqualTo(200);
  }

  @Test
  @DisplayName("plays each scenario with the contract's answer")
  void scenarios() throws Exception {
    assertThat(scenario("mock-provider-rejected").statusCode()).isEqualTo(422);
    assertThat(scenario("mock-provider-rejected").body()).contains("provider-rejected");
    assertThat(scenario("mock-recipient-refused").body()).contains("recipient-refused");
    assertThat(scenario("mock-provider-unavailable").statusCode()).isEqualTo(422);
    assertThat(scenario("mock-cap-reached").body()).contains("connector-cap-reached");
    assertThat(scenario("mock-provider-timeout").statusCode()).isEqualTo(504);
    assertThat(scenario("mock-server-error").statusCode()).isEqualTo(500);
    assertThat(scenario("mock-redirect").statusCode()).isEqualTo(302);
    assertThat(scenario("mock-unreadable").body()).isEqualTo("not json");
  }

  @Test
  @DisplayName("the slow scenario waits, then accepts")
  void slow() throws Exception {
    long start = System.nanoTime();
    assertThat(scenario("mock-slow").statusCode()).isEqualTo(200);
    assertThat((System.nanoTime() - start) / 1_000_000).isGreaterThanOrEqualTo(900);
  }

  @Test
  @DisplayName("refuses anything outside the contract")
  void refusesOutOfContract() throws Exception {
    assertThat(send("current-token", "not json").statusCode()).isEqualTo(400);
    assertThat(
            send("current-token", job("a@example.com", PNG_BASE64, ",\"challenge\":123456"))
                .statusCode())
        .isEqualTo(400);
    assertThat(
            send("current-token", job("a@example.com", PNG_BASE64, ",\"subject\":\"hi\""))
                .statusCode())
        .isEqualTo(400);
    HttpResponse<String> notPng = send("current-token", job("a@example.com", "aGVsbG8=", ""));
    assertThat(notPng.statusCode()).isEqualTo(422);
    assertThat(notPng.body()).contains("invalid-qr");
    String tooLong = "iVBORw0KGgo" + "A".repeat(90_000);
    assertThat(send("current-token", job("a@example.com", tooLong, "")).statusCode())
        .isEqualTo(413);
    assertThat(send("current-token", job("not-an-address", PNG_BASE64, "")).body())
        .contains("invalid-recipient");
  }

  @Test
  @DisplayName("logs hold neither the recipient nor the QR")
  void logsStayClean(CapturedOutput output) throws Exception {
    scenario("private.person");
    assertThat(output.getAll()).contains(SEND_ID).doesNotContain("private.person", "iVBORw0KGgo");
  }

  /** The version the build wrote, which the health must report (not a hardcoded constant). */
  private static String buildVersion() throws Exception {
    try (InputStream in =
        ConnectorVersion.class.getResourceAsStream("/META-INF/build-info.properties")) {
      assertThat(in).as("the spring-boot build-info goal must run").isNotNull();
      Properties properties = new Properties();
      properties.load(in);
      return properties.getProperty("build.version");
    }
  }
}
