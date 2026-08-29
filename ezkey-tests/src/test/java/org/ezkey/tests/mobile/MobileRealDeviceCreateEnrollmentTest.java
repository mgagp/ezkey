/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: MobileRealDeviceCreateEnrollmentTest
 * Description: Creates a fresh enrollment and writes F2a seed fields for Maestro.
 */

package org.ezkey.tests.mobile;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.ezkey.tests.util.RestAssuredTestConfig.configureForAdminApi;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.ezkey.tests.security.AbstractSecurityTest;
import org.ezkey.tests.tags.TestTags;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

/**
 * Building block: create one Admin API enrollment and emit bind/verify seed JSON for F2a Maestro.
 *
 * <p>System properties: {@code mobile.outputFile} (required), {@code mobile.authUrl} (required —
 * must be reachable from the phone), optional {@code mobile.integrationId}, {@code
 * mobile.enrollmentName}.
 *
 * @since 2026
 */
@Tag(TestTags.MOBILE_REAL_DEVICE)
@DisplayName("Mobile real-device: create enrollment seed")
public class MobileRealDeviceCreateEnrollmentTest extends AbstractSecurityTest {

  @Test
  @DisplayName("Create enrollment and write F2a seed JSON")
  void createEnrollmentAndWriteSeed() throws IOException {
    Path outputFile = Path.of(MobileRealDeviceIo.requiredProperty("mobile.outputFile"));
    String authUrl = MobileRealDeviceIo.requiredProperty("mobile.authUrl");
    String enrollmentName = MobileRealDeviceIo.optionalProperty("mobile.enrollmentName");
    if (enrollmentName == null) {
      enrollmentName = "pixel-campaign-" + Instant.now().toEpochMilli();
    }

    String adminToken = authTokenManager.getAdminToken();
    Integer integrationId;
    String integrationIdRaw = MobileRealDeviceIo.optionalProperty("mobile.integrationId");
    if (integrationIdRaw != null) {
      integrationId = Integer.valueOf(integrationIdRaw);
    } else {
      integrationId =
          testDataFactory.createIntegration(
              "mobile-real-device-" + UUID.randomUUID().toString().substring(0, 8),
              "Real-device Maestro campaign integration");
    }

    Integer enrollmentId =
        testDataFactory.createEnrollment(integrationId, enrollmentName, false, adminToken);

    configureForAdminApi(dockerStackConfig);
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
    Integer enrollmentChallenge = enrollmentResponse.jsonPath().getInt("enrollmentChallenge");
    assertThat(enrollmentProofToken).isNotBlank();
    assertThat(enrollmentChallenge).isNotNull();

    String challengeSix = String.format("%06d", enrollmentChallenge);

    ObjectNode node = MobileRealDeviceIo.objectNode();
    node.put("enrollmentId", enrollmentId);
    node.put("enrollmentProofToken", enrollmentProofToken);
    node.put("enrollmentAuthUrl", authUrl);
    node.put("enrollmentChallenge", challengeSix);
    node.put("integrationId", integrationId);
    node.put("enrollmentName", enrollmentName);
    node.put("createdAt", Instant.now().toString());
    MobileRealDeviceIo.writeJson(outputFile, node);
  }
}
