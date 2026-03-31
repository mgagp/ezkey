/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: DockerStackConfig
 * Description: Configuration for connecting to Docker stack services
 */

package org.ezkey.tests.config;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration class for Docker stack service URLs and health checks.
 *
 * <p>This class manages the connection to the Docker stack services (Admin API, Auth API, Crypto
 * API) and provides health check utilities to verify services are running before tests execute.
 *
 * <p>Service URLs can be configured via environment variables:
 *
 * <ul>
 *   <li>{@code EZKEY_ADMIN_API_URL} - Admin API base URL (default: http://localhost:9080)
 *   <li>{@code EZKEY_AUTH_API_URL} - Auth API base URL (default: http://localhost:8080)
 *   <li>{@code EZKEY_CRYPTO_API_URL} - Crypto API base URL (default: http://localhost:9090)
 * </ul>
 *
 * @since 2025
 */
public class DockerStackConfig {

  private static final Logger log = LoggerFactory.getLogger(DockerStackConfig.class);

  private static final String DEFAULT_ADMIN_API_URL = "http://localhost:9080";
  private static final String DEFAULT_AUTH_API_URL = "http://localhost:8080";
  private static final String DEFAULT_CRYPTO_API_URL = "http://localhost:9090";
  private static final String DEFAULT_ADMIN_ACTUATOR_URL = "http://localhost:9081";
  private static final String DEFAULT_AUTH_ACTUATOR_URL = "http://localhost:8085";

  private final String adminApiUrl;
  private final String authApiUrl;
  private final String cryptoApiUrl;
  private final String adminActuatorUrl;
  private final String authActuatorUrl;

  /**
   * Creates a new DockerStackConfig with URLs from environment variables or defaults.
   *
   * <p>Reads service URLs from environment variables, falling back to localhost defaults if not
   * set.
   */
  public DockerStackConfig() {
    this.adminApiUrl = System.getenv().getOrDefault("EZKEY_ADMIN_API_URL", DEFAULT_ADMIN_API_URL);
    this.authApiUrl = System.getenv().getOrDefault("EZKEY_AUTH_API_URL", DEFAULT_AUTH_API_URL);
    this.cryptoApiUrl =
        System.getenv().getOrDefault("EZKEY_CRYPTO_API_URL", DEFAULT_CRYPTO_API_URL);
    this.adminActuatorUrl =
        System.getenv().getOrDefault("EZKEY_ADMIN_ACTUATOR_URL", DEFAULT_ADMIN_ACTUATOR_URL);
    this.authActuatorUrl =
        System.getenv().getOrDefault("EZKEY_AUTH_ACTUATOR_URL", DEFAULT_AUTH_ACTUATOR_URL);

    log.info("Docker Stack Configuration:");
    log.info("  Admin API: {}", this.adminApiUrl);
    log.info("  Auth API: {}", this.authApiUrl);
    log.info("  Crypto API: {}", this.cryptoApiUrl);
    log.info("  Admin Actuator: {}", this.adminActuatorUrl);
    log.info("  Auth Actuator: {}", this.authActuatorUrl);
  }

  /**
   * Gets the Admin API base URL.
   *
   * @return Admin API URL
   */
  public String getAdminApiUrl() {
    return adminApiUrl;
  }

  /**
   * Gets the Auth API base URL.
   *
   * @return Auth API URL
   */
  public String getAuthApiUrl() {
    return authApiUrl;
  }

  /**
   * Gets the Crypto API base URL.
   *
   * @return Crypto API URL
   */
  public String getCryptoApiUrl() {
    return cryptoApiUrl;
  }

  /**
   * Verifies that all Docker stack services are healthy and accessible.
   *
   * <p>Checks the health endpoint of each service via their management ports (Actuator endpoints).
   * Admin API, Auth API, and Crypto API are required.
   *
   * <p>Uses dedicated management ports (9081, 8085, 9090) for health verification. These ports are
   * exposed in both standard and HA modes for consistent verification logic.
   *
   * @throws IllegalStateException if any service is not healthy
   */
  public void verifyServicesHealthy() {
    log.info("Verifying Docker stack services are healthy...");

    verifyServiceHealthy(adminActuatorUrl, "Admin API");
    verifyServiceHealthy(authActuatorUrl, "Auth API");
    verifyServiceHealthy(cryptoApiUrl, "Crypto API");

    log.info("All Docker stack services are healthy");
  }

  /**
   * Verifies a single service is healthy by checking its health endpoint.
   *
   * @param baseUrl the service base URL
   * @param serviceName the service name for logging
   * @throws IllegalStateException if the service is not healthy
   */
  private void verifyServiceHealthy(String baseUrl, String serviceName) {
    verifyServiceHealthyUrl(baseUrl + "/actuator/health", serviceName);
  }

  private void verifyServiceHealthyUrl(String url, String serviceName) {
    try {
      HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(url))
              .timeout(Duration.ofSeconds(5))
              .GET()
              .build();

      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

      if (response == null) {
        throw new IllegalStateException(
            "%s at %s returned no response (null). Is the Docker stack running?"
                .formatted(serviceName, url));
      }

      if (response.statusCode() != 200) {
        throw new IllegalStateException(
            "%s at %s returned status %d. Expected 200. Is the Docker stack running?"
                .formatted(serviceName, url, response.statusCode()));
      }

      log.debug("{} is healthy", serviceName);
    } catch (Exception e) {
      throw new IllegalStateException(
          "%s at %s is not accessible: %s. Is the Docker stack running?"
              .formatted(serviceName, url, e.getMessage()),
          e);
    }
  }
}
