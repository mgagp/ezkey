/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AdminProvisioningSecurityTest
 * Description: Integration tests for admin provisioning endpoints (PATCH admin profile, optimistic locking).
 */

package org.ezkey.tests.security.admin;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.ezkey.tests.util.RestAssuredTestConfig.configureForAdminApi;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.Map;
import org.ezkey.tests.security.AbstractSecurityTest;
import org.ezkey.tests.tags.TestTags;
import org.ezkey.tests.util.DatabaseHelper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for admin provisioning endpoints.
 *
 * <p>Validates PATCH /api/v1/admins/{id} including:
 *
 * <ul>
 *   <li>Happy path: partial update returns 200 with updated fields
 *   <li>Optimistic locking: stale version returns 409 Conflict
 * </ul>
 *
 * @since 2025
 */
@Tag(TestTags.ADMIN)
@Tag(TestTags.INTEGRATION)
@DisplayName("Admin Provisioning Security Tests")
public class AdminProvisioningSecurityTest extends AbstractSecurityTest {

  @Test
  @DisplayName("PATCH admin profile - happy path returns 200 with updated fields")
  void patchAdminProfile_happyPath_returns200() {
    String adminToken;
    try {
      adminToken = authTokenManager.getAdminToken();
    } catch (IllegalStateException e) {
      Assumptions.assumeTrue(false, "Admin token not available. Run bootstrap-init first.");
      return;
    }

    configureForAdminApi(dockerStackConfig);

    // Get current admin (bootstrap creates admin 1)
    Response getResponse =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + adminToken)
            .when()
            .get("/admins/1")
            .then()
            .extract()
            .response();

    Assumptions.assumeTrue(
        getResponse.getStatusCode() == 200, "Admin 1 not found (bootstrap may not have run)");
    Long version = getResponse.jsonPath().getLong("version");

    // PATCH with new firstName (use unique suffix for independence)
    String newFirstName = "PatchTest-" + System.currentTimeMillis();
    Map<String, Object> patchBody =
        Map.of(
            "version", version,
            "firstName", newFirstName);

    Response patchResponse =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + adminToken)
            .body(patchBody)
            .when()
            .patch("/admins/1")
            .then()
            .extract()
            .response();

    assertThat(patchResponse.getStatusCode()).isEqualTo(200);
    assertThat(patchResponse.jsonPath().getString("firstName")).isEqualTo(newFirstName);
    assertThat(patchResponse.jsonPath().getLong("version")).isGreaterThan(version);
  }

  @Test
  @DisplayName("PATCH admin profile - stale version returns 409 Conflict")
  void patchAdminProfile_staleVersion_returns409() {
    String adminToken;
    try {
      adminToken = authTokenManager.getAdminToken();
    } catch (IllegalStateException e) {
      Assumptions.assumeTrue(false, "Admin token not available. Run bootstrap-init first.");
      return;
    }

    configureForAdminApi(dockerStackConfig);

    // Get current admin and version
    Response getResponse =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + adminToken)
            .when()
            .get("/admins/1")
            .then()
            .extract()
            .response();

    Assumptions.assumeTrue(
        getResponse.getStatusCode() == 200, "Admin 1 not found (bootstrap may not have run)");
    Long staleVersion = getResponse.jsonPath().getLong("version");

    // Simulate concurrent update: bump version in DB directly
    DatabaseHelper db = new DatabaseHelper();
    boolean success =
        db.executeUpdate("UPDATE ezkey_admin SET version = version + 1 WHERE admin_id = 1;");
    Assumptions.assumeTrue(success, "Failed to bump version in DB");

    // PATCH with stale version
    Map<String, Object> patchBody = Map.of("version", staleVersion, "firstName", "StaleUpdate");

    Response patchResponse =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + adminToken)
            .body(patchBody)
            .when()
            .patch("/admins/1")
            .then()
            .extract()
            .response();

    assertThat(patchResponse.getStatusCode()).isEqualTo(409);
  }
}
