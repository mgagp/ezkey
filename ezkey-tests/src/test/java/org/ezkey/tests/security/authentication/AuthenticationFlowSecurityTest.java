/*
 * Ezkey - Open Source MFA/Passkey Alternative
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
import java.util.HashMap;
import java.util.Map;
import org.ezkey.tests.security.AbstractSecurityTest;
import org.ezkey.tests.tags.TestTags;
import org.ezkey.tests.util.CryptoApiClient.EcP256KeyPair;
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
      String signature = cryptoApiClient.signData(bindProofToken, deviceKeyPair.privateKey());

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
}
