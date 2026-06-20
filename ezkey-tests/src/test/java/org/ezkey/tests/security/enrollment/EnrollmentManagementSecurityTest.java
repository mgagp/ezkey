/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: EnrollmentManagementSecurityTest
 * Description: Security tests for enrollment CRUD operations
 */

package org.ezkey.tests.security.enrollment;

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
 * Security tests for enrollment CRUD operations.
 *
 * <p>Validates security aspects of enrollment management including:
 *
 * <ul>
 *   <li>Admin can perform all CRUD operations (200, 201, 204)
 *   <li>Unauthorized access returns 401
 *   <li>API keys cannot access enrollment endpoints (403)
 *   <li>Non-existent enrollments return 404
 *   <li>Invalid data returns 400
 * </ul>
 *
 * <p>Note: These tests require admin token to create test data.
 *
 * @since 2025
 */
@Tag(TestTags.FAST)
@Tag(TestTags.ENROLLMENT)
@DisplayName("Enrollment Management Security Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class EnrollmentManagementSecurityTest extends AbstractSecurityTest {

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
  @DisplayName("Admin can list all enrollments (200)")
  public void testAdminCanListEnrollments() {
    try {
      String adminToken = authTokenManager.getAdminToken();
      configureForAdminApi(dockerStackConfig);

      Response response =
          given()
              .contentType(ContentType.JSON)
              .header("Authorization", "Bearer " + adminToken)
              .when()
              .get("/enrollments")
              .then()
              .extract()
              .response();

      assertThat(response.getStatusCode()).isEqualTo(200);
      // Response is now paginated - content is in "content" field
      List<Map<String, Object>> enrollments = response.jsonPath().getList("content");
      assertThat(enrollments).isNotNull();
      for (Map<String, Object> row : enrollments) {
        if (row.get("integrationId") != null) {
          assertThat(row.get("integrationName"))
              .as(
                  "enrollment list row integrationName for integrationId=%s",
                  row.get("integrationId"))
              .isInstanceOf(String.class);
          assertThat(((String) row.get("integrationName")).trim()).isNotEmpty();
          if (row.get("tenantId") != null) {
            assertThat(row.get("tenantName"))
                .as("enrollment list row tenantName for tenantId=%s", row.get("tenantId"))
                .isInstanceOf(String.class);
            assertThat(((String) row.get("tenantName")).trim()).isNotEmpty();
          }
        }
      }
    } catch (IllegalStateException e) {
      org.junit.jupiter.api.Assumptions.assumeTrue(
          false, "Admin token not available. Set EZKEY_ADMIN_TOKEN environment variable.");
    }
  }

  @Test
  @Order(2)
  @DisplayName("Admin can get enrollment by ID (200)")
  public void testAdminCanGetEnrollmentById() {
    try {
      String adminToken = authTokenManager.getAdminToken();
      configureForAdminApi(dockerStackConfig);

      // Create integration and enrollment first
      Integer integrationId = testDataFactory.createIntegration();
      Integer enrollmentId = testDataFactory.createEnrollment(integrationId);

      // Get enrollment by ID
      Response response =
          given()
              .contentType(ContentType.JSON)
              .header("Authorization", "Bearer " + adminToken)
              .when()
              .get("/enrollments/" + enrollmentId)
              .then()
              .extract()
              .response();

      assertThat(response.getStatusCode()).isEqualTo(200);
      assertThat(response.jsonPath().getInt("enrollmentId")).isEqualTo(enrollmentId);
      assertThat(response.jsonPath().getInt("integrationId")).isEqualTo(integrationId);
      assertThat(response.jsonPath().getString("integrationName")).isNotBlank();
      assertThat(response.jsonPath().getInt("tenantId")).isPositive();
      assertThat(response.jsonPath().getString("tenantName")).isNotBlank();
    } catch (IllegalStateException e) {
      org.junit.jupiter.api.Assumptions.assumeTrue(
          false, "Admin token not available. Set EZKEY_ADMIN_TOKEN environment variable.");
    }
  }

  @Test
  @Order(3)
  @DisplayName("Admin can create enrollment with valid data (201)")
  public void testAdminCanCreateEnrollment() {
    try {
      String adminToken = authTokenManager.getAdminToken();
      configureForAdminApi(dockerStackConfig);

      // Create integration first
      Integer integrationId = testDataFactory.createIntegration();

      Map<String, Object> request = new HashMap<>();
      request.put("integrationId", integrationId);
      request.put("name", "Test Enrollment");
      request.put("authAttemptChallengeRequired", false);

      Response response =
          given()
              .contentType(ContentType.JSON)
              .header("Authorization", "Bearer " + adminToken)
              .body(request)
              .when()
              .post("/enrollments")
              .then()
              .extract()
              .response();

      assertThat(response.getStatusCode()).isEqualTo(201);
      Integer enrollmentId = response.jsonPath().getInt("enrollmentId");
      assertThat(enrollmentId).isNotNull();
      assertThat(response.jsonPath().getInt("enrollmentChallenge")).isNotNull();

      // Verify enrollment was created by retrieving it
      Response getResponse =
          given()
              .contentType(ContentType.JSON)
              .header("Authorization", "Bearer " + adminToken)
              .when()
              .get("/enrollments/" + enrollmentId)
              .then()
              .extract()
              .response();

      assertThat(getResponse.getStatusCode()).isEqualTo(200);
      assertThat(getResponse.jsonPath().getString("enrollmentName")).isEqualTo("Test Enrollment");
    } catch (IllegalStateException e) {
      org.junit.jupiter.api.Assumptions.assumeTrue(
          false, "Admin token not available. Set EZKEY_ADMIN_TOKEN environment variable.");
    }
  }

  @Test
  @Order(4)
  @DisplayName("Admin can delete enrollment (204)")
  public void testAdminCanDeleteEnrollment() {
    try {
      String adminToken = authTokenManager.getAdminToken();
      configureForAdminApi(dockerStackConfig);

      // Create integration and enrollment first
      Integer integrationId = testDataFactory.createIntegration();
      Integer enrollmentId = testDataFactory.createEnrollment(integrationId);

      // Delete enrollment
      Response deleteResponse =
          given()
              .contentType(ContentType.JSON)
              .header("Authorization", "Bearer " + adminToken)
              .queryParam("reason", "Enrollment decommissioned during functional test run")
              .when()
              .delete("/enrollments/" + enrollmentId)
              .then()
              .extract()
              .response();

      assertThat(deleteResponse.getStatusCode()).isEqualTo(204);

      // Verify enrollment was deleted by trying to retrieve it
      Response getResponse =
          given()
              .contentType(ContentType.JSON)
              .header("Authorization", "Bearer " + adminToken)
              .when()
              .get("/enrollments/" + enrollmentId)
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
  @DisplayName("Unauthorized access to GET /enrollments returns 401")
  public void testUnauthorizedCannotListEnrollments() {
    configureForAdminApi(dockerStackConfig);

    Response response =
        given()
            .contentType(ContentType.JSON)
            .when()
            .get("/enrollments")
            .then()
            .extract()
            .response();

    assertThat(response.getStatusCode()).isEqualTo(401);
  }

  @Test
  @Order(6)
  @DisplayName("Invalid token to GET /enrollments returns 401")
  public void testInvalidTokenCannotListEnrollments() {
    configureForAdminApi(dockerStackConfig);

    Response response =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer invalid-token-12345")
            .when()
            .get("/enrollments")
            .then()
            .extract()
            .response();

    assertThat(response.getStatusCode()).isEqualTo(401);
  }

  @Test
  @Order(7)
  @DisplayName("Unauthorized access to POST /enrollments returns 401")
  public void testUnauthorizedCannotCreateEnrollment() {
    configureForAdminApi(dockerStackConfig);

    Map<String, Object> request = new HashMap<>();
    request.put("integrationId", 1);
    request.put("name", "Test Enrollment");

    Response response =
        given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/enrollments")
            .then()
            .extract()
            .response();

    assertThat(response.getStatusCode()).isEqualTo(401);
  }

  @Test
  @Order(8)
  @DisplayName("Unauthorized access to DELETE /enrollments/{id} returns 401")
  public void testUnauthorizedCannotDeleteEnrollment() {
    configureForAdminApi(dockerStackConfig);

    Response response =
        given()
            .contentType(ContentType.JSON)
            .when()
            .delete("/enrollments/1")
            .then()
            .extract()
            .response();

    assertThat(response.getStatusCode()).isEqualTo(401);
  }

  // ========== AUTHORIZATION FAILURES (403) ==========

  @Test
  @Order(9)
  @DisplayName("API key cannot list enrollments (403)")
  public void testApiKeyCannotListEnrollments() {
    try {
      String adminToken = authTokenManager.getAdminToken();
      configureForAdminApi(dockerStackConfig);

      // Create integration and API key
      Integer integrationId = testDataFactory.createIntegration();
      String apiKey = createApiKeyForIntegration(integrationId, adminToken);

      // Try to access enrollments endpoint with API key
      Response response =
          given()
              .contentType(ContentType.JSON)
              .header(
                  "Authorization",
                  createApiKeyAuthHeader(apiKey.split(":")[0], apiKey.split(":")[1]))
              .when()
              .get("/enrollments")
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
  @DisplayName("API key cannot get enrollment by ID (403)")
  public void testApiKeyCannotGetEnrollmentById() {
    try {
      String adminToken = authTokenManager.getAdminToken();
      configureForAdminApi(dockerStackConfig);

      // Create integration, enrollment, and API key
      Integer integrationId = testDataFactory.createIntegration();
      Integer enrollmentId = testDataFactory.createEnrollment(integrationId);
      String apiKey = createApiKeyForIntegration(integrationId, adminToken);

      // Try to get enrollment with API key
      Response response =
          given()
              .contentType(ContentType.JSON)
              .header(
                  "Authorization",
                  createApiKeyAuthHeader(apiKey.split(":")[0], apiKey.split(":")[1]))
              .when()
              .get("/enrollments/" + enrollmentId)
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
  @DisplayName("API key cannot create enrollment (403)")
  public void testApiKeyCannotCreateEnrollment() {
    try {
      String adminToken = authTokenManager.getAdminToken();
      configureForAdminApi(dockerStackConfig);

      // Create integration and API key
      Integer integrationId = testDataFactory.createIntegration();
      String apiKey = createApiKeyForIntegration(integrationId, adminToken);

      Map<String, Object> request = new HashMap<>();
      request.put("integrationId", integrationId);
      request.put("name", "Test Enrollment");

      // Try to create enrollment with API key
      Response response =
          given()
              .contentType(ContentType.JSON)
              .header(
                  "Authorization",
                  createApiKeyAuthHeader(apiKey.split(":")[0], apiKey.split(":")[1]))
              .body(request)
              .when()
              .post("/enrollments")
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
  @DisplayName("API key cannot delete enrollment (403)")
  public void testApiKeyCannotDeleteEnrollment() {
    try {
      String adminToken = authTokenManager.getAdminToken();
      configureForAdminApi(dockerStackConfig);

      // Create integration, enrollment, and API key
      Integer integrationId = testDataFactory.createIntegration();
      Integer enrollmentId = testDataFactory.createEnrollment(integrationId);
      String apiKey = createApiKeyForIntegration(integrationId, adminToken);

      // Try to delete enrollment with API key
      Response response =
          given()
              .contentType(ContentType.JSON)
              .header(
                  "Authorization",
                  createApiKeyAuthHeader(apiKey.split(":")[0], apiKey.split(":")[1]))
              .queryParam("reason", "Enrollment decommissioned during functional test run")
              .when()
              .delete("/enrollments/" + enrollmentId)
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
  @DisplayName("GET non-existent enrollment returns 404")
  public void testGetNonExistentEnrollmentReturns404() {
    try {
      String adminToken = authTokenManager.getAdminToken();
      configureForAdminApi(dockerStackConfig);

      // Use a non-existent ID (99999)
      Response response =
          given()
              .contentType(ContentType.JSON)
              .header("Authorization", "Bearer " + adminToken)
              .when()
              .get("/enrollments/99999")
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
  @DisplayName("DELETE non-existent enrollment returns 404")
  public void testDeleteNonExistentEnrollmentReturns404() {
    try {
      String adminToken = authTokenManager.getAdminToken();
      configureForAdminApi(dockerStackConfig);

      // Use a non-existent ID (99999)
      Response response =
          given()
              .contentType(ContentType.JSON)
              .header("Authorization", "Bearer " + adminToken)
              .queryParam("reason", "Enrollment decommissioned during functional test run")
              .when()
              .delete("/enrollments/99999")
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
  @DisplayName("Create enrollment with missing integrationId returns 400")
  public void testCreateEnrollmentMissingIntegrationIdReturns400() {
    try {
      String adminToken = authTokenManager.getAdminToken();
      configureForAdminApi(dockerStackConfig);

      Map<String, Object> request = new HashMap<>();
      request.put("name", "Test Enrollment");
      // Missing integrationId

      Response response =
          given()
              .contentType(ContentType.JSON)
              .header("Authorization", "Bearer " + adminToken)
              .body(request)
              .when()
              .post("/enrollments")
              .then()
              .extract()
              .response();

      assertThat(response.getStatusCode()).isEqualTo(400);
    } catch (IllegalStateException e) {
      org.junit.jupiter.api.Assumptions.assumeTrue(
          false, "Admin token not available. Set EZKEY_ADMIN_TOKEN environment variable.");
    }
  }

  @Test
  @Order(16)
  @DisplayName("Create enrollment with non-existent integrationId returns 400")
  public void testCreateEnrollmentNonExistentIntegrationIdReturns400() {
    try {
      String adminToken = authTokenManager.getAdminToken();
      configureForAdminApi(dockerStackConfig);

      Map<String, Object> request = new HashMap<>();
      request.put("integrationId", 99999); // Non-existent integration
      request.put("name", "Test Enrollment");

      Response response =
          given()
              .contentType(ContentType.JSON)
              .header("Authorization", "Bearer " + adminToken)
              .body(request)
              .when()
              .post("/enrollments")
              .then()
              .extract()
              .response();

      assertThat(response.getStatusCode()).isEqualTo(400);
    } catch (IllegalStateException e) {
      org.junit.jupiter.api.Assumptions.assumeTrue(
          false, "Admin token not available. Set EZKEY_ADMIN_TOKEN environment variable.");
    }
  }

  @Test
  @Order(17)
  @DisplayName("Create enrollment with missing name returns 400")
  public void testCreateEnrollmentMissingNameReturns400() {
    try {
      String adminToken = authTokenManager.getAdminToken();
      configureForAdminApi(dockerStackConfig);

      // Create integration first
      Integer integrationId = testDataFactory.createIntegration();

      Map<String, Object> request = new HashMap<>();
      request.put("integrationId", integrationId);
      // Missing name

      Response response =
          given()
              .contentType(ContentType.JSON)
              .header("Authorization", "Bearer " + adminToken)
              .body(request)
              .when()
              .post("/enrollments")
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
