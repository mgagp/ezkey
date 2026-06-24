/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: MobileAuthChurnPhaseAHarnessTest
 * Description: Phase A helper that creates one auth attempt for a real-device Maestro iteration.
 */

package org.ezkey.tests.churn;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.ezkey.tests.util.RestAssuredTestConfig.configureForAdminApi;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.ezkey.tests.security.AbstractSecurityTest;
import org.ezkey.tests.tags.TestTags;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Phase A bridge between JUnit and Maestro for one deterministic real-device iteration.
 *
 * <p>Inputs via system properties:
 *
 * <ul>
 *   <li>{@code f1.enrollmentId} (required)
 *   <li>{@code f1.challengeRequested} (optional, default false)
 *   <li>{@code f1.outputFile} (required)
 * </ul>
 *
 * @since 2025
 */
@Tag(TestTags.OPERATIONAL_CHURN)
@DisplayName("Mobile F1 churn harness - Phase A")
public class MobileAuthChurnPhaseAHarnessTest extends AbstractSecurityTest {

  @Test
  @DisplayName("Create one auth attempt and emit machine-readable iteration metadata")
  void createSingleAuthAttemptAndWriteOutput() throws IOException {
    String enrollmentIdRaw = System.getProperty("f1.enrollmentId");
    String outputFileRaw = System.getProperty("f1.outputFile");
    boolean challengeRequested =
        Boolean.parseBoolean(System.getProperty("f1.challengeRequested", "false"));

    assertThat(enrollmentIdRaw)
        .withFailMessage("Missing required -Df1.enrollmentId=<numeric enrollment id>")
        .isNotBlank();
    assertThat(outputFileRaw)
        .withFailMessage("Missing required -Df1.outputFile=<absolute or relative path>")
        .isNotBlank();

    int enrollmentId = Integer.parseInt(enrollmentIdRaw.trim());
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
    Instant endedAt = Instant.now();

    Path outputFile = Path.of(outputFileRaw);
    if (outputFile.getParent() != null) {
      Files.createDirectories(outputFile.getParent());
    }

    ObjectMapper mapper = new ObjectMapper();
    ObjectNode node = mapper.createObjectNode();
    node.put("startedAt", startedAt.toString());
    node.put("endedAt", endedAt.toString());
    node.put("enrollmentId", enrollmentId);
    node.put("challengeRequested", challengeRequested);
    node.put("authAttemptId", authAttemptId);
    node.put("authAttemptStatus", authAttemptStatus);

    mapper.writerWithDefaultPrettyPrinter().writeValue(outputFile.toFile(), node);

    assertThat(authAttemptStatus)
        .withFailMessage("Expected newly created auth attempt to be PENDING, got: %s", authAttemptStatus)
        .isEqualTo("PENDING");
  }
}
