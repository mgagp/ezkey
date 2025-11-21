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

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.ezkey.tests.config.DockerStackConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;

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

  private final DockerStackConfig dockerStackConfig;
  private String adminToken;
  private final Map<String, String> apiKeys = new HashMap<>();

  /**
   * Creates a new AuthTokenManager.
   *
   * @param dockerStackConfig Docker stack configuration
   */
  public AuthTokenManager(DockerStackConfig dockerStackConfig) {
    this.dockerStackConfig = dockerStackConfig;
    // Try to get admin token from environment variable
    this.adminToken = System.getenv("EZKEY_ADMIN_TOKEN");
  }

  /**
   * Gets the admin bearer token.
   *
   * <p>Returns the token from environment variable if set, otherwise attempts to obtain via login
   * flow (requires device approval).
   *
   * @return Admin bearer token
   * @throws IllegalStateException if token cannot be obtained
   */
  public String getAdminToken() {
    if (adminToken != null && !adminToken.isEmpty()) {
      return adminToken;
    }

    // For now, throw exception - tests should set EZKEY_ADMIN_TOKEN environment variable
    // In future, could implement login flow with device approval
    throw new IllegalStateException(
        "Admin token not available. Set EZKEY_ADMIN_TOKEN environment variable or implement login flow.");
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
          "Login requires challenge approval. Use passwordless-wait endpoint or set EZKEY_ADMIN_TOKEN environment variable.");
    }

    throw new IllegalStateException(
        "Admin login failed. Status: " + response.getStatusCode() + ", Response: " + response.asString());
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

