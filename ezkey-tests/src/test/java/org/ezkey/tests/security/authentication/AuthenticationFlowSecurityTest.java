/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AuthenticationFlowSecurityTest
 * Description: End-to-end security tests for authentication flow
 */

package org.ezkey.tests.security.authentication;

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
import org.ezkey.tests.util.DatabaseHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * End-to-end security tests for authentication flow.
 *
 * <p>Validates the complete authentication security flow:
 *
 * <ul>
 *   <li>Auth attempt creation via Admin API
 *   <li>Pending auth attempt retrieval via Auth API
 *   <li>Auth attempt response with signature
 *   <li>Wait API with polling security
 * </ul>
 *
 * <p>Note: These tests require admin token and a completed enrollment.
 *
 * @since 2025
 */
@Tag(TestTags.FAST)
@Tag(TestTags.SMOKE)
@Tag(TestTags.AUTHENTICATION)
@DisplayName("Authentication Flow Security Tests")
public class AuthenticationFlowSecurityTest extends AbstractSecurityTest {

  private final DatabaseHelper databaseHelper = new DatabaseHelper();

  @Test
  @DisplayName("Complete passwordless authentication flow")
  public void testCompleteAuthenticationFlow() {
    // Skip if admin token not available
    try {
      String adminToken = authTokenManager.getAdminToken();

      // Setup: Create integration, enrollment, and complete enrollment
      configureForAdminApi(dockerStackConfig);
      Integer integrationId = testDataFactory.createIntegration();
      Integer enrollmentId = testDataFactory.createEnrollment(integrationId);

      // Get enrollment proof token
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

      // Complete enrollment (bind + verify)
      EcP256KeyPair deviceKeyPair = cryptoApiClient.generateKeyPair();
      configureForAuthApi(dockerStackConfig);

      // Bind
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
      String verifyPayload =
          org.ezkey.tests.util.EnrollmentVerifyDevicePayload.build(
              bindProofToken, enrollmentId, challengeCode, deviceKeyPair.publicKey());
      String signature = cryptoApiClient.signData(verifyPayload, deviceKeyPair.privateKey());

      // Reconfigure RestAssured for Auth API after Crypto API call
      configureForAuthApi(dockerStackConfig);

      // Verify - Fixed: use correct field names and include challengeResponse
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

      // Step 1: Create auth attempt via Admin API
      configureForAdminApi(dockerStackConfig);
      Integer authAttemptId = testDataFactory.createAuthAttempt(enrollmentId, false);

      // Step 2: Get pending auth attempt via Auth API
      // Generate device proof token and sign it (required for pending request)
      String deviceProofToken = cryptoApiClient.generateProofToken();
      String deviceProofTokenSigned =
          cryptoApiClient.signData(deviceProofToken, deviceKeyPair.privateKey());

      // Reconfigure RestAssured for Auth API after Crypto API calls
      configureForAuthApi(dockerStackConfig);

      Map<String, Object> pendingRequest = new HashMap<>();
      pendingRequest.put("enrollmentId", enrollmentId);
      pendingRequest.put("enrollmentProofToken", enrollmentProofToken);
      pendingRequest.put("deviceProofToken", deviceProofToken);
      pendingRequest.put("deviceProofTokenSigned", deviceProofTokenSigned);

      Response pendingResponse =
          given()
              .contentType(ContentType.JSON)
              .body(pendingRequest)
              .when()
              .post("/auth-attempts/pending")
              .then()
              .statusCode(200)
              .extract()
              .response();

      String authAttemptProofToken = pendingResponse.jsonPath().getString("authAttemptProofToken");
      assertThat(authAttemptProofToken).isNotNull().isNotEmpty();

      // Step 3: Sign canonical respond payload (proofToken|accepted) per
      // AUTH_ATTEMPT_SIGNATURE_PAYLOAD
      // Note: signData() configures RestAssured for Crypto API, so we need to reconfigure for Auth
      // API after
      boolean accepted = true;
      String respondPayload = authAttemptProofToken + "|" + (accepted ? "true" : "false");
      String authSignature = cryptoApiClient.signData(respondPayload, deviceKeyPair.privateKey());

      // Reconfigure RestAssured for Auth API after Crypto API call
      configureForAuthApi(dockerStackConfig);

      // Step 4: Respond to auth attempt - Fixed: use correct field names
      Map<String, Object> respondRequest = new HashMap<>();
      respondRequest.put("authAttemptId", authAttemptId);
      respondRequest.put("authAttemptAccepted", true);
      respondRequest.put("authAttemptProofTokenSignedByDevice", authSignature);

      Response respondResponse =
          given()
              .contentType(ContentType.JSON)
              .body(respondRequest)
              .when()
              .post("/auth-attempts/respond")
              .then()
              .statusCode(200)
              .extract()
              .response();

      // Response contains authAttemptResult (APPROVED, DENIED, etc.) and integration signature
      assertThat(respondResponse.jsonPath().getString("authAttemptResult")).isEqualTo("APPROVED");
      assertThat(
              respondResponse
                  .jsonPath()
                  .getString("authAttemptProofTokenResultSignedByIntegration"))
          .isNotNull()
          .isNotEmpty();

      // Step 5: Verify completion via Admin API
      configureForAdminApi(dockerStackConfig);
      Response statusResponse =
          given()
              .contentType(ContentType.JSON)
              .header("Authorization", "Bearer " + adminToken)
              .when()
              .get("/auth-attempts/" + authAttemptId)
              .then()
              .statusCode(200)
              .extract()
              .response();

      // AuthAttemptDto uses authAttemptStatus field (enum: PENDING, READ, INVALID, REJECTED,
      // ACCEPTED, EXPIRED)
      assertThat(statusResponse.jsonPath().getString("authAttemptStatus")).isEqualTo("ACCEPTED");
    } catch (IllegalStateException e) {
      org.junit.jupiter.api.Assumptions.assumeTrue(
          false, "Admin token not available. Set EZKEY_ADMIN_TOKEN environment variable.");
    }
  }

  @Test
  @DisplayName("Admin wait endpoint returns RFC 9457 for invalid wait parameters")
  public void testAdminWaitEndpointRejectsInvalidParameters() {
    try {
      String adminToken = authTokenManager.getAdminToken();

      configureForAdminApi(dockerStackConfig);
      Integer integrationId = testDataFactory.createIntegration();
      Integer enrollmentId = testDataFactory.createEnrollment(integrationId);

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
      Integer authAttemptId = testDataFactory.createAuthAttempt(enrollmentId, false);

      Response response =
          given()
              .contentType(ContentType.JSON)
              .header("Authorization", "Bearer " + adminToken)
              .queryParam("timeout", 30)
              .queryParam("polling", 30)
              .when()
              .get("/auth-attempts/" + authAttemptId + "/wait")
              .then()
              .statusCode(400)
              .extract()
              .response();

      assertThat(response.asString()).isNotBlank();
    } catch (IllegalStateException e) {
      org.junit.jupiter.api.Assumptions.assumeTrue(
          false, "Admin token not available. Set EZKEY_ADMIN_TOKEN environment variable.");
    }
  }

  @Test
  @DisplayName("Admin create auth attempt rejects missing identifiers with RFC 9457 ProblemDetail")
  public void testAdminCreateAuthAttemptRejectsMissingIdentifiers() {
    try {
      String adminToken = authTokenManager.getAdminToken();
      configureForAdminApi(dockerStackConfig);

      Map<String, Object> request = new HashMap<>();
      request.put("challengeRequested", false);

      Response response =
          given()
              .contentType(ContentType.JSON)
              .header("Authorization", "Bearer " + adminToken)
              .body(request)
              .when()
              .post("/auth-attempts")
              .then()
              .statusCode(400)
              .extract()
              .response();

      assertThat(response.jsonPath().getString("type"))
          .isEqualTo("https://ezkey.io/problems/validation/auth-attempt-create-invalid");
      assertThat(response.jsonPath().getString("title"))
          .isEqualTo("Invalid Auth Attempt Create Request");
      assertThat(response.jsonPath().getString("detail"))
          .contains("Either enrollmentId or userIdentifier is required");
    } catch (IllegalStateException e) {
      org.junit.jupiter.api.Assumptions.assumeTrue(
          false, "Admin token not available. Set EZKEY_ADMIN_TOKEN environment variable.");
    }
  }

  @Test
  @DisplayName(
      "Integration create auth attempt rejects missing identifiers with RFC 9457 ProblemDetail")
  public void testIntegrationCreateAuthAttemptRejectsMissingIdentifiers() {
    try {
      String adminToken = authTokenManager.getAdminToken();
      configureForAdminApi(dockerStackConfig);

      Integer integrationId = testDataFactory.createIntegration();
      String apiKeyCredentials =
          testDataFactory.createApiKeyForIntegration(integrationId, adminToken);

      Map<String, Object> request = new HashMap<>();
      request.put("challengeRequested", false);

      Response response =
          given()
              .baseUri(getIntegrationApiUrl())
              .basePath("/api/v1")
              .contentType(ContentType.JSON)
              .header("Authorization", createBasicAuthHeader(apiKeyCredentials))
              .body(request)
              .when()
              .post("/auth-attempts")
              .then()
              .statusCode(400)
              .extract()
              .response();

      assertThat(response.jsonPath().getString("type"))
          .isEqualTo("https://ezkey.io/problems/validation/auth-attempt-create-invalid");
      assertThat(response.jsonPath().getString("title"))
          .isEqualTo("Invalid Auth Attempt Create Request");
      assertThat(response.jsonPath().getString("detail"))
          .contains("Either enrollmentId or userIdentifier is required");
    } catch (IllegalStateException e) {
      org.junit.jupiter.api.Assumptions.assumeTrue(
          false, "Admin token not available. Set EZKEY_ADMIN_TOKEN environment variable.");
    }
  }

  @Test
  @DisplayName("Pending rejects invalid device signature with safe RFC 9457 ProblemDetail")
  public void testPendingRejectsInvalidDeviceSignature() {
    try {
      String adminToken = authTokenManager.getAdminToken();
      configureForAdminApi(dockerStackConfig);

      Integer integrationId = testDataFactory.createIntegration();
      VerifiedEnrollmentFixture fixture = createVerifiedEnrollment(integrationId, adminToken);
      testDataFactory.createAuthAttempt(fixture.enrollmentId(), false);

      String deviceProofToken = cryptoApiClient.generateProofToken();
      String invalidSignature =
          cryptoApiClient.signData(
              deviceProofToken + "-tampered", fixture.deviceKeyPair().privateKey());

      configureForAuthApi(dockerStackConfig);
      Map<String, Object> pendingRequest = new HashMap<>();
      pendingRequest.put("enrollmentId", fixture.enrollmentId());
      pendingRequest.put("enrollmentProofToken", fixture.enrollmentProofToken());
      pendingRequest.put("deviceProofToken", deviceProofToken);
      pendingRequest.put("deviceProofTokenSigned", invalidSignature);

      Response response =
          given()
              .contentType(ContentType.JSON)
              .body(pendingRequest)
              .when()
              .post("/auth-attempts/pending")
              .then()
              .statusCode(400)
              .extract()
              .response();

      assertThat(response.jsonPath().getString("type")).endsWith("/auth-attempt-binding-failed");
      assertThat(response.jsonPath().getString("title")).isEqualTo("Request not acceptable");
      assertThat(response.jsonPath().getString("detail"))
          .isEqualTo("The authentication request could not be processed.");
      assertThat(response.jsonPath().getString("detail")).doesNotContain("signature");
    } catch (IllegalStateException e) {
      org.junit.jupiter.api.Assumptions.assumeTrue(
          false, "Admin token not available. Set EZKEY_ADMIN_TOKEN environment variable.");
    }
  }

  @Test
  @DisplayName("Respond invalid signature returns signed FAILED business result")
  public void testRespondInvalidSignatureReturnsSignedFailedResult() {
    try {
      String adminToken = authTokenManager.getAdminToken();
      configureForAdminApi(dockerStackConfig);

      Integer integrationId = testDataFactory.createIntegration();
      VerifiedEnrollmentFixture fixture = createVerifiedEnrollment(integrationId, adminToken);
      Integer authAttemptId = testDataFactory.createAuthAttempt(fixture.enrollmentId(), false);
      PendingAttemptFixture pendingAttempt = claimPendingAttempt(fixture, authAttemptId);

      String invalidRespondSignature =
          cryptoApiClient.signData(
              pendingAttempt.authAttemptProofToken() + "|false",
              fixture.deviceKeyPair().privateKey());

      configureForAuthApi(dockerStackConfig);
      Map<String, Object> respondRequest = new HashMap<>();
      respondRequest.put("authAttemptId", authAttemptId);
      respondRequest.put("authAttemptAccepted", true);
      respondRequest.put("authAttemptProofTokenSignedByDevice", invalidRespondSignature);

      Response response =
          given()
              .contentType(ContentType.JSON)
              .body(respondRequest)
              .when()
              .post("/auth-attempts/respond")
              .then()
              .statusCode(200)
              .extract()
              .response();

      assertThat(response.jsonPath().getString("authAttemptResult")).isEqualTo("FAILED");
      assertThat(response.jsonPath().getString("authAttemptMessage"))
          .isEqualTo("The response could not be processed.");
      assertThat(response.jsonPath().getString("authAttemptProofTokenResultSignedByIntegration"))
          .isNotBlank();

      configureForAdminApi(dockerStackConfig);
      Response statusResponse =
          given()
              .contentType(ContentType.JSON)
              .header("Authorization", "Bearer " + adminToken)
              .when()
              .get("/auth-attempts/" + authAttemptId)
              .then()
              .statusCode(200)
              .extract()
              .response();

      assertThat(statusResponse.jsonPath().getString("authAttemptStatus")).isEqualTo("INVALID");
    } catch (IllegalStateException e) {
      org.junit.jupiter.api.Assumptions.assumeTrue(
          false, "Admin token not available. Set EZKEY_ADMIN_TOKEN environment variable.");
    }
  }

  @Test
  @DisplayName("Respond expired attempt returns safe RFC 9457 state conflict")
  public void testRespondExpiredAttemptReturnsSafeStateConflict() {
    try {
      String adminToken = authTokenManager.getAdminToken();
      configureForAdminApi(dockerStackConfig);

      Integer integrationId = testDataFactory.createIntegration();
      VerifiedEnrollmentFixture fixture = createVerifiedEnrollment(integrationId, adminToken);
      Integer authAttemptId = testDataFactory.createAuthAttempt(fixture.enrollmentId(), false);
      PendingAttemptFixture pendingAttempt = claimPendingAttempt(fixture, authAttemptId);

      boolean updated =
          databaseHelper.executeUpdate(
              "UPDATE ezkey_auth_attempt SET expires_at = NOW() - INTERVAL '5 minutes' "
                  + "WHERE auth_attempt_id = "
                  + authAttemptId);
      assertThat(updated).isTrue();

      String respondSignature =
          cryptoApiClient.signData(
              pendingAttempt.authAttemptProofToken() + "|true",
              fixture.deviceKeyPair().privateKey());

      configureForAuthApi(dockerStackConfig);
      Map<String, Object> respondRequest = new HashMap<>();
      respondRequest.put("authAttemptId", authAttemptId);
      respondRequest.put("authAttemptAccepted", true);
      respondRequest.put("authAttemptProofTokenSignedByDevice", respondSignature);

      Response response =
          given()
              .contentType(ContentType.JSON)
              .body(respondRequest)
              .when()
              .post("/auth-attempts/respond")
              .then()
              .statusCode(409)
              .extract()
              .response();

      assertThat(response.jsonPath().getString("type")).endsWith("/auth-attempt-state-conflict");
      assertThat(response.jsonPath().getString("title")).isEqualTo("Request cannot be completed");
      assertThat(response.jsonPath().getString("detail"))
          .isEqualTo(
              "The authentication request is in a state that does not allow this operation.");
      assertThat(response.jsonPath().getString("detail")).doesNotContain("expired");
    } catch (IllegalStateException e) {
      org.junit.jupiter.api.Assumptions.assumeTrue(
          false, "Admin token not available. Set EZKEY_ADMIN_TOKEN environment variable.");
    }
  }

  private VerifiedEnrollmentFixture createVerifiedEnrollment(
      Integer integrationId, String adminToken) {
    Integer enrollmentId = testDataFactory.createEnrollment(integrationId);

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
    return new VerifiedEnrollmentFixture(enrollmentId, enrollmentProofToken, deviceKeyPair);
  }

  private PendingAttemptFixture claimPendingAttempt(
      VerifiedEnrollmentFixture fixture, Integer authAttemptId) {
    String deviceProofToken = cryptoApiClient.generateProofToken();
    String deviceProofTokenSigned =
        cryptoApiClient.signData(deviceProofToken, fixture.deviceKeyPair().privateKey());

    configureForAuthApi(dockerStackConfig);
    Map<String, Object> pendingRequest = new HashMap<>();
    pendingRequest.put("enrollmentId", fixture.enrollmentId());
    pendingRequest.put("enrollmentProofToken", fixture.enrollmentProofToken());
    pendingRequest.put("deviceProofToken", deviceProofToken);
    pendingRequest.put("deviceProofTokenSigned", deviceProofTokenSigned);

    Response pendingResponse =
        given()
            .contentType(ContentType.JSON)
            .body(pendingRequest)
            .when()
            .post("/auth-attempts/pending")
            .then()
            .statusCode(200)
            .extract()
            .response();

    return new PendingAttemptFixture(
        authAttemptId, pendingResponse.jsonPath().getString("authAttemptProofToken"));
  }

  private String createBasicAuthHeader(String credentials) {
    return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());
  }

  private String getIntegrationApiUrl() {
    return System.getenv().getOrDefault("EZKEY_INTEGRATION_API_URL", "http://localhost:7080");
  }

  private record VerifiedEnrollmentFixture(
      Integer enrollmentId, String enrollmentProofToken, EcP256KeyPair deviceKeyPair) {}

  private record PendingAttemptFixture(Integer authAttemptId, String authAttemptProofToken) {}
}
