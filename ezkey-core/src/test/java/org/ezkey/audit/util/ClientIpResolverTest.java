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
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link ClientIpResolver}: trusted proxy CIDR behaviour and right-to-left
 * X-Forwarded-For resolution (#1140).
 *
 * @since 2025
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ClientIpResolver tests")
class ClientIpResolverTest {

  @Mock private HttpServletRequest request;

  @Nested
  @DisplayName("When trusted proxy list is null or empty")
  class EmptyOrNullTrustedList {

    @Test
    @DisplayName("returns only remoteAddr when list is null")
    void nullList_returnsRemoteAddrOnly() {
      when(request.getRemoteAddr()).thenReturn("203.0.113.50");
      lenient().when(request.getHeader("X-Forwarded-For")).thenReturn("192.168.1.1");

      String result = ClientIpResolver.resolve(request, null);

      assertThat(result).isEqualTo("203.0.113.50");
    }

    @Test
    @DisplayName("returns only remoteAddr when list is empty")
    void emptyList_returnsRemoteAddrOnly() {
      when(request.getRemoteAddr()).thenReturn("203.0.113.50");
      lenient().when(request.getHeader("X-Forwarded-For")).thenReturn("192.168.1.1");

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
      lenient().when(request.getHeader("X-Forwarded-For")).thenReturn("198.51.100.1");
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
      when(request.getHeader("X-Forwarded-For")).thenReturn("198.51.100.99, 203.0.113.10");

      String result = ClientIpResolver.resolve(request, List.of("10.0.0.0/8"));

      // Rightmost untrusted is 203.0.113.10; leftmost forged 198.51.100.99 is never chosen.
      assertThat(result).isEqualTo("203.0.113.10");
    }

    @Test
    @DisplayName("chain forged, real, proxy yields real (right-to-left skip trusted)")
    void forgedRealProxy_yieldsReal() {
      when(request.getRemoteAddr()).thenReturn("10.0.0.5");
      // leftmost forged, then real client, then trusted proxy hop
      when(request.getHeader("X-Forwarded-For")).thenReturn("198.51.100.1, 203.0.113.40, 10.0.0.9");

      String result = ClientIpResolver.resolve(request, List.of("10.0.0.0/8"));

      assertThat(result).isEqualTo("203.0.113.40");
    }

    @Test
    @DisplayName("CF-Connecting-IP is ignored when Cloudflare edge setting is off")
    void cfConnectingIp_ignoredWithoutEdgeSetting() {
      when(request.getRemoteAddr()).thenReturn("172.16.0.5");
      // CF header must not be read when the edge setting is off (strict stubbing).
      lenient().when(request.getHeader("CF-Connecting-IP")).thenReturn("198.51.100.20");
      when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.55");

      String result = ClientIpResolver.resolve(request, List.of("172.16.0.0/12"), false);

      assertThat(result).isEqualTo("203.0.113.55");
    }

    @Test
    @DisplayName("CF-Connecting-IP is used when Cloudflare edge setting is on")
    void cfConnectingIp_usedWithEdgeSetting() {
      when(request.getRemoteAddr()).thenReturn("172.16.0.5");
      when(request.getHeader("CF-Connecting-IP")).thenReturn("198.51.100.20");
      lenient().when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.55");

      String result = ClientIpResolver.resolve(request, List.of("172.16.0.0/12"), true);

      assertThat(result).isEqualTo("198.51.100.20");
    }

    @Test
    @DisplayName("X-Real-IP is ignored even when CF and XFF are absent")
    void xRealIp_neverUsed() {
      when(request.getRemoteAddr()).thenReturn("192.168.1.1");
      when(request.getHeader("X-Forwarded-For")).thenReturn(null);
      // Resolver never reads X-Real-IP; stub stays lenient so a regression that reads it fails
      // the assertion (remoteAddr) rather than UnnecessaryStubbing.
      lenient().when(request.getHeader("X-Real-IP")).thenReturn("198.51.100.30");

      String result = ClientIpResolver.resolve(request, List.of("192.168.0.0/16"));

      assertThat(result).isEqualTo("192.168.1.1");
    }

    @Test
    @DisplayName("falls back to remoteAddr when XFF entry is malformed")
    void malformedXff_fallbackToRemoteAddr() {
      when(request.getRemoteAddr()).thenReturn("10.0.0.1");
      when(request.getHeader("X-Forwarded-For")).thenReturn("not-an-ip, 203.0.113.1");

      String result = ClientIpResolver.resolve(request, List.of("10.0.0.0/8"));

      assertThat(result).isEqualTo("10.0.0.1");
    }

    @Test
    @DisplayName("falls back to remoteAddr when entire XFF chain is trusted")
    void entireChainTrusted_fallbackToRemoteAddr() {
      when(request.getRemoteAddr()).thenReturn("10.0.0.1");
      when(request.getHeader("X-Forwarded-For")).thenReturn("10.0.0.8, 10.0.0.9");

      String result = ClientIpResolver.resolve(request, List.of("10.0.0.0/8"));

      assertThat(result).isEqualTo("10.0.0.1");
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
