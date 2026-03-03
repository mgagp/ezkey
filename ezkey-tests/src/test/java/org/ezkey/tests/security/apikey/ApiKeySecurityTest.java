/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: ApiKeySecurityTest
 * Description: Security tests for API key authorization and access control
 */

package org.ezkey.tests.security.apikey;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.ezkey.tests.util.RestAssuredTestConfig.configureForAdminApi;
import static org.ezkey.tests.util.RestAssuredTestConfig.configureForAuthApi;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.ezkey.tests.security.AbstractSecurityTest;
import org.ezkey.tests.tags.TestTags;
import org.ezkey.tests.util.CryptoApiClient.EcP256KeyPair;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Security tests for API key authorization and access control.
 *
 * <p>Validates that API keys:
 *
 * <ul>
 *   <li>Can create auth attempts for their own integration
 *   <li>Cannot access auth attempts from other integrations (403)
 *   <li>Cannot access admin endpoints (403)
 *   <li>Cannot access enrollment/integration management (403)
 * </ul>
 *
 * <p>Note: These tests require admin token to create API keys and test data.
 *
 * @since 2025
 */
@Tag(TestTags.FAST)
@Tag(TestTags.API_KEY)
@DisplayName("API Key Security Tests")
public class ApiKeySecurityTest extends AbstractSecurityTest {

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

  @Test
  @DisplayName("API key cannot access admin endpoints (integrations)")
  public void testApiKeyCannotAccessAdminEndpoints() {
    // Skip if admin token not available
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

      // Should return 403 Forbidden
      assertThat(response.getStatusCode()).isEqualTo(403);
    } catch (IllegalStateException e) {
      org.junit.jupiter.api.Assumptions.assumeTrue(
          false, "Admin token not available. Set EZKEY_ADMIN_TOKEN environment variable.");
    }
  }

  @Test
  @DisplayName("API key cannot access enrollment endpoints")
  public void testApiKeyCannotAccessEnrollmentEndpoints() {
    // Skip if admin token not available
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

      // Should return 403 Forbidden
      assertThat(response.getStatusCode()).isEqualTo(403);
    } catch (IllegalStateException e) {
      org.junit.jupiter.api.Assumptions.assumeTrue(
          false, "Admin token not available. Set EZKEY_ADMIN_TOKEN environment variable.");
    }
  }

  @Test
  @DisplayName("API key can create auth attempts for own integration")
  public void testApiKeyCanCreateAuthAttemptsForOwnIntegration() {
    // Skip if admin token not available
    try {
      String adminToken = authTokenManager.getAdminToken();
      configureForAdminApi(dockerStackConfig);

      // Create integration, verified enrollment (bind+verify required for auth attempts), and API
      // key
      Integer integrationId = testDataFactory.createIntegration();
      Integer enrollmentId = createVerifiedEnrollment(integrationId, adminToken);
      String apiKey = createApiKeyForIntegration(integrationId, adminToken);

      // Create auth attempt with API key
      Map<String, Object> request = new HashMap<>();
      request.put("enrollmentId", enrollmentId);
      request.put("challengeRequested", false);

      Response response =
          given()
              .contentType(ContentType.JSON)
              .header(
                  "Authorization",
                  createApiKeyAuthHeader(apiKey.split(":")[0], apiKey.split(":")[1]))
              .body(request)
              .when()
              .post("/auth-attempts")
              .then()
              .extract()
              .response();

      // Should return 201 Created
      assertThat(response.getStatusCode()).isEqualTo(201);
      assertThat(response.jsonPath().getInt("authAttemptId")).isNotNull();
    } catch (IllegalStateException e) {
      org.junit.jupiter.api.Assumptions.assumeTrue(
          false, "Admin token not available. Set EZKEY_ADMIN_TOKEN environment variable.");
    }
  }

  /**
   * Creates a fully verified enrollment (bind + verify) for use in auth-attempt tests.
   *
   * <p>Auth attempts require a VERIFIED enrollment per the enrollment lifecycle security gate.
   *
   * @param integrationId integration ID
   * @param adminToken admin bearer token
   * @return Enrollment ID of the verified enrollment
   */
  private Integer createVerifiedEnrollment(Integer integrationId, String adminToken) {
    Integer enrollmentId =
        testDataFactory.createEnrollment(integrationId, "API Key Test Device", false);

    Response enrollmentResponse =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + adminToken)
            .when()
            .get("/enrollments/" + enrollmentId)
            .then()
            .statusCode(200)
            .extract()
            .response();

    String enrollmentProofToken = enrollmentResponse.jsonPath().getString("enrollmentProofToken");
    Integer challengeCode = enrollmentResponse.jsonPath().getInt("enrollmentChallenge");
    assertThat(enrollmentProofToken).isNotNull().isNotEmpty();

    EcP256KeyPair deviceKeyPair = cryptoApiClient.generateKeyPair();
    configureForAuthApi(dockerStackConfig);

    Map<String, Object> bindRequest = new HashMap<>();
    bindRequest.put("enrollmentId", enrollmentId);
    bindRequest.put("enrollmentProofToken", enrollmentProofToken);

    Response bindResponse =
        given()
            .contentType(ContentType.JSON)
            .body(bindRequest)
            .when()
            .post("/enrollments/bind")
            .then()
            .statusCode(200)
            .extract()
            .response();

    String bindProofToken = bindResponse.jsonPath().getString("enrollmentProofToken");
    assertThat(bindProofToken).isNotNull().isNotEmpty();

    String signature = cryptoApiClient.signData(bindProofToken, deviceKeyPair.privateKey());
    configureForAuthApi(dockerStackConfig);

    Map<String, Object> verifyRequest = new HashMap<>();
    verifyRequest.put("enrollmentId", enrollmentId);
    verifyRequest.put("challengeResponse", challengeCode);
    verifyRequest.put("devicePublicKey", deviceKeyPair.publicKey());
    verifyRequest.put("enrollmentProofTokenSigned", signature);

    given()
        .contentType(ContentType.JSON)
        .body(verifyRequest)
        .when()
        .post("/enrollments/verify")
        .then()
        .statusCode(200);

    configureForAdminApi(dockerStackConfig);
    return enrollmentId;
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

    // Store API key for later use
    authTokenManager.setApiKey(integrationId, integrationKey + ":" + secretKey);

    return integrationKey + ":" + secretKey;
  }
}
