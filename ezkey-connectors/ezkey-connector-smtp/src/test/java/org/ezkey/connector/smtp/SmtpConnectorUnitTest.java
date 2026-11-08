/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 *
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.connector.smtp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.mail.SendFailedException;
import jakarta.mail.internet.InternetAddress;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;

@DisplayName("SMTP connector rules")
class SmtpConnectorUnitTest {

  private static SmtpConnectorSettings settings(String token, String security, boolean privateNet) {
    return new SmtpConnectorSettings(
        token, "", "smtp.example.com", 587, "", "", "a@example.com", "Ezkey", security, privateNet, 15, 100);
  }

  @Test
  @DisplayName("startup refuses a missing token, an unknown mode and an undeclared plain connection")
  void startupGuard() {
    assertThatThrownBy(() -> SmtpConnectorApplication.check(settings("", "starttls", false)))
        .hasMessageContaining("TOKEN");
    assertThatThrownBy(() -> SmtpConnectorApplication.check(settings("t", "ssl3", false)))
        .hasMessageContaining("starttls, tls or plain");
    assertThatThrownBy(() -> SmtpConnectorApplication.check(settings("t", "plain", false)))
        .hasMessageContaining("PLAIN_PRIVATE_NETWORK");
    assertThatCode(() -> SmtpConnectorApplication.check(settings("t", "plain", true)))
        .doesNotThrowAnyException();
    assertThatCode(() -> SmtpConnectorApplication.check(settings("t", "starttls", false)))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("STARTTLS is required by default and checks the server identity")
  void starttlsByDefault() {
    var props = new SmtpConnectorApplication().mailSender(settings("t", "starttls", false)).getJavaMailProperties();
    assertThat(props.getProperty("mail.smtp.starttls.required")).isEqualTo("true");
    assertThat(props.getProperty("mail.smtp.ssl.checkserveridentity")).isEqualTo("true");
    assertThat(props.getProperty("mail.smtp.timeout")).isEqualTo("15000");
  }

  @Test
  @DisplayName("settings never print secrets")
  void settingsToString() {
    SmtpConnectorSettings s =
        new SmtpConnectorSettings(
            "tok-123", "", "smtp.example.com", 587, "user", "pw-456", "a@example.com", "Ezkey", "starttls", false, 15, 100);
    assertThat(s.toString()).doesNotContain("tok-123", "pw-456", "smtp.example.com", "user");
  }

  @Test
  @DisplayName("provider failures map to the contract; only a sure non-delivery is a rejection")
  void classify() {
    assertThat(SmtpDeliveryController.classify(new MailSendException("x", new SocketTimeoutException())))
        .isEqualTo("provider-timeout");
    assertThat(SmtpDeliveryController.classify(new MailSendException("x", new ConnectException())))
        .isEqualTo("provider-unavailable");
    SendFailedException refused =
        new SendFailedException(
            "x", null, null, null, new jakarta.mail.Address[] {address("a@example.com")});
    assertThat(SmtpDeliveryController.classify(new MailSendException("x", refused)))
        .isEqualTo("recipient-refused");
    assertThat(SmtpDeliveryController.classify(new MailSendException("x", new RuntimeException("?"))))
        .isEqualTo("unknown");
  }

  @Test
  @DisplayName("daily cap: counts per UTC day, gives back a released send, resets the next day")
  void dailyCap() {
    AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-08T23:59:00Z"));
    Clock clock =
        new Clock() {
          @Override
          public ZoneOffset getZone() {
            return ZoneOffset.UTC;
          }

          @Override
          public Clock withZone(java.time.ZoneId zone) {
            return this;
          }

          @Override
          public Instant instant() {
            return now.get();
          }
        };
    DailyCap cap = new DailyCap(2, clock);
    assertThat(cap.tryAcquire()).isTrue();
    assertThat(cap.tryAcquire()).isTrue();
    assertThat(cap.tryAcquire()).isFalse();
    cap.release();
    assertThat(cap.tryAcquire()).isTrue();
    now.set(Instant.parse("2026-11-09T00:00:01Z"));
    assertThat(cap.tryAcquire()).isTrue();
  }

  private static InternetAddress address(String value) {
    try {
      return new InternetAddress(value);
    } catch (jakarta.mail.internet.AddressException e) {
      throw new IllegalStateException(e);
    }
  }
}
