/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Utility: AuthTokenManager
 * Description: Manages admin tokens and API keys for tests
 */

package org.ezkey.tests.util;

import static io.restassured.RestAssured.given;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import org.ezkey.tests.config.DockerStackConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages authentication tokens and API keys for test execution.
 *
 * <p>Provides methods to obtain and manage admin bearer tokens and API keys needed for
 * authenticated test requests. Supports both programmatic token acquisition and manual token
 * injection for testing scenarios.
 *
 * <p>Note: Admin authentication uses passwordless flow which requires device approval. For tests,
 * tokens can be:
 *
 * <ul>
 *   <li>Obtained via environment variable (EZKEY_ADMIN_TOKEN) for manual testing
 *   <li>Obtained via login flow (requires device approval in Docker stack)
 *   <li>Pre-configured for specific test scenarios
 * </ul>
 *
 * @since 2025
 */
public class AuthTokenManager {

  private static final Logger log = LoggerFactory.getLogger(AuthTokenManager.class);

  private static final String TOKEN_FILE_PATH = ".ezkey-test/admin-token.json";

  private final DockerStackConfig dockerStackConfig;
  private String adminToken;
  private final Map<String, String> apiKeys = new HashMap<>();
  private AdminBootstrapService bootstrapService;

  // Dependencies stored for bootstrap service (used indirectly via bootstrapService)
  @SuppressWarnings("unused")
  private BootstrapCredentialsExtractor bootstrapCredentialsExtractor;

  @SuppressWarnings("unused")
  private CryptoApiClient cryptoApiClient;

  /**
   * Creates a new AuthTokenManager.
   *
   * @param dockerStackConfig Docker stack configuration
   */
  public AuthTokenManager(DockerStackConfig dockerStackConfig) {
    this.dockerStackConfig = dockerStackConfig;
    // Try to get admin token from environment variable (priority 1)
    this.adminToken = System.getenv("EZKEY_ADMIN_TOKEN");
  }

  /**
   * Sets the bootstrap service dependencies for automatic token acquisition.
   *
   * @param bootstrapCredentialsExtractor Bootstrap credentials extractor
   * @param cryptoApiClient Crypto API client
   */
  public void setBootstrapDependencies(
      BootstrapCredentialsExtractor bootstrapCredentialsExtractor,
      CryptoApiClient cryptoApiClient) {
    this.bootstrapCredentialsExtractor = bootstrapCredentialsExtractor;
    this.cryptoApiClient = cryptoApiClient;
    if (bootstrapCredentialsExtractor != null && cryptoApiClient != null) {
      this.bootstrapService =
          new AdminBootstrapService(
              dockerStackConfig, bootstrapCredentialsExtractor, cryptoApiClient);
    }
  }

  /**
   * Gets the admin bearer token.
   *
   * <p>Token acquisition follows this priority order:
   *
   * <ol>
   *   <li>Environment variable EZKEY_ADMIN_TOKEN (highest priority)
   *   <li>Cached token from .ezkey-test/admin-token.json file
   *   <li>Automatic bootstrap via AdminBootstrapService (if dependencies set)
   * </ol>
   *
   * @return Admin bearer token
   * @throws IllegalStateException if token cannot be obtained
   */
  public String getAdminToken() {
    // Priority 1: Environment variable
    if (adminToken != null && !adminToken.isEmpty()) {
      return adminToken;
    }

    // Priority 2: Load from cache file
    String cachedToken = loadTokenFromFile();
    if (cachedToken != null && !cachedToken.isEmpty()) {
      this.adminToken = cachedToken;
      log.debug("Using cached admin token from file");
      return cachedToken;
    }

    // Priority 3: Automatic bootstrap (if dependencies available)
    if (bootstrapService != null) {
      try {
        String token = bootstrapService.ensureAdminToken();
        this.adminToken = token;
        return token;
      } catch (Exception e) {
        log.warn("Automatic bootstrap failed: {}", e.getMessage());
        // Fall through to throw exception
      }
    }

    throw new IllegalStateException(
        "Admin token not available. Set EZKEY_ADMIN_TOKEN environment variable, run"
            + " AdminTokenCreationTest to create token, or ensure bootstrap dependencies are set.");
  }

  /**
   * Loads admin token from cache file.
   *
   * @return Admin token, or null if not found
   */
  private String loadTokenFromFile() {
    Path tokenPath = Paths.get(TOKEN_FILE_PATH);
    if (!Files.exists(tokenPath)) {
      return null;
    }

    try {
      ObjectMapper mapper = new ObjectMapper();
      ObjectNode jsonNode = (ObjectNode) mapper.readTree(tokenPath.toFile());
      return jsonNode.get("token").asText();
    } catch (IOException e) {
      log.warn("Failed to load token from file: {}", e.getMessage());
      return null;
    }
  }

  /**
   * Sets the admin bearer token manually.
   *
   * <p>Useful for tests that obtain tokens through other means.
   *
   * @param token Admin bearer token
   */
  public void setAdminToken(String token) {
    this.adminToken = token;
    log.debug("Admin token set manually");
  }

  /**
   * Attempts to login and obtain admin token via passwordless flow.
   *
   * <p>Note: This requires device approval in the Docker stack, so it may not be suitable for all
   * test scenarios. Consider using environment variable instead.
   *
   * @param username Admin username
   * @param challengeRequested Whether challenge is requested
   * @return Admin bearer token if login succeeds
   * @throws IllegalStateException if login fails
   */
  public String loginAdmin(String username, Boolean challengeRequested) {
    log.info("Attempting admin login for user: {}", username);

    RestAssuredTestConfig.configureForAdminApi(dockerStackConfig);

    Map<String, Object> request = new HashMap<>();
    request.put("username", username);
    if (challengeRequested != null) {
      request.put("challengeRequested", challengeRequested);
    }

    Response response =
        given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/admin/auth/login")
            .then()
            .extract()
            .response();

    if (response.getStatusCode() == 200) {
      String token = response.jsonPath().getString("token");
      if (token != null && !token.isEmpty()) {
        this.adminToken = token;
        log.info("Admin login successful");
        return token;
      }
    }

    // If challenge mode, need to wait separately
    if (response.getStatusCode() == 202) {
      Integer authAttemptId = response.jsonPath().getInt("authAttemptId");
      String challengeCode = response.jsonPath().getString("challengeCode");
      log.info(
          "Login requires challenge. Auth attempt ID: {}, Challenge code: {}",
          authAttemptId,
          challengeCode);
      throw new IllegalStateException(
          "Login requires challenge approval. Use passwordless-wait endpoint or set"
              + " EZKEY_ADMIN_TOKEN environment variable.");
    }

    throw new IllegalStateException(
        "Admin login failed. Status: "
            + response.getStatusCode()
            + ", Response: "
            + response.asString());
  }

  /**
   * Stores an API key for a given integration.
   *
   * @param integrationId Integration ID
   * @param apiKey API key value
   */
  public void setApiKey(Integer integrationId, String apiKey) {
    this.apiKeys.put(String.valueOf(integrationId), apiKey);
    log.debug("API key stored for integration: {}", integrationId);
  }

  /**
   * Gets an API key for a given integration.
   *
   * @param integrationId Integration ID
   * @return API key, or null if not set
   */
  public String getApiKey(Integer integrationId) {
    return this.apiKeys.get(String.valueOf(integrationId));
  }
}
