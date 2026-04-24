/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.demo.acme.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.ezkey.demo.acme.config.AcmeRateLimitProperties;
import org.ezkey.demo.acme.config.TrustedProxyProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DemoRateLimitService")
class DemoRateLimitServiceTest {

  @Test
  @DisplayName("blocks login after configured number of requests from same client IP")
  void blocksLoginAfterConfiguredThreshold() {
    AcmeRateLimitProperties properties = new AcmeRateLimitProperties();
    properties.getLogin().setRequests(2);
    properties.getLogin().setWindowMinutes(5);
    TrustedProxyProperties trustedProxyProperties = new TrustedProxyProperties();

    DemoRateLimitService service = new DemoRateLimitService(properties, trustedProxyProperties);
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getRemoteAddr()).thenReturn("127.0.0.1");

    assertTrue(service.checkLogin(request).allowed());
    assertTrue(service.checkLogin(request).allowed());

    DemoRateLimitService.RateLimitDecision denied = service.checkLogin(request);
    assertFalse(denied.allowed());
    assertTrue(denied.retryAfterSeconds() >= 1);
    assertEquals("127.0.0.1", denied.clientId());
  }

  @Test
  @DisplayName("uses CF-Connecting-IP only when remote address is trusted proxy")
  void usesForwardedHeadersOnlyForTrustedProxy() {
    AcmeRateLimitProperties properties = new AcmeRateLimitProperties();
    properties.getApplyApiKey().setRequests(1);
    properties.getApplyApiKey().setWindowMinutes(5);

    TrustedProxyProperties trustedProxyProperties = new TrustedProxyProperties();
    trustedProxyProperties.setCidrs(List.of("10.0.0.0/8"));

    DemoRateLimitService service = new DemoRateLimitService(properties, trustedProxyProperties);
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getRemoteAddr()).thenReturn("10.1.2.3");
    when(request.getHeader("CF-Connecting-IP")).thenReturn("198.51.100.25");

    assertTrue(service.checkApplyApiKey(request).allowed());

    DemoRateLimitService.RateLimitDecision denied = service.checkApplyApiKey(request);
    assertFalse(denied.allowed());
    assertEquals("198.51.100.25", denied.clientId());
  }
}
