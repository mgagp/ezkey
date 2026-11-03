/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: ClientIpResolverTest
 * Description: Unit tests for ClientIpResolver trusted-proxy and header resolution.
 */

package org.ezkey.audit.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link ClientIpResolver}: trusted proxy CIDR behaviour and right-to-left
 * X-Forwarded-For resolution (#1140 / follow-ups).
 *
 * @since 2025
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ClientIpResolver tests")
class ClientIpResolverTest {

  @Mock private HttpServletRequest request;

  /**
   * Stubs {@code getHeaders("X-Forwarded-For")} with the given header values (declaration order).
   */
  private void stubXffHeaders(String... headerValues) {
    when(request.getHeaders("X-Forwarded-For"))
        .thenReturn(Collections.enumeration(List.of(headerValues)));
  }

  private void stubNoXffHeaders() {
    when(request.getHeaders("X-Forwarded-For")).thenReturn(Collections.emptyEnumeration());
  }

  @Nested
  @DisplayName("When trusted proxy list is null or empty")
  class EmptyOrNullTrustedList {

    @Test
    @DisplayName("returns only remoteAddr when list is null")
    void nullList_returnsRemoteAddrOnly() {
      when(request.getRemoteAddr()).thenReturn("203.0.113.50");
      lenient()
          .when(request.getHeaders("X-Forwarded-For"))
          .thenReturn(Collections.enumeration(List.of("192.168.1.1")));

      String result = ClientIpResolver.resolve(request, null);

      assertThat(result).isEqualTo("203.0.113.50");
    }

    @Test
    @DisplayName("returns only remoteAddr when list is empty")
    void emptyList_returnsRemoteAddrOnly() {
      when(request.getRemoteAddr()).thenReturn("203.0.113.50");
      lenient()
          .when(request.getHeaders("X-Forwarded-For"))
          .thenReturn(Collections.enumeration(List.of("192.168.1.1")));

      String result = ClientIpResolver.resolve(request, List.of());

      assertThat(result).isEqualTo("203.0.113.50");
    }
  }

  @Nested
  @DisplayName("When remoteAddr is not in any trusted CIDR")
  class RemoteAddrNotInTrustedCidr {

    @Test
    @DisplayName("returns remoteAddr and ignores all headers including forged XFF")
    void untrustedRemote_ignoresAllHeaders() {
      when(request.getRemoteAddr()).thenReturn("203.0.113.50");
      lenient()
          .when(request.getHeaders("X-Forwarded-For"))
          .thenReturn(Collections.enumeration(List.of("198.51.100.1")));
      lenient().when(request.getHeader("CF-Connecting-IP")).thenReturn("198.51.100.2");
      lenient().when(request.getHeader("X-Real-IP")).thenReturn("198.51.100.3");

      String result =
          ClientIpResolver.resolve(request, List.of("10.0.0.0/8", "172.16.0.0/12"), true);

      assertThat(result).isEqualTo("203.0.113.50");
    }
  }

  @Nested
  @DisplayName("When remoteAddr is in a trusted CIDR")
  class RemoteAddrInTrustedCidr {

    @Test
    @DisplayName("ignores leftmost forged XFF entry and keeps rightmost untrusted hop")
    void forgedLeftmostXff_ignored() {
      when(request.getRemoteAddr()).thenReturn("10.1.2.3");
      stubXffHeaders("198.51.100.99, 203.0.113.10");

      String result = ClientIpResolver.resolve(request, List.of("10.0.0.0/8"));

      assertThat(result).isEqualTo("203.0.113.10");
    }

    @Test
    @DisplayName("chain forged, real, proxy yields real (right-to-left skip trusted)")
    void forgedRealProxy_yieldsReal() {
      when(request.getRemoteAddr()).thenReturn("10.0.0.5");
      stubXffHeaders("198.51.100.1, 203.0.113.40, 10.0.0.9");

      String result = ClientIpResolver.resolve(request, List.of("10.0.0.0/8"));

      assertThat(result).isEqualTo("203.0.113.40");
    }

    @Test
    @DisplayName("joins multiple X-Forwarded-For header values in order before right-to-left walk")
    void multipleXffHeaders_joinedInOrder() {
      when(request.getRemoteAddr()).thenReturn("10.0.0.5");
      // Two header lines: forged+real, then trusted hop — same as one "forged, real, proxy" chain.
      stubXffHeaders("198.51.100.1, 203.0.113.40", "10.0.0.9");

      String result = ClientIpResolver.resolve(request, List.of("10.0.0.0/8"));

      assertThat(result).isEqualTo("203.0.113.40");
    }

    @Test
    @DisplayName("CF-Connecting-IP is ignored when Cloudflare edge setting is off")
    void cfConnectingIp_ignoredWithoutEdgeSetting() {
      when(request.getRemoteAddr()).thenReturn("172.16.0.5");
      lenient().when(request.getHeader("CF-Connecting-IP")).thenReturn("198.51.100.20");
      stubXffHeaders("203.0.113.55");

      String result = ClientIpResolver.resolve(request, List.of("172.16.0.0/12"), false);

      assertThat(result).isEqualTo("203.0.113.55");
    }

    @Test
    @DisplayName("CF-Connecting-IP is used when Cloudflare edge setting is on")
    void cfConnectingIp_usedWithEdgeSetting() {
      when(request.getRemoteAddr()).thenReturn("172.16.0.5");
      when(request.getHeader("CF-Connecting-IP")).thenReturn("198.51.100.20");
      lenient()
          .when(request.getHeaders("X-Forwarded-For"))
          .thenReturn(Collections.enumeration(List.of("203.0.113.55")));

      String result = ClientIpResolver.resolve(request, List.of("172.16.0.0/12"), true);

      assertThat(result).isEqualTo("198.51.100.20");
    }

    @Test
    @DisplayName("X-Real-IP is ignored even when CF and XFF are absent")
    void xRealIp_neverUsed() {
      when(request.getRemoteAddr()).thenReturn("192.168.1.1");
      stubNoXffHeaders();
      lenient().when(request.getHeader("X-Real-IP")).thenReturn("198.51.100.30");

      String result = ClientIpResolver.resolve(request, List.of("192.168.0.0/16"));

      assertThat(result).isEqualTo("192.168.1.1");
    }

    @Test
    @DisplayName("falls back to remoteAddr when XFF entry is malformed")
    void malformedXff_fallbackToRemoteAddr() {
      when(request.getRemoteAddr()).thenReturn("10.0.0.1");
      stubXffHeaders("not-an-ip, 203.0.113.1");

      String result = ClientIpResolver.resolve(request, List.of("10.0.0.0/8"));

      assertThat(result).isEqualTo("10.0.0.1");
    }

    @Test
    @DisplayName("falls back to remoteAddr when entire XFF chain is trusted")
    void entireChainTrusted_fallbackToRemoteAddr() {
      when(request.getRemoteAddr()).thenReturn("10.0.0.1");
      stubXffHeaders("10.0.0.8, 10.0.0.9");

      String result = ClientIpResolver.resolve(request, List.of("10.0.0.0/8"));

      assertThat(result).isEqualTo("10.0.0.1");
    }

    @Test
    @DisplayName("trims whitespace around XFF entries before validation and selection")
    void xffEntries_whitespaceTrimmed() {
      when(request.getRemoteAddr()).thenReturn("10.0.0.5");
      stubXffHeaders("  198.51.100.1  ,   203.0.113.77  ,  10.0.0.9  ");

      String result = ClientIpResolver.resolve(request, List.of("10.0.0.0/8"));

      assertThat(result).isEqualTo("203.0.113.77");
    }

    @Test
    @DisplayName("selects untrusted IPv6 hop when walking XFF right-to-left")
    void ipv6Xff_rightToLeft() {
      when(request.getRemoteAddr()).thenReturn("fd00::1");
      stubXffHeaders("2001:db8::99, 2001:db8::40, fd00::9");

      String result = ClientIpResolver.resolve(request, List.of("fd00::/8"));

      assertThat(result).isEqualTo("2001:db8::40");
    }

    @Test
    @DisplayName(
        "rejects ip:port XFF entry (fail-closed; not normalized) and falls back to remoteAddr")
    void ipPortForm_rejectedNotNormalized() {
      when(request.getRemoteAddr()).thenReturn("10.0.0.1");
      stubXffHeaders("203.0.113.40:8080, 10.0.0.9");

      String result = ClientIpResolver.resolve(request, List.of("10.0.0.0/8"));

      assertThat(result).isEqualTo("10.0.0.1");
    }
  }

  @Nested
  @DisplayName("isValidIp strict literal validation")
  class StrictIpValidation {

    @ParameterizedTest
    @ValueSource(
        strings = {"203.0.113.1", "2001:db8::1", "::1", "  198.51.100.10  ", "::ffff:203.0.113.1"})
    @DisplayName("accepts IPv4 and IPv6 literals (optional surrounding whitespace)")
    void acceptsIpLiterals(String candidate) {
      assertThat(ClientIpResolver.isValidIp(candidate)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(
        strings = {
          "10.0.0.0/8",
          "203.0.113.1/32",
          "*",
          "example.com",
          "203.0.113.1:8080",
          "[2001:db8::1]:8080",
          "not-an-ip",
          "0xcb.0.113.1",
          "3405803777",
          "203.0.113",
          "0313.0.0161.1",
          "fe80::1%eth0"
        })
    @DisplayName("rejects CIDR, wildcard, hostnames, ip:port, inet_aton shorthands, and zones")
    void rejectsNonLiterals(String candidate) {
      assertThat(ClientIpResolver.isValidIp(candidate)).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("rejects null, empty, and blank")
    void rejectsBlank(String candidate) {
      assertThat(ClientIpResolver.isValidIp(candidate)).isFalse();
    }
  }

  @Nested
  @DisplayName("Canonicalization and inet_aton / zone fail-closed (#1140)")
  class Canonicalization {

    @ParameterizedTest
    @CsvSource({"2001:0db8:0:0::1, 2001:db8::1", "::ffff:203.0.113.1, 203.0.113.1"})
    @DisplayName("canonical() compresses IPv6 and maps IPv4-mapped to IPv4")
    void canonical_forms(String input, String expected) {
      assertThat(ClientIpResolver.canonical(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(
        strings = {"0xcb.0.113.1", "3405803777", "203.0.113", "0313.0.0161.1", "fe80::1%eth0"})
    @DisplayName("inet_aton / zone XFF entries fall back to remoteAddr")
    void inetAtonAndZone_fallbackToRemoteAddr(String badHop) {
      when(request.getRemoteAddr()).thenReturn("10.0.0.1");
      stubXffHeaders(badHop + ", 10.0.0.9");

      String result = ClientIpResolver.resolve(request, List.of("10.0.0.0/8"));

      assertThat(result).isEqualTo("10.0.0.1");
    }

    @Test
    @DisplayName(
        "IPv4-mapped trusted hop ::ffff:172.28.0.5 is recognized as proxy after canonicalize")
    void ipv4MappedTrustedHop_recognizedAsProxy() {
      when(request.getRemoteAddr()).thenReturn("172.28.0.5");
      stubXffHeaders("198.51.100.1, 203.0.113.40, ::ffff:172.28.0.5");

      String result = ClientIpResolver.resolve(request, List.of("172.28.0.5/32"));

      assertThat(result).isEqualTo("203.0.113.40");
    }

    @Test
    @DisplayName("returned client hop is canonicalized (expanded IPv6)")
    void returnedHop_isCanonicalized() {
      when(request.getRemoteAddr()).thenReturn("fd00::1");
      stubXffHeaders("2001:0db8:0:0::40, fd00::9");

      String result = ClientIpResolver.resolve(request, List.of("fd00::/8"));

      assertThat(result).isEqualTo("2001:db8::40");
    }
  }

  @Nested
  @DisplayName("Edge cases")
  class EdgeCases {

    @Test
    @DisplayName("null request returns empty string")
    void nullRequest_returnsEmpty() {
      String result = ClientIpResolver.resolve(null, List.of("10.0.0.0/8"));

      assertThat(result).isEqualTo("");
    }

    @Test
    @DisplayName("null remoteAddr returns empty string when list empty")
    void nullRemoteAddr_returnsEmpty() {
      when(request.getRemoteAddr()).thenReturn(null);

      String result = ClientIpResolver.resolve(request, null);

      assertThat(result).isEqualTo("");
    }
  }
}
