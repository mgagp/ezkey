/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 *
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.connector.smtp;

import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Connector settings, bound from {@code EZKEY_CONNECTOR_SMTP_*}. Secrets stay out of
 * {@link #toString()}.
 *
 * @param token bearer token Ezkey presents
 * @param nextToken second token accepted during a rotation, or blank
 * @param host SMTP host; blank leaves the connector degraded
 * @param smtpPort SMTP port
 * @param username SMTP user, or blank for no authentication
 * @param password SMTP password
 * @param fromAddress sender address; blank leaves the connector degraded
 * @param fromName sender display name
 * @param security {@code starttls} (default), {@code tls}, or {@code plain}
 * @param plainPrivateNetwork explicit declaration that a plain connection stays on a private network
 * @param providerTimeoutSeconds connect, read and write timeout towards the provider
 * @param dailyCap sends per UTC day that may reach the provider
 */
@ConfigurationProperties("ezkey.connector.smtp")
public record SmtpConnectorSettings(
    String token,
    String nextToken,
    String host,
    int smtpPort,
    String username,
    String password,
    String fromAddress,
    String fromName,
    String security,
    boolean plainPrivateNetwork,
    int providerTimeoutSeconds,
    int dailyCap) {

  /** Transport security towards the provider. */
  enum Security {
    STARTTLS,
    TLS,
    PLAIN
  }

  Security securityMode() {
    return Security.valueOf(security.trim().toUpperCase(Locale.ROOT));
  }

  /** Whether the connector has what it needs to send. Health reports {@code degraded} otherwise. */
  boolean configured() {
    return !isBlank(host) && !isBlank(fromAddress);
  }

  boolean authenticated() {
    return !isBlank(username);
  }

  static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  @Override
  public String toString() {
    return "SmtpConnectorSettings[security=" + security + ", configured=" + configured() + "]";
  }
}
