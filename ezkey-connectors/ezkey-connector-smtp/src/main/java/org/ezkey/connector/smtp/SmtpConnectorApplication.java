/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 *
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.connector.smtp;

import java.util.Properties;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/** Delivery contract v0 over SMTP (#1206 step 5). */
@SpringBootApplication
@EnableConfigurationProperties(SmtpConnectorSettings.class)
public class SmtpConnectorApplication {

  /**
   * Starts the connector.
   *
   * @param args command line arguments
   */
  public static void main(String[] args) {
    SpringApplication.run(SmtpConnectorApplication.class, args);
  }

  /**
   * Refuses to start without a token, or with a plain SMTP connection that was not declared
   * private.
   *
   * @param settings the connector settings
   * @return the startup guard
   */
  @Bean
  ApplicationRunner startupGuard(SmtpConnectorSettings settings) {
    return _ -> check(settings);
  }

  static void check(SmtpConnectorSettings settings) {
    if (SmtpConnectorSettings.isBlank(settings.token())) {
      throw new IllegalStateException("Set EZKEY_CONNECTOR_SMTP_TOKEN to run the connector.");
    }
    SmtpConnectorSettings.Security security;
    try {
      security = settings.securityMode();
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException(
          "EZKEY_CONNECTOR_SMTP_SECURITY must be starttls, tls or plain.", e);
    }
    if (security == SmtpConnectorSettings.Security.PLAIN && !settings.plainPrivateNetwork()) {
      throw new IllegalStateException(
          "A plain SMTP connection exposes the QR on the wire. Use starttls or tls, or declare a"
              + " private network with EZKEY_CONNECTOR_SMTP_PLAIN_PRIVATE_NETWORK=true.");
    }
    if (settings.dailyCap() < 1 || settings.providerTimeoutSeconds() < 1) {
      throw new IllegalStateException("The daily cap and the provider timeout must be positive.");
    }
  }

  /**
   * Builds the SMTP client. TLS checks the server identity, and every timeout is the provider
   * timeout, which stays shorter than the read timeout Ezkey uses for the call.
   *
   * @param settings the connector settings
   * @return the mail sender
   */
  @Bean
  JavaMailSenderImpl mailSender(SmtpConnectorSettings settings) {
    JavaMailSenderImpl sender = new JavaMailSenderImpl();
    sender.setHost(settings.host());
    sender.setPort(settings.smtpPort());
    if (settings.authenticated()) {
      sender.setUsername(settings.username());
      sender.setPassword(settings.password());
    }
    String timeout = String.valueOf(settings.providerTimeoutSeconds() * 1000L);
    Properties props = sender.getJavaMailProperties();
    props.put("mail.smtp.connectiontimeout", timeout);
    props.put("mail.smtp.timeout", timeout);
    props.put("mail.smtp.writetimeout", timeout);
    props.put("mail.smtp.auth", String.valueOf(settings.authenticated()));
    switch (safeSecurity(settings)) {
      case STARTTLS -> {
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");
        props.put("mail.smtp.ssl.checkserveridentity", "true");
      }
      case TLS -> {
        props.put("mail.smtp.ssl.enable", "true");
        props.put("mail.smtp.ssl.checkserveridentity", "true");
      }
      case PLAIN -> props.put("mail.smtp.starttls.enable", "false");
      default -> throw new IllegalStateException("Unsupported SMTP security mode");
    }
    return sender;
  }

  private static SmtpConnectorSettings.Security safeSecurity(SmtpConnectorSettings settings) {
    try {
      return settings.securityMode();
    } catch (IllegalArgumentException e) {
      // The startup guard reports the error; keep the strictest mode meanwhile.
      return SmtpConnectorSettings.Security.STARTTLS;
    }
  }
}
