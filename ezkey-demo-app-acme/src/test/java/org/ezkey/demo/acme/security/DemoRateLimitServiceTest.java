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
  @DisplayName("two slots behind the same IP do not share a slot+IP bucket")
  void twoSlotsSameIpDoNotShareBucket() {
    AcmeRateLimitProperties properties = new AcmeRateLimitProperties();
    properties.getLogin().setRequests(2);
    properties.getLogin().setWindowMinutes(5);
    properties.getLoginIpBound().setRequests(100);
    DemoRateLimitService service =
        new DemoRateLimitService(properties, new TrustedProxyProperties());
    HttpServletRequest request = requestFromIp("203.0.113.10");

    assertTrue(service.checkLogin(request, "northwind").allowed());
    assertTrue(service.checkLogin(request, "northwind").allowed());
    assertFalse(service.checkLogin(request, "northwind").allowed());

    assertTrue(service.checkLogin(request, "partner").allowed());
    assertTrue(service.checkLogin(request, "partner").allowed());
    assertFalse(service.checkLogin(request, "partner").allowed());
  }

  @Test
  @DisplayName("same slot is throttled on the 21st request when login ceiling is 20")
  void sameSlotThrottledAtTwentyOneWithDefaultCeiling() {
    AcmeRateLimitProperties properties = new AcmeRateLimitProperties();
    assertEquals(20, properties.getLogin().getRequests());
    assertEquals(60, properties.getLoginIpBound().getRequests());
    properties.getLogin().setWindowMinutes(5);
    DemoRateLimitService service =
        new DemoRateLimitService(properties, new TrustedProxyProperties());
    HttpServletRequest request = requestFromIp("198.51.100.1");

    for (int i = 0; i < 20; i++) {
      assertTrue(
          service.checkLogin(request, "northwind").allowed(),
          "request " + (i + 1) + " should pass");
    }
    assertFalse(service.checkLogin(request, "northwind").allowed());
  }

  @Test
  @DisplayName("slot-mode login pins slot ceiling and wider per-IP login-ip-bound")
  void slotModePinsLoginCeilingAndGlobalIpBound() {
    AcmeRateLimitProperties properties = new AcmeRateLimitProperties();
    properties.getLogin().setRequests(2);
    properties.getLogin().setWindowMinutes(5);
    properties.getLoginIpBound().setRequests(3);
    properties.getLoginIpBound().setWindowMinutes(5);
    DemoRateLimitService service =
        new DemoRateLimitService(properties, new TrustedProxyProperties());
    HttpServletRequest request = requestFromIp("198.51.100.40");

    // Slot ceiling (2): two successes on slot-a (also burn 2 of 3 global tokens).
    assertTrue(service.checkLogin(request, "slot-a").allowed());
    assertTrue(service.checkLogin(request, "slot-a").allowed());

    // Different slot still allowed once (last global IP-bound token).
    assertTrue(service.checkLogin(request, "slot-b").allowed());
    // Global per-IP login-ip-bound (3) exhausted across slots.
    assertFalse(service.checkLogin(request, "slot-b").allowed());
    assertFalse(service.checkLogin(request, "slot-c").allowed());

    // With a high IP bound, the per-slot login ceiling alone throttles.
    AcmeRateLimitProperties slotOnly = new AcmeRateLimitProperties();
    slotOnly.getLogin().setRequests(2);
    slotOnly.getLoginIpBound().setRequests(100);
    DemoRateLimitService slotService =
        new DemoRateLimitService(slotOnly, new TrustedProxyProperties());
    HttpServletRequest otherIp = requestFromIp("198.51.100.41");
    assertTrue(slotService.checkLogin(otherIp, "slot-a").allowed());
    assertTrue(slotService.checkLogin(otherIp, "slot-a").allowed());
    assertFalse(slotService.checkLogin(otherIp, "slot-a").allowed());
  }

  @Test
  @DisplayName("same slot from two different IPs gets independent budgets")
  void sameSlotDifferentIpsHaveIndependentBudgets() {
    AcmeRateLimitProperties properties = new AcmeRateLimitProperties();
    properties.getLogin().setRequests(2);
    properties.getLogin().setWindowMinutes(5);
    DemoRateLimitService service =
        new DemoRateLimitService(properties, new TrustedProxyProperties());
    HttpServletRequest ip1 = requestFromIp("203.0.113.1");
    HttpServletRequest ip2 = requestFromIp("203.0.113.2");

    assertTrue(service.checkLogin(ip1, "northwind").allowed());
    assertTrue(service.checkLogin(ip1, "northwind").allowed());
    assertFalse(service.checkLogin(ip1, "northwind").allowed());

    assertTrue(service.checkLogin(ip2, "northwind").allowed());
    assertTrue(service.checkLogin(ip2, "northwind").allowed());
    assertFalse(service.checkLogin(ip2, "northwind").allowed());
  }

  @Test
  @DisplayName("invalid access-link codes share the IP-only bucket regardless of guessed codes")
  void invalidAccessLinksShareIpOnlyBucket() {
    AcmeRateLimitProperties properties = new AcmeRateLimitProperties();
    properties.getLogin().setRequests(2);
    properties.getLogin().setWindowMinutes(5);
    DemoRateLimitService service =
        new DemoRateLimitService(properties, new TrustedProxyProperties());
    HttpServletRequest request = requestFromIp("203.0.113.50");

    assertTrue(service.checkAccessLink(request, null).allowed());
    assertTrue(service.checkAccessLink(request, null).allowed());
    assertFalse(service.checkAccessLink(request, null).allowed());
    assertFalse(service.checkAccessLink(request, null).allowed());
  }

  @Test
  @DisplayName("exhausted IP-only bucket also blocks a subsequent valid access-link (anti-oracle)")
  void exhaustedIpBucketBlocksValidAccessLink() {
    AcmeRateLimitProperties properties = new AcmeRateLimitProperties();
    properties.getLogin().setRequests(2);
    properties.getLogin().setWindowMinutes(5);
    DemoRateLimitService service =
        new DemoRateLimitService(properties, new TrustedProxyProperties());
    HttpServletRequest request = requestFromIp("203.0.113.60");

    assertTrue(service.checkAccessLink(request, null).allowed());
    assertTrue(service.checkAccessLink(request, null).allowed());
    assertFalse(service.checkAccessLink(request, null).allowed());

    assertFalse(service.checkAccessLink(request, "northwind").allowed());
  }

  @Test
  @DisplayName("20 valid /t activations from one IP exhaust IP bucket; invalid then blocked")
  void twentyValidAccessLinksExhaustIpBucketThenInvalidBlocked() {
    AcmeRateLimitProperties properties = new AcmeRateLimitProperties();
    assertEquals(20, properties.getLogin().getRequests());
    DemoRateLimitService service =
        new DemoRateLimitService(properties, new TrustedProxyProperties());
    HttpServletRequest request = requestFromIp("198.51.100.70");

    for (int i = 0; i < 20; i++) {
      assertTrue(
          service.checkAccessLink(request, "slot-" + i).allowed(),
          "valid activation " + (i + 1) + " should pass");
    }
    assertFalse(service.checkAccessLink(request, null).allowed());
  }

  @Test
  @DisplayName("properties override changes the login ceiling")
  void propertiesOverrideChangesCeiling() {
    AcmeRateLimitProperties properties = new AcmeRateLimitProperties();
    properties.getLogin().setRequests(3);
    properties.getLogin().setWindowMinutes(5);
    properties.getLoginIpBound().setRequests(100);
    DemoRateLimitService service =
        new DemoRateLimitService(properties, new TrustedProxyProperties());
    HttpServletRequest request = requestFromIp("192.0.2.10");

    assertTrue(service.checkLogin(request, "slot-a").allowed());
    assertTrue(service.checkLogin(request, "slot-a").allowed());
    assertTrue(service.checkLogin(request, "slot-a").allowed());
    assertFalse(service.checkLogin(request, "slot-a").allowed());
  }

  @Test
  @DisplayName("self-service login (null slot) uses IP-only key distinct from slot keys")
  void selfServiceUsesIpOnlyKey() {
    assertEquals("login:ip:1.2.3.4", DemoRateLimitService.bucketKey("login", "1.2.3.4", null));
    assertEquals(
        "login:slot:northwind:ip:1.2.3.4",
        DemoRateLimitService.bucketKey("login", "1.2.3.4", "northwind"));
    assertEquals(
        "login-ip-bound:ip:1.2.3.4",
        DemoRateLimitService.bucketKey("login-ip-bound", "1.2.3.4", null));
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

  private static HttpServletRequest requestFromIp(String ip) {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getRemoteAddr()).thenReturn(ip);
    return request;
  }
}
