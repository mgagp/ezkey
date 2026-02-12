/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: EzkeyClientConfig
 * Description: Spring configuration that creates an EzkeyClient bean from application properties.
 */

package org.ezkey.demo.acme.config;

import org.ezkey.sdk.EzkeyClient;
import org.ezkey.sdk.EzkeyConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration that creates an {@link EzkeyClient} bean from externalized properties.
 *
 * <p>Bridges the framework-agnostic Ezkey SDK with the Spring Boot configuration system by reading
 * credentials and URL from {@link AcmeProperties} and constructing an immutable SDK client.
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Configuration
@EnableConfigurationProperties(AcmeProperties.class)
public class EzkeyClientConfig {

  private static final Logger LOG = LoggerFactory.getLogger(EzkeyClientConfig.class);

  /**
   * Creates the {@link EzkeyClient} bean configured from application properties.
   *
   * <p>The client uses HTTP Basic Auth with the configured integration key and secret key for M2M
   * communication with the Ezkey Admin API.
   *
   * @param properties the ACME application properties containing Ezkey credentials
   * @return a configured, immutable {@link EzkeyClient} instance
   */
  @Bean
  public EzkeyClient ezkeyClient(AcmeProperties properties) {
    String integrationKey = properties.getIntegrationKey();
    String secretKey = properties.getSecretKey();
    String adminApiUrl = properties.getAdminApiUrl();

    if (integrationKey == null
        || integrationKey.isBlank()
        || secretKey == null
        || secretKey.isBlank()) {
      LOG.warn(
          "\n"
              + "╔══════════════════════════════════════════════════════════════════╗\n"
              + "║  ⚠️  EZKEY SDK NOT CONFIGURED — DEGRADED MODE                   ║\n"
              + "║                                                                 ║\n"
              + "║  Login will be unavailable until credentials are set.            ║\n"
              + "║  Edit /app/config/application.properties and set:                ║\n"
              + "║    ezkey.integration-key=ezkey_ikey_xxx                          ║\n"
              + "║    ezkey.secret-key=ezkey_skey_xxx                               ║\n"
              + "║  Then restart the container.                                     ║\n"
              + "╚══════════════════════════════════════════════════════════════════╝");
      return null;
    }

    LOG.info(
        "Ezkey SDK configured — Integration Key: {}..., Admin API: {}",
        integrationKey.substring(0, Math.min(20, integrationKey.length())),
        adminApiUrl);

    return EzkeyClient.builder()
        .integrationKey(integrationKey)
        .secretKey(secretKey)
        .baseUrl(adminApiUrl != null ? adminApiUrl : EzkeyConfig.DEFAULT_BASE_URL)
        .build();
  }
}
