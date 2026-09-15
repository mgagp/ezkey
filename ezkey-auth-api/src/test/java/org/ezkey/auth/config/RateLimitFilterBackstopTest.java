/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: RateLimitFilterBackstopTest
 * Description: End-to-end filter tests for the unkeyed per-instance rate-limit backstop.
 */

package org.ezkey.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * Filter-chain coverage of the process-wide backstop: rotating client IPs still hit one unkeyed
 * bucket. Live docker-test disables rate limits; prod-safe per-IP caps fire before this backstop,
 * so this is the appropriate end-to-end surface.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RateLimitFilter per-instance backstop")
class RateLimitFilterBackstopTest {

  private static final String VERIFY_PATH = "/api/v1/enrollments/verify";
  private static final String BIND_PATH = "/api/v1/enrollments/bind";
  private static final String INSTANCE_INFO_PATH = "/api/v1/enrollments/instance-info";
  private static final String PENDING_PATH = "/api/v1/auth-attempts/pending";

  @Mock private FilterChain filterChain;

  private RateLimitProperties properties;
  private SimpleMeterRegistry meterRegistry;
  private RateLimitFilter filter;

  @BeforeEach
  void setUp() {
    properties = new RateLimitProperties();
    properties.setEnabled(true);
    properties.getVerify().setRequests(100);
    properties.getVerify().setWindowMinutes(5);
    properties.getBind().setRequests(100);
    properties.getBind().setWindowMinutes(5);
    properties.getPending().setRequests(100);
    properties.getPending().setWindowMinutes(5);
    properties.getBackstop().setEnabled(true);
    properties.getBackstop().getVerify().setRequests(3);
    properties.getBackstop().getVerify().setWindowMinutes(5);
    properties.getBackstop().getBind().setRequests(2);
    properties.getBackstop().getBind().setWindowMinutes(5);
    meterRegistry = new SimpleMeterRegistry();
    filter =
        new RateLimitFilter(
            properties, new TrustedProxyProperties(), new ObjectMapper(), meterRegistry);
  }

  @Test
  @DisplayName("Rotating IPs on verify trip the unkeyed backstop after the process budget")
  void rotatingIpsOnVerify_tripBackstop() throws Exception {
    assertThat(postFromIp(VERIFY_PATH, "198.51.100.1").getStatus()).isEqualTo(200);
    assertThat(postFromIp(VERIFY_PATH, "198.51.100.2").getStatus()).isEqualTo(200);
    assertThat(postFromIp(VERIFY_PATH, "198.51.100.3").getStatus()).isEqualTo(200);

    MockHttpServletResponse blocked = postFromIp(VERIFY_PATH, "198.51.100.4");
    assertThat(blocked.getStatus()).isEqualTo(429);
    assertThat(blocked.getHeader("Retry-After")).isEqualTo("300");
    assertThat(
            meterRegistry
                .counter("ezkey.rate_limit.backstop.rejected", "endpoint", "verify")
                .count())
        .isEqualTo(1);
    verify(filterChain, times(3))
        .doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));
  }

  @Test
  @DisplayName("Bind and enrolled instance-info share the bind backstop bucket")
  void bindAndInstanceInfo_shareBindBackstop() throws Exception {
    assertThat(postFromIp(BIND_PATH, "203.0.113.1").getStatus()).isEqualTo(200);
    assertThat(postFromIp(INSTANCE_INFO_PATH, "203.0.113.2").getStatus()).isEqualTo(200);

    MockHttpServletResponse blocked = postFromIp(BIND_PATH, "203.0.113.3");
    assertThat(blocked.getStatus()).isEqualTo(429);
    assertThat(
            meterRegistry.counter("ezkey.rate_limit.backstop.rejected", "endpoint", "bind").count())
        .isEqualTo(1);
  }

  @Test
  @DisplayName("Pending (target-id keyed) does not consume the verify backstop")
  void pending_doesNotConsumeVerifyBackstop() throws Exception {
    assertThat(postFromIp(VERIFY_PATH, "192.0.2.1").getStatus()).isEqualTo(200);
    assertThat(postFromIp(VERIFY_PATH, "192.0.2.2").getStatus()).isEqualTo(200);
    assertThat(postFromIp(VERIFY_PATH, "192.0.2.3").getStatus()).isEqualTo(200);

    MockHttpServletResponse pending = postFromIp(PENDING_PATH, "192.0.2.4");
    assertThat(pending.getStatus()).isEqualTo(200);

    MockHttpServletResponse blocked = postFromIp(VERIFY_PATH, "192.0.2.5");
    assertThat(blocked.getStatus()).isEqualTo(429);
  }

  @Test
  @DisplayName("Disabled backstop lets rotating IPs through when keyed budgets allow")
  void disabledBackstop_allowsRotatingIps() throws Exception {
    properties.getBackstop().setEnabled(false);
    filter =
        new RateLimitFilter(
            properties, new TrustedProxyProperties(), new ObjectMapper(), meterRegistry);

    for (int i = 1; i <= 4; i++) {
      assertThat(postFromIp(VERIFY_PATH, "198.51.100." + i).getStatus()).isEqualTo(200);
    }
    verify(filterChain, times(4))
        .doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));
  }

  private MockHttpServletResponse postFromIp(String path, String ip) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
    request.setRemoteAddr(ip);
    request.setContentType("application/json");
    request.setContent("{}".getBytes());
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, filterChain);
    return response;
  }
}
