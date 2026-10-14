/*
 * Ezkey - Open Source Cryptographic MFA Platform
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import org.ezkey.admin.config.AdminRateLimitProperties;
import org.ezkey.admin.config.TrustedProxyProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Unit tests for {@link AdminRateLimitFilter}: separate login / passwordless-wait buckets, greedy
 * refill Retry-After, and trusted-proxy client IP resolution (#831).
 *
 * @since 2025
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminRateLimitFilter tests")
class AdminRateLimitFilterTest {

  private static final String LOGIN_PATH = "/api/v1/admin/auth/login";
  private static final String PASSWORDLESS_WAIT_PATH = "/api/v1/admin/auth/passwordless-wait";
  private static final String PASSWORDLESS_CANCEL_PATH = "/api/v1/admin/auth/passwordless-cancel";

  @Mock private FilterChain filterChain;

  private AdminRateLimitProperties properties;
  private TrustedProxyProperties trustedProxyProperties;
  private SimpleMeterRegistry meterRegistry;
  private AdminRateLimitFilter filter;
  private ListAppender<ILoggingEvent> logAppender;
  private Logger filterLogger;
  private Level previousLogLevel;

  @BeforeEach
  void setUp() {
    properties = new AdminRateLimitProperties();
    properties.setEnabled(true);
    AdminRateLimitProperties.LoginConfig login = new AdminRateLimitProperties.LoginConfig();
    login.setRequests(1);
    login.setWindowMinutes(5);
    properties.setLogin(login);
    AdminRateLimitProperties.PasswordlessWaitConfig wait =
        new AdminRateLimitProperties.PasswordlessWaitConfig();
    wait.setRequests(1);
    wait.setWindowMinutes(5);
    properties.setPasswordlessWait(wait);

    trustedProxyProperties = new TrustedProxyProperties();
    meterRegistry = new SimpleMeterRegistry();
    filter = new AdminRateLimitFilter(properties, trustedProxyProperties, meterRegistry);

    filterLogger = (Logger) LoggerFactory.getLogger(AdminRateLimitFilter.class);
    previousLogLevel = filterLogger.getLevel();
    filterLogger.setLevel(Level.WARN);
    logAppender = new ListAppender<>();
    logAppender.start();
    filterLogger.addAppender(logAppender);
  }

  @AfterEach
  void tearDownLogs() {
    if (filterLogger != null && logAppender != null) {
      filterLogger.detachAppender(logAppender);
      filterLogger.setLevel(previousLogLevel);
    }
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

  @Test
  @DisplayName("POST passwordless-wait is rate limited per authAttemptId")
  void passwordlessWait_rateLimitedPerAuthAttemptId() throws Exception {
    MockHttpServletResponse response1 =
        filterWait("203.0.113.60", "{\"authAttemptId\":42,\"waiterSecret\":\"s\"}");
    assertThat(response1.getStatus()).isEqualTo(HttpStatus.OK.value());

    MockHttpServletResponse response2 =
        filterWait("203.0.113.60", "{\"authAttemptId\":42,\"waiterSecret\":\"s\"}");
    assertThat(response2.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    assertThat(response2.getHeader("Retry-After")).isNotNull();
  }

  @Test
  @DisplayName("Login and passwordless-wait use separate rate-limit buckets (#831)")
  void loginAndPasswordlessWait_separateBuckets() throws Exception {
    MockHttpServletRequest loginRequest = new MockHttpServletRequest("POST", LOGIN_PATH);
    loginRequest.setRemoteAddr("203.0.113.70");
    MockHttpServletResponse loginResponse = new MockHttpServletResponse();
    filter.doFilter(loginRequest, loginResponse, filterChain);
    assertThat(loginResponse.getStatus()).isEqualTo(HttpStatus.OK.value());

    MockHttpServletResponse waitResponse =
        filterWait("203.0.113.70", "{\"authAttemptId\":99,\"waiterSecret\":\"w\"}");
    assertThat(waitResponse.getStatus()).isEqualTo(HttpStatus.OK.value());
    verify(filterChain, times(2))
        .doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));
  }

  @Test
  @DisplayName("Different authAttemptId values do not share the passwordless-wait bucket")
  void passwordlessWait_differentAttemptIds_separateBuckets() throws Exception {
    assertThat(
            filterWait("203.0.113.80", "{\"authAttemptId\":1,\"waiterSecret\":\"a\"}").getStatus())
        .isEqualTo(HttpStatus.OK.value());
    assertThat(
            filterWait("203.0.113.80", "{\"authAttemptId\":2,\"waiterSecret\":\"b\"}").getStatus())
        .isEqualTo(HttpStatus.OK.value());
  }

  @Test
  @DisplayName("Retry-After reflects remaining greedy refill seconds when login budget exhausted")
  void login_retryAfter_fromGreedyRefillProbe() throws Exception {
    properties.getLogin().setRequests(1);
    properties.getLogin().setWindowMinutes(1);
    filter = new AdminRateLimitFilter(properties, trustedProxyProperties, meterRegistry);

    MockHttpServletRequest first = new MockHttpServletRequest("POST", LOGIN_PATH);
    first.setRemoteAddr("198.51.100.20");
    filter.doFilter(first, new MockHttpServletResponse(), filterChain);

    MockHttpServletRequest second = new MockHttpServletRequest("POST", LOGIN_PATH);
    second.setRemoteAddr("198.51.100.20");
    MockHttpServletResponse denied = new MockHttpServletResponse();
    filter.doFilter(second, denied, filterChain);

    assertThat(denied.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    long retryAfter = Long.parseLong(denied.getHeader("Retry-After"));
    assertThat(retryAfter).isBetween(1L, 60L);
  }

  @Test
  @DisplayName("passwordless-cancel shares the passwordless-wait bucket for the same attempt id")
  void passwordlessCancel_sharesWaitBucket() throws Exception {
    assertThat(
            filterWait("203.0.113.90", "{\"authAttemptId\":7,\"waiterSecret\":\"x\"}").getStatus())
        .isEqualTo(HttpStatus.OK.value());

    MockHttpServletRequest cancel = new MockHttpServletRequest("POST", PASSWORDLESS_CANCEL_PATH);
    cancel.setRemoteAddr("203.0.113.90");
    cancel.setContentType("application/json");
    cancel.setContent(
        "{\"authAttemptId\":7,\"waiterSecret\":\"x\"}".getBytes(StandardCharsets.UTF_8));
    MockHttpServletResponse cancelResponse = new MockHttpServletResponse();
    filter.doFilter(cancel, cancelResponse, filterChain);
    assertThat(cancelResponse.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
  }

  @Test
  @DisplayName("extractAuthAttemptId parses JSON body without requiring full deserialization")
  void extractAuthAttemptId_parsesBody() {
    assertThat(
            AdminRateLimitFilter.extractAuthAttemptId(
                "{\"authAttemptId\":123,\"waiterSecret\":\"s\"}".getBytes(StandardCharsets.UTF_8)))
        .isEqualTo(123);
    assertThat(AdminRateLimitFilter.passwordlessWaitBucketKey(123, "1.2.3.4"))
        .isEqualTo("passwordless-wait:attempt:123");
    assertThat(AdminRateLimitFilter.passwordlessWaitBucketKey(null, "1.2.3.4"))
        .isEqualTo("passwordless-wait:ip:1.2.3.4");
  }

  @Test
  @DisplayName("Failure tracking maps evict entries beyond cache capacity")
  void failureTrackingMaps_evictBeyondCapacity() {
    properties.getLogin().setBlockAfterFailures(1);
    filter = new AdminRateLimitFilter(properties, trustedProxyProperties, meterRegistry);

    for (int i = 0; i < 10_500; i++) {
      filter.recordFailedAttempt("198.51.100." + i);
    }
    filter.runFailureTrackingMaintenanceForTest();

    assertThat(filter.failureTrackingEntryCount()).isLessThanOrEqualTo(10_000);
  }

  @Test
  @DisplayName("Rotating IPs on login trip the unkeyed per-instance backstop")
  void rotatingIpsOnLogin_tripBackstop() throws Exception {
    properties.getLogin().setRequests(100);
    properties.getBackstop().setEnabled(true);
    properties.getBackstop().getLogin().setRequests(2);
    properties.getBackstop().getLogin().setWindowMinutes(1);
    meterRegistry = new SimpleMeterRegistry();
    filter = new AdminRateLimitFilter(properties, trustedProxyProperties, meterRegistry);

    MockHttpServletRequest ip1 = new MockHttpServletRequest("POST", LOGIN_PATH);
    ip1.setRemoteAddr("198.51.100.10");
    MockHttpServletResponse r1 = new MockHttpServletResponse();
    filter.doFilter(ip1, r1, filterChain);
    assertThat(r1.getStatus()).isEqualTo(HttpStatus.OK.value());

    MockHttpServletRequest ip2 = new MockHttpServletRequest("POST", LOGIN_PATH);
    ip2.setRemoteAddr("198.51.100.11");
    MockHttpServletResponse r2 = new MockHttpServletResponse();
    filter.doFilter(ip2, r2, filterChain);
    assertThat(r2.getStatus()).isEqualTo(HttpStatus.OK.value());

    MockHttpServletRequest ip3 = new MockHttpServletRequest("POST", LOGIN_PATH);
    ip3.setRemoteAddr("198.51.100.12");
    MockHttpServletResponse r3 = new MockHttpServletResponse();
    filter.doFilter(ip3, r3, filterChain);
    assertThat(r3.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    assertThat(Long.parseLong(r3.getHeader("Retry-After"))).isBetween(1L, 60L);
    assertThat(
            meterRegistry
                .counter("ezkey.rate_limit.backstop.rejected", "endpoint", "login")
                .count())
        .isEqualTo(1);
  }

  @Test
  @DisplayName("Rate-limit WARN logs sanitize CR/LF in client IP (LogSanitizer)")
  void rateLimitWarnDoesNotEchoCrLfFromClientIp() throws Exception {
    MockHttpServletRequest first = new MockHttpServletRequest("POST", LOGIN_PATH);
    first.setRemoteAddr("203.0.113.9\r\ninjected");
    filter.doFilter(first, new MockHttpServletResponse(), filterChain);

    MockHttpServletRequest second = new MockHttpServletRequest("POST", LOGIN_PATH);
    second.setRemoteAddr("203.0.113.9\r\ninjected");
    MockHttpServletResponse denied = new MockHttpServletResponse();
    filter.doFilter(second, denied, filterChain);

    assertThat(denied.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());

    String joined =
        logAppender.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .reduce("", (a, b) -> a + "\n" + b);

    assertThat(joined).contains("Rate limit exceeded");
    assertThat(joined).doesNotContain("\rinjected");
    assertThat(joined).doesNotContain("\ninjected");
    assertThat(joined).contains("203.0.113.9__injected");
  }

  private MockHttpServletResponse filterWait(String remoteAddr, String jsonBody) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", PASSWORDLESS_WAIT_PATH);
    request.setRemoteAddr(remoteAddr);
    request.setContentType("application/json");
    request.setContent(jsonBody.getBytes(StandardCharsets.UTF_8));
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, filterChain);
    return response;
  }
}
