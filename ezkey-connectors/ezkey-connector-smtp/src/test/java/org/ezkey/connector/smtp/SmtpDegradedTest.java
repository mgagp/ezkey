/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 *
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.connector.smtp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/** Without a host or sender, the connector runs, says degraded, and sends nothing. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"ezkey.connector.smtp.token=current-token"})
@DisplayName("SMTP connector without provider settings")
class SmtpDegradedTest {

  @LocalServerPort int port;

  @Test
  @DisplayName("health is degraded and a send is provider-unavailable")
  void degraded() throws Exception {
    ConnectorClient client = new ConnectorClient(port);

    assertThat(client.health("current-token").body()).contains("\"status\":\"degraded\"");
    assertThat(
            client
                .post(
                    "current-token",
                    ConnectorClient.job("en", "person@example.com", "Acme", ConnectorClient.PNG_BASE64))
                .body())
        .contains("provider-unavailable");
  }
}
