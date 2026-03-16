/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: RateLimitFilterRespondTest
 * Description: Unit tests for rate limiting on POST /api/v1/auth-attempts/respond.
 */

package org.ezkey.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Unit tests for {@link RateLimitFilter} applied to the respond endpoint: key derived from body
 * authAttemptId, fallback to client IP, and 429 when limit exceeded.
 *
 * @since 2025
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RateLimitFilter respond endpoint tests")
class RateLimitFilterRespondTest {

  private static final String RESPOND_PATH = "/api/v1/auth-attempts/respond";

  @Mock private FilterChain filterChain;

  private RateLimitProperties properties;

  private RateLimitFilter filter;

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    properties = new RateLimitProperties();
    properties.setEnabled(true);
    // Respond: 1 request per 5 min, key by auth-attempt-id
    RateLimitProperties.EndpointConfig respondConfig = new RateLimitProperties.EndpointConfig();
    respondConfig.setRequests(1);
    respondConfig.setWindowMinutes(5);
    respondConfig.setKeyStrategy("auth-attempt-id");
    properties.setRespond(respondConfig);
    filter =
        new RateLimitFilter(
            properties, new org.ezkey.auth.config.TrustedProxyProperties(), objectMapper);
  }

  @Test
  @DisplayName("First respond request for authAttemptId is allowed and chain is invoked")
  void firstRespondRequestAllowed() throws Exception {
    MockHttpServletRequest request = respondRequest(456, "sig", true);
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, filterChain);

    assertThat(response.getStatus()).isEqualTo(200);
    verify(filterChain).doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));
  }

  @Test
  @DisplayName("Second respond request for same authAttemptId within window returns 429")
  void secondRespondRequestSameAuthAttemptIdReturns429() throws Exception {
    MockHttpServletRequest request1 = respondRequest(789, "sig1", true);
    MockHttpServletRequest request2 = respondRequest(789, "sig2", true);
    MockHttpServletResponse response1 = new MockHttpServletResponse();
    MockHttpServletResponse response2 = new MockHttpServletResponse();

    filter.doFilter(request1, response1, filterChain);
    filter.doFilter(request2, response2, filterChain);

    assertThat(response1.getStatus()).isEqualTo(200);
    assertThat(response2.getStatus()).isEqualTo(429);
    assertThat(response2.getHeader("Retry-After")).isNotNull();
    verify(filterChain).doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));
  }

  @Test
  @DisplayName("Different authAttemptIds get separate buckets - both allowed")
  void differentAuthAttemptIdsBothAllowed() throws Exception {
    MockHttpServletRequest request1 = respondRequest(100, "sig1", true);
    MockHttpServletRequest request2 = respondRequest(200, "sig2", true);
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
  @DisplayName("Respond with empty body falls back to client IP for bucket key")
  void respondEmptyBodyFallsBackToClientIp() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", RESPOND_PATH);
    request.setContentType("application/json");
    request.setContent(new byte[0]);
    request.setRemoteAddr("192.168.1.10");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, filterChain);

    assertThat(response.getStatus()).isEqualTo(200);
    verify(filterChain).doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));
    // Second request from same IP (same empty body scenario) should hit rate limit
    MockHttpServletRequest request2 = new MockHttpServletRequest("POST", RESPOND_PATH);
    request2.setContentType("application/json");
    request2.setContent(new byte[0]);
    request2.setRemoteAddr("192.168.1.10");
    MockHttpServletResponse response2 = new MockHttpServletResponse();
    filter.doFilter(request2, response2, filterChain);
    assertThat(response2.getStatus()).isEqualTo(429);
  }

  @Test
  @DisplayName("Respond with invalid JSON body falls back to client IP")
  void respondInvalidJsonFallsBackToClientIp() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", RESPOND_PATH);
    request.setContentType("application/json");
    request.setContent("not valid json".getBytes(StandardCharsets.UTF_8));
    request.setRemoteAddr("10.0.0.5");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, filterChain);

    assertThat(response.getStatus()).isEqualTo(200);
    verify(filterChain).doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));
  }

  @Test
  @DisplayName("Non-respond path is not rate limited by respond config")
  void pendingPathNotAffectedByRespondLimit() throws Exception {
    MockHttpServletRequest respondReq = respondRequest(1, "sig", true);
    MockHttpServletRequest pendingReq =
        new MockHttpServletRequest("POST", "/api/v1/auth-attempts/pending");
    pendingReq.setContentType("application/json");
    pendingReq.setRemoteAddr("127.0.0.1");
    // Pending default is 10 requests - use different endpoint so respond limit doesn't apply
    MockHttpServletResponse resp1 = new MockHttpServletResponse();
    MockHttpServletResponse resp2 = new MockHttpServletResponse();

    filter.doFilter(respondReq, resp1, filterChain);
    filter.doFilter(pendingReq, resp2, filterChain);

    assertThat(resp1.getStatus()).isEqualTo(200);
    assertThat(resp2.getStatus()).isEqualTo(200);
    verify(filterChain, org.mockito.Mockito.times(2))
        .doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));
  }

  private MockHttpServletRequest respondRequest(
      int authAttemptId, String signature, boolean accepted) throws Exception {
    String body =
        new ObjectMapper()
            .writeValueAsString(
                new org.ezkey.authattempt.dto.AuthAttemptRespondRequestDto(
                    authAttemptId, signature, null, accepted));
    MockHttpServletRequest request = new MockHttpServletRequest("POST", RESPOND_PATH);
    request.setContentType("application/json");
    request.setContent(body.getBytes(StandardCharsets.UTF_8));
    request.setRemoteAddr("192.168.1.1");
    return request;
  }
}
