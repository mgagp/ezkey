/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Description: Live proof that only contactEmail can change before bind (#1206 step 5).
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * An invitation can get a contact email while {@code CREATED}, and nothing else. After bind, the
 * {@code VERIFIED}-only rule applies again.
 *
 * @author Ezkey contributors
 * @since 2026
 */
@Tag(TestTags.FAST)
@Tag(TestTags.ENROLLMENT)
@DisplayName("Enrollment Contact Email Before Bind Tests")
public class EnrollmentContactEmailBeforeBindTest extends AbstractSecurityTest {

  @Test
  @DisplayName("CREATED accepts contactEmail alone; BOUND refuses it")
  public void contactEmailEditableOnlyBeforeBind() {
    String adminToken = authTokenManager.getAdminToken();
    configureForAdminApi(dockerStackConfig);
    Integer integrationId = testDataFactory.createIntegration();
    Integer enrollmentId = testDataFactory.createEnrollment(integrationId);

    Map<String, Object> created = getEnrollment(enrollmentId, adminToken);
    assertThat(created.get("enrollmentStatus")).isEqualTo("CREATED");
    Number version = (Number) created.get("version");

    Response ok =
        patch(
            enrollmentId,
            adminToken,
            Map.of("version", version, "contactEmail", "alice@example.com"));
    assertThat(ok.statusCode()).isEqualTo(200);
    assertThat(ok.jsonPath().getString("contactEmail")).isEqualTo("alice@example.com");

    Number next = ok.jsonPath().get("version");
    Response nameChange =
        patch(enrollmentId, adminToken, Map.of("version", next, "enrollmentName", "Renamed"));
    assertThat(nameChange.statusCode()).isEqualTo(400);
    assertThat(getEnrollment(enrollmentId, adminToken).get("enrollmentName"))
        .isEqualTo(created.get("enrollmentName"));

    configureForAuthApi(dockerStackConfig);
    given()
        .contentType(ContentType.JSON)
        .body(
            Map.of(
                "enrollmentId",
                enrollmentId,
                "enrollmentProofToken",
                created.get("enrollmentProofToken")))
        .when()
        .post("/enrollments/bind")
        .then()
        .statusCode(200);

    Map<String, Object> bound = getEnrollment(enrollmentId, adminToken);
    assertThat(bound.get("enrollmentStatus")).isEqualTo("BOUND");
    Response afterBind =
        patch(
            enrollmentId,
            adminToken,
            Map.of("version", bound.get("version"), "contactEmail", "bob@example.com"));
    assertThat(afterBind.statusCode()).isEqualTo(400);
    assertThat(getEnrollment(enrollmentId, adminToken).get("contactEmail"))
        .isEqualTo("alice@example.com");
  }

  private Response patch(Integer enrollmentId, String adminToken, Map<String, Object> body) {
    configureForAdminApi(dockerStackConfig);
    return given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + adminToken)
        .body(body)
        .when()
        .patch("/enrollments/" + enrollmentId)
        .then()
        .extract()
        .response();
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
}
