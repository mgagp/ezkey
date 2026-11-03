/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AccessLinkRateLimitedCookieTest
 * Description: Rate-limited /t responses stay link-lost (no session cookie, no CSRF meta).
 */

package org.ezkey.demo.acme.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.List;
import org.ezkey.demo.acme.DemoAcmeApplication;
import org.ezkey.demo.acme.DemoAuthMessages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestTemplate;

/**
 * Rate-limit branch for access-link cookie hygiene: exhausting the IP-only {@code /t} budget must
 * still render link-lost login without minting a session cookie.
 *
 * @since 2026
 */
@SpringBootTest(
    classes = DemoAcmeApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    properties = {
      "ezkey.rate-limit.enabled=true",
      "ezkey.rate-limit.login.requests=1",
      "ezkey.rate-limit.login.window-minutes=5",
      "ezkey.access-codes.northwind.code=0123456789abcdef0123456789abcdef",
      "ezkey.access-codes.northwind.integration-key=ezkey_ikey_test",
      "ezkey.access-codes.northwind.secret-key=ezkey_skey_test",
      "ezkey.access-codes.northwind.label=Northwind Portal"
    })
class AccessLinkRateLimitedCookieTest {

  @LocalServerPort private int port;

  private RestTemplate restTemplate;
  private String baseUrl;

  @BeforeEach
  void setUp() {
    restTemplate = new RestTemplate();
    restTemplate.setRequestFactory(
        new org.springframework.http.client.SimpleClientHttpRequestFactory() {
          @Override
          protected void prepareConnection(java.net.HttpURLConnection connection, String httpMethod)
              throws java.io.IOException {
            super.prepareConnection(connection, httpMethod);
            connection.setInstanceFollowRedirects(false);
          }
        });
    baseUrl = "http://localhost:" + port;
  }

  @Test
  void rateLimitedUnknownAccessLinkDoesNotMintSessionCookie() {
    // First unknown burns the IP-only login budget (ceiling=1).
    restTemplate.exchange(
        URI.create(baseUrl + "/t/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"),
        HttpMethod.GET,
        HttpEntity.EMPTY,
        String.class);

    ResponseEntity<String> limited =
        restTemplate.exchange(
            URI.create(baseUrl + "/t/bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"),
            HttpMethod.GET,
            HttpEntity.EMPTY,
            String.class);

    assertThat(limited.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(limited.getBody()).contains(DemoAuthMessages.RATE_LIMIT_LOGIN);
    assertThat(limited.getBody()).doesNotContain("name=\"_csrf\"");

    List<String> setCookies = limited.getHeaders().get(HttpHeaders.SET_COOKIE);
    if (setCookies == null) {
      setCookies = List.of();
    }
    assertThat(setCookies).noneMatch(c -> c.startsWith("EZKEY_ACME_SESSION="));
    assertThat(setCookies).noneMatch(c -> c.startsWith("JSESSIONID="));
  }
}
