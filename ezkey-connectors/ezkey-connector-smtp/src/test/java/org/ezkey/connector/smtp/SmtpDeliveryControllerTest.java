/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 *
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.connector.smtp;

import static org.assertj.core.api.Assertions.assertThat;

import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetup;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;

@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "ezkey.connector.smtp.token=current-token",
      "ezkey.connector.smtp.next-token=next-token",
      "ezkey.connector.smtp.host=localhost",
      "ezkey.connector.smtp.smtp-port=3925",
      "ezkey.connector.smtp.from-address=invitations@example.com",
      "ezkey.connector.smtp.from-name=Acme invitations",
      "ezkey.connector.smtp.security=plain",
      "ezkey.connector.smtp.plain-private-network=true",
      "ezkey.connector.smtp.daily-cap=1000"
    })
@DisplayName("SMTP delivery connector")
class SmtpDeliveryControllerTest {

  @RegisterExtension
  static GreenMailExtension greenMail =
      new GreenMailExtension(new ServerSetup(3925, null, ServerSetup.PROTOCOL_SMTP))
          .withConfiguration(GreenMailConfiguration.aConfig().withDisabledAuthentication());

  @LocalServerPort int port;

  private ConnectorClient client;

  @BeforeEach
  void setUp() {
    client = new ConnectorClient(port);
  }

  @Test
  @DisplayName("health answers ok without contacting the provider, and reveals no provider detail")
  void health() throws Exception {
    HttpResponse<String> response = client.health("current-token");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body())
        .contains("\"status\":\"ok\"", "\"testMode\":false", "\"contractVersion\":\"v1\"")
        .doesNotContain("localhost", "invitations@example.com", "3925");
    assertThat(response.body()).contains("\"connectorVersion\":\"" + buildVersion() + "\"");
    assertThat(greenMail.getReceivedMessages()).isEmpty();
  }

  @Test
  @DisplayName("every route needs the token")
  void unauthorized() throws Exception {
    assertThat(client.health(null).statusCode()).isEqualTo(401);
    assertThat(client.health("wrong").statusCode()).isEqualTo(401);
    HttpResponse<String> send =
        client.post(
            "wrong",
            ConnectorClient.job("en", "a@example.com", "Acme", ConnectorClient.PNG_BASE64));
    assertThat(send.statusCode()).isEqualTo(401);
    assertThat(greenMail.getReceivedMessages()).isEmpty();
  }

  @Test
  @DisplayName("French email: fixed wording, server named, QR attached inline, no challenge")
  void sendsFrenchEmailWithInlineQr() throws Exception {
    HttpResponse<String> response =
        client.post(
            "current-token",
            ConnectorClient.job("fr", "person@example.com", "Acme", ConnectorClient.PNG_BASE64));

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("\"outcome\":\"accepted\"", "\"testMode\":false");
    MimeMessage[] received = greenMail.getReceivedMessages();
    assertThat(received).hasSize(1);
    MimeMessage message = received[0];
    assertThat(message.getSubject())
        .isEqualTo("Acme vous invite à configurer l’approbation de connexion");
    assertThat(message.getFrom()[0].toString()).contains("invitations@example.com");

    List<Part> parts = leaves(message);
    String text = bodyOf(parts, "text/plain");
    String html = bodyOf(parts, "text/html");
    assertThat(text)
        .contains("auth.example.com", "code à 6 chiffres", "Vous n’attendiez pas ce courriel");
    assertThat(html).contains("cid:enrollment-qr", "auth.example.com");
    Part image = parts.stream().filter(p -> isType(p, "image/png")).findFirst().orElseThrow();
    assertThat(image.getHeader("Content-ID")[0]).isEqualTo("<enrollment-qr>");
    assertThat(image.getDisposition()).isEqualToIgnoringCase(Part.INLINE);
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    image.getInputStream().transferTo(bytes);
    assertThat(bytes.toByteArray())
        .isEqualTo(Base64.getDecoder().decode(ConnectorClient.PNG_BASE64));
  }

  @Test
  @DisplayName("the job's deadline and zone reach the email; a malformed deadline is refused")
  void deadlineFromJob() throws Exception {
    String deadline = ",\"expiresAt\":\"2026-11-16T14:43:00Z\",\"timeZone\":\"America/Toronto\"";
    HttpResponse<String> response =
        client.post(
            "current-token",
            ConnectorClient.job(
                "fr", "person@example.com", "Acme", ConnectorClient.PNG_BASE64, deadline));

    assertThat(response.statusCode()).isEqualTo(200);
    String text = bodyOf(leaves(greenMail.getReceivedMessages()[0]), "text/plain");
    assertThat(text).contains("valable jusqu’au 16 novembre 2026 à 9 h 43");

    String malformed = ",\"expiresAt\":\"next week\"";
    assertThat(
            client
                .post(
                    "current-token",
                    ConnectorClient.job(
                        "fr", "person@example.com", "Acme", ConnectorClient.PNG_BASE64, malformed))
                .statusCode())
        .isEqualTo(400);
    assertThat(greenMail.getReceivedMessages()).hasSize(1);
  }

  @Test
  @DisplayName("English email, and the organisation name is escaped in the HTML part")
  void escapesOrganisationName() throws Exception {
    HttpResponse<String> response =
        client.post(
            "next-token",
            ConnectorClient.job(
                "en", "person@example.com", "Bob & <i>Co</i>", ConnectorClient.PNG_BASE64));

    assertThat(response.statusCode()).isEqualTo(200);
    MimeMessage message = greenMail.getReceivedMessages()[0];
    assertThat(message.getSubject())
        .isEqualTo("Bob & <i>Co</i> invites you to set up sign-in approval");
    String html = bodyOf(leaves(message), "text/html");
    assertThat(html).contains("Bob &amp; &lt;i&gt;Co&lt;/i&gt;").doesNotContain("<i>Co</i>");
  }

  @Test
  @DisplayName("refuses what the contract forbids, and nothing leaves")
  void refusesInvalidJobs() throws Exception {
    assertThat(post("en", "person@example.com", "Acme", "R0lGODlhAQABAAAAACw=").body())
        .contains("invalid-qr");
    assertThat(post("de", "person@example.com", "Acme", ConnectorClient.PNG_BASE64).body())
        .contains("unsupported-locale");
    assertThat(post("en", "not-an-address", "Acme", ConnectorClient.PNG_BASE64).body())
        .contains("invalid-recipient");
    HttpResponse<String> injected =
        post(
            "en", "person@example.com", "Acme\\r\\nBcc: x@example.com", ConnectorClient.PNG_BASE64);
    assertThat(injected.statusCode()).isEqualTo(400);
    assertThat(
            client
                .post(
                    "current-token", "{\"sendId\":\"" + ConnectorClient.SEND_ID + "\",\"extra\":1}")
                .statusCode())
        .isEqualTo(400);
    assertThat(greenMail.getReceivedMessages()).isEmpty();
  }

  @Test
  @DisplayName("logs hold the send id, never the recipient, the token or the QR")
  void logCanary(CapturedOutput output) throws Exception {
    client.post(
        "current-token",
        ConnectorClient.job("en", "canary-7f3a@example.com", "Acme", ConnectorClient.PNG_BASE64));

    // GreenMail plays the provider in this JVM and logs the mailbox it creates; the provider sees
    // the
    // recipient by design. Only the connector's own output is under test.
    String connectorOutput =
        (output.getOut() + output.getErr())
            .lines()
            .filter(line -> !line.contains("icegreen"))
            .collect(java.util.stream.Collectors.joining("\n"));
    assertThat(connectorOutput)
        .contains(ConnectorClient.SEND_ID)
        .doesNotContain("canary-7f3a", "current-token", "iVBORw0KGgo", "invitations@example.com");
  }

  private HttpResponse<String> post(String locale, String recipient, String org, String qr)
      throws Exception {
    return client.post("current-token", ConnectorClient.job(locale, recipient, org, qr));
  }

  private static List<Part> leaves(Part part) throws Exception {
    List<Part> out = new ArrayList<>();
    if (part.getContent() instanceof Multipart multipart) {
      for (int i = 0; i < multipart.getCount(); i++) {
        out.addAll(leaves(multipart.getBodyPart(i)));
      }
    } else {
      out.add(part);
    }
    return out;
  }

  private static boolean isType(Part part, String type) {
    try {
      return part.isMimeType(type);
    } catch (Exception e) {
      return false;
    }
  }

  private static String bodyOf(List<Part> parts, String type) throws Exception {
    for (Part part : parts) {
      if (isType(part, type)) {
        return (String) part.getContent();
      }
    }
    throw new AssertionError("No " + type + " part");
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
