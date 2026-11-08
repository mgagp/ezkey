/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Description: Live proof of the exposure rule for administrator onboarding material (#1266).
 */
package org.ezkey.tests.security.enrollment;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.ezkey.tests.util.RestAssuredTestConfig.configureForAdminApi;
import static org.ezkey.tests.util.RestAssuredTestConfig.configureForAuthApi;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.Map;
import org.ezkey.tests.security.AbstractSecurityTest;
import org.ezkey.tests.tags.TestTags;
import org.ezkey.tests.util.CryptoApiClient.EcP256KeyPair;
import org.ezkey.tests.util.EnrollmentVerifyDevicePayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Walks one administrator enrollment through {@code CREATED}, {@code BOUND} and {@code VERIFIED}
 * and checks what the onboarding endpoints return at each step: the proof token and QR only while
 * {@code CREATED}, the challenge while {@code CREATED} or {@code BOUND}.
 *
 * @author Ezkey contributors
 * @since 2026
 */
@Tag(TestTags.FAST)
@Tag(TestTags.ENROLLMENT)
@DisplayName("Admin Onboarding Exposure Security Tests")
public class AdminOnboardingExposureSecurityTest extends AbstractSecurityTest {

  @Test
  @DisplayName("Onboarding endpoints return bind material only while it is needed")
  public void onboardingReturnsBindMaterialOnlyWhileNeeded() {
    String adminToken = authTokenManager.getAdminToken();
    long suffix = System.currentTimeMillis();
    Integer tenantId = testDataFactory.createTenant("Exposure 1266 " + suffix, adminToken);
    String username = "exposure-1266-" + suffix;

    configureForAdminApi(dockerStackConfig);
    Integer adminId =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + adminToken)
            .body(
                Map.of(
                    "username",
                    username,
                    "email",
                    username + "@example.com",
                    "firstName",
                    "Exposure",
                    "lastName",
                    "Test",
                    "tenantId",
                    tenantId))
            .when()
            .post("/admins/tenant")
            .then()
            .statusCode(201)
            .extract()
            .path("adminId");

    Map<String, Object> created = getOnboarding(adminId, adminToken);
    assertThat(enrollmentStatus(adminId, adminToken)).isEqualTo("CREATED");
    assertThat(created.get("enrollmentProofToken")).isNotNull();
    assertThat(created.get("enrollmentChallenge")).isNotNull();
    assertThat(qrStatus(adminId, adminToken)).isEqualTo(200);

    Integer enrollmentId = (Integer) created.get("enrollmentId");
    String proofToken = (String) created.get("enrollmentProofToken");
    Integer challenge = (Integer) created.get("enrollmentChallenge");

    configureForAuthApi(dockerStackConfig);
    Response bind =
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("enrollmentId", enrollmentId, "enrollmentProofToken", proofToken))
            .when()
            .post("/enrollments/bind")
            .then()
            .statusCode(200)
            .extract()
            .response();

    Map<String, Object> bound = getOnboarding(adminId, adminToken);
    assertThat(enrollmentStatus(adminId, adminToken)).isEqualTo("BOUND");
    assertThat(bound.get("enrollmentId")).isEqualTo(enrollmentId);
    assertThat(bound.get("enrollmentProofToken")).isNull();
    assertThat(bound.get("enrollmentChallenge")).isEqualTo(challenge);
    assertThat(qrStatus(adminId, adminToken)).isEqualTo(409);

    EcP256KeyPair deviceKeyPair = cryptoApiClient.generateKeyPair();
    String verifyPayload =
        EnrollmentVerifyDevicePayload.build(
            bind.jsonPath().getString("enrollmentProofToken"),
            enrollmentId,
            challenge,
            deviceKeyPair.publicKey());
    String signature = cryptoApiClient.signData(verifyPayload, deviceKeyPair.privateKey());
    configureForAuthApi(dockerStackConfig);
    given()
        .contentType(ContentType.JSON)
        .body(
            Map.of(
                "enrollmentId", enrollmentId,
                "challengeResponse", challenge,
                "devicePublicKey", deviceKeyPair.publicKey(),
                "enrollmentProofTokenSigned", signature,
                "devicePrivateKeyStorageTier", "STANDARD"))
        .when()
        .post("/enrollments/verify")
        .then()
        .statusCode(200);

    Map<String, Object> verified = getOnboarding(adminId, adminToken);
    assertThat(enrollmentStatus(adminId, adminToken)).isEqualTo("VERIFIED");
    assertThat(verified.get("enrollmentId")).isEqualTo(enrollmentId);
    assertThat(verified.get("enrollmentProofToken")).isNull();
    assertThat(verified.get("enrollmentChallenge")).isNull();
    assertThat(qrStatus(adminId, adminToken)).isEqualTo(409);
  }

  private Map<String, Object> getOnboarding(Integer adminId, String adminToken) {
    configureForAdminApi(dockerStackConfig);
    return given()
        .header("Authorization", "Bearer " + adminToken)
        .when()
        .get("/admins/" + adminId + "/onboarding")
        .then()
        .statusCode(200)
        .extract()
        .jsonPath()
        .getMap("$");
  }

  private String enrollmentStatus(Integer adminId, String adminToken) {
    configureForAdminApi(dockerStackConfig);
    return given()
        .header("Authorization", "Bearer " + adminToken)
        .when()
        .get("/admins/" + adminId)
        .then()
        .statusCode(200)
        .extract()
        .path("enrollmentStatus");
  }

  private int qrStatus(Integer adminId, String adminToken) {
    configureForAdminApi(dockerStackConfig);
    return given()
        .header("Authorization", "Bearer " + adminToken)
        .accept("image/png")
        .when()
        .get("/admins/" + adminId + "/onboarding/qrcode")
        .getStatusCode();
  }
}
