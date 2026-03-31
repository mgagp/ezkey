/*
 * Ezkey - Open Source Cryptographic MFA Platform
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

    // Note: Cannot create second enrollment with same name via API when VERIFIED
    // exists
    // (application-level validation rejects it). Instead, we verify the constraint
    // exists
    // and test the constraint directly via database update.

    // Act: Verify the constraint exists by checking database
    // Note: This test verifies that the database constraint is in place.
    // Application-level validation would catch duplicate VERIFIED attempts before
    // database constraint violation. The database constraint is a safety net.
    String constraintExists =
        databaseHelper.executeQuerySingleValue(
            "SELECT COUNT(*) FROM pg_indexes WHERE indexname ="
                + " 'idx_enrollment_unique_verified_name'");
    assertThat(constraintExists).isEqualTo("1");

    // Verify constraint prevents second VERIFIED enrollment directly in database
    // Create a second enrollment directly in DB (bypassing application validation)
    // to test the database constraint
    // Get required keys from first enrollment
    String integrationPrivateKey =
        databaseHelper.executeQuerySingleValue(
            "SELECT integration_private_key FROM ezkey_enrollment WHERE enrollment_id = "
                + enrollmentId1);
    String integrationPublicKey =
        databaseHelper.executeQuerySingleValue(
            "SELECT integration_public_key FROM ezkey_enrollment WHERE enrollment_id = "
                + enrollmentId1);
    assertThat(integrationPrivateKey).isNotNull();
    assertThat(integrationPublicKey).isNotNull();

    // Escape single quotes in keys for SQL
    String escapedPrivateKey = integrationPrivateKey.replace("'", "''");
    String escapedPublicKey = integrationPublicKey.replace("'", "''");
    // Encrypt proof token so raw INSERT matches application encryption-at-rest. Use unique
    // plaintext per INSERT so enrollment_proof_token_hash stays unique across tests.
    String plaintextToken =
        "test-token-2-" + integrationId + "-" + enrollmentId1 + "-" + UUID.randomUUID();
    String encryptedProofToken = cryptoApiClient.encrypt(plaintextToken);
    assertThat(encryptedProofToken)
        .as("Encryption required for test data integrity (Crypto API must be available)")
        .isNotNull();
    String proofTokenHash = sha256Hex(plaintextToken);
    String escapedEncryptedToken = encryptedProofToken.replace("'", "''");
    // Use executeQuery to get all results, then find the enrollment_id value
    List<String> insertResults =
        databaseHelper.executeQuery(
            "INSERT INTO ezkey_enrollment (integration_id, enrollment_name,"
                + " enrollment_status, enrollment_active, enrollment_proof_token,"
                + " enrollment_proof_token_hash, enrollment_challenge, integration_private_key,"
                + " integration_public_key, created_at) VALUES ("
                + integrationId
                + ", 'Test Device', 'CREATED', false, '"
                + escapedEncryptedToken
                + "', '"
                + proofTokenHash
                + "', 123457, '"
                + escapedPrivateKey
                + "', '"
                + escapedPublicKey
                + "', NOW()) RETURNING"
                + " enrollment_id;");
    // INSERT ... RETURNING with psql -t -A returns: "INSERT 0 1" then the value
    // Find the enrollment_id value (should be the last numeric value)
    String enrollmentId2Str = null;
    for (int i = insertResults.size() - 1; i >= 0; i--) {
      String result = insertResults.get(i);
      if (result.matches("\\d+")) {
        enrollmentId2Str = result;
        break;
      }
    }
    assertThat(enrollmentId2Str).as("INSERT ... RETURNING should return enrollment_id").isNotNull();
    Integer enrollmentId2 = Integer.parseInt(enrollmentId2Str.trim());

    // Try to update to VERIFIED - should be blocked by constraint
    boolean updateSucceeded =
        databaseHelper.executeUpdate(
            "UPDATE ezkey_enrollment SET enrollment_status = 'VERIFIED', enrollment_active = true"
                + " WHERE enrollment_id = "
                + enrollmentId2);
    // executeUpdate returns false on constraint violation
    assertThat(updateSucceeded)
        .as("Database constraint should have prevented second VERIFIED enrollment")
        .isFalse();
  }

  @Test
  @DisplayName(
      "Complete flow: create → verify → create another → verify (second verification rejected)")
  void testCompleteFlowCreateVerifyCreateVerifyRejected() {
    // Skip if admin token not available
    try {
      authTokenManager.getAdminToken();
    } catch (Exception e) {
      org.junit.jupiter.api.Assumptions.assumeTrue(false, "Admin token not available");
    }

    configureForAdminApi(dockerStackConfig);

    // Setup: Create integration
    Integer integrationId = testDataFactory.createIntegration();

    // Step 1: Create first enrollment
    Map<String, Object> createRequest1 = new HashMap<>();
    createRequest1.put("integrationId", integrationId);
    createRequest1.put("name", "Test Device");

    Response createResponse1 =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + authTokenManager.getAdminToken())
            .body(createRequest1)
            .when()
            .post("/enrollments")
            .then()
            .extract()
            .response();

    assertThat(createResponse1.getStatusCode()).isEqualTo(201);
    Integer enrollmentId1 = createResponse1.jsonPath().getInt("enrollmentId");
    assertThat(enrollmentId1).isNotNull();

    // Verify enrollment exists and is CREATED before update
    String initialStatus = databaseHelper.getEnrollmentStatus(enrollmentId1);
    assertThat(initialStatus).isEqualTo("CREATED");

    // Step 2: Verify first enrollment (simulate mobile device verification)
    boolean verifyUpdateSucceeded =
        databaseHelper.executeUpdate(
            "UPDATE ezkey_enrollment SET enrollment_status = 'VERIFIED', enrollment_active = true"
                + " WHERE enrollment_id = "
                + enrollmentId1);
    assertThat(verifyUpdateSucceeded)
        .as("First enrollment should be successfully updated to VERIFIED")
        .isTrue();

    // Verify first enrollment is VERIFIED and active
    String status1 = databaseHelper.getEnrollmentStatus(enrollmentId1);
    String active1Str =
        databaseHelper.executeQuerySingleValue(
            "SELECT enrollment_active FROM ezkey_enrollment WHERE enrollment_id = "
                + enrollmentId1);
    // PostgreSQL returns 't' for true and 'f' for false, not 'true'/'false'
    Boolean active1 =
        active1Str != null
            && ("t".equalsIgnoreCase(active1Str.trim()) || Boolean.parseBoolean(active1Str.trim()));
    assertThat(status1)
        .as("First enrollment status should be VERIFIED after update")
        .isEqualTo("VERIFIED");
    assertThat(active1)
        .as("First enrollment should be active after verification. Current value: " + active1Str)
        .isTrue();

    // Step 3: Create second enrollment with same name
    // NOTE: Application-level validation rejects creation when VERIFIED exists, so
    // we create
    // directly in DB to test the database constraint
    // Get required keys from first enrollment
    String integrationPrivateKey =
        databaseHelper.executeQuerySingleValue(
            "SELECT integration_private_key FROM ezkey_enrollment WHERE enrollment_id = "
                + enrollmentId1);
    String integrationPublicKey =
        databaseHelper.executeQuerySingleValue(
            "SELECT integration_public_key FROM ezkey_enrollment WHERE enrollment_id = "
                + enrollmentId1);
    assertThat(integrationPrivateKey).isNotNull();
    assertThat(integrationPublicKey).isNotNull();

    // Escape single quotes in keys for SQL
    String escapedPrivateKey = integrationPrivateKey.replace("'", "''");
    String escapedPublicKey = integrationPublicKey.replace("'", "''");
    String plaintextToken =
        "test-token-2-" + integrationId + "-" + enrollmentId1 + "-" + UUID.randomUUID();
    String encryptedProofToken = cryptoApiClient.encrypt(plaintextToken);
    assertThat(encryptedProofToken)
        .as("Encryption required for test data integrity (Crypto API must be available)")
        .isNotNull();
    String proofTokenHash = sha256Hex(plaintextToken);
    String escapedEncryptedToken = encryptedProofToken.replace("'", "''");
    List<String> insertResults =
        databaseHelper.executeQuery(
            "INSERT INTO ezkey_enrollment (integration_id, enrollment_name,"
                + " enrollment_status, enrollment_active, enrollment_proof_token,"
                + " enrollment_proof_token_hash, enrollment_challenge, integration_private_key,"
                + " integration_public_key, created_at) VALUES ("
                + integrationId
                + ", 'Test Device', 'CREATED', false, '"
                + escapedEncryptedToken
                + "', '"
                + proofTokenHash
                + "', 123457, '"
                + escapedPrivateKey
                + "', '"
                + escapedPublicKey
                + "', NOW()) RETURNING"
                + " enrollment_id;");
    // Find the enrollment_id value (should be the last numeric value)
    String enrollmentId2Str = null;
    for (int i = insertResults.size() - 1; i >= 0; i--) {
      String result = insertResults.get(i);
      if (result.matches("\\d+")) {
        enrollmentId2Str = result;
        break;
      }
    }
    assertThat(enrollmentId2Str).as("INSERT ... RETURNING should return enrollment_id").isNotNull();
    Integer enrollmentId2 = Integer.parseInt(enrollmentId2Str.trim());
    assertThat(enrollmentId2).isNotEqualTo(enrollmentId1);

    // Verify second enrollment is CREATED
    String status2 = databaseHelper.getEnrollmentStatus(enrollmentId2);
    assertThat(status2).isEqualTo("CREATED");

    // Step 4: Try to verify second enrollment (should be rejected)
    // Simulate verification attempt by trying to update to VERIFIED
    // Application-level validation should prevent this, but we'll test via API if
    // possible
    // For this test, we verify that the constraint prevents direct DB update
    // executeUpdate returns false on constraint violation
    boolean updateSucceeded =
        databaseHelper.executeUpdate(
            "UPDATE ezkey_enrollment SET enrollment_status = 'VERIFIED', enrollment_active = true"
                + " WHERE enrollment_id = "
                + enrollmentId2);
    assertThat(updateSucceeded)
        .as("Database constraint should have prevented second VERIFIED enrollment")
        .isFalse();

    // Verify first enrollment is still VERIFIED and active
    String finalStatus1 = databaseHelper.getEnrollmentStatus(enrollmentId1);
    String finalActive1Str =
        databaseHelper.executeQuerySingleValue(
            "SELECT enrollment_active FROM ezkey_enrollment WHERE enrollment_id = "
                + enrollmentId1);
    // PostgreSQL returns 't' for true and 'f' for false, not 'true'/'false'
    Boolean finalActive1 =
        finalActive1Str != null
            && ("t".equalsIgnoreCase(finalActive1Str.trim())
                || Boolean.parseBoolean(finalActive1Str.trim()));
    assertThat(finalStatus1).isEqualTo("VERIFIED");
    assertThat(finalActive1).isTrue();
  }

  @Test
  @DisplayName("Verification rejected when VERIFIED enrollment exists (end-to-end)")
  void testVerificationRejectedWhenVerifiedExists() {
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

    // Create second enrollment (CREATED status) directly in DB to bypass
    // application validation
    // Application-level validation would reject creation when VERIFIED exists, but
    // we want to test
    // the database constraint, so we create it directly
    // Get required keys from first enrollment
    String integrationPrivateKey =
        databaseHelper.executeQuerySingleValue(
            "SELECT integration_private_key FROM ezkey_enrollment WHERE enrollment_id = "
                + enrollmentId1);
    String integrationPublicKey =
        databaseHelper.executeQuerySingleValue(
            "SELECT integration_public_key FROM ezkey_enrollment WHERE enrollment_id = "
                + enrollmentId1);
    assertThat(integrationPrivateKey).isNotNull();
    assertThat(integrationPublicKey).isNotNull();

    // Escape single quotes in keys for SQL
    String escapedPrivateKey = integrationPrivateKey.replace("'", "''");
    String escapedPublicKey = integrationPublicKey.replace("'", "''");
    String plaintextToken =
        "test-token-2-" + integrationId + "-" + enrollmentId1 + "-" + UUID.randomUUID();
    String encryptedProofToken = cryptoApiClient.encrypt(plaintextToken);
    assertThat(encryptedProofToken)
        .as("Encryption required for test data integrity (Crypto API must be available)")
        .isNotNull();
    String proofTokenHash = sha256Hex(plaintextToken);
    String escapedEncryptedToken = encryptedProofToken.replace("'", "''");
    List<String> insertResults =
        databaseHelper.executeQuery(
            "INSERT INTO ezkey_enrollment (integration_id, enrollment_name,"
                + " enrollment_status, enrollment_active, enrollment_proof_token,"
                + " enrollment_proof_token_hash, enrollment_challenge, integration_private_key,"
                + " integration_public_key, created_at) VALUES ("
                + integrationId
                + ", 'Test Device', 'CREATED', false, '"
                + escapedEncryptedToken
                + "', '"
                + proofTokenHash
                + "', 123457, '"
                + escapedPrivateKey
                + "', '"
                + escapedPublicKey
                + "', NOW()) RETURNING"
                + " enrollment_id;");
    // Find the enrollment_id value (should be the last numeric value)
    String enrollmentId2Str = null;
    for (int i = insertResults.size() - 1; i >= 0; i--) {
      String result = insertResults.get(i);
      if (result.matches("\\d+")) {
        enrollmentId2Str = result;
        break;
      }
    }
    assertThat(enrollmentId2Str).as("INSERT ... RETURNING should return enrollment_id").isNotNull();
    Integer enrollmentId2 = Integer.parseInt(enrollmentId2Str.trim());

    // Bind the second enrollment (required before verification)
    databaseHelper.executeUpdate(
        "UPDATE ezkey_enrollment SET enrollment_status = 'BOUND' WHERE enrollment_id = "
            + enrollmentId2);

    // Act: Try to verify second enrollment via API
    // Note: This would require actual cryptographic signatures, so we test the
    // constraint at DB level
    // The application-level validation in EnrollmentVerifyService should catch this
    // before DB

    // Verify via database that constraint prevents second VERIFIED
    boolean updateSucceeded =
        databaseHelper.executeUpdate(
            "UPDATE ezkey_enrollment SET enrollment_status = 'VERIFIED', enrollment_active = true"
                + " WHERE enrollment_id = "
                + enrollmentId2);
    // executeUpdate returns false on constraint violation
    assertThat(updateSucceeded)
        .as("Should not be able to create second VERIFIED enrollment")
        .isFalse();

    // Verify first enrollment is still VERIFIED
    String status1 = databaseHelper.getEnrollmentStatus(enrollmentId1);
    assertThat(status1).isEqualTo("VERIFIED");
  }

  @Test
  @DisplayName(
      "Inactive VERIFIED enrollments prevent new verification (active flag doesn't affect"
          + " uniqueness)")
  void testInactiveVerifiedPreventsVerification() {
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
    // Verify and then deactivate
    databaseHelper.executeUpdate(
        "UPDATE ezkey_enrollment SET enrollment_status = 'VERIFIED', enrollment_active = false"
            + " WHERE enrollment_id = "
            + enrollmentId1);

    // Verify enrollment is VERIFIED but inactive
    String status1 = databaseHelper.getEnrollmentStatus(enrollmentId1);
    String active1Str =
        databaseHelper.executeQuerySingleValue(
            "SELECT enrollment_active FROM ezkey_enrollment WHERE enrollment_id = "
                + enrollmentId1);
    // PostgreSQL returns 't' for true and 'f' for false, not 'true'/'false'
    Boolean active1 =
        active1Str != null
            && ("t".equalsIgnoreCase(active1Str.trim()) || Boolean.parseBoolean(active1Str.trim()));
    assertThat(status1).isEqualTo("VERIFIED");
    assertThat(active1).isFalse();

    // Create second enrollment (CREATED status) - this SHOULD be allowed via API
    // when VERIFIED is
    // inactive (according to business logic)
    Integer enrollmentId2 = testDataFactory.createEnrollment(integrationId, "Test Device", false);

    // Bind the second enrollment
    databaseHelper.executeUpdate(
        "UPDATE ezkey_enrollment SET enrollment_status = 'BOUND' WHERE enrollment_id = "
            + enrollmentId2);

    // Act: Try to verify second enrollment - should be rejected even though first
    // is inactive
    // CRITICAL: Inactive VERIFIED enrollments also prevent verification (active
    // flag doesn't affect
    // uniqueness)
    boolean updateSucceeded =
        databaseHelper.executeUpdate(
            "UPDATE ezkey_enrollment SET enrollment_status = 'VERIFIED', enrollment_active = true"
                + " WHERE enrollment_id = "
                + enrollmentId2);
    // executeUpdate returns false on constraint violation
    // CRITICAL: Inactive VERIFIED enrollments also prevent new VERIFIED (active
    // flag doesn't affect
    // uniqueness)
    assertThat(updateSucceeded)
        .as("Should not be able to create second VERIFIED enrollment even if first is inactive")
        .isFalse();

    // Verify first enrollment is still VERIFIED (even though inactive)
    String finalStatus1 = databaseHelper.getEnrollmentStatus(enrollmentId1);
    assertThat(finalStatus1).isEqualTo("VERIFIED");
  }

  @Test
  @DisplayName("Error message directs user to recovery process")
  void testErrorMessageDirectsToRecoveryProcess() {
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

    // Verify error message contains recovery process endpoints
    assertThat(responseBody)
        .containsAnyOf("/api/v1/admin/auth/recover", "/auth/recover", "recovery process");
    assertThat(responseBody)
        .containsAnyOf("/api/v1/admin/enrollments/reset", "/enrollments/reset", "recovery process");
  }

  /**
   * SHA-256 hash of the value as lowercase hex (64 chars). Matches entity behavior for
   * enrollment_proof_token_hash. Uses standard Java only (ezkey-tests has no ezkey-core
   * dependency).
   */
  private static String sha256Hex(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashBytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(hashBytes.length * 2);
      for (byte b : hashBytes) {
        hex.append(String.format("%02x", b & 0xff));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
