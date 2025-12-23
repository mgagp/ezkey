/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: DockerStackConfig
 * Description: Configuration for connecting to Docker stack services
 */

package org.ezkey.tests.config;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
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
  private static final String DEFAULT_AUTH_ACTUATOR_URL = "http://localhost:8081";
  private static final String HAPROXY_ADMIN_STATS_URL = "http://localhost:9081/stats";
  private static final String HAPROXY_AUTH_STATS_URL = "http://localhost:8081/stats";

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
   * <p>Checks the health endpoint of each service. Admin API, Auth API, and Crypto API are
   * required.
   *
   * @throws IllegalStateException if any service is not healthy
   */
  public void verifyServicesHealthy() {
    log.info("Verifying Docker stack services are healthy...");

    verifyServiceHealthyWithFallback(
        adminActuatorUrl, "Admin API", "Admin Actuator", HAPROXY_ADMIN_STATS_URL);
    verifyServiceHealthyWithFallback(
        authActuatorUrl, "Auth API", "Auth Actuator", HAPROXY_AUTH_STATS_URL);
    verifyServiceHealthy(cryptoApiUrl, "Crypto API");

    log.info("All Docker stack services are healthy");
  }

  /**
   * Verifies a service is healthy using its Actuator URL, with a fallback to HAProxy stats.
   *
   * <p>Rationale:
   *
   * <ul>
   *   <li>In the standard Docker stack, Actuator runs on a dedicated management port (e.g., 9081,
   *       8081) and may or may not be published depending on the compose mode.
   *   <li>In the HA stack, ports 9081 and 8081 are used by HAProxy stats pages. Actuator is not
   *       routed through HAProxy by default. In that case, the HAProxy stats endpoint is a
   *       reasonable liveness signal for the load balancer (and indirectly for backend health).
   * </ul>
   *
   * @param actuatorBaseUrl actuator base URL (no path)
   * @param serviceName logical service name (for error messages)
   * @param actuatorName actuator label (for logs)
   * @param haproxyStatsUrl HAProxy stats URL (full path)
   * @throws IllegalStateException if neither Actuator nor HAProxy stats are reachable
   * @since 2025
   */
  private void verifyServiceHealthyWithFallback(
      String actuatorBaseUrl, String serviceName, String actuatorName, String haproxyStatsUrl) {
    try {
      verifyServiceHealthy(actuatorBaseUrl, actuatorName);
    } catch (Exception actuatorFailure) {
      log.warn(
          "{} at {} is not accessible ({}). Trying HAProxy stats fallback: {}",
          actuatorName,
          actuatorBaseUrl,
          actuatorFailure.getMessage(),
          haproxyStatsUrl);

      verifyServiceHealthyUrl(haproxyStatsUrl, serviceName + " Load Balancer (stats)");
    }
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
      // Save current RestAssured settings to restore later
      String savedBaseUri = RestAssured.baseURI;
      String savedBasePath = RestAssured.basePath;

      try {
        // Set baseURI and basePath before using RestAssured (required by RestAssured 5.x)
        RestAssured.baseURI = url;
        RestAssured.basePath = "";

        Response response =
            RestAssured.given()
                .contentType(ContentType.JSON)
                .when()
                .get()
                .then()
                .extract()
                .response();

        if (response.getStatusCode() != 200) {
          throw new IllegalStateException(
              String.format(
                  "%s at %s returned status %d. Expected 200. Is the Docker stack running?",
                  serviceName, url, response.getStatusCode()));
        }

        log.debug("{} is healthy", serviceName);
      } finally {
        // Restore original RestAssured settings
        RestAssured.baseURI = savedBaseUri;
        RestAssured.basePath = savedBasePath;
      }
    } catch (Exception e) {
      throw new IllegalStateException(
          String.format(
              "%s at %s is not accessible: %s. Is the Docker stack running?",
              serviceName, url, e.getMessage()),
          e);
    }
  }
}
