/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 *
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.connector.smtp;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/** No SMTP server listens on the configured port: the message surely did not leave. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "ezkey.connector.smtp.token=current-token",
      "ezkey.connector.smtp.host=localhost",
      "ezkey.connector.smtp.smtp-port=1",
      "ezkey.connector.smtp.from-address=invitations@example.com",
      "ezkey.connector.smtp.security=plain",
      "ezkey.connector.smtp.plain-private-network=true",
      "ezkey.connector.smtp.daily-cap=1",
      "ezkey.connector.smtp.provider-timeout-seconds=2"
    })
@DisplayName("SMTP connector with the provider down")
class SmtpProviderDownTest {

  @LocalServerPort int port;

  @Test
  @DisplayName("a refused connection is provider-unavailable and does not use the daily cap")
  void providerUnavailable() throws Exception {
    ConnectorClient client = new ConnectorClient(port);
    String job = ConnectorClient.job("en", "person@example.com", "Acme", ConnectorClient.PNG_BASE64);

    for (int i = 0; i < 2; i++) {
      HttpResponse<String> response = client.post("current-token", job);
      assertThat(response.statusCode()).isEqualTo(422);
      assertThat(response.body()).contains("provider-unavailable").doesNotContain("localhost");
    }
  }
}
