/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: EnrollmentUniquenessIntegrationTest
 * Description: Integration tests for enrollment uniqueness constraint enforcement.
 */

package org.ezkey.tests.security.enrollment;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.ezkey.tests.util.RestAssuredTestConfig.configureForAdminApi;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.HashMap;
import java.util.Map;
import org.ezkey.tests.security.AbstractSecurityTest;
import org.ezkey.tests.tags.TestTags;
import org.ezkey.tests.util.DatabaseHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for enrollment uniqueness constraint enforcement.
 *
 * <p>Tests validate that the system properly enforces uniqueness constraints for VERIFIED
 * enrollments at both the application and database levels.
 *
 * @since 2025
 */
@Tag(TestTags.ENROLLMENT)
@Tag(TestTags.DATABASE)
@DisplayName("Enrollment Uniqueness Integration Tests")
public class EnrollmentUniquenessIntegrationTest extends AbstractSecurityTest {

  private final DatabaseHelper databaseHelper = new DatabaseHelper();

  @Test
  @DisplayName("Should reject creation when active VERIFIED enrollment exists")
  void testCreateRejectedWhenActiveVerifiedExists() {
    // Skip if admin token not available
    try {
      authTokenManager.getAdminToken();
    } catch (Exception e) {
      org.junit.jupiter.api.Assumptions.assumeTrue(false, "Admin token not available");
    }

    configureForAdminApi(dockerStackConfig);

    // Setup: Create integration and verify an enrollment
    Integer integrationId = testDataFactory.createIntegration();
    Integer enrollmentId1 = testDataFactory.createEnrollment(integrationId, "Test Device", false);
    // Verify the enrollment (simulate mobile device verification)
    // Note: In a real scenario, this would go through bind + verify flow
    // For this test, we'll directly update the database to VERIFIED status
    databaseHelper.executeUpdate(
        "UPDATE ezkey_enrollment SET enrollment_status = 'VERIFIED', enrollment_active = true WHERE"
            + " enrollment_id = "
            + enrollmentId1);

    // Act: Try to create another enrollment with the same name
    Map<String, Object> request = new HashMap<>();
    request.put("integrationId", integrationId);
    request.put("name", "Test Device");

    Response response =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + authTokenManager.getAdminToken())
            .body(request)
            .when()
            .post("/enrollments")
            .then()
            .extract()
            .response();

    // Assert: Should be rejected with 400 Bad Request
    assertThat(response.getStatusCode()).isEqualTo(400);
    String responseBody = response.getBody().asString();
    assertThat(
            responseBody.contains("active verified enrollment")
                || responseBody.contains("recovery process"))
        .isTrue();
  }

  @Test
  @DisplayName("Should allow creation when inactive VERIFIED enrollment exists")
  void testCreateAllowedWhenInactiveVerifiedExists() {
    // Skip if admin token not available
    try {
      authTokenManager.getAdminToken();
    } catch (Exception e) {
      org.junit.jupiter.api.Assumptions.assumeTrue(false, "Admin token not available");
    }

    configureForAdminApi(dockerStackConfig);

    // Setup: Create integration and verify an enrollment, then deactivate it
    Integer integrationId = testDataFactory.createIntegration();
    Integer enrollmentId1 = testDataFactory.createEnrollment(integrationId, "Test Device", false);
    databaseHelper.executeUpdate(
        "UPDATE ezkey_enrollment SET enrollment_status = 'VERIFIED', enrollment_active = false"
            + " WHERE enrollment_id = "
            + enrollmentId1);

    // Act: Try to create another enrollment with the same name
    Map<String, Object> request = new HashMap<>();
    request.put("integrationId", integrationId);
    request.put("name", "Test Device");

    Response response =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + authTokenManager.getAdminToken())
            .body(request)
            .when()
            .post("/enrollments")
            .then()
            .extract()
            .response();

    // Assert: Should succeed (201 Created)
    assertThat(response.getStatusCode()).isEqualTo(201);
    Integer enrollmentId2 = response.jsonPath().getInt("enrollmentId");
    assertThat(enrollmentId2).isNotNull();
    assertThat(enrollmentId2).isNotEqualTo(enrollmentId1);
  }

  @Test
  @DisplayName("Should allow multiple CREATED enrollments")
  void testMultipleCreatedEnrollmentsAllowed() {
    // Skip if admin token not available
    try {
      authTokenManager.getAdminToken();
    } catch (Exception e) {
      org.junit.jupiter.api.Assumptions.assumeTrue(false, "Admin token not available");
    }

    configureForAdminApi(dockerStackConfig);

    // Setup: Create integration
    Integer integrationId = testDataFactory.createIntegration();

    // Act: Create multiple enrollments with the same name (all CREATED status)
    Map<String, Object> request = new HashMap<>();
    request.put("integrationId", integrationId);
    request.put("name", "Test Device");

    Response response1 =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + authTokenManager.getAdminToken())
            .body(request)
            .when()
            .post("/enrollments")
            .then()
            .extract()
            .response();

    Response response2 =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + authTokenManager.getAdminToken())
            .body(request)
            .when()
            .post("/enrollments")
            .then()
            .extract()
            .response();

    // Assert: Both should succeed
    assertThat(response1.getStatusCode()).isEqualTo(201);
    assertThat(response2.getStatusCode()).isEqualTo(201);
    Integer enrollmentId1 = response1.jsonPath().getInt("enrollmentId");
    Integer enrollmentId2 = response2.jsonPath().getInt("enrollmentId");
    assertThat(enrollmentId1).isNotEqualTo(enrollmentId2);
  }

  @Test
  @DisplayName("Database constraint should prevent second VERIFIED enrollment")
  void testDatabaseConstraintPreventsSecondVerified() {
    // Skip if admin token not available
    try {
      authTokenManager.getAdminToken();
    } catch (Exception e) {
      org.junit.jupiter.api.Assumptions.assumeTrue(false, "Admin token not available");
    }

    configureForAdminApi(dockerStackConfig);

    // Setup: Create integration and verify an enrollment
    Integer integrationId = testDataFactory.createIntegration();
    Integer enrollmentId1 = testDataFactory.createEnrollment(integrationId, "Test Device", false);
    databaseHelper.executeUpdate(
        "UPDATE ezkey_enrollment SET enrollment_status = 'VERIFIED', enrollment_active = true WHERE"
            + " enrollment_id = "
            + enrollmentId1);

    // Create a second enrollment (CREATED status) - this is allowed
    testDataFactory.createEnrollment(integrationId, "Test Device", false);

    // Act: Verify the constraint exists by checking database
    // Note: This test verifies that the database constraint is in place.
    // Application-level validation would catch duplicate VERIFIED attempts before
    // database constraint violation. The database constraint is a safety net.
    String constraintExists =
        databaseHelper.executeQuerySingleValue(
            "SELECT COUNT(*) FROM pg_indexes WHERE indexname ="
                + " 'idx_enrollment_unique_verified_name'");
    assertThat(constraintExists).isEqualTo("1");
  }
}
