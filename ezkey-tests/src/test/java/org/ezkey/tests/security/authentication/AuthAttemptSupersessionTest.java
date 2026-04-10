/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AuthAttemptSupersessionTest
 * Description: Security tests for authentication attempt supersession logic
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
import org.ezkey.tests.util.DatabaseHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Security tests for authentication attempt supersession logic.
 *
 * <p>Validates that creating a new authentication attempt for an enrollment automatically expires
 * any previous pending attempts. This is a critical security feature to prevent user confusion and
 * replay attacks.
 *
 * @since 2025
 */
@Tag(TestTags.FAST)
@Tag(TestTags.AUTHENTICATION)
@Tag(TestTags.DATABASE)
@DisplayName("Auth Attempt Supersession Security Tests")
public class AuthAttemptSupersessionTest extends AbstractSecurityTest {

  private final DatabaseHelper databaseHelper = new DatabaseHelper();

  @Test
  @DisplayName("Newer auth attempt supersedes older one (Status -> EXPIRED)")
  public void testNewerAttemptSupersedesOlder() {
    // Skip if admin token not available
    try {
      authTokenManager.getAdminToken(); // Check availability

      // Setup: Create integration and verified enrollment (auth attempts require VERIFIED)
      configureForAdminApi(dockerStackConfig);
      String adminToken = authTokenManager.getAdminToken();
      Integer integrationId = testDataFactory.createIntegration();
      Integer enrollmentId = createVerifiedEnrollment(integrationId, adminToken);

      // Step 1: Create first auth attempt
      Integer attempt1Id = testDataFactory.createAuthAttempt(enrollmentId, false);

      // Verify attempt 1 is initially PENDING
      String status1 =
          databaseHelper.executeQuerySingleValue(
              "SELECT auth_attempt_status FROM ezkey_auth_attempt WHERE auth_attempt_id = "
                  + attempt1Id);
      assertThat(status1).isEqualTo("PENDING");

      // Step 2: Create second auth attempt (superseding the first)
      // Small delay to ensure timestamp difference if running extremely fast
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
      }
      Integer attempt2Id = testDataFactory.createAuthAttempt(enrollmentId, false);

      // Step 3: Probe Database to verify supersession

      // Attempt 1 should now be EXPIRED
      String status1After =
          databaseHelper.executeQuerySingleValue(
              "SELECT auth_attempt_status FROM ezkey_auth_attempt WHERE auth_attempt_id = "
                  + attempt1Id);
      assertThat(status1After)
          .as("Older attempt should be expired by newer attempt")
          .isEqualTo("EXPIRED");

      // Attempt 2 should be PENDING
      String status2 =
          databaseHelper.executeQuerySingleValue(
              "SELECT auth_attempt_status FROM ezkey_auth_attempt WHERE auth_attempt_id = "
                  + attempt2Id);
      assertThat(status2).as("Newer attempt should be pending").isEqualTo("PENDING");

    } catch (IllegalStateException e) {
      org.junit.jupiter.api.Assumptions.assumeTrue(
          false, "Admin token not available. Set EZKEY_ADMIN_TOKEN environment variable.");
    }
  }

  private Integer createVerifiedEnrollment(Integer integrationId, String adminToken) {
    Integer enrollmentId =
        testDataFactory.createEnrollment(integrationId, "Supersession Test Device", false);

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
}
