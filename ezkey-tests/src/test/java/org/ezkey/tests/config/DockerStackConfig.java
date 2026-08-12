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
import java.util.ArrayList;
import java.util.List;
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
 *   <li>{@code EZKEY_ADMIN_ACTUATOR_URL} - Admin management base (default probe: 9081, then HA
 *       docker-dev 19081/29081)
 *   <li>{@code EZKEY_AUTH_ACTUATOR_URL} - Auth management base (default probe: 8085, then HA
 *       docker-dev 18085/28085)
 * </ul>
 *
 * <p>In HA mode, host ports {@code 9081}/{@code 8085}/{@code 7081} are HAProxy <em>stats</em>
 * pages, not Spring Actuator. Health resolution therefore probes management ports published by
 * {@code docker-compose.ha.docker-dev.yml}, then falls back to public {@code /api/v1/public/
 * instance-info} on the load-balanced API ports.
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

  /** HA docker-dev published management ports (admin-api-1 / admin-api-2). */
  private static final String HA_ADMIN_ACTUATOR_1 = "http://localhost:19081";

  private static final String HA_ADMIN_ACTUATOR_2 = "http://localhost:29081";

  /** HA docker-dev published management ports (auth-api-1 / auth-api-2). */
  private static final String HA_AUTH_ACTUATOR_1 = "http://localhost:18085";

  private static final String HA_AUTH_ACTUATOR_2 = "http://localhost:28085";

  private final String adminApiUrl;
  private final String authApiUrl;
  private final String cryptoApiUrl;
  private final String adminActuatorUrl;
  private final String authActuatorUrl;
  private final String adminHealthUrl;
  private final String authHealthUrl;
  private final String cryptoHealthUrl;

  /**
   * Creates a new DockerStackConfig with URLs from environment variables or defaults.
   *
   * <p>Reads service URLs from environment variables, falling back to localhost defaults if not
   * set. Actuator/health URLs are resolved so both standard and HA stacks pass {@link
   * #verifyServicesHealthy()}.
   */
  public DockerStackConfig() {
    this.adminApiUrl = System.getenv().getOrDefault("EZKEY_ADMIN_API_URL", DEFAULT_ADMIN_API_URL);
    this.authApiUrl = System.getenv().getOrDefault("EZKEY_AUTH_API_URL", DEFAULT_AUTH_API_URL);
    this.cryptoApiUrl =
        System.getenv().getOrDefault("EZKEY_CRYPTO_API_URL", DEFAULT_CRYPTO_API_URL);

    ResolvedHealth adminHealth =
        resolveAdminOrAuthHealth(
            "EZKEY_ADMIN_ACTUATOR_URL",
            DEFAULT_ADMIN_ACTUATOR_URL,
            List.of(HA_ADMIN_ACTUATOR_1, HA_ADMIN_ACTUATOR_2),
            this.adminApiUrl);
    ResolvedHealth authHealth =
        resolveAdminOrAuthHealth(
            "EZKEY_AUTH_ACTUATOR_URL",
            DEFAULT_AUTH_ACTUATOR_URL,
            List.of(HA_AUTH_ACTUATOR_1, HA_AUTH_ACTUATOR_2),
            this.authApiUrl);

    this.adminActuatorUrl = adminHealth.actuatorBaseUrl();
    this.authActuatorUrl = authHealth.actuatorBaseUrl();
    this.adminHealthUrl = adminHealth.healthUrl();
    this.authHealthUrl = authHealth.healthUrl();
    this.cryptoHealthUrl = this.cryptoApiUrl + "/actuator/health";

    log.info("Docker Stack Configuration:");
    log.info("  Admin API: {}", this.adminApiUrl);
    log.info("  Auth API: {}", this.authApiUrl);
    log.info("  Crypto API: {}", this.cryptoApiUrl);
    log.info("  Admin Actuator: {}", this.adminActuatorUrl);
    log.info("  Auth Actuator: {}", this.authActuatorUrl);
    log.info("  Admin health probe: {}", this.adminHealthUrl);
    log.info("  Auth health probe: {}", this.authHealthUrl);
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
   * <p>Checks Admin API, Auth API, and Crypto API. On a standard stack this uses management
   * Actuator ports ({@code 9081}/{@code 8085}/{@code 9090}). On HA it prefers docker-dev
   * per-instance Actuator ports, then public instance-info through the load balancers.
   *
   * @throws IllegalStateException if any service is not healthy
   */
  public void verifyServicesHealthy() {
    log.info("Verifying Docker stack services are healthy...");

    verifyServiceHealthyUrl(adminHealthUrl, "Admin API");
    verifyServiceHealthyUrl(authHealthUrl, "Auth API");
    verifyServiceHealthyUrl(cryptoHealthUrl, "Crypto API");

    log.info("All Docker stack services are healthy");
  }

  /**
   * Resolves actuator base + concrete health URL for Admin or Auth.
   *
   * <p>Order: explicit env → standard management port → HA docker-dev management ports → public
   * instance-info on the API base URL (works through HAProxy without Actuator on the LB port).
   *
   * @param envKey environment variable for an explicit actuator base URL
   * @param standardActuatorUrl standard-stack management base URL
   * @param haActuatorUrls HA docker-dev management base URLs
   * @param apiBaseUrl load-balanced (or direct) API base URL
   * @return resolved health probe pair
   */
  private static ResolvedHealth resolveAdminOrAuthHealth(
      String envKey, String standardActuatorUrl, List<String> haActuatorUrls, String apiBaseUrl) {
    String fromEnv = System.getenv(envKey);
    if (fromEnv != null && !fromEnv.isBlank()) {
      String healthUrl = fromEnv + "/actuator/health";
      return new ResolvedHealth(fromEnv, healthUrl);
    }

    List<String> actuatorCandidates = new ArrayList<>();
    actuatorCandidates.add(standardActuatorUrl);
    actuatorCandidates.addAll(haActuatorUrls);

    for (String base : actuatorCandidates) {
      String healthUrl = base + "/actuator/health";
      if (isHttpOk(healthUrl)) {
        if (!base.equals(standardActuatorUrl)) {
          log.info(
              "Using HA-compatible Actuator health at {} (standard {} is not Actuator)",
              healthUrl,
              standardActuatorUrl + "/actuator/health");
        }
        return new ResolvedHealth(base, healthUrl);
      }
    }

    String publicProbe = apiBaseUrl + "/api/v1/public/instance-info";
    if (isHttpOk(publicProbe)) {
      log.info(
          "Using public instance-info health probe at {} (Actuator not reachable on host"
              + " management ports)",
          publicProbe);
      return new ResolvedHealth(standardActuatorUrl, publicProbe);
    }

    // Keep standard URL so verifyServicesHealthy() reports the primary expected probe.
    return new ResolvedHealth(standardActuatorUrl, standardActuatorUrl + "/actuator/health");
  }

  /**
   * Quiet HTTP GET that returns whether the URL responds with HTTP 200.
   *
   * @param url absolute URL to probe
   * @return {@code true} when status is 200
   */
  private static boolean isHttpOk(String url) {
    try {
      HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(url))
              .timeout(Duration.ofSeconds(3))
              .GET()
              .build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      return response != null && response.statusCode() == 200;
    } catch (Exception e) {
      return false;
    }
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

  /**
   * Resolved actuator base URL plus the concrete HTTP health probe URL.
   *
   * @param actuatorBaseUrl management base used for logging / env semantics
   * @param healthUrl full URL verified by {@link #verifyServicesHealthy()}
   */
  private record ResolvedHealth(String actuatorBaseUrl, String healthUrl) {}
}
