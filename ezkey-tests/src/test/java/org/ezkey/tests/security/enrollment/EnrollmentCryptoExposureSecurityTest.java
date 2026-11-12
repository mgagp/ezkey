/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Description: Live proof of the Admin API exposure rule for enrollment bind material (#1180).
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
 * Walks one enrollment through {@code CREATED}, {@code BOUND} and {@code VERIFIED} and checks what
 * the Admin API returns at each step: the proof token and QR only while {@code CREATED}, the
 * challenge while {@code CREATED} or {@code BOUND}, the public keys never.
 *
 * @author Ezkey contributors
 * @since 2026
 */
@Tag(TestTags.FAST)
@Tag(TestTags.ENROLLMENT)
@DisplayName("Enrollment Crypto Exposure Security Tests")
public class EnrollmentCryptoExposureSecurityTest extends AbstractSecurityTest {

  @Test
  @DisplayName("Admin API returns bind material only while it plays an operator role")
  public void adminApiReturnsBindMaterialOnlyWhileUseful() {
    String adminToken = authTokenManager.getAdminToken();
    configureForAdminApi(dockerStackConfig);
    Integer integrationId = testDataFactory.createIntegration();
    Integer enrollmentId = testDataFactory.createEnrollment(integrationId);

    Map<String, Object> created = getEnrollment(enrollmentId, adminToken);
    assertThat(created.get("enrollmentStatus")).isEqualTo("CREATED");
    assertThat(created.get("enrollmentProofToken")).isNotNull();
    assertThat(created.get("enrollmentChallenge")).isNotNull();
    assertThat(created).doesNotContainKeys("integrationPublicKey", "devicePublicKey");
    assertThat(qrStatus(enrollmentId, adminToken)).isEqualTo(200);

    String proofToken = (String) created.get("enrollmentProofToken");
    Integer challenge = (Integer) created.get("enrollmentChallenge");

    configureForAuthApi(dockerStackConfig);
    Response bind =
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("enrollmentProofToken", proofToken))
            .when()
            .post("/enrollments/bind")
            .then()
            .statusCode(200)
            .extract()
            .response();
    assertThat(bind.jsonPath().getString("integrationPublicKey")).isNotBlank();

    Map<String, Object> bound = getEnrollment(enrollmentId, adminToken);
    assertThat(bound.get("enrollmentStatus")).isEqualTo("BOUND");
    assertThat(bound.get("enrollmentProofToken")).isNull();
    assertThat(bound.get("enrollmentChallenge")).isEqualTo(challenge);
    assertThat(bound).doesNotContainKeys("integrationPublicKey", "devicePublicKey");
    assertThat(qrStatus(enrollmentId, adminToken)).isEqualTo(409);

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

    Map<String, Object> verified = getEnrollment(enrollmentId, adminToken);
    assertThat(verified.get("enrollmentStatus")).isEqualTo("VERIFIED");
    assertThat(verified.get("enrollmentProofToken")).isNull();
    assertThat(verified.get("enrollmentChallenge")).isNull();
    assertThat(verified).doesNotContainKeys("integrationPublicKey", "devicePublicKey");
    assertThat(qrStatus(enrollmentId, adminToken)).isEqualTo(409);
  }

  private Map<String, Object> getEnrollment(Integer enrollmentId, String adminToken) {
    configureForAdminApi(dockerStackConfig);
    return given()
        .header("Authorization", "Bearer " + adminToken)
        .when()
        .get("/enrollments/" + enrollmentId)
        .then()
        .statusCode(200)
        .extract()
        .jsonPath()
        .getMap("$");
  }

  private int qrStatus(Integer enrollmentId, String adminToken) {
    configureForAdminApi(dockerStackConfig);
    return given()
        .header("Authorization", "Bearer " + adminToken)
        .accept("image/png")
        .when()
        .get("/enrollments/" + enrollmentId + "/qrcode")
        .getStatusCode();
  }
}
