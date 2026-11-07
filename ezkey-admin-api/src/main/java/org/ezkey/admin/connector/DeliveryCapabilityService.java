/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 *
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: DeliveryCapabilityService
 */

package org.ezkey.admin.connector;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Says whether a delivery connector can be used right now, from its settings and a short-lived
 * health read. Any doubt means unavailable, and integrated delivery stays (fail-open).
 *
 * @since 2026
 */
@Service
public class DeliveryCapabilityService {

  private static final Logger logger = LoggerFactory.getLogger(DeliveryCapabilityService.class);

  private static final Capability UNAVAILABLE = new Capability(false, false, "", List.of());

  private final DeliveryConnectorProperties properties;
  private final DeliveryConnectorPort port;
  private final Clock clock;
  private final boolean configured;

  private volatile Capability cached;
  private volatile Instant cachedUntil = Instant.MIN;

  /**
   * Builds the service and reports a refused configuration once at startup.
   *
   * @param properties connector settings
   * @param port the delivery port
   */
  @Autowired
  public DeliveryCapabilityService(
      DeliveryConnectorProperties properties, DeliveryConnectorPort port) {
    this(properties, port, Clock.systemUTC());
  }

  DeliveryCapabilityService(
      DeliveryConnectorProperties properties, DeliveryConnectorPort port, Clock clock) {
    this.properties = properties;
    this.port = port;
    this.clock = clock;
    this.configured = checkConfiguration();
  }

  /**
   * Reads the current capability. Cached briefly, so opening a screen stays light.
   *
   * @return the capability
   */
  public Capability current() {
    if (!configured) {
      return UNAVAILABLE;
    }
    Instant now = clock.instant();
    Capability value = cached;
    if (value != null && now.isBefore(cachedUntil)) {
      return value;
    }
    value = port.readHealth().map(this::toCapability).orElse(UNAVAILABLE);
    cached = value;
    cachedUntil = now.plus(properties.getHealthCacheTtl());
    return value;
  }

  private Capability toCapability(DeliveryConnectorPort.ConnectorHealth health) {
    boolean usable =
        "ok".equals(health.status())
            && DeliveryConnectorPort.CONTRACT_VERSION.equals(health.contractVersion())
            && "email".equals(health.channel())
            && !health.locales().isEmpty();
    if (!usable) {
      logger.warn(
          "Delivery connector not usable: status or contract version does not fit this core");
      return UNAVAILABLE;
    }
    return new Capability(true, health.testMode(), health.connectorName(), health.locales());
  }

  private boolean checkConfiguration() {
    String url = properties.getUrl();
    if (url == null || url.isBlank()) {
      return false;
    }
    if (properties.getToken() == null || properties.getToken().isBlank()) {
      logger.warn("Delivery connector ignored: a URL is set without a token");
      return false;
    }
    String scheme;
    try {
      scheme = URI.create(url.strip()).getScheme();
    } catch (IllegalArgumentException e) {
      logger.warn("Delivery connector ignored: the URL cannot be read");
      return false;
    }
    if ("https".equalsIgnoreCase(scheme)) {
      return true;
    }
    if ("http".equalsIgnoreCase(scheme) && properties.isPlainHttpPrivateNetwork()) {
      return true;
    }
    logger.warn(
        "Delivery connector ignored: use https, or declare a private network to allow http");
    return false;
  }

  /**
   * What the Admin UI and the send path need to know.
   *
   * @param available true when a send can be attempted
   * @param testMode true for a test double, which must not pass for real delivery
   * @param connectorName connector name, for the audit
   * @param locales locales offered
   */
  public record Capability(
      boolean available, boolean testMode, String connectorName, List<String> locales) {}
}
