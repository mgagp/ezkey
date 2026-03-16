/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AdminRateLimitFilterTest
 * Description: Unit tests for AdminRateLimitFilter including trusted-proxy client IP resolution.
 */

package org.ezkey.admin.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.ezkey.admin.config.AdminRateLimitProperties;
import org.ezkey.admin.config.TrustedProxyProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Unit tests for {@link AdminRateLimitFilter}: login rate limiting and that forged X-Forwarded-For
 * does not change the rate limit bucket when trusted proxies are empty.
 *
 * @since 2025
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminRateLimitFilter tests")
class AdminRateLimitFilterTest {

  private static final String LOGIN_PATH = "/api/v1/admin/auth/login";

  @Mock private FilterChain filterChain;

  private AdminRateLimitProperties properties;
  private TrustedProxyProperties trustedProxyProperties;
  private SimpleMeterRegistry meterRegistry;
  private AdminRateLimitFilter filter;

  @BeforeEach
  void setUp() {
    properties = new AdminRateLimitProperties();
    properties.setEnabled(true);
    AdminRateLimitProperties.LoginConfig login = new AdminRateLimitProperties.LoginConfig();
    login.setRequests(1);
    login.setWindowMinutes(5);
    properties.setLogin(login);

    trustedProxyProperties = new TrustedProxyProperties();
    meterRegistry = new SimpleMeterRegistry();
    filter = new AdminRateLimitFilter(properties, trustedProxyProperties, meterRegistry);
  }

  @Test
  @DisplayName(
      "Forged X-Forwarded-For with empty trusted proxies does not change rate limit bucket")
  void forgedXffWithEmptyTrustedProxies_sameBucketAsRemoteAddr() throws Exception {
    MockHttpServletRequest request1 = new MockHttpServletRequest("POST", LOGIN_PATH);
    request1.setRemoteAddr("203.0.113.50");
    MockHttpServletResponse response1 = new MockHttpServletResponse();
    filter.doFilter(request1, response1, filterChain);
    assertThat(response1.getStatus()).isEqualTo(HttpStatus.OK.value());
    verify(filterChain).doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));

    MockHttpServletRequest request2 = new MockHttpServletRequest("POST", LOGIN_PATH);
    request2.setRemoteAddr("203.0.113.50");
    request2.addHeader("X-Forwarded-For", "192.168.1.1");
    MockHttpServletResponse response2 = new MockHttpServletResponse();
    filter.doFilter(request2, response2, filterChain);
    assertThat(response2.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    assertThat(response2.getHeader("Retry-After")).isNotNull();
  }
}
