/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 *
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.admin.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.ezkey.admin.connector.DeliveryConnectorPort.ConnectorHealth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DeliveryCapabilityService")
class DeliveryCapabilityServiceTest {

  private static final ConnectorHealth HEALTHY =
      new ConnectorHealth("ok", "mock", "v0", "email", List.of("en", "fr"), true);

  private final DeliveryConnectorPort port = mock(DeliveryConnectorPort.class);

  private static DeliveryConnectorProperties properties(String url, String token, boolean plain) {
    DeliveryConnectorProperties properties = new DeliveryConnectorProperties();
    properties.setUrl(url);
    properties.setToken(token);
    properties.setPlainHttpPrivateNetwork(plain);
    return properties;
  }

  private DeliveryCapabilityService service(DeliveryConnectorProperties properties) {
    return new DeliveryCapabilityService(properties, port);
  }

  @Test
  @DisplayName("no connector: unavailable with no call")
  void noConnector() {
    assertThat(service(properties("", "", false)).current().available()).isFalse();
    verify(port, never()).readHealth();
  }

  @Test
  @DisplayName("a URL without a token is ignored")
  void noToken() {
    assertThat(service(properties("https://c.example", "", false)).current().available()).isFalse();
    verify(port, never()).readHealth();
  }

  @Test
  @DisplayName("plain http without the declaration is refused, with no call")
  void plainHttpRefused() {
    assertThat(service(properties("http://c:8080", "t", false)).current().available()).isFalse();
    verify(port, never()).readHealth();
  }

  @Test
  @DisplayName("plain http with the private network declaration is allowed")
  void plainHttpDeclared() {
    when(port.readHealth()).thenReturn(Optional.of(HEALTHY));
    assertThat(service(properties("http://c:8080", "t", true)).current().available()).isTrue();
  }

  @Test
  @DisplayName("https is allowed, and test mode is reported")
  void https() {
    when(port.readHealth()).thenReturn(Optional.of(HEALTHY));

    DeliveryCapabilityService.Capability capability =
        service(properties("https://c.example", "t", false)).current();

    assertThat(capability.available()).isTrue();
    assertThat(capability.testMode()).isTrue();
    assertThat(capability.locales()).containsExactly("en", "fr");
  }

  @Test
  @DisplayName("unreachable, degraded, other contract version or no locale: unavailable")
  void notUsable() {
    List<Optional<ConnectorHealth>> cases =
        List.of(
            Optional.empty(),
            Optional.of(new ConnectorHealth("degraded", "m", "v0", "email", List.of("en"), false)),
            Optional.of(new ConnectorHealth("ok", "m", "v1", "email", List.of("en"), false)),
            Optional.of(new ConnectorHealth("ok", "m", "v0", "sms", List.of("en"), false)),
            Optional.of(new ConnectorHealth("ok", "m", "v0", "email", List.of(), false)));
    for (Optional<ConnectorHealth> health : cases) {
      DeliveryConnectorPort local = mock(DeliveryConnectorPort.class);
      when(local.readHealth()).thenReturn(health);
      assertThat(
              new DeliveryCapabilityService(properties("https://c.example", "t", false), local)
                  .current()
                  .available())
          .isFalse();
    }
  }

  @Test
  @DisplayName("health is cached, then read again after the TTL")
  void cached() {
    when(port.readHealth()).thenReturn(Optional.of(HEALTHY));
    MutableClock clock = new MutableClock();
    DeliveryConnectorProperties properties = properties("https://c.example", "t", false);
    properties.setHealthCacheTtl(Duration.ofSeconds(10));
    DeliveryCapabilityService service = new DeliveryCapabilityService(properties, port, clock);

    service.current();
    clock.advance(Duration.ofSeconds(5));
    service.current();
    verify(port, times(1)).readHealth();

    clock.advance(Duration.ofSeconds(6));
    service.current();
    verify(port, times(2)).readHealth();
  }

  private static final class MutableClock extends Clock {
    private Instant now = Instant.parse("2026-11-06T12:00:00Z");

    void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneId.of("UTC");
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
