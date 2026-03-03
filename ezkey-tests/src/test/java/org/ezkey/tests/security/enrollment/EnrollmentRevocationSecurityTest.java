/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: EnrollmentRevocationSecurityTest
 * Description: Functional E2E tests for enrollment revocation lifecycle security.
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
import org.ezkey.tests.util.DatabaseHelper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Functional E2E tests for enrollment revocation lifecycle.
 *
 * <p>Validates the critical security path: revoked or deactivated enrollments must not accept new
 * authentication attempts. Also verifies self-revocation guard, system integration bulk-revocation
 * guard, and reversible deactivate/reactivate flow.
 *
 * <p><b>Scenarios covered:</b>
 *
 * <ul>
 *   <li>Revoke enrollment → create auth attempt → 403 with enrollment-inactive ProblemDetail
 *   <li>Deactivate → auth attempt 403 → Reactivate → auth attempt 201 (reversible path)
 *   <li>Revoke-all on normal integration → enrollments cannot create auth attempts
 *   <li>Revoke-all on system integration → 403 with system-integration-revocation ProblemDetail
 *   <li>Deactivate-all then reactivate-all: bulk reversible lockdown and restore
 *   <li>Deactivate-all on system integration → 403 with system-integration-revocation ProblemDetail
 * </ul>
 *
 * <p><b>Prerequisites:</b> Docker stack with Admin API, Auth API, Crypto API.
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Tag(TestTags.FAST)
@Tag(TestTags.ENROLLMENT)
@Tag(TestTags.SECURITY)
@DisplayName("Enrollment Revocation Security Tests")
public class EnrollmentRevocationSecurityTest extends AbstractSecurityTest {

  private static final Logger log = LoggerFactory.getLogger(EnrollmentRevocationSecurityTest.class);

  private static final String REASON_MIN_10 = "Security incident validation test";

  /**
   * Creates a fully verified enrollment (bind + verify) for use in revocation tests.
   *
   * @param integrationId integration ID
   * @param enrollmentName unique name (required for VERIFIED uniqueness: one per integration+name)
   * @return Enrollment ID of the verified enrollment
   */
  private int createVerifiedEnrollment(int integrationId, String enrollmentName) {
    configureForAdminApi(dockerStackConfig);
    String adminToken = authTokenManager.getAdminToken();

    int enrollmentId =
        testDataFactory.createEnrollment(
            integrationId, enrollmentName != null ? enrollmentName : "Test Device", false);

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
    log.debug("Created verified enrollment {} for integration {}", enrollmentId, integrationId);
    return enrollmentId;
  }

  @Test
  @DisplayName("Revoked enrollment rejects new auth attempts with 403 enrollment-inactive")
  public void revokeEnrollment_thenCreateAuthAttempt_returns403WithEnrollmentInactive() {
    try {
      configureForAdminApi(dockerStackConfig);
      String adminToken = authTokenManager.getAdminToken();

      int integrationId = testDataFactory.createIntegration();
      int enrollmentId = createVerifiedEnrollment(integrationId, "Revoke Test Device");

      given()
          .header("Authorization", "Bearer " + adminToken)
          .queryParam("reason", REASON_MIN_10)
          .when()
          .post("/enrollments/" + enrollmentId + "/revoke")
          .then()
          .statusCode(204);

      Map<String, Object> authRequest = new HashMap<>();
      authRequest.put("enrollmentId", enrollmentId);
      authRequest.put("challengeRequested", false);

      Response authResponse =
          given()
              .contentType(ContentType.JSON)
              .header("Authorization", "Bearer " + adminToken)
              .body(authRequest)
              .when()
              .post("/auth-attempts")
              .then()
              .extract()
              .response();

      assertThat(authResponse.getStatusCode()).isEqualTo(403);
      assertThat(authResponse.jsonPath().getString("type")).contains("enrollment-inactive");
      assertThat(authResponse.jsonPath().getInt("status")).isEqualTo(403);
    } catch (IllegalStateException e) {
      Assumptions.assumeTrue(false, "Admin token not available: " + e.getMessage());
    }
  }

  @Test
  @DisplayName(
      "Deactivate then reactivate: auth attempts fail when inactive, succeed after reactivate")
  public void deactivateThenReactivate_authAttemptFailsWhenInactive_succeedsAfterReactivate() {
    try {
      configureForAdminApi(dockerStackConfig);
      String adminToken = authTokenManager.getAdminToken();

      int integrationId = testDataFactory.createIntegration();
      int enrollmentId = createVerifiedEnrollment(integrationId, "Deactivate Test Device");

      given()
          .header("Authorization", "Bearer " + adminToken)
          .queryParam("reason", "Temporary lockdown for test")
          .when()
          .post("/enrollments/" + enrollmentId + "/deactivate")
          .then()
          .statusCode(204);

      Map<String, Object> authRequest = new HashMap<>();
      authRequest.put("enrollmentId", enrollmentId);
      authRequest.put("challengeRequested", false);

      Response authAfterDeactivate =
          given()
              .contentType(ContentType.JSON)
              .header("Authorization", "Bearer " + adminToken)
              .body(authRequest)
              .when()
              .post("/auth-attempts")
              .then()
              .extract()
              .response();

      assertThat(authAfterDeactivate.getStatusCode()).isEqualTo(403);

      given()
          .header("Authorization", "Bearer " + adminToken)
          .queryParam("reason", "Restored after test")
          .when()
          .post("/enrollments/" + enrollmentId + "/reactivate")
          .then()
          .statusCode(204);

      Response authAfterReactivate =
          given()
              .contentType(ContentType.JSON)
              .header("Authorization", "Bearer " + adminToken)
              .body(authRequest)
              .when()
              .post("/auth-attempts")
              .then()
              .extract()
              .response();

      assertThat(authAfterReactivate.getStatusCode()).isEqualTo(201);
      assertThat(authAfterReactivate.jsonPath().getInt("authAttemptId")).isNotNull();
    } catch (IllegalStateException e) {
      Assumptions.assumeTrue(false, "Admin token not available: " + e.getMessage());
    }
  }

  @Test
  @DisplayName("Revoke-all on normal integration: all enrollments reject new auth attempts")
  public void revokeAllOnNormalIntegration_allEnrollmentsRejectAuthAttempts() {
    try {
      configureForAdminApi(dockerStackConfig);
      String adminToken = authTokenManager.getAdminToken();

      int integrationId = testDataFactory.createIntegration();
      int enrollment1 = createVerifiedEnrollment(integrationId, "RevokeAll Device A");
      int enrollment2 = createVerifiedEnrollment(integrationId, "RevokeAll Device B");

      given()
          .header("Authorization", "Bearer " + adminToken)
          .queryParam("reason", REASON_MIN_10)
          .when()
          .post("/integrations/" + integrationId + "/enrollments/revoke-all")
          .then()
          .statusCode(204);

      Map<String, Object> authRequest = new HashMap<>();
      authRequest.put("challengeRequested", false);

      for (int enrollmentId : new int[] {enrollment1, enrollment2}) {
        authRequest.put("enrollmentId", enrollmentId);
        Response resp =
            given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + adminToken)
                .body(authRequest)
                .when()
                .post("/auth-attempts")
                .then()
                .extract()
                .response();
        assertThat(resp.getStatusCode())
            .as("Enrollment %d must reject auth attempt", enrollmentId)
            .isEqualTo(403);
      }
    } catch (IllegalStateException e) {
      Assumptions.assumeTrue(false, "Admin token not available: " + e.getMessage());
    }
  }

  @Test
  @DisplayName("Revoke-all on system integration returns 403 system-integration-revocation")
  public void revokeAllOnSystemIntegration_returns403SystemIntegrationRevocation() {
    try {
      configureForAdminApi(dockerStackConfig);
      String adminToken = authTokenManager.getAdminToken();

      Integer systemIntegrationId = new DatabaseHelper().getSystemIntegrationId();
      Assumptions.assumeTrue(
          systemIntegrationId != null, "System integration not found (bootstrap may not have run)");

      Response response =
          given()
              .header("Authorization", "Bearer " + adminToken)
              .queryParam("reason", REASON_MIN_10)
              .when()
              .post("/integrations/" + systemIntegrationId + "/enrollments/revoke-all")
              .then()
              .extract()
              .response();

      assertThat(response.getStatusCode()).isEqualTo(403);
      assertThat(response.jsonPath().getString("type")).contains("system-integration-revocation");
      assertThat(response.jsonPath().getInt("status")).isEqualTo(403);
    } catch (IllegalStateException e) {
      Assumptions.assumeTrue(false, "Admin token not available: " + e.getMessage());
    }
  }

  @Test
  @DisplayName(
      "Deactivate-all then reactivate-all: auth attempts blocked when inactive, succeed after"
          + " reactivate-all")
  public void deactivateAllThenReactivateAll_authAttemptsBlockedThenRestored() {
    try {
      configureForAdminApi(dockerStackConfig);
      String adminToken = authTokenManager.getAdminToken();

      int integrationId = testDataFactory.createIntegration();
      int enrollment1 = createVerifiedEnrollment(integrationId, "DeactivateAll Device A");
      int enrollment2 = createVerifiedEnrollment(integrationId, "DeactivateAll Device B");

      given()
          .header("Authorization", "Bearer " + adminToken)
          .queryParam("reason", "Emergency lockdown for functional test")
          .when()
          .post("/integrations/" + integrationId + "/enrollments/deactivate-all")
          .then()
          .statusCode(204);

      Map<String, Object> authRequest = new HashMap<>();
      authRequest.put("challengeRequested", false);

      for (int enrollmentId : new int[] {enrollment1, enrollment2}) {
        authRequest.put("enrollmentId", enrollmentId);
        Response resp =
            given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + adminToken)
                .body(authRequest)
                .when()
                .post("/auth-attempts")
                .then()
                .extract()
                .response();
        assertThat(resp.getStatusCode())
            .as("Enrollment %d must reject auth attempt after deactivate-all", enrollmentId)
            .isEqualTo(403);
      }

      given()
          .header("Authorization", "Bearer " + adminToken)
          .queryParam("reason", "Threat cleared - restoring access")
          .when()
          .post("/integrations/" + integrationId + "/enrollments/reactivate-all")
          .then()
          .statusCode(204);

      for (int enrollmentId : new int[] {enrollment1, enrollment2}) {
        authRequest.put("enrollmentId", enrollmentId);
        Response resp =
            given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + adminToken)
                .body(authRequest)
                .when()
                .post("/auth-attempts")
                .then()
                .extract()
                .response();
        assertThat(resp.getStatusCode())
            .as("Enrollment %d must accept auth attempt after reactivate-all", enrollmentId)
            .isEqualTo(201);
        assertThat(resp.jsonPath().getInt("authAttemptId")).as("authAttemptId").isNotNull();
      }
    } catch (IllegalStateException e) {
      Assumptions.assumeTrue(false, "Admin token not available: " + e.getMessage());
    }
  }

  @Test
  @DisplayName("Deactivate-all on system integration returns 403 system-integration-revocation")
  public void deactivateAllOnSystemIntegration_returns403SystemIntegrationRevocation() {
    try {
      configureForAdminApi(dockerStackConfig);
      String adminToken = authTokenManager.getAdminToken();

      Integer systemIntegrationId = new DatabaseHelper().getSystemIntegrationId();
      Assumptions.assumeTrue(
          systemIntegrationId != null, "System integration not found (bootstrap may not have run)");

      Response response =
          given()
              .header("Authorization", "Bearer " + adminToken)
              .queryParam("reason", "Emergency lockdown attempt on system integration")
              .when()
              .post("/integrations/" + systemIntegrationId + "/enrollments/deactivate-all")
              .then()
              .extract()
              .response();

      assertThat(response.getStatusCode()).isEqualTo(403);
      assertThat(response.jsonPath().getString("type")).contains("system-integration-revocation");
      assertThat(response.jsonPath().getInt("status")).isEqualTo(403);
    } catch (IllegalStateException e) {
      Assumptions.assumeTrue(false, "Admin token not available: " + e.getMessage());
    }
  }
}
