/*
 * Ezkey - Open Source Cryptographic MFA Platform
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
import org.ezkey.tests.tags.TestTags;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
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
@Tag(TestTags.FAST)
@Tag(TestTags.INTEGRATION)
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
      // Response is now paginated - content is in "content" field
      List<Map<String, Object>> integrations = response.jsonPath().getList("content");
      assertThat(integrations).isNotNull();
      // Operator list enrichment (I-2026-0014): list rows with tenantId must expose tenantName
      for (Map<String, Object> row : integrations) {
        if (row.get("tenantId") != null) {
          assertThat(row.get("tenantName"))
              .as("integration list row tenantName for tenantId=%s", row.get("tenantId"))
              .isInstanceOf(String.class);
          assertThat(((String) row.get("tenantName")).trim()).isNotEmpty();
        }
      }
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
      assertThat(response.jsonPath().getBoolean("operational")).isNotNull();
      assertThat(response.jsonPath().getString("lifecycleStatus")).isEqualTo("ACTIVE");
      assertThat(response.jsonPath().getInt("tenantId")).isPositive();
      assertThat(response.jsonPath().getString("tenantName")).isNotBlank();
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

      // Generate unique code using UUID
      String code = "test-" + java.util.UUID.randomUUID().toString().substring(0, 8);

      Map<String, Object> request = new HashMap<>();
      request.put("code", code);
      request.put("name", "Test Integration");
      request.put("description", "Test Description");

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
      assertThat(getResponse.jsonPath().getString("name")).isEqualTo("Test Integration");
    } catch (IllegalStateException e) {
      org.junit.jupiter.api.Assumptions.assumeTrue(
          false, "Admin token not available. Set EZKEY_ADMIN_TOKEN environment variable.");
    }
  }

  @Test
  @Order(4)
  @DisplayName("Admin can retire then delete an unused integration (204)")
  public void testAdminCanDeleteIntegration() {
    try {
      String adminToken = authTokenManager.getAdminToken();
      configureForAdminApi(dockerStackConfig);

      // Create integration first
      Integer integrationId = testDataFactory.createIntegration();

      Response retireResponse =
          given()
              .contentType(ContentType.JSON)
              .header("Authorization", "Bearer " + adminToken)
              .queryParam("reason", "Retiring unused integration for lifecycle test")
              .when()
              .post("/integrations/" + integrationId + "/retire")
              .then()
              .extract()
              .response();

      assertThat(retireResponse.getStatusCode()).isEqualTo(204);

      Response retiredGetResponse =
          given()
              .contentType(ContentType.JSON)
              .header("Authorization", "Bearer " + adminToken)
              .when()
              .get("/integrations/" + integrationId)
              .then()
              .extract()
              .response();

      assertThat(retiredGetResponse.getStatusCode()).isEqualTo(200);
      assertThat(retiredGetResponse.jsonPath().getString("lifecycleStatus")).isEqualTo("RETIRED");

      Response defaultListResponse =
          given()
              .contentType(ContentType.JSON)
              .header("Authorization", "Bearer " + adminToken)
              .when()
              .get("/integrations")
              .then()
              .extract()
              .response();

      List<Integer> defaultIds = defaultListResponse.jsonPath().getList("content.id");
      assertThat(defaultIds).doesNotContain(integrationId);

      Response listWithRetiredResponse =
          given()
              .contentType(ContentType.JSON)
              .header("Authorization", "Bearer " + adminToken)
              .queryParam("includeRetired", true)
              .when()
              .get("/integrations")
              .then()
              .extract()
              .response();

      List<Integer> idsWithRetired = listWithRetiredResponse.jsonPath().getList("content.id");
      assertThat(idsWithRetired).contains(integrationId);

      Response deleteResponse =
          given()
              .contentType(ContentType.JSON)
              .header("Authorization", "Bearer " + adminToken)
              .queryParam("reason", "Deleting retired unused integration for lifecycle test")
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

      // Generate unique code using UUID; send valid payload so request reaches authorization
      String code = "test-" + java.util.UUID.randomUUID().toString().substring(0, 8);

      Map<String, Object> request = new HashMap<>();
      request.put("code", code);
      request.put("name", "API Key Blocked Integration");

      // Try to create integration with API key (expect 403 Forbidden)
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
              .queryParam("reason", "Integration decommissioned during functional test run")
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
  @DisplayName("DELETE integration with enrollments returns 409")
  public void testDeleteIntegrationWithEnrollmentsReturns409() {
    try {
      String adminToken = authTokenManager.getAdminToken();
      configureForAdminApi(dockerStackConfig);

      Integer integrationId = testDataFactory.createIntegration();
      testDataFactory.createEnrollment(integrationId, "Delete Constraint Test Device", false);

      Response retireResponse =
          given()
              .contentType(ContentType.JSON)
              .header("Authorization", "Bearer " + adminToken)
              .queryParam("reason", "Retiring integration before exceptional delete attempt")
              .when()
              .post("/integrations/" + integrationId + "/retire")
              .then()
              .extract()
              .response();

      assertThat(retireResponse.getStatusCode()).isEqualTo(204);

      Response response =
          given()
              .contentType(ContentType.JSON)
              .header("Authorization", "Bearer " + adminToken)
              .queryParam("reason", "Test delete blocked by enrollments")
              .when()
              .delete("/integrations/" + integrationId)
              .then()
              .extract()
              .response();

      assertThat(response.getStatusCode()).isEqualTo(409);
      String body = response.getBody().asString();
      assertThat(body).contains("enrollments");
      assertThat(body).contains("Cannot delete integration: it has one or more enrollments");

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
    } catch (IllegalStateException e) {
      org.junit.jupiter.api.Assumptions.assumeTrue(
          false, "Admin token not available. Set EZKEY_ADMIN_TOKEN environment variable.");
    }
  }

  @Test
  @Order(15)
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
              .queryParam("reason", "Integration decommissioned during functional test run")
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
  @Order(16)
  @DisplayName("Create integration with minimal required fields (code, name) succeeds")
  public void testCreateIntegrationWithMinimalFieldsSucceeds() {
    try {
      String adminToken = authTokenManager.getAdminToken();
      configureForAdminApi(dockerStackConfig);

      String code = "test-" + java.util.UUID.randomUUID().toString().substring(0, 8);
      Map<String, Object> request = new HashMap<>();
      request.put("code", code);
      request.put("name", "Minimal Integration");

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
      assertThat(getResponse.jsonPath().getString("name")).isEqualTo("Minimal Integration");
    } catch (IllegalStateException e) {
      org.junit.jupiter.api.Assumptions.assumeTrue(
          false, "Admin token not available. Set EZKEY_ADMIN_TOKEN environment variable.");
    }
  }

  @Test
  @Order(17)
  @DisplayName("Create integration with blank name returns 400")
  public void testCreateIntegrationBlankNameReturns400() {
    try {
      String adminToken = authTokenManager.getAdminToken();
      configureForAdminApi(dockerStackConfig);

      Map<String, Object> request = new HashMap<>();
      request.put("code", "test-" + java.util.UUID.randomUUID().toString().substring(0, 8));
      request.put("name", "   ");

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
