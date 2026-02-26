/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: EnrollmentFlowSecurityTest
 * Description: End-to-end security tests for enrollment flow
 */

package org.ezkey.tests.security.enrollment;

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
 * End-to-end security tests for enrollment flow.
 *
 * <p>Validates the complete enrollment security flow:
 *
 * <ul>
 *   <li>Enrollment creation via Admin API
 *   <li>Device binding with proof token
 *   <li>Enrollment verification with cryptographic signatures
 *   <li>Invalid enrollment token handling
 * </ul>
 *
 * <p>Note: These tests require admin token to create enrollments.
 *
 * @since 2025
 */
@Tag(TestTags.FAST)
@Tag(TestTags.SMOKE)
@Tag(TestTags.ENROLLMENT)
@DisplayName("Enrollment Flow Security Tests")
public class EnrollmentFlowSecurityTest extends AbstractSecurityTest {

  @Test
  @DisplayName("Complete enrollment flow with signature validation")
  public void testCompleteEnrollmentFlow() {
    // Skip if admin token not available
    try {
      String adminToken = authTokenManager.getAdminToken();

      // Step 1: Create integration and enrollment via Admin API
      configureForAdminApi(dockerStackConfig);
      Integer integrationId = testDataFactory.createIntegration();
      Integer enrollmentId = testDataFactory.createEnrollment(integrationId);

      // Get enrollment details including proof token
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

      // Step 2: Generate EC P-256 device key pair via Crypto API
      EcP256KeyPair deviceKeyPair = cryptoApiClient.generateKeyPair();

      // Step 3: Bind device via Auth API
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

      // Step 4: Sign proof token with device private key
      // Note: signData() configures RestAssured for Crypto API, so we need to reconfigure for Auth
      // API after
      String signature = cryptoApiClient.signData(bindProofToken, deviceKeyPair.privateKey());

      // Reconfigure RestAssured for Auth API after Crypto API call
      configureForAuthApi(dockerStackConfig);

      // Step 5: Verify enrollment via Auth API - Fixed: use correct field names and include
      // challengeResponse
      Map<String, Object> verifyRequest = new HashMap<>();
      verifyRequest.put("enrollmentId", enrollmentId);
      verifyRequest.put("challengeResponse", challengeCode);
      verifyRequest.put("devicePublicKey", deviceKeyPair.publicKey());
      verifyRequest.put("enrollmentProofTokenSigned", signature);

      Response verifyResponse =
          given()
              .contentType(ContentType.JSON)
              .body(verifyRequest)
              .when()
              .post("/enrollments/verify")
              .then()
              .statusCode(200)
              .extract()
              .response();

      assertThat(verifyResponse.jsonPath().getBoolean("active")).isTrue();
    } catch (IllegalStateException e) {
      org.junit.jupiter.api.Assumptions.assumeTrue(
          false, "Admin token not available. Set EZKEY_ADMIN_TOKEN environment variable.");
    }
  }

  @Test
  @DisplayName("Invalid enrollment proof token should be rejected")
  public void testInvalidEnrollmentProofToken() {
    // Skip if admin token not available
    try {
      String adminToken = authTokenManager.getAdminToken();

      // Create enrollment
      configureForAdminApi(dockerStackConfig);
      Integer integrationId = testDataFactory.createIntegration();
      Integer enrollmentId = testDataFactory.createEnrollment(integrationId);

      // Try to bind with invalid proof token
      configureForAuthApi(dockerStackConfig);
      Map<String, Object> bindRequest = new HashMap<>();
      bindRequest.put("enrollmentId", enrollmentId);
      bindRequest.put("enrollmentProofToken", "invalid-proof-token");

      Response bindResponse =
          given()
              .contentType(ContentType.JSON)
              .body(bindRequest)
              .when()
              .post("/enrollments/bind")
              .then()
              .extract()
              .response();

      // Should return 400 Bad Request
      assertThat(bindResponse.getStatusCode()).isEqualTo(400);
    } catch (IllegalStateException e) {
      org.junit.jupiter.api.Assumptions.assumeTrue(
          false, "Admin token not available. Set EZKEY_ADMIN_TOKEN environment variable.");
    }
  }
}
