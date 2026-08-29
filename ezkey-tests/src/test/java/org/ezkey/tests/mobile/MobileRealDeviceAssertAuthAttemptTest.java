/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: MobileRealDeviceAssertAuthAttemptTest
 * Description: Asserts Admin API auth-attempt status after a Maestro (or skip-consume) step.
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
 * Building block: GET one auth attempt and assert {@code authAttemptStatus}.
 *
 * <p>System properties: {@code mobile.authAttemptId}, {@code mobile.expectedStatus}, {@code
 * mobile.outputFile}.
 *
 * @since 2026
 */
@Tag(TestTags.MOBILE_REAL_DEVICE)
@DisplayName("Mobile real-device: assert auth attempt status")
public class MobileRealDeviceAssertAuthAttemptTest extends AbstractSecurityTest {

  @Test
  @DisplayName("Assert auth attempt status and write JSON")
  void assertAuthAttemptStatus() throws IOException {
    int authAttemptId =
        Integer.parseInt(MobileRealDeviceIo.requiredProperty("mobile.authAttemptId"));
    String expectedStatus = MobileRealDeviceIo.requiredProperty("mobile.expectedStatus");
    Path outputFile = Path.of(MobileRealDeviceIo.requiredProperty("mobile.outputFile"));

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

    String actual = statusResponse.jsonPath().getString("authAttemptStatus");

    ObjectNode node = MobileRealDeviceIo.objectNode();
    node.put("authAttemptId", authAttemptId);
    node.put("expectedStatus", expectedStatus);
    node.put("authAttemptStatus", actual);
    node.put("assertedAt", Instant.now().toString());
    MobileRealDeviceIo.writeJson(outputFile, node);

    assertThat(actual)
        .withFailMessage(
            "Expected auth attempt %s status %s, got %s", authAttemptId, expectedStatus, actual)
        .isEqualTo(expectedStatus);
  }
}
