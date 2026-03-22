/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: EnrollmentUpdateSecurityTest
 * Description: Integration tests for PATCH /api/v1/enrollments/{id} (enrollment metadata update).
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
import org.ezkey.tests.util.DatabaseHelper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for enrollment PATCH endpoint.
 *
 * <p>Validates PATCH /api/v1/enrollments/{id} including:
 *
 * <ul>
 *   <li>Happy path: partial update returns 200 with updated fields
 *   <li>PATCH with {@code userIdentifier} serializes and persists (contract smoke)
 *   <li>Optimistic locking: stale version returns 409 Conflict
 * </ul>
 *
 * <p>Requires a VERIFIED enrollment. Uses bootstrap admin's enrollment or creates one via
 * testDataFactory (integration + enrollment + bind/verify flow).
 *
 * @since 2025
 */
@Tag(TestTags.ENROLLMENT)
@Tag(TestTags.INTEGRATION)
@DisplayName("Enrollment Update (PATCH) Security Tests")
public class EnrollmentUpdateSecurityTest extends AbstractSecurityTest {

  /**
   * Finds a VERIFIED enrollment ID from the list. Bootstrap creates one for admin 1.
   *
   * @param adminToken Admin bearer token
   * @return enrollment ID or null if none found
   */
  private Integer findVerifiedEnrollmentId(String adminToken) {
    configureForAdminApi(dockerStackConfig);
    Response listResponse =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + adminToken)
            .when()
            .get("/enrollments?status=VERIFIED&size=50")
            .then()
            .extract()
            .response();

    if (listResponse.getStatusCode() != 200) {
      return null;
    }
    List<Map<String, Object>> content = listResponse.jsonPath().getList("content");
    if (content == null || content.isEmpty()) {
      return null;
    }
    // Use first VERIFIED enrollment (bootstrap admin's or any)
    Object id = content.get(0).get("enrollmentId");
    return id != null ? ((Number) id).intValue() : null;
  }

  @Test
  @DisplayName("PATCH enrollment - happy path returns 200 with updated fields")
  void patchEnrollment_happyPath_returns200() {
    String adminToken;
    try {
      adminToken = authTokenManager.getAdminToken();
    } catch (IllegalStateException e) {
      Assumptions.assumeTrue(false, "Admin token not available. Run bootstrap-init first.");
      return;
    }

    Integer enrollmentId = findVerifiedEnrollmentId(adminToken);
    Assumptions.assumeTrue(
        enrollmentId != null, "No VERIFIED enrollment found. Bootstrap creates one for admin 1.");

    configureForAdminApi(dockerStackConfig);

    // Get current enrollment and version
    Response getResponse =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + adminToken)
            .when()
            .get("/enrollments/" + enrollmentId)
            .then()
            .extract()
            .response();

    Assumptions.assumeTrue(
        getResponse.getStatusCode() == 200, "Enrollment " + enrollmentId + " not found");
    Long version = getResponse.jsonPath().getLong("version");
    Assumptions.assumeTrue(version != null, "Enrollment response missing version field");

    // PATCH with new enrollmentName (unique suffix for independence)
    String newName = "PatchTest-" + System.currentTimeMillis();
    Map<String, Object> patchBody = Map.of("version", version, "enrollmentName", newName);

    Response patchResponse =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + adminToken)
            .body(patchBody)
            .when()
            .patch("/enrollments/" + enrollmentId)
            .then()
            .extract()
            .response();

    assertThat(patchResponse.getStatusCode()).isEqualTo(200);
    assertThat(patchResponse.jsonPath().getString("enrollmentName")).isEqualTo(newName);
    assertThat(patchResponse.jsonPath().getLong("version")).isGreaterThan(version);
  }

  @Test
  @DisplayName("PATCH enrollment - userIdentifier returns 200 with updated field")
  void patchEnrollment_userIdentifier_returns200() {
    String adminToken;
    try {
      adminToken = authTokenManager.getAdminToken();
    } catch (IllegalStateException e) {
      Assumptions.assumeTrue(false, "Admin token not available. Run bootstrap-init first.");
      return;
    }

    Integer enrollmentId = findVerifiedEnrollmentId(adminToken);
    Assumptions.assumeTrue(
        enrollmentId != null, "No VERIFIED enrollment found. Bootstrap creates one for admin 1.");

    configureForAdminApi(dockerStackConfig);

    Response getResponse =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + adminToken)
            .when()
            .get("/enrollments/" + enrollmentId)
            .then()
            .extract()
            .response();

    Assumptions.assumeTrue(
        getResponse.getStatusCode() == 200, "Enrollment " + enrollmentId + " not found");
    Long version = getResponse.jsonPath().getLong("version");
    Assumptions.assumeTrue(version != null, "Enrollment response missing version field");

    String newUserId = "patch-api-" + System.currentTimeMillis();
    Map<String, Object> patchBody = Map.of("version", version, "userIdentifier", newUserId);

    Response patchResponse =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + adminToken)
            .body(patchBody)
            .when()
            .patch("/enrollments/" + enrollmentId)
            .then()
            .extract()
            .response();

    assertThat(patchResponse.getStatusCode()).isEqualTo(200);
    assertThat(patchResponse.jsonPath().getString("userIdentifier")).isEqualTo(newUserId);
    assertThat(patchResponse.jsonPath().getLong("version")).isGreaterThan(version);
  }

  @Test
  @DisplayName("PATCH enrollment - stale version returns 409 Conflict")
  void patchEnrollment_staleVersion_returns409() {
    String adminToken;
    try {
      adminToken = authTokenManager.getAdminToken();
    } catch (IllegalStateException e) {
      Assumptions.assumeTrue(false, "Admin token not available. Run bootstrap-init first.");
      return;
    }

    Integer enrollmentId = findVerifiedEnrollmentId(adminToken);
    Assumptions.assumeTrue(
        enrollmentId != null, "No VERIFIED enrollment found. Bootstrap creates one for admin 1.");

    configureForAdminApi(dockerStackConfig);

    // Get current enrollment and version
    Response getResponse =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + adminToken)
            .when()
            .get("/enrollments/" + enrollmentId)
            .then()
            .extract()
            .response();

    Assumptions.assumeTrue(
        getResponse.getStatusCode() == 200, "Enrollment " + enrollmentId + " not found");
    Long staleVersion = getResponse.jsonPath().getLong("version");

    // Simulate concurrent update: bump version in DB directly
    DatabaseHelper db = new DatabaseHelper();
    boolean success =
        db.executeUpdate(
            "UPDATE ezkey_enrollment SET version = version + 1 WHERE enrollment_id = "
                + enrollmentId
                + ";");
    Assumptions.assumeTrue(success, "Failed to bump version in DB");

    // PATCH with stale version
    Map<String, Object> patchBody =
        Map.of("version", staleVersion, "enrollmentName", "StaleUpdate");

    Response patchResponse =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + adminToken)
            .body(patchBody)
            .when()
            .patch("/enrollments/" + enrollmentId)
            .then()
            .extract()
            .response();

    assertThat(patchResponse.getStatusCode()).isEqualTo(409);
  }
}
