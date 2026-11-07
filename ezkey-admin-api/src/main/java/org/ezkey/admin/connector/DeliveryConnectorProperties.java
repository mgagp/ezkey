/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 *
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: DeliveryConnectorProperties
 */

package org.ezkey.admin.connector;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for reaching an optional delivery connector. Ezkey stays fully operational with none.
 *
 * <p>Provider credentials never appear here: they stay in the connector (ADR-0018).
 *
 * @since 2026
 */
@ConfigurationProperties(prefix = "ezkey.admin.connector.delivery")
public class DeliveryConnectorProperties {

  /** Base URL of the connector. Blank means no connector: integrated delivery only. */
  private String url = "";

  /** Bearer token for this connector. Never logged. */
  private String token = "";

  /**
   * Declares that {@code http://} is acceptable because the connector sits on a private network.
   * Without it, Ezkey refuses to call a plain HTTP URL, since the job carries the QR.
   */
  private boolean plainHttpPrivateNetwork = false;

  /** Connect timeout for every call to the connector. */
  private Duration connectTimeout = Duration.ofSeconds(3);

  /** Read timeout for a send. Keep it longer than the connector's own provider timeout. */
  private Duration readTimeout = Duration.ofSeconds(20);

  /** Timeout for a health read. */
  private Duration healthTimeout = Duration.ofSeconds(2);

  /** How long a health read stays cached. */
  private Duration healthCacheTtl = Duration.ofSeconds(10);

  /** Sends per tenant per UTC day. */
  private int tenantDailyCap = 50;

  /** Sends for the whole instance per UTC day. */
  private int instanceDailyCap = 200;

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getToken() {
    return token;
  }

  public void setToken(String token) {
    this.token = token;
  }

  public boolean isPlainHttpPrivateNetwork() {
    return plainHttpPrivateNetwork;
  }

  public void setPlainHttpPrivateNetwork(boolean plainHttpPrivateNetwork) {
    this.plainHttpPrivateNetwork = plainHttpPrivateNetwork;
  }

  public Duration getConnectTimeout() {
    return connectTimeout;
  }

  public void setConnectTimeout(Duration connectTimeout) {
    this.connectTimeout = connectTimeout;
  }

  public Duration getReadTimeout() {
    return readTimeout;
  }

  public void setReadTimeout(Duration readTimeout) {
    this.readTimeout = readTimeout;
  }

  public Duration getHealthTimeout() {
    return healthTimeout;
  }

  public void setHealthTimeout(Duration healthTimeout) {
    this.healthTimeout = healthTimeout;
  }

  public Duration getHealthCacheTtl() {
    return healthCacheTtl;
  }

  public void setHealthCacheTtl(Duration healthCacheTtl) {
    this.healthCacheTtl = healthCacheTtl;
  }

  public int getTenantDailyCap() {
    return tenantDailyCap;
  }

  public void setTenantDailyCap(int tenantDailyCap) {
    this.tenantDailyCap = tenantDailyCap;
  }

  public int getInstanceDailyCap() {
    return instanceDailyCap;
  }

  public void setInstanceDailyCap(int instanceDailyCap) {
    this.instanceDailyCap = instanceDailyCap;
  }
}
