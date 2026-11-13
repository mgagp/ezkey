/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: RateLimitFilterPendingTest
 * Description: Unit tests for rate limiting on POST /api/v1/auth-attempts/pending.
 * The filter applies an IP ceiling and does not key on the body enrollmentId.
 */

package org.ezkey.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.OptionalLong;
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
 * Unit tests for {@link RateLimitFilter} applied to the pending endpoint with enrollment-id
 * strategy: the filter applies an IP ceiling only. The body enrollmentId is not a bucket key
 * (#1124).
 *
 * @since 2025
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RateLimitFilter pending endpoint tests (IP ceiling, not body enrollmentId)")
class RateLimitFilterPendingTest {

  private static final String PENDING_PATH = "/api/v1/auth-attempts/pending";

  @Mock private FilterChain filterChain;

  private RateLimitProperties properties;

  private RateLimitFilter filter;

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    properties = new RateLimitProperties();
    properties.setEnabled(true);
    RateLimitProperties.EndpointConfig pendingConfig = new RateLimitProperties.EndpointConfig();
    pendingConfig.setRequests(10);
    pendingConfig.setWindowMinutes(1);
    pendingConfig.setKeyStrategy("enrollment-id");
    properties.setPending(pendingConfig);
    // Respond not under test - use default
    filter = new RateLimitFilter(properties, new TrustedProxyProperties());
  }

  @Test
  @DisplayName("Different enrollment IDs get separate buckets - both allowed")
  void differentEnrollmentIdsGetSeparateBuckets() throws Exception {
    MockHttpServletRequest request1 = pendingRequest(1);
    MockHttpServletRequest request2 = pendingRequest(2);
    MockHttpServletResponse response1 = new MockHttpServletResponse();
    MockHttpServletResponse response2 = new MockHttpServletResponse();

    filter.doFilter(request1, response1, filterChain);
    filter.doFilter(request2, response2, filterChain);

    assertThat(response1.getStatus()).isEqualTo(200);
    assertThat(response2.getStatus()).isEqualTo(200);
    verify(filterChain, org.mockito.Mockito.times(2))
        .doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));
  }

  @Test
  @DisplayName("#1124: 11 fake pending calls do not 429 the device or debit the enrollment budget")
  void fakePendingCallsDoNotExhaustEnrollmentBudget() throws Exception {
    RateLimitProperties.EndpointConfig pendingConfig = new RateLimitProperties.EndpointConfig();
    pendingConfig.setRequests(10);
    pendingConfig.setWindowMinutes(1);
    pendingConfig.setKeyStrategy("enrollment-id");
    pendingConfig.setFailIpRequests(1000);
    pendingConfig.setFailIpWindowMinutes(1);
    properties.setPending(pendingConfig);
    AuthPendingRateLimitService pendingLimits = new AuthPendingRateLimitService(properties);
    filter =
        new RateLimitFilter(properties, new TrustedProxyProperties(), null, null, pendingLimits);

    for (int i = 0; i < 11; i++) {
      MockHttpServletRequest fake = pendingRequest(42);
      fake.setRemoteAddr("203.0.113.8");
      MockHttpServletResponse fakeResponse = new MockHttpServletResponse();
      filter.doFilter(fake, fakeResponse, filterChain);
      assertThat(fakeResponse.getStatus())
          .as("fake pending %s must not be rejected from the victim enrollment budget", i)
          .isEqualTo(200);
    }

    MockHttpServletRequest device = pendingRequest(42);
    device.setRemoteAddr("198.51.100.9");
    MockHttpServletResponse deviceResponse = new MockHttpServletResponse();
    filter.doFilter(device, deviceResponse, filterChain);

    assertThat(deviceResponse.getStatus()).isEqualTo(200);
    assertThat(pendingLimits.tryConsumeAfterOwnership(42))
        .as("proven device still has a full enrollment budget after the fakes")
        .isEmpty();
  }

  @Test
  @DisplayName("Pending fail-IP ceiling still 429s a repeated unproven caller")
  void unprovenPendingFromSameIpHitsFailIpCeiling() throws Exception {
    RateLimitProperties.EndpointConfig pendingConfig = new RateLimitProperties.EndpointConfig();
    pendingConfig.setRequests(10);
    pendingConfig.setWindowMinutes(1);
    pendingConfig.setKeyStrategy("enrollment-id");
    pendingConfig.setFailIpRequests(1);
    pendingConfig.setFailIpWindowMinutes(5);
    properties.setPending(pendingConfig);
    AuthPendingRateLimitService pendingLimits = new AuthPendingRateLimitService(properties);
    filter =
        new RateLimitFilter(properties, new TrustedProxyProperties(), null, null, pendingLimits);

    MockHttpServletRequest first = pendingRequest(42);
    first.setRemoteAddr("203.0.113.8");
    MockHttpServletResponse firstResponse = new MockHttpServletResponse();
    filter.doFilter(first, firstResponse, filterChain);
    assertThat(firstResponse.getStatus()).isEqualTo(200);

    MockHttpServletRequest second = pendingRequest(42);
    second.setRemoteAddr("203.0.113.8");
    MockHttpServletResponse secondResponse = new MockHttpServletResponse();
    filter.doFilter(second, secondResponse, filterChain);
    assertThat(secondResponse.getStatus()).isEqualTo(429);
    assertThat(Long.parseLong(secondResponse.getHeader("Retry-After"))).isBetween(1L, 5L * 60L);

    MockHttpServletRequest device = pendingRequest(42);
    device.setRemoteAddr("198.51.100.9");
    MockHttpServletResponse deviceResponse = new MockHttpServletResponse();
    filter.doFilter(device, deviceResponse, filterChain);
    assertThat(deviceResponse.getStatus()).isEqualTo(200);
    assertThat(pendingLimits.tryConsumeAfterOwnership(42)).isEqualTo(OptionalLong.empty());
  }

  @Test
  @DisplayName("Pending with enrollment-id strategy and empty body falls back to client IP")
  void pendingEmptyBodyFallsBackToClientIp() throws Exception {
    RateLimitProperties.EndpointConfig pendingConfig = new RateLimitProperties.EndpointConfig();
    pendingConfig.setRequests(1);
    pendingConfig.setWindowMinutes(5);
    pendingConfig.setKeyStrategy("enrollment-id");
    properties.setPending(pendingConfig);
    filter = new RateLimitFilter(properties, new TrustedProxyProperties());

    MockHttpServletRequest request1 = new MockHttpServletRequest("POST", PENDING_PATH);
    request1.setContentType("application/json");
    request1.setContent(new byte[0]);
    request1.setRemoteAddr("192.168.1.20");
    MockHttpServletResponse response1 = new MockHttpServletResponse();

    filter.doFilter(request1, response1, filterChain);

    assertThat(response1.getStatus()).isEqualTo(200);
    verify(filterChain).doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));

    MockHttpServletRequest request2 = new MockHttpServletRequest("POST", PENDING_PATH);
    request2.setContentType("application/json");
    request2.setContent(new byte[0]);
    request2.setRemoteAddr("192.168.1.20");
    MockHttpServletResponse response2 = new MockHttpServletResponse();
    filter.doFilter(request2, response2, filterChain);

    assertThat(response2.getStatus()).isEqualTo(429);
  }

  @Test
  @DisplayName("When trusted proxy list is set, X-Forwarded-For is used for rate limit key")
  void trustedProxySet_xffUsedForRateLimitKey() throws Exception {
    TrustedProxyProperties trusted = new TrustedProxyProperties();
    trusted.setCidrs(List.of("10.0.0.0/8"));
    RateLimitProperties.EndpointConfig pendingConfig = new RateLimitProperties.EndpointConfig();
    pendingConfig.setRequests(1);
    pendingConfig.setWindowMinutes(5);
    pendingConfig.setKeyStrategy("enrollment-id");
    properties.setPending(pendingConfig);
    filter = new RateLimitFilter(properties, trusted);

    MockHttpServletRequest request1 = new MockHttpServletRequest("POST", PENDING_PATH);
    request1.setContentType("application/json");
    request1.setContent(new byte[0]);
    request1.setRemoteAddr("10.1.2.3");
    request1.addHeader("X-Forwarded-For", "198.51.100.10");
    MockHttpServletResponse response1 = new MockHttpServletResponse();
    filter.doFilter(request1, response1, filterChain);
    assertThat(response1.getStatus()).isEqualTo(200);

    MockHttpServletRequest request2 = new MockHttpServletRequest("POST", PENDING_PATH);
    request2.setContentType("application/json");
    request2.setContent(new byte[0]);
    request2.setRemoteAddr("10.1.2.4");
    request2.addHeader("X-Forwarded-For", "198.51.100.10");
    MockHttpServletResponse response2 = new MockHttpServletResponse();
    filter.doFilter(request2, response2, filterChain);
    assertThat(response2.getStatus()).isEqualTo(429);
  }

  @Test
  @DisplayName(
      "Forged X-Forwarded-For with empty trusted proxies does not change rate limit bucket")
  void forgedXffWithEmptyTrustedProxies_sameBucketAsRemoteAddr() throws Exception {
    RateLimitProperties.EndpointConfig pendingConfig = new RateLimitProperties.EndpointConfig();
    pendingConfig.setRequests(1);
    pendingConfig.setWindowMinutes(5);
    pendingConfig.setKeyStrategy("enrollment-id");
    properties.setPending(pendingConfig);
    filter = new RateLimitFilter(properties, new TrustedProxyProperties());

    MockHttpServletRequest request1 = new MockHttpServletRequest("POST", PENDING_PATH);
    request1.setContentType("application/json");
    request1.setContent(new byte[0]);
    request1.setRemoteAddr("203.0.113.50");
    MockHttpServletResponse response1 = new MockHttpServletResponse();
    filter.doFilter(request1, response1, filterChain);
    assertThat(response1.getStatus()).isEqualTo(200);

    MockHttpServletRequest request2 = new MockHttpServletRequest("POST", PENDING_PATH);
    request2.setContentType("application/json");
    request2.setContent(new byte[0]);
    request2.setRemoteAddr("203.0.113.50");
    request2.addHeader("X-Forwarded-For", "192.168.1.1");
    MockHttpServletResponse response2 = new MockHttpServletResponse();
    filter.doFilter(request2, response2, filterChain);
    assertThat(response2.getStatus()).isEqualTo(429);
  }

  @Test
  @DisplayName("Pending with invalid JSON body falls back to client IP")
  void pendingInvalidJsonFallsBackToClientIp() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", PENDING_PATH);
    request.setContentType("application/json");
    request.setContent("not valid json".getBytes(StandardCharsets.UTF_8));
    request.setRemoteAddr("10.0.0.10");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, filterChain);

    assertThat(response.getStatus()).isEqualTo(200);
    verify(filterChain).doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));
  }

  private MockHttpServletRequest pendingRequest(int enrollmentId) throws Exception {
    String body = objectMapper.writeValueAsString(new PendingBody(enrollmentId));
    MockHttpServletRequest request = new MockHttpServletRequest("POST", PENDING_PATH);
    request.setContentType("application/json");
    request.setContent(body.getBytes(StandardCharsets.UTF_8));
    request.setRemoteAddr("127.0.0.1");
    return request;
  }

  /** Minimal body for pending request; only enrollmentId is needed for rate limit key. */
  @SuppressWarnings("unused")
  private static final class PendingBody {
    private final int enrollmentId;

    PendingBody(int enrollmentId) {
      this.enrollmentId = enrollmentId;
    }

    public int getEnrollmentId() {
      return enrollmentId;
    }
  }
}
