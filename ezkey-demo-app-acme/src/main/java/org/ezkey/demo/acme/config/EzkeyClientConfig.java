/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: EzkeyClientConfig
 * Description: RestTemplate configuration for Admin API calls with API Key authentication.
 */

package org.ezkey.demo.acme.config;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration for RestTemplate used to call EZKey Admin API.
 *
 * <p>Configures RestTemplate with HTTP Basic Authentication using API Key credentials (integration
 * key + secret key) for M2M communication.
 *
 * <p><b>Authentication Format:</b>
 *
 * <pre>
 * Authorization: Basic base64(integrationKey:secretKey)
 * </pre>
 *
 * <p>Where:
 *
 * <ul>
 *   <li>integrationKey: Public integration key (e.g., ezkey_ikey_xxx)
 *   <li>secretKey: Secret key (e.g., ezkey_skey_xxx)
 * </ul>
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Configuration
@EnableConfigurationProperties(AcmeProperties.class)
public class EzkeyClientConfig {

  private static final Logger logger = LoggerFactory.getLogger(EzkeyClientConfig.class);

  private final AcmeProperties properties;
  private final Environment environment;

  public EzkeyClientConfig(AcmeProperties properties, Environment environment) {
    this.properties = properties;
    this.environment = environment;
    
    // Log configuration status at startup
    // Read directly from Environment to bypass @RefreshScope proxy issues
    String integrationKey = environment.getProperty("ezkey.integration.key");
    String secretKey = environment.getProperty("ezkey.secret.key");
    
    if (integrationKey != null && !integrationKey.isBlank() 
        && secretKey != null && !secretKey.isBlank()) {
      logger.info("✅ API Key credentials configured - Integration Key: {}...", 
          integrationKey.substring(0, Math.min(20, integrationKey.length())));
    } else {
      logger.warn("⚠️  API Key credentials NOT configured - Integration Key: {}, Secret Key: {}", 
          integrationKey != null ? "present" : "missing",
          secretKey != null ? "present" : "missing");
      logger.warn("   Configure via /app/config/application.properties or environment variables");
    }
  }

  /**
   * Creates RestTemplate bean configured with HTTP Basic Auth using API Key credentials.
   *
   * <p>Adds an interceptor that automatically adds the Authorization header with HTTP Basic Auth
   * (base64-encoded integrationKey:secretKey) for all requests to Admin API.
   *
   * <p><b>RefreshScope:</b> This bean is marked with @RefreshScope to ensure it is recreated when
   * configuration properties are refreshed via ContextRefresher, allowing API key credentials to be
   * updated without restarting the application.
   *
   * @return configured RestTemplate instance
   */
  @Bean
  @RefreshScope
  public RestTemplate ezkeyRestTemplate() {
    RestTemplate restTemplate = new RestTemplate();

    // Add interceptor to inject HTTP Basic Auth header with API Key credentials
    // Read directly from Environment to bypass @RefreshScope proxy issues
    restTemplate
        .getInterceptors()
        .add(
            (ClientHttpRequestInterceptor) (request, body, execution) -> {
              // Read directly from Environment to ensure we get the latest values
              // This bypasses potential @RefreshScope proxy issues
              String integrationKey = environment.getProperty("ezkey.integration.key");
              String secretKey = environment.getProperty("ezkey.secret.key");

              if (integrationKey != null
                  && !integrationKey.isBlank()
                  && secretKey != null
                  && !secretKey.isBlank()) {
                // Create HTTP Basic Auth: base64(integrationKey:secretKey)
                String credentials = integrationKey + ":" + secretKey;
                String encodedCredentials =
                    Base64.getEncoder()
                        .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
                request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Basic " + encodedCredentials);
                logger.debug("Added HTTP Basic Auth header for request to: {}", request.getURI());
              } else {
                logger.warn("Missing API Key credentials - request to {} will fail authentication", 
                    request.getURI());
              }
              return execution.execute(request, body);
            });

    return restTemplate;
  }
}