/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: MobileRealDeviceCreateAuthAttemptTest
 * Description: Creates one auth attempt and writes correlation metadata for Maestro.
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
import org.ezkey.tests.security.AbstractSecurityTest;
import org.ezkey.tests.tags.TestTags;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

/**
 * Building block: create one Admin API auth attempt and emit ids plus optional 2-digit challenge.
 *
 * <p>System properties: {@code mobile.enrollmentId}, {@code mobile.outputFile}, optional {@code
 * mobile.challengeRequested} (default {@code false}).
 *
 * @since 2026
 */
@Tag(TestTags.MOBILE_REAL_DEVICE)
@DisplayName("Mobile real-device: create auth attempt")
public class MobileRealDeviceCreateAuthAttemptTest extends AbstractSecurityTest {

  @Test
  @DisplayName("Create auth attempt and write iteration JSON")
  void createAuthAttemptAndWriteOutput() throws IOException {
    int enrollmentId = Integer.parseInt(MobileRealDeviceIo.requiredProperty("mobile.enrollmentId"));
    Path outputFile = Path.of(MobileRealDeviceIo.requiredProperty("mobile.outputFile"));
    String challengeRequestedRaw = MobileRealDeviceIo.optionalProperty("mobile.challengeRequested");
    boolean challengeRequested = Boolean.parseBoolean(challengeRequestedRaw);

    Instant startedAt = Instant.now();
    Integer authAttemptId = testDataFactory.createAuthAttempt(enrollmentId, challengeRequested);

    configureForAdminApi(dockerStackConfig);
    Response statusResponse =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + authTokenManager.getAdminToken())
            .when()
            .get("/auth-attempts/" + authAttemptId)
            .then()
            .statusCode(200)
            .extract()
            .response();

    String authAttemptStatus = statusResponse.jsonPath().getString("authAttemptStatus");
    assertThat(authAttemptStatus).isEqualTo("PENDING");

    Integer authAttemptChallenge = null;
    Object challengeRaw = statusResponse.jsonPath().get("authAttemptChallenge");
    if (challengeRaw != null) {
      authAttemptChallenge = Integer.valueOf(String.valueOf(challengeRaw));
    }

    String challengeTwo = null;
    if (authAttemptChallenge != null) {
      challengeTwo = String.format("%02d", authAttemptChallenge);
    }

    ObjectNode node = MobileRealDeviceIo.objectNode();
    node.put("startedAt", startedAt.toString());
    node.put("endedAt", Instant.now().toString());
    node.put("enrollmentId", enrollmentId);
    node.put("challengeRequested", challengeRequested);
    node.put("authAttemptId", authAttemptId);
    node.put("authAttemptStatus", authAttemptStatus);
    if (challengeTwo == null) {
      node.putNull("challengeCode");
    } else {
      node.put("challengeCode", challengeTwo);
    }
    MobileRealDeviceIo.writeJson(outputFile, node);
  }
}
