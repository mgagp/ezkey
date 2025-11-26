/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: IntegrationManagementSecurityTest
 * Description: Security tests for integration CRUD operations
 */

package org.ezkey.tests.security.integration;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.ezkey.tests.util.RestAssuredTestConfig.configureForAdminApi;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.ezkey.tests.security.AbstractSecurityTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Security tests for integration CRUD operations.
 *
 * <p>Validates security aspects of integration management including:
 *
 * <ul>
 *   <li>Admin can perform all CRUD operations (200, 201, 204)
 *   <li>Unauthorized access returns 401
 *   <li>API keys cannot access integration endpoints (403)
 *   <li>Non-existent integrations return 404
 *   <li>Invalid data returns 400
 * </ul>
 *
 * <p>Note: These tests require admin token to create test data.
 *
 * @since 2025
 */
@DisplayName("Integration Management Security Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class IntegrationManagementSecurityTest extends AbstractSecurityTest {

  /**
   * Creates HTTP Basic Auth header for API key authentication.
   *
   * @param integrationKey Integration key (username)
   * @param secretKey Secret key (password)
   * @return Authorization header value
   */
  private String createApiKeyAuthHeader(String integrationKey, String secretKey) {
    String credentials = integrationKey + ":" + secretKey;
    String encoded = Base64.getEncoder().encodeToString(credentials.getBytes());
    return "Basic " + encoded;
  }

  /**
   * Helper method to create an API key for an integration.
   *
   * @param integrationId Integration ID
   * @param adminToken Admin bearer token
   * @return API key credentials as "integrationKey:secretKey"
   */
  private String createApiKeyForIntegration(Integer integrationId, String adminToken) {
    Map<String, Object> request = new HashMap<>();
    request.put("integrationId", integrationId);
    request.put("description", "Test API Key");

    Response response =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + adminToken)
            .body(request)
            .when()
            .post("/api-keys")
            .then()
            .statusCode(201)
            .extract()
            .response();

    String integrationKey = response.jsonPath().getString("integrationKey");
    String secretKey = response.jsonPath().getString("secretKey");

    return integrationKey + ":" + secretKey;
  }

  // ========== SUCCESS CASES (Admin with valid token) ==========

  @Test
  @Order(1)
  @DisplayName("Admin can list all integrations (200)")
  public void testAdminCanListIntegrations() {
    try {
      String adminToken = authTokenManager.getAdminToken();
      configureForAdminApi(dockerStackConfig);

      Response response =
          given()
              .contentType(ContentType.JSON)
              .header("Authorization", "Bearer " + adminToken)
              .when()
              .get("/integrations")
              .then()
              .extract()
              .response();

      assertThat(response.getStatusCode()).isEqualTo(200);
      List<Map<String, Object>> integrations = response.jsonPath().getList("");
      assertThat(integrations).isNotNull();
    } catch (IllegalStateException e) {
      org.junit.jupiter.api.Assumptions.assumeTrue(
          false, "Admin token not available. Set EZKEY_ADMIN_TOKEN environment variable.");
    }
  }

  @Test
  @Order(2)
  @DisplayName("Admin can get integration by ID (200)")
  public void testAdminCanGetIntegrationById() {
    try {
      String adminToken = authTokenManager.getAdminToken();
      configureForAdminApi(dockerStackConfig);

      // Create integration first
      Integer integrationId = testDataFactory.createIntegration();

      // Get integration by ID
      Response response =
          given()
              .contentType(ContentType.JSON)
              .header("Authorization", "Bearer " + adminToken)
              .when()
              .get("/integrations/" + integrationId)
              .then()
              .extract()
              .response();

      assertThat(response.getStatusCode()).isEqualTo(200);
      assertThat(response.jsonPath().getInt("id")).isEqualTo(integrationId);
      assertThat(response.jsonPath().getBoolean("active")).isNotNull();
    } catch (IllegalStateException e) {
      org.junit.jupiter.api.Assumptions.assumeTrue(
          false, "Admin token not available. Set EZKEY_ADMIN_TOKEN environment variable.");
    }
  }

  @Test
  @Order(3)
  @DisplayName("Admin can create integration with valid data (201)")
  public void testAdminCanCreateIntegration() {
    try {
      String adminToken = authTokenManager.getAdminToken();
      configureForAdminApi(dockerStackConfig);

      Map<String, Object> i18n = new HashMap<>();
      i18n.put("language", "en");
      i18n.put("name", "Test Integration");
      i18n.put("description", "Test Description");

      Map<String, Object> request = new HashMap<>();
      request.put("logo", "https://example.com/logo.png");
      request.put("i18n", new Object[] {i18n});

      Response response =
          given()
              .contentType(ContentType.JSON)
              .header("Authorization", "Bearer " + adminToken)
              .body(request)
              .when()
              .post("/integrations")
              .then()
              .extract()
              .response();

      assertThat(response.getStatusCode()).isEqualTo(201);
      Integer integrationId = response.jsonPath().getInt("id");
      assertThat(integrationId).isNotNull();

      // Verify integration was created by retrieving it
      Response getResponse =
          given()
              .contentType(ContentType.JSON)
              .header("Authorization", "Bearer " + adminToken)
              .when()
              .get("/integrations/" + integrationId)
              .then()
              .extract()
              .response();

      assertThat(getResponse.getStatusCode()).isEqualTo(200);
      assertThat(getResponse.jsonPath().getString("i18n[0].name")).isEqualTo("Test Integration");
    } catch (IllegalStateException e) {
      org.junit.jupiter.api.Assumptions.assumeTrue(
          false, "Admin token not available. Set EZKEY_ADMIN_TOKEN environment variable.");
    }
  }

  @Test
  @Order(4)
  @DisplayName("Admin can delete integration (204)")
  public void testAdminCanDeleteIntegration() {
    try {
      String adminToken = authTokenManager.getAdminToken();
      configureForAdminApi(dockerStackConfig);

      // Create integration first
      Integer integrationId = testDataFactory.createIntegration();

      // Delete integration
      Response deleteResponse =
          given()
              .contentType(ContentType.JSON)
              .header("Authorization", "Bearer " + adminToken)
              .when()
              .delete("/integrations/" + integrationId)
              .then()
              .extract()
              .response();

      assertThat(deleteResponse.getStatusCode()).isEqualTo(204);

      // Verify integration was deleted by trying to retrieve it
      Response getResponse =
          given()
              .contentType(ContentType.JSON)
              .header("Authorization", "Bearer " + adminToken)
              .when()
              .get("/integrations/" + integrationId)
              .then()
              .extract()
              .response();

      assertThat(getResponse.getStatusCode()).isEqualTo(404);
    } catch (IllegalStateException e) {
      org.junit.jupiter.api.Assumptions.assumeTrue(
          false, "Admin token not available. Set EZKEY_ADMIN_TOKEN environment variable.");
    }
  }

  // ========== AUTHENTICATION FAILURES (401) ==========

  @Test
  @Order(5)
  @DisplayName("Unauthorized access to GET /integrations returns 401")
  public void testUnauthorizedCannotListIntegrations() {
    configureForAdminApi(dockerStackConfig);

    Response response =
        given()
            .contentType(ContentType.JSON)
            .when()
            .get("/integrations")
            .then()
            .extract()
            .response();

    assertThat(response.getStatusCode()).isEqualTo(401);
  }

  @Test
  @Order(6)
  @DisplayName("Invalid token to GET /integrations returns 401")
  public void testInvalidTokenCannotListIntegrations() {
    configureForAdminApi(dockerStackConfig);

    Response response =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer invalid-token-12345")
            .when()
            .get("/integrations")
            .then()
            .extract()
            .response();

    assertThat(response.getStatusCode()).isEqualTo(401);
  }

  @Test
  @Order(7)
  @DisplayName("Unauthorized access to POST /integrations returns 401")
  public void testUnauthorizedCannotCreateIntegration() {
    configureForAdminApi(dockerStackConfig);

    Map<String, Object> request = new HashMap<>();
    request.put("logo", "https://example.com/logo.png");

    Response response =
        given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/integrations")
            .then()
            .extract()
            .response();

    assertThat(response.getStatusCode()).isEqualTo(401);
  }

  @Test
  @Order(8)
  @DisplayName("Unauthorized access to DELETE /integrations/{id} returns 401")
  public void testUnauthorizedCannotDeleteIntegration() {
    configureForAdminApi(dockerStackConfig);

    Response response =
        given()
            .contentType(ContentType.JSON)
            .when()
            .delete("/integrations/1")
            .then()
            .extract()
            .response();

    assertThat(response.getStatusCode()).isEqualTo(401);
  }

  // ========== AUTHORIZATION FAILURES (403) ==========

  @Test
  @Order(9)
  @DisplayName("API key cannot list integrations (403)")
  public void testApiKeyCannotListIntegrations() {
    try {
      String adminToken = authTokenManager.getAdminToken();
      configureForAdminApi(dockerStackConfig);

      // Create integration and API key
      Integer integrationId = testDataFactory.createIntegration();
      String apiKey = createApiKeyForIntegration(integrationId, adminToken);

      // Try to access integrations endpoint with API key
      Response response =
          given()
              .contentType(ContentType.JSON)
              .header(
                  "Authorization",
                  createApiKeyAuthHeader(apiKey.split(":")[0], apiKey.split(":")[1]))
              .when()
              .get("/integrations")
              .then()
              .extract()
              .response();

      assertThat(response.getStatusCode()).isEqualTo(403);
    } catch (IllegalStateException e) {
      org.junit.jupiter.api.Assumptions.assumeTrue(
          false, "Admin token not available. Set EZKEY_ADMIN_TOKEN environment variable.");
    }
  }

  @Test
  @Order(10)
  @DisplayName("API key cannot get integration by ID (403)")
  public void testApiKeyCannotGetIntegrationById() {
    try {
      String adminToken = authTokenManager.getAdminToken();
      configureForAdminApi(dockerStackConfig);

      // Create integration and API key
      Integer integrationId = testDataFactory.createIntegration();
      String apiKey = createApiKeyForIntegration(integrationId, adminToken);

      // Try to get integration with API key
      Response response =
          given()
              .contentType(ContentType.JSON)
              .header(
                  "Authorization",
                  createApiKeyAuthHeader(apiKey.split(":")[0], apiKey.split(":")[1]))
              .when()
              .get("/integrations/" + integrationId)
              .then()
              .extract()
              .response();

      assertThat(response.getStatusCode()).isEqualTo(403);
    } catch (IllegalStateException e) {
      org.junit.jupiter.api.Assumptions.assumeTrue(
          false, "Admin token not available. Set EZKEY_ADMIN_TOKEN environment variable.");
    }
  }

  @Test
  @Order(11)
  @DisplayName("API key cannot create integration (403)")
  public void testApiKeyCannotCreateIntegration() {
    try {
      String adminToken = authTokenManager.getAdminToken();
      configureForAdminApi(dockerStackConfig);

      // Create integration and API key
      Integer integrationId = testDataFactory.createIntegration();
      String apiKey = createApiKeyForIntegration(integrationId, adminToken);

      Map<String, Object> request = new HashMap<>();
      request.put("logo", "https://example.com/logo.png");

      // Try to create integration with API key
      Response response =
          given()
              .contentType(ContentType.JSON)
              .header(
                  "Authorization",
                  createApiKeyAuthHeader(apiKey.split(":")[0], apiKey.split(":")[1]))
              .body(request)
              .when()
              .post("/integrations")
              .then()
              .extract()
              .response();

      assertThat(response.getStatusCode()).isEqualTo(403);
    } catch (IllegalStateException e) {
      org.junit.jupiter.api.Assumptions.assumeTrue(
          false, "Admin token not available. Set EZKEY_ADMIN_TOKEN environment variable.");
    }
  }

  @Test
  @Order(12)
  @DisplayName("API key cannot delete integration (403)")
  public void testApiKeyCannotDeleteIntegration() {
    try {
      String adminToken = authTokenManager.getAdminToken();
      configureForAdminApi(dockerStackConfig);

      // Create integration and API key
      Integer integrationId = testDataFactory.createIntegration();
      String apiKey = createApiKeyForIntegration(integrationId, adminToken);

      // Try to delete integration with API key
      Response response =
          given()
              .contentType(ContentType.JSON)
              .header(
                  "Authorization",
                  createApiKeyAuthHeader(apiKey.split(":")[0], apiKey.split(":")[1]))
              .when()
              .delete("/integrations/" + integrationId)
              .then()
              .extract()
              .response();

      assertThat(response.getStatusCode()).isEqualTo(403);
    } catch (IllegalStateException e) {
      org.junit.jupiter.api.Assumptions.assumeTrue(
          false, "Admin token not available. Set EZKEY_ADMIN_TOKEN environment variable.");
    }
  }

  // ========== NOT FOUND CASES (404) ==========

  @Test
  @Order(13)
  @DisplayName("GET non-existent integration returns 404")
  public void testGetNonExistentIntegrationReturns404() {
    try {
      String adminToken = authTokenManager.getAdminToken();
      configureForAdminApi(dockerStackConfig);

      // Use a non-existent ID (99999)
      Response response =
          given()
              .contentType(ContentType.JSON)
              .header("Authorization", "Bearer " + adminToken)
              .when()
              .get("/integrations/99999")
              .then()
              .extract()
              .response();

      assertThat(response.getStatusCode()).isEqualTo(404);
    } catch (IllegalStateException e) {
      org.junit.jupiter.api.Assumptions.assumeTrue(
          false, "Admin token not available. Set EZKEY_ADMIN_TOKEN environment variable.");
    }
  }

  @Test
  @Order(14)
  @DisplayName("DELETE non-existent integration returns 404")
  public void testDeleteNonExistentIntegrationReturns404() {
    try {
      String adminToken = authTokenManager.getAdminToken();
      configureForAdminApi(dockerStackConfig);

      // Use a non-existent ID (99999)
      Response response =
          given()
              .contentType(ContentType.JSON)
              .header("Authorization", "Bearer " + adminToken)
              .when()
              .delete("/integrations/99999")
              .then()
              .extract()
              .response();

      assertThat(response.getStatusCode()).isEqualTo(404);
    } catch (IllegalStateException e) {
      org.junit.jupiter.api.Assumptions.assumeTrue(
          false, "Admin token not available. Set EZKEY_ADMIN_TOKEN environment variable.");
    }
  }

  // ========== VALIDATION FAILURES (400) ==========

  @Test
  @Order(15)
  @DisplayName("Create integration without i18n succeeds (i18n is optional)")
  public void testCreateIntegrationWithoutI18nSucceeds() {
    try {
      String adminToken = authTokenManager.getAdminToken();
      configureForAdminApi(dockerStackConfig);

      Map<String, Object> request = new HashMap<>();
      request.put("logo", "https://example.com/logo.png");
      // i18n is optional - System Integration is created without i18n

      Response response =
          given()
              .contentType(ContentType.JSON)
              .header("Authorization", "Bearer " + adminToken)
              .body(request)
              .when()
              .post("/integrations")
              .then()
              .extract()
              .response();

      // i18n is optional, so creation should succeed
      assertThat(response.getStatusCode()).isEqualTo(201);
      Integer integrationId = response.jsonPath().getInt("id");
      assertThat(integrationId).isNotNull();

      // Verify integration was created without i18n
      Response getResponse =
          given()
              .contentType(ContentType.JSON)
              .header("Authorization", "Bearer " + adminToken)
              .when()
              .get("/integrations/" + integrationId)
              .then()
              .extract()
              .response();

      assertThat(getResponse.getStatusCode()).isEqualTo(200);
      // i18n list should be empty or null
      List<Map<String, Object>> i18n = getResponse.jsonPath().getList("i18n");
      assertThat(i18n).isNullOrEmpty();
    } catch (IllegalStateException e) {
      org.junit.jupiter.api.Assumptions.assumeTrue(
          false, "Admin token not available. Set EZKEY_ADMIN_TOKEN environment variable.");
    }
  }

  @Test
  @Order(16)
  @DisplayName("Create integration with invalid i18n data returns 400")
  public void testCreateIntegrationInvalidI18nReturns400() {
    try {
      String adminToken = authTokenManager.getAdminToken();
      configureForAdminApi(dockerStackConfig);

      Map<String, Object> i18n = new HashMap<>();
      // Missing required fields: name, description
      i18n.put("language", "en");

      Map<String, Object> request = new HashMap<>();
      request.put("logo", "https://example.com/logo.png");
      request.put("i18n", new Object[] {i18n});

      Response response =
          given()
              .contentType(ContentType.JSON)
              .header("Authorization", "Bearer " + adminToken)
              .body(request)
              .when()
              .post("/integrations")
              .then()
              .extract()
              .response();

      assertThat(response.getStatusCode()).isEqualTo(400);
    } catch (IllegalStateException e) {
      org.junit.jupiter.api.Assumptions.assumeTrue(
          false, "Admin token not available. Set EZKEY_ADMIN_TOKEN environment variable.");
    }
  }
}
