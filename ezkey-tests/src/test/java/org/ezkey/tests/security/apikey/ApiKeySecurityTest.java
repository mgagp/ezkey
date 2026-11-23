/*
 * Ezkey - Open Source Cryptographic MFA Platform
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
import static org.ezkey.tests.util.RestAssuredTestConfig.configureForIntegrationApi;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
 *   <li>Can create auth attempts for their own integration (Integration API)
 *   <li>Do not authenticate on Admin API (401)
 *   <li>Cannot access Admin operator endpoints (401)
 *   <li>Cannot access Admin enrollment management (401)
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

      assertThat(response.getStatusCode()).isEqualTo(401);
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

      assertThat(response.getStatusCode()).isEqualTo(401);
    } catch (IllegalStateException e) {
      org.junit.jupiter.api.Assumptions.assumeTrue(
          false, "Admin token not available. Set EZKEY_ADMIN_TOKEN environment variable.");
    }
  }

  @Test
  @DisplayName("API key can create auth attempts for own integration on Integration API")
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

      // Create auth attempt with API key on Integration API (canonical M2M surface)
      Map<String, Object> request = new HashMap<>();
      request.put("enrollmentId", enrollmentId);
      request.put("challengeRequested", false);

      configureForIntegrationApi(dockerStackConfig);
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

  @Test
  @DisplayName("Admin API does not authenticate API-key credentials on auth-attempt create")
  public void testAdminApiRejectsApiKeyAuthAttemptByDefault() {
    try {
      String adminToken = authTokenManager.getAdminToken();
      configureForAdminApi(dockerStackConfig);

      Integer integrationId = testDataFactory.createIntegration();
      Integer enrollmentId = createVerifiedEnrollment(integrationId, adminToken);
      String apiKey = createApiKeyForIntegration(integrationId, adminToken);

      Map<String, Object> request = new HashMap<>();
      request.put("enrollmentId", enrollmentId);
      request.put("challengeRequested", false);

      configureForAdminApi(dockerStackConfig);
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

      assertThat(response.getStatusCode()).isEqualTo(401);
    } catch (IllegalStateException e) {
      org.junit.jupiter.api.Assumptions.assumeTrue(
          false, "Admin token not available. Set EZKEY_ADMIN_TOKEN environment variable.");
    }
  }

  /**
   * Concurrent requests that share one valid API key must never fail with 401 (#1433).
   *
   * <p>Before #1433, each successful validation saved the versioned {@code ApiKey} to record {@code
   * lastUsedAt}; two validations of the same key at the same moment raced on the optimistic lock
   * and the loser surfaced as 401. This replays the Demo Login trigger (cancel while other calls
   * are in flight) with many parallel cancels on one pending attempt, over several rounds. Any
   * outcome except 401 (and 5xx) is acceptable: the first cancel wins (200), the others see a
   * finalized attempt (409).
   */
  @Test
  @DisplayName("Parallel requests with one API key never yield 401 (#1433)")
  public void testParallelRequestsWithOneApiKeyNeverYield401() throws Exception {
    final int rounds = 5;
    final int parallel = 16;
    try {
      String adminToken = authTokenManager.getAdminToken();
      configureForAdminApi(dockerStackConfig);

      Integer integrationId = testDataFactory.createIntegration();
      Integer enrollmentId = createVerifiedEnrollment(integrationId, adminToken);
      String apiKey = createApiKeyForIntegration(integrationId, adminToken);
      String authHeader = createApiKeyAuthHeader(apiKey.split(":")[0], apiKey.split(":")[1]);
      String baseUrl = dockerStackConfig.getIntegrationApiUrl() + "/api/v1/auth-attempts";

      HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
      ExecutorService pool = Executors.newFixedThreadPool(parallel);
      Map<Integer, Integer> statusCounts = new TreeMap<>();
      try {
        for (int round = 0; round < rounds; round++) {
          Integer authAttemptId = createAuthAttempt(client, baseUrl, authHeader, enrollmentId);
          HttpRequest cancel =
              HttpRequest.newBuilder(URI.create(baseUrl + "/" + authAttemptId + "/cancel"))
                  .timeout(Duration.ofSeconds(30))
                  .header("Authorization", authHeader)
                  .POST(HttpRequest.BodyPublishers.noBody())
                  .build();
          CountDownLatch start = new CountDownLatch(1);
          List<Future<Integer>> results = new ArrayList<>();
          for (int i = 0; i < parallel; i++) {
            results.add(
                pool.submit(() -> {
                  start.await();
                  return client.send(cancel, HttpResponse.BodyHandlers.discarding()).statusCode();
                }));
          }
          start.countDown();
          for (Future<Integer> result : results) {
            statusCounts.merge(result.get(60, TimeUnit.SECONDS), 1, Integer::sum);
          }
        }
      } finally {
        pool.shutdownNow();
      }

      log.info("#1433 parallel cancel status distribution: {}", statusCounts);
      assertThat(statusCounts)
          .as("a valid API key must never be rejected under concurrency")
          .doesNotContainKey(401);
      assertThat(statusCounts.keySet()).allMatch(status -> status < 500);
      assertThat(statusCounts).containsKey(200);
    } catch (IllegalStateException e) {
      org.junit.jupiter.api.Assumptions.assumeTrue(
          false, "Admin token not available. Set EZKEY_ADMIN_TOKEN environment variable.");
    }
  }

  private Integer createAuthAttempt(
      HttpClient client, String baseUrl, String authHeader, Integer enrollmentId) throws Exception {
    HttpRequest create =
        HttpRequest.newBuilder(URI.create(baseUrl))
            .timeout(Duration.ofSeconds(30))
            .header("Authorization", authHeader)
            .header("Content-Type", "application/json")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    "{\"enrollmentId\":" + enrollmentId + ",\"challengeRequested\":false}"))
            .build();
    HttpResponse<String> response = client.send(create, HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).as("create auth attempt").isEqualTo(201);
    return io.restassured.path.json.JsonPath.from(response.body()).getInt("authAttemptId");
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

    String verifyPayload =
        org.ezkey.tests.util.EnrollmentVerifyDevicePayload.build(
            bindProofToken, enrollmentId, challengeCode, deviceKeyPair.publicKey());
    String signature = cryptoApiClient.signData(verifyPayload, deviceKeyPair.privateKey());
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
