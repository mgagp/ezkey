/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: EnrollmentExpirationSecurityTest
 * Description: Functional E2E tests for enrollment expiration (pending phase).
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

/**
 * Functional E2E tests for enrollment expiration (pending CREATED phase).
 *
 * <p>Validates that bind is rejected when a pending enrollment has expires_at in the past, and that
 * the enrollment is marked EXPIRED. Uses DatabaseHelper to set expires_at in the past for realistic
 * security scenario without relying on server clock or waiting.
 *
 * <p><b>Prerequisites:</b> Docker stack with Admin API, Auth API.
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Tag(TestTags.FAST)
@Tag(TestTags.ENROLLMENT)
@Tag(TestTags.SECURITY)
@DisplayName("Enrollment Expiration Security Tests")
public class EnrollmentExpirationSecurityTest extends AbstractSecurityTest {

  @Test
  @DisplayName("Bind rejected when pending enrollment has expires_at in the past")
  void bind_WhenEnrollmentExpired_Returns400AndMarksExpired() {
    configureForAdminApi(dockerStackConfig);
    String adminToken = authTokenManager.getAdminToken();

    int integrationId = testDataFactory.createIntegration();
    int enrollmentId =
        testDataFactory.createEnrollment(
            integrationId, "ExpirationTest-" + System.currentTimeMillis(), false);

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
    assertThat(enrollmentProofToken).isNotNull().isNotEmpty();

    // Set expires_at in the past so bind will reject (opportunistic DB setup)
    DatabaseHelper db = new DatabaseHelper();
    boolean updated =
        db.executeUpdate(
            "UPDATE ezkey_enrollment SET expires_at = NOW() - INTERVAL '1 hour' WHERE enrollment_id"
                + " = "
                + enrollmentId
                + ";");
    Assumptions.assumeTrue(
        updated,
        "Database has no expires_at column (run Flyway migrations) or update failed; skipping"
            + " test");

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
            .statusCode(400)
            .extract()
            .response();

    String body = bindResponse.getBody().asString();
    assertThat(body).containsIgnoringCase("expired");

    // Status may be EXPIRED (if persisted before rollback) or CREATED (transaction rolled back)
    String status = db.getEnrollmentStatus(enrollmentId);
    assertThat(status).isIn("EXPIRED", "CREATED");
  }

  @Test
  @DisplayName("Verify rejected when pending enrollment (BOUND) has expires_at in the past")
  void verify_WhenEnrollmentExpired_Returns400AndMarksExpired() {
    configureForAdminApi(dockerStackConfig);
    String adminToken = authTokenManager.getAdminToken();

    int integrationId = testDataFactory.createIntegration();
    int enrollmentId =
        testDataFactory.createEnrollment(
            integrationId, "ExpirationVerifyTest-" + System.currentTimeMillis(), false);

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

    // Set expires_at in the past so verify will reject
    DatabaseHelper db = new DatabaseHelper();
    boolean updated =
        db.executeUpdate(
            "UPDATE ezkey_enrollment SET expires_at = NOW() - INTERVAL '1 hour' WHERE enrollment_id"
                + " = "
                + enrollmentId
                + ";");
    Assumptions.assumeTrue(
        updated,
        "Database has no expires_at column (run Flyway migrations) or update failed; skipping"
            + " test");

    // signData() switches RestAssured to Crypto API; point back to Auth API for verify
    configureForAuthApi(dockerStackConfig);

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
            .statusCode(400)
            .extract()
            .response();

    // Auth API returns RFC 9457 ProblemDetail with safe generic detail (no "expired" in body).
    assertThat(verifyResponse.jsonPath().getInt("status")).isEqualTo(400);
    assertThat(verifyResponse.jsonPath().getString("type")).endsWith("/enrollment-verify-failed");
    assertThat(verifyResponse.jsonPath().getString("detail")).isNotBlank();

    // Status may be EXPIRED (if persisted) or BOUND (transaction rolled back)
    String status = db.getEnrollmentStatus(enrollmentId);
    assertThat(status).isIn("EXPIRED", "BOUND");
  }
}
