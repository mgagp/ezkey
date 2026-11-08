/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 *
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.connector.smtp;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/** Minimal HTTP client for the tests, so they speak to the connector as Ezkey does. */
final class ConnectorClient {

  static final String PNG_BASE64 =
      "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==";
  static final String SEND_ID = "3f0e2f6a-9b1c-4d7e-8a55-2c1d9e7b6a10";

  private final HttpClient http = HttpClient.newHttpClient();
  private final int port;

  ConnectorClient(int port) {
    this.port = port;
  }

  static String job(String locale, String recipient, String organisationName, String qr) {
    return """
        {"sendId":"%s","trigger":"operator","tenantRef":"42","template":"enrollment-qr",        "locale":"%s","recipient":"%s","organisationName":"%s",        "expectedServer":"auth.example.com","qrPngBase64":"%s"}"""
        .formatted(SEND_ID, locale, recipient, organisationName, qr);
  }

  HttpResponse<String> post(String token, String body) throws Exception {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v0/deliveries"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body));
    if (token != null) {
      builder.header("Authorization", "Bearer " + token);
    }
    return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  HttpResponse<String> health(String token) throws Exception {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v0/health")).GET();
    if (token != null) {
      builder.header("Authorization", "Bearer " + token);
    }
    return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }
}
