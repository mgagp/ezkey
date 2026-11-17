/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Description: Live proof that an administrator's bind material has one audited source (#1313).
 */
package org.ezkey.tests.security.enrollment;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.ezkey.tests.util.RestAssuredTestConfig.configureForAdminApi;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.List;
import java.util.Map;
import org.ezkey.tests.security.AbstractSecurityTest;
import org.ezkey.tests.tags.TestTags;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * On a live stack, for one administrator enrollment at {@code CREATED} (#1313, tests T1, T2, T4, T8
 * and T9 of the issue):
 *
 * <ul>
 *   <li>{@code GET /enrollments/{id}} opens the record with no token and no challenge, and gives a
 *       Global Admin the owning administrator;
 *   <li>{@code /enrollments/{id}/qrcode} answers {@code 409 admin-enrollment};
 *   <li>writing or clearing {@code contactEmail} answers {@code 409 admin-enrollment} and changes
 *       nothing;
 *   <li>{@code /admins/{id}/onboarding} still returns the material, and that reveal leaves one
 *       audit row that does not hold the material.
 * </ul>
 *
 * @author Ezkey contributors
 * @since 2026
 */
@Tag(TestTags.FAST)
@Tag(TestTags.ENROLLMENT)
@DisplayName("Admin enrollment single source security tests (#1313)")
public class AdminEnrollmentSingleSourceSecurityTest extends AbstractSecurityTest {

  @Test
  @DisplayName("An administrator's bind material comes only from the audited onboarding endpoints")
  public void adminBindMaterialHasOneAuditedSource() {
    String adminToken = authTokenManager.getAdminToken();
    long suffix = System.currentTimeMillis();
    Integer tenantId = testDataFactory.createTenant("Single source 1313 " + suffix, adminToken);
    String username = "single-source-1313-" + suffix;

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
                    "Single",
                    "lastName",
                    "Source",
                    "tenantId",
                    tenantId))
            .when()
            .post("/admins/tenant")
            .then()
            .statusCode(201)
            .extract()
            .path("adminId");

    // The audited source still returns the material.
    Map<String, Object> onboarding =
        given()
            .header("Authorization", "Bearer " + adminToken)
            .when()
            .get("/admins/" + adminId + "/onboarding")
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()
            .getMap("$");
    Integer enrollmentId = (Integer) onboarding.get("enrollmentId");
    String proofToken = (String) onboarding.get("enrollmentProofToken");
    assertThat(proofToken).isNotBlank();
    assertThat(onboarding.get("enrollmentChallenge")).isNotNull();

    // T4 + T8: the enrollment record opens, without bind material, with the owner for Global.
    Response record =
        given()
            .header("Authorization", "Bearer " + adminToken)
            .when()
            .get("/enrollments/" + enrollmentId)
            .then()
            .statusCode(200)
            .extract()
            .response();
    assertThat(record.jsonPath().getString("enrollmentStatus")).isEqualTo("CREATED");
    assertThat(record.jsonPath().getBoolean("isSystemIntegration")).isTrue();
    assertThat(record.jsonPath().getString("enrollmentProofToken")).isNull();
    assertThat(record.jsonPath().getString("enrollmentChallenge")).isNull();
    assertThat(record.jsonPath().getInt("ownerAdminId")).isEqualTo(adminId);
    assertThat(record.getBody().asString()).doesNotContain(proofToken);

    // T8: the QR is refused on the enrollment API.
    Response qr =
        given()
            .header("Authorization", "Bearer " + adminToken)
            .when()
            .get("/enrollments/" + enrollmentId + "/qrcode");
    assertThat(qr.getStatusCode()).isEqualTo(409);
    assertThat(qr.jsonPath().getString("code")).isEqualTo("admin-enrollment");

    // T1 + T2: no contact email on an administrator enrollment, set or clear.
    for (Map<String, Object> body :
        List.<Map<String, Object>>of(
            Map.of("contactEmail", "someone@example.com"), Map.of("clearContactEmail", true))) {
      Response patch =
          given()
              .contentType(ContentType.JSON)
              .header("Authorization", "Bearer " + adminToken)
              .body(body)
              .when()
              .patch("/enrollments/" + enrollmentId);
      assertThat(patch.getStatusCode()).as("PATCH %s", body).isEqualTo(409);
      assertThat(patch.jsonPath().getString("code")).isEqualTo("admin-enrollment");
    }
    String contactEmail =
        given()
            .header("Authorization", "Bearer " + adminToken)
            .when()
            .get("/enrollments/" + enrollmentId)
            .then()
            .statusCode(200)
            .extract()
            .path("contactEmail");
    assertThat(contactEmail).isNull();

    // T9: the onboarding reveal above left an audit row without the material.
    List<Map<String, Object>> rows =
        given()
            .header("Authorization", "Bearer " + adminToken)
            .queryParam("eventType", "ADMIN_BIND_MATERIAL_REVEALED")
            .queryParam("page", 0)
            .queryParam("size", 100)
            .queryParam("sort", "createdAt,DESC")
            .when()
            .get("/audit-logs")
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()
            .getList("content");
    List<Map<String, Object>> forThisAdmin =
        rows.stream().filter(row -> adminId.equals(row.get("targetAdminId"))).toList();
    assertThat(forThisAdmin).hasSize(1);
    Map<String, Object> row = forThisAdmin.get(0);
    assertThat(row.get("enrollmentId")).isEqualTo(enrollmentId);
    assertThat(String.valueOf(row.get("eventDetails")))
        .contains("json")
        .doesNotContain(proofToken)
        .doesNotContain(String.valueOf(onboarding.get("enrollmentChallenge")));
  }
}
