/*
 * Ezkey - Open Source MFA/Passkey Alternative
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
 * Unit tests for {@link ClientIpResolver}: trusted proxy CIDR behaviour and header resolution order
 * (CF-Connecting-IP, X-Forwarded-For, X-Real-IP).
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
    @DisplayName("returns remoteAddr and ignores forged X-Forwarded-For")
    void forgedXff_ignored_returnsRemoteAddr() {
      when(request.getRemoteAddr()).thenReturn("203.0.113.50");
      lenient().when(request.getHeader("X-Forwarded-For")).thenReturn("192.168.1.1");

      String result = ClientIpResolver.resolve(request, List.of("10.0.0.0/8", "172.16.0.0/12"));

      assertThat(result).isEqualTo("203.0.113.50");
    }
  }

  @Nested
  @DisplayName("When remoteAddr is in a trusted CIDR")
  class RemoteAddrInTrustedCidr {

    @Test
    @DisplayName("returns first X-Forwarded-For IP when set")
    void xffSet_returnsFirstXffIp() {
      when(request.getRemoteAddr()).thenReturn("10.1.2.3");
      lenient().when(request.getHeader("CF-Connecting-IP")).thenReturn(null);
      when(request.getHeader("X-Forwarded-For")).thenReturn("198.51.100.10, 10.0.0.1");
      lenient().when(request.getHeader("X-Real-IP")).thenReturn(null);

      String result = ClientIpResolver.resolve(request, List.of("10.0.0.0/8"));

      assertThat(result).isEqualTo("198.51.100.10");
    }

    @Test
    @DisplayName("returns CF-Connecting-IP when set and from trusted proxy")
    void cfConnectingIpSet_returnsCfConnectingIp() {
      when(request.getRemoteAddr()).thenReturn("172.16.0.5");
      when(request.getHeader("CF-Connecting-IP")).thenReturn("198.51.100.20");
      lenient().when(request.getHeader("X-Forwarded-For")).thenReturn("198.51.100.10");

      String result = ClientIpResolver.resolve(request, List.of("172.16.0.0/12"));

      assertThat(result).isEqualTo("198.51.100.20");
    }

    @Test
    @DisplayName("returns X-Real-IP when CF and XFF absent")
    void xRealIpUsedWhenOthersAbsent() {
      when(request.getRemoteAddr()).thenReturn("192.168.1.1");
      when(request.getHeader("CF-Connecting-IP")).thenReturn(null);
      when(request.getHeader("X-Forwarded-For")).thenReturn(null);
      when(request.getHeader("X-Real-IP")).thenReturn("198.51.100.30");

      String result = ClientIpResolver.resolve(request, List.of("192.168.0.0/16"));

      assertThat(result).isEqualTo("198.51.100.30");
    }

    @Test
    @DisplayName("falls back to remoteAddr when no valid header IP")
    void noValidHeader_fallbackToRemoteAddr() {
      when(request.getRemoteAddr()).thenReturn("10.0.0.1");
      when(request.getHeader("CF-Connecting-IP")).thenReturn(null);
      when(request.getHeader("X-Forwarded-For")).thenReturn("not-an-ip");
      when(request.getHeader("X-Real-IP")).thenReturn(null);

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
