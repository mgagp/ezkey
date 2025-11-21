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

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.ezkey.tests.security.AbstractSecurityTest;
import org.ezkey.tests.util.CryptoApiClient.RsaKeyPair;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.ezkey.tests.util.RestAssuredTestConfig.configureForAdminApi;
import static org.ezkey.tests.util.RestAssuredTestConfig.configureForAuthApi;

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

      // Complete enrollment (bind + verify)
      RsaKeyPair deviceKeyPair = cryptoApiClient.generateKeyPair();
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

      // Verify
      Map<String, Object> verifyRequest = new HashMap<>();
      verifyRequest.put("enrollmentId", enrollmentId);
      verifyRequest.put("devicePublicKey", deviceKeyPair.publicKey());
      verifyRequest.put("deviceProofTokenSignature", signature);

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
      configureForAuthApi(dockerStackConfig);
      Map<String, Object> pendingRequest = new HashMap<>();
      pendingRequest.put("enrollmentId", enrollmentId);
      pendingRequest.put("enrollmentProofToken", enrollmentProofToken);

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

      // Step 3: Sign auth attempt proof token
      String authSignature = cryptoApiClient.signData(authAttemptProofToken, deviceKeyPair.privateKey());

      // Step 4: Respond to auth attempt
      Map<String, Object> respondRequest = new HashMap<>();
      respondRequest.put("authAttemptId", authAttemptId);
      respondRequest.put("accepted", true);
      respondRequest.put("authAttemptProofTokenSignature", authSignature);

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

      assertThat(respondResponse.jsonPath().getBoolean("success")).isTrue();

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

      assertThat(statusResponse.jsonPath().getString("status")).isEqualTo("ACCEPTED");
    } catch (IllegalStateException e) {
      org.junit.jupiter.api.Assumptions.assumeTrue(
          false, "Admin token not available. Set EZKEY_ADMIN_TOKEN environment variable.");
    }
  }
}

