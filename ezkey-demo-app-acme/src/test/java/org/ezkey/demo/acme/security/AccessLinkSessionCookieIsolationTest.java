/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AccessLinkSessionCookieIsolationTest
 * Description: Access-link CSRF survives sibling localhost JSESSIONID overwrite (#799).
 */

package org.ezkey.demo.acme.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.HttpCookie;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.ezkey.demo.acme.DemoAcmeApplication;
import org.ezkey.demo.acme.web.LinkEntryMarker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

/**
 * Guards Acme session/CSRF isolation by cookie <em>name</em> after an access-link activation.
 *
 * <p>This is not a browser-level reproduction of host-scoped cookie collision across two ports. It
 * proves that Acme emits {@code EZKEY_ACME_SESSION} (not {@code JSESSIONID}) and that a sibling
 * {@code JSESSIONID} cookie on the same request does not invalidate Acme CSRF on {@code POST
 * /login}. The real multi-port browser jar behaviour is why distinct names are required (#799).
 *
 * <p>Uses a real HTTP port (not MockMvc) so {@code Set-Cookie} names are observable over HTTP.
 *
 * @since 2026
 */
@SpringBootTest(
    classes = DemoAcmeApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    properties = {
      "ezkey.rate-limit.enabled=false",
      "ezkey.access-codes.northwind.code=0123456789abcdef0123456789abcdef",
      "ezkey.access-codes.northwind.integration-key=ezkey_ikey_test",
      "ezkey.access-codes.northwind.secret-key=ezkey_skey_test",
      "ezkey.access-codes.northwind.label=Northwind Portal"
    })
class AccessLinkSessionCookieIsolationTest {

  private static final String ACCESS_CODE = "0123456789abcdef0123456789abcdef";
  private static final Pattern CSRF_META =
      Pattern.compile("name=\"_csrf\"\\s+content=\"([^\"]+)\"");
  private static final Pattern CSRF_INPUT = Pattern.compile("name=\"_csrf\"\\s+value=\"([^\"]+)\"");

  @LocalServerPort private int port;

  @Autowired private Environment environment;

  private RestTemplate restTemplate;
  private String baseUrl;

  @BeforeEach
  void setUp() {
    restTemplate = new RestTemplate();
    // Follow redirects manually so we can inspect Set-Cookie on /t/{code}.
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
  void configuresDistinctSessionCookieName() {
    assertThat(environment.getProperty("server.servlet.session.cookie.name"))
        .isEqualTo("EZKEY_ACME_SESSION");
  }

  @Test
  void accessLinkUsesDistinctSessionCookieName() {
    ResponseEntity<String> activate =
        restTemplate.exchange(
            URI.create(baseUrl + "/t/" + ACCESS_CODE),
            HttpMethod.GET,
            HttpEntity.EMPTY,
            String.class);

    assertThat(activate.getStatusCode().is3xxRedirection()).isTrue();
    assertThat(activate.getHeaders().getLocation()).isNotNull();
    assertThat(activate.getHeaders().getLocation().getPath()).isEqualTo("/login");

    List<String> setCookies = setCookieHeaders(activate);
    assertThat(setCookies)
        .as("Acme must emit EZKEY_ACME_SESSION, not shared JSESSIONID (#799)")
        .anyMatch(c -> c.startsWith("EZKEY_ACME_SESSION="));
    assertThat(setCookies).noneMatch(c -> c.startsWith("JSESSIONID="));
  }

  @Test
  void accessLinkUsernameSubmitSurvivesSiblingJsessionIdOverwrite() {
    ResponseEntity<String> activate =
        restTemplate.exchange(
            URI.create(baseUrl + "/t/" + ACCESS_CODE),
            HttpMethod.GET,
            HttpEntity.EMPTY,
            String.class);
    assertThat(activate.getStatusCode().is3xxRedirection()).isTrue();

    String acmeSession = firstCookieValue(activate, "EZKEY_ACME_SESSION");
    assertThat(acmeSession).isNotBlank();

    HttpHeaders loginHeaders = new HttpHeaders();
    loginHeaders.add(HttpHeaders.COOKIE, "EZKEY_ACME_SESSION=" + acmeSession);
    ResponseEntity<String> loginPage =
        restTemplate.exchange(
            URI.create(baseUrl + "/login"),
            HttpMethod.GET,
            new HttpEntity<>(loginHeaders),
            String.class);
    assertThat(loginPage.getStatusCode().is2xxSuccessful()).isTrue();
    String csrf = extractCsrf(loginPage.getBody());
    assertThat(csrf).isNotBlank();

    // Refresh Acme cookie if login GET rotated it.
    String sessionForPost = firstCookieValue(loginPage, "EZKEY_ACME_SESSION");
    if (sessionForPost == null) {
      sessionForPost = acmeSession;
    }

    // Simulate the pre-fix browser jar: demo-device still sets JSESSIONID while Acme keeps
    // EZKEY_ACME_SESSION. Overwriting JSESSIONID must not clear Acme CSRF.
    HttpHeaders postHeaders = new HttpHeaders();
    postHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
    postHeaders.add(
        HttpHeaders.COOKIE,
        "EZKEY_ACME_SESSION="
            + sessionForPost
            + "; JSESSIONID=demo-device-overwrote-shared-cookie");

    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("username", "alice");
    form.add("_csrf", csrf);
    form.add(LinkEntryMarker.PARAM, LinkEntryMarker.VALUE);

    ResponseEntity<String> loginPost =
        restTemplate.exchange(
            URI.create(baseUrl + "/login"),
            HttpMethod.POST,
            new HttpEntity<>(form, postHeaders),
            String.class);

    assertThat(loginPost.getStatusCode().is3xxRedirection()).isTrue();
    URI location = loginPost.getHeaders().getLocation();
    assertThat(location).isNotNull();
    assertThat(location.toString())
        .as("CSRF/session must survive sibling JSESSIONID overwrite (#799)")
        .doesNotContain("sessionexpired");
  }

  private static List<String> setCookieHeaders(ResponseEntity<?> response) {
    List<String> headers = response.getHeaders().get(HttpHeaders.SET_COOKIE);
    return headers != null ? headers : List.of();
  }

  private static String firstCookieValue(ResponseEntity<?> response, String name) {
    for (String header : setCookieHeaders(response)) {
      List<HttpCookie> parsed = new ArrayList<>(HttpCookie.parse(header));
      for (HttpCookie cookie : parsed) {
        if (name.equals(cookie.getName())) {
          return cookie.getValue();
        }
      }
    }
    return null;
  }

  private static String extractCsrf(String html) {
    if (html == null) {
      return null;
    }
    Matcher input = CSRF_INPUT.matcher(html);
    if (input.find()) {
      return input.group(1);
    }
    Matcher meta = CSRF_META.matcher(html);
    if (meta.find()) {
      return meta.group(1);
    }
    return null;
  }
}
