/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: KeyRotationSyncWindowTest
 * Description: Tests for key rotation synchronization window behavior
 */

package org.ezkey.tests.security.crypto;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.ezkey.tests.util.RestAssuredTestConfig.configureForAdminApi;
import static org.ezkey.tests.util.RestAssuredTestConfig.configureForAuthApi;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.HashMap;
import java.util.Map;
import org.ezkey.tests.security.AbstractSecurityTest;
import org.ezkey.tests.tags.TestTags;
import org.ezkey.tests.util.CryptoApiClient.EcP256KeyPair;
import org.ezkey.tests.util.DatabaseHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tests for key rotation synchronization window behavior.
 *
 * <p>These tests validate that:
 *
 * <ul>
 *   <li>During the sync window, the OLD primary key is still used for encryption
 *   <li>PENDING keys are not used for encryption until promoted
 *   <li>After promotion, the NEW primary key is used for encryption
 *   <li>Data encrypted with any key (old or new) can be decrypted by both APIs
 * </ul>
 *
 * <p><b>Key Rotation Workflow:</b>
 *
 * <pre>
 * T=0:   Admin API creates new key with PENDING status
 *        - New key added to keyset but NOT set as primary in Tink
 *        - Old key remains PRIMARY for encryption
 *        - effective_at = T + sync_window_seconds
 *
 * T=0 to T=sync_window:
 *        - All instances sync the keyset (have new key for decryption)
 *        - Old key still used for encryption (consistent across all instances)
 *
 * T=sync_window:
 *        - Promotion job promotes PENDING key to PRIMARY
 *        - New key set as primary in Tink keyset
 *        - All instances now use new key for encryption
 * </pre>
 *
 * <p><b>Note:</b> These tests depend on the sync window configuration (default 10s for
 * docker-test). The tests use database queries to verify which key was used for encryption.
 *
 * <p><b>Admin bearer token:</b> Long-running scenarios must not cache the admin JWT in a local
 * variable across waits or factory calls. {@link org.ezkey.tests.util.AuthTokenManager} may replace
 * the in-memory token when validation fails and bootstrap runs again; {@link
 * org.ezkey.tests.util.TestDataFactory} always calls {@code getAdminToken()}. Using a stale bearer
 * string after that produces <strong>401 Unauthorized</strong> and is unrelated to Tink keyset sync
 * across instances.
 *
 * <p><b>Elective:</b> Tagged as elective because key rotation code is stable and rarely modified.
 * Run with {@code mvn test -pl ezkey-tests -P elective-tests} for periodic spot-checks.
 *
 * @since 2025
 */
@Tag(TestTags.ELECTIVE)
@Tag(TestTags.SLOW)
@Tag(TestTags.TIME_DEPENDENT)
@Tag(TestTags.ENCRYPTION)
@Tag(TestTags.CROSS_INSTANCE)
@DisplayName("Key Rotation Sync Window Tests")
public class KeyRotationSyncWindowTest extends AbstractSecurityTest {

  private static final Logger log = LoggerFactory.getLogger(KeyRotationSyncWindowTest.class);

  private final DatabaseHelper databaseHelper = new DatabaseHelper();

  @Test
  @DisplayName("During sync window, old key should still be used for encryption")
  public void testOldKeyUsedDuringSyncWindow() throws Exception {
    // Skip if admin token not available
    String adminToken;
    try {
      adminToken = authTokenManager.getAdminToken();
    } catch (IllegalStateException e) {
      org.junit.jupiter.api.Assumptions.assumeTrue(
          false, "Admin token not available. Skipping test.");
      return;
    }

    // Step 1: Get current primary key ID from database
    Long initialPrimaryKeyId = getCurrentPrimaryKeyId();
    assertThat(initialPrimaryKeyId).as("Should have a PRIMARY key before test").isNotNull();
    log.info("Initial PRIMARY key ID: {}", initialPrimaryKeyId);

    // Step 2: Trigger key rotation (creates PENDING key)
    configureForAdminApi(dockerStackConfig);
    Response rotateResponse =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + adminToken)
            .when()
            .post("/encryption-keys/rotate")
            .then()
            .extract()
            .response();

    // Check if rotation was successful or if there's already a pending key (409 + ProblemDetail)
    if (rotateResponse.getStatusCode() == 409) {
      String detail = rotateResponse.jsonPath().getString("detail");
      if (detail != null && detail.contains("PENDING key already exists")) {
        log.warn("PENDING key already exists. Test may need clean state.");
        org.junit.jupiter.api.Assumptions.assumeTrue(
            false, "PENDING key already exists. Run with clean state.");
        return;
      }
    }

    assertThat(rotateResponse.getStatusCode()).as("Key rotation should succeed").isEqualTo(200);

    Long newKeyId = rotateResponse.jsonPath().getLong("newPrimaryKeyId");
    log.info("New PENDING key ID: {}", newKeyId);

    // Step 3: Verify new key is PENDING in database
    String newKeyStatus = getKeyStatus(newKeyId);
    assertThat(newKeyStatus)
        .as("New key should be PENDING during sync window")
        .isEqualTo("PENDING");

    // Step 4: Verify old key is still PRIMARY
    String oldKeyStatus = getKeyStatus(initialPrimaryKeyId);
    assertThat(oldKeyStatus)
        .as("Old key should still be PRIMARY during sync window")
        .isEqualTo("PRIMARY");

    // Step 5: Verify which key is used for encryption (Crypto API shares keyset with Admin API)
    // Use Crypto API encrypt endpoint - enrollment_proof_token may be plaintext if encryption
    // fails in entity listener; Crypto API encrypt reliably returns keyId for ENC: format.
    Long keyUsedForEncryption = cryptoApiClient.encryptAndGetKeyId("test-verify-old-key");

    log.info(
        "Key used for encryption: {} (expected old primary: {})",
        keyUsedForEncryption,
        initialPrimaryKeyId);

    // CRITICAL ASSERTION: During sync window, OLD key should be used
    assertThat(keyUsedForEncryption)
        .as("During sync window, OLD PRIMARY key should be used for encryption, not PENDING key")
        .isEqualTo(initialPrimaryKeyId);

    log.info("✅ Verified: Old PRIMARY key is used for encryption during sync window");
  }

  @Test
  @DisplayName("After promotion, new key should be used for encryption")
  public void testNewKeyUsedAfterPromotion() throws Exception {
    // Skip if admin token not available
    String adminToken;
    try {
      adminToken = authTokenManager.getAdminToken();
    } catch (IllegalStateException e) {
      org.junit.jupiter.api.Assumptions.assumeTrue(
          false, "Admin token not available. Skipping test.");
      return;
    }

    // Step 1: Get current primary key ID
    Long initialPrimaryKeyId = getCurrentPrimaryKeyId();
    assertThat(initialPrimaryKeyId).as("Should have a PRIMARY key before test").isNotNull();
    log.info("Initial PRIMARY key ID: {}", initialPrimaryKeyId);

    // Step 2: Check if there's already a PENDING key ready for promotion
    Long pendingKeyId = getPendingKeyId();

    if (pendingKeyId == null) {
      // No pending key - trigger rotation first
      configureForAdminApi(dockerStackConfig);
      Response rotateResponse =
          given()
              .contentType(ContentType.JSON)
              .header("Authorization", "Bearer " + adminToken)
              .when()
              .post("/encryption-keys/rotate")
              .then()
              .statusCode(200)
              .extract()
              .response();

      pendingKeyId = rotateResponse.jsonPath().getLong("newPrimaryKeyId");
      log.info("Created new PENDING key ID: {}", pendingKeyId);
    } else {
      log.info("Found existing PENDING key ID: {}", pendingKeyId);
    }

    // Step 3: Wait for sync window to expire and promotion to occur
    // Sync window is 10 seconds in docker-test config
    log.info("Waiting for sync window to expire and promotion job to run...");
    int maxWaitSeconds = 45; // Sync window (10s) + promotion interval (5s) + buffer
    int waitedSeconds = 0;
    String keyStatus;

    do {
      Thread.sleep(5000);
      waitedSeconds += 5;
      keyStatus = getKeyStatus(pendingKeyId);
      log.info(
          "After {}s: PENDING key {} status is now: {}", waitedSeconds, pendingKeyId, keyStatus);
    } while ("PENDING".equals(keyStatus) && waitedSeconds < maxWaitSeconds);

    assertThat(keyStatus)
        .as("Key should be promoted to PRIMARY after sync window")
        .isEqualTo("PRIMARY");

    // Step 4: Verify old key is now ENABLED
    String oldKeyStatus = getKeyStatus(initialPrimaryKeyId);
    assertThat(oldKeyStatus)
        .as("Old PRIMARY key should be demoted to ENABLED after promotion")
        .isEqualTo("ENABLED");

    // Step 5: Restart Crypto API so it loads the updated keyset from file (AGENTS.md: restart
    // required after key rotation). Then verify new key is used for encryption.
    restartCryptoApiAndWaitHealthy();

    Long keyUsedForEncryption = cryptoApiClient.encryptAndGetKeyId("test-verify-new-key");

    log.info(
        "Key used for encryption: {} (expected new primary: {})",
        keyUsedForEncryption,
        pendingKeyId);

    // CRITICAL ASSERTION: After promotion, NEW key should be used
    assertThat(keyUsedForEncryption)
        .as("After promotion, NEW PRIMARY key should be used for encryption")
        .isEqualTo(pendingKeyId);

    log.info("✅ Verified: New PRIMARY key is used for encryption after promotion");
  }

  @Test
  @DisplayName("Data encrypted before and after rotation can be decrypted")
  public void testDataDecryptableAcrossKeyRotation() throws Exception {
    // Skip if admin token not available
    try {
      authTokenManager.getAdminToken();
    } catch (IllegalStateException e) {
      org.junit.jupiter.api.Assumptions.assumeTrue(
          false, "Admin token not available. Skipping test.");
      return;
    }

    configureForAdminApi(dockerStackConfig);

    // Step 1: Create enrollment BEFORE rotation
    Integer integrationId1 = testDataFactory.createIntegration();
    Integer enrollmentIdBefore = testDataFactory.createEnrollment(integrationId1);
    log.info("Created enrollment {} BEFORE rotation", enrollmentIdBefore);

    // Step 2: Get enrollment details (this decrypts the proof token)
    Response responseBefore =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + authTokenManager.getAdminToken())
            .when()
            .get("/enrollments/" + enrollmentIdBefore)
            .then()
            .statusCode(200)
            .extract()
            .response();

    String proofTokenBefore = responseBefore.jsonPath().getString("enrollmentProofToken");
    assertThat(proofTokenBefore)
        .as("Proof token should be decrypted successfully before rotation")
        .isNotNull()
        .isNotEmpty()
        .doesNotStartWith("ENC:"); // Should be decrypted, not encrypted format

    // Step 3: Trigger rotation and wait for promotion (if needed)
    Long pendingKeyId = getPendingKeyId();
    if (pendingKeyId == null) {
      Response rotateResponse =
          given()
              .contentType(ContentType.JSON)
              .header("Authorization", "Bearer " + authTokenManager.getAdminToken())
              .when()
              .post("/encryption-keys/rotate")
              .then()
              .statusCode(200)
              .extract()
              .response();
      pendingKeyId = rotateResponse.jsonPath().getLong("newPrimaryKeyId");
    }

    // Wait for promotion
    int waitedSeconds = 0;
    while ("PENDING".equals(getKeyStatus(pendingKeyId)) && waitedSeconds < 45) {
      Thread.sleep(5000);
      waitedSeconds += 5;
    }

    // Step 4: Create enrollment AFTER rotation
    Integer integrationId2 = testDataFactory.createIntegration();
    Integer enrollmentIdAfter = testDataFactory.createEnrollment(integrationId2);
    log.info("Created enrollment {} AFTER rotation", enrollmentIdAfter);

    // Step 5: Verify BOTH enrollments can be decrypted (always use current token from manager —
    // TestDataFactory may have refreshed it; a stale local bearer causes 401, not crypto mismatch)
    Response responseBeforeAgain =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + authTokenManager.getAdminToken())
            .when()
            .get("/enrollments/" + enrollmentIdBefore)
            .then()
            .statusCode(200)
            .extract()
            .response();

    Response responseAfter =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + authTokenManager.getAdminToken())
            .when()
            .get("/enrollments/" + enrollmentIdAfter)
            .then()
            .statusCode(200)
            .extract()
            .response();

    String proofTokenBeforeAgain = responseBeforeAgain.jsonPath().getString("enrollmentProofToken");
    String proofTokenAfter = responseAfter.jsonPath().getString("enrollmentProofToken");

    assertThat(proofTokenBeforeAgain)
        .as("Data encrypted with OLD key should still be decryptable")
        .isNotNull()
        .isNotEmpty()
        .doesNotStartWith("ENC:");

    assertThat(proofTokenAfter)
        .as("Data encrypted with NEW key should be decryptable")
        .isNotNull()
        .isNotEmpty()
        .doesNotStartWith("ENC:");

    log.info("✅ Verified: Data encrypted before AND after rotation can be decrypted");
  }

  @Test
  @DisplayName("Auth API can decrypt data encrypted by Admin API after rotation")
  public void testCrossApiDecryptionAfterRotation() throws Exception {
    // Skip if admin token not available
    String adminToken;
    try {
      adminToken = authTokenManager.getAdminToken();
    } catch (IllegalStateException e) {
      org.junit.jupiter.api.Assumptions.assumeTrue(
          false, "Admin token not available. Skipping test.");
      return;
    }

    // Step 1: Create enrollment via Admin API
    configureForAdminApi(dockerStackConfig);
    Integer integrationId = testDataFactory.createIntegration();
    Integer enrollmentId = testDataFactory.createEnrollment(integrationId);

    // Get proof token from Admin API (decrypts internally)
    Response adminResponse =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + adminToken)
            .when()
            .get("/enrollments/" + enrollmentId)
            .then()
            .statusCode(200)
            .extract()
            .response();

    String proofToken = adminResponse.jsonPath().getString("enrollmentProofToken");
    assertThat(proofToken).isNotNull().isNotEmpty();

    // Step 2: Bind enrollment via Auth API (this uses the proof token)
    configureForAuthApi(dockerStackConfig);

    EcP256KeyPair deviceKeyPair = cryptoApiClient.generateKeyPair();
    configureForAuthApi(dockerStackConfig);

    Map<String, Object> bindRequest = new HashMap<>();
    bindRequest.put("enrollmentId", enrollmentId);
    bindRequest.put("enrollmentProofToken", proofToken);

    Response bindResponse =
        given()
            .contentType(ContentType.JSON)
            .body(bindRequest)
            .when()
            .post("/enrollments/bind")
            .then()
            .extract()
            .response();

    // This validates that Auth API can decrypt the enrollment data
    assertThat(bindResponse.getStatusCode())
        .as("Auth API should decrypt enrollment data successfully")
        .isEqualTo(200);

    log.info(
        "✅ Verified: Auth API can decrypt data encrypted by Admin API "
            + "(cross-instance decryption works)");
  }

  // ==================== Helper Methods ====================

  /**
   * Gets the current PRIMARY key ID from database.
   *
   * @return PRIMARY key ID, or null if not found
   */
  private Long getCurrentPrimaryKeyId() {
    String result =
        databaseHelper.executeQuerySingleValue(
            "SELECT key_id FROM ezkey_encryption_key WHERE key_status = 'PRIMARY' LIMIT 1");
    return result != null ? Long.parseLong(result.trim()) : null;
  }

  /**
   * Gets any PENDING key ID from database.
   *
   * @return PENDING key ID, or null if not found
   */
  private Long getPendingKeyId() {
    String result =
        databaseHelper.executeQuerySingleValue(
            "SELECT key_id FROM ezkey_encryption_key WHERE key_status = 'PENDING' LIMIT 1");
    return result != null ? Long.parseLong(result.trim()) : null;
  }

  /**
   * Gets the status of a specific key.
   *
   * @param keyId key ID to check
   * @return key status (PRIMARY, PENDING, ENABLED, DISABLED), or null if not found
   */
  private String getKeyStatus(Long keyId) {
    String result =
        databaseHelper.executeQuerySingleValue(
            "SELECT key_status FROM ezkey_encryption_key WHERE key_id = " + keyId);
    return result != null ? result.trim() : null;
  }

  /**
   * Restarts the Crypto API container so it loads the updated keyset from file.
   *
   * <p>After key promotion, the keyset file is updated by Admin API. Crypto API loads keyset at
   * startup only, so a restart is required to use the new primary key. See ezkey-crypto-api
   * AGENTS.md.
   */
  private void restartCryptoApiAndWaitHealthy() {
    String containerName =
        containerExists("ezkey-crypto-api-ha") ? "ezkey-crypto-api-ha" : "ezkey-crypto-api";
    log.info("Restarting Crypto API container {} to load updated keyset...", containerName);
    try {
      ProcessBuilder pb = new ProcessBuilder("docker", "restart", containerName);
      Process p = pb.start();
      int exitCode = p.waitFor();
      if (exitCode != 0) {
        throw new IllegalStateException(
            "docker restart " + containerName + " failed with code " + exitCode);
      }
      // Poll health endpoint (start_period 40s in docker-compose)
      String healthUrl = dockerStackConfig.getCryptoApiUrl() + "/actuator/health";
      for (int i = 0; i < 25; i++) {
        Thread.sleep(2000);
        try {
          java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
          java.net.http.HttpRequest req =
              java.net.http.HttpRequest.newBuilder()
                  .uri(java.net.URI.create(healthUrl))
                  .GET()
                  .build();
          var response = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
          if (response.statusCode() == 200) {
            log.info("Crypto API healthy after {}s", (i + 1) * 2);
            return;
          }
        } catch (Exception ignored) {
          // Retry
        }
      }
      throw new IllegalStateException("Crypto API did not become healthy within 50s after restart");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Interrupted while restarting Crypto API", e);
    } catch (Exception e) {
      throw new RuntimeException("Failed to restart Crypto API: " + e.getMessage(), e);
    }
  }

  private boolean containerExists(String name) {
    try {
      ProcessBuilder pb =
          new ProcessBuilder("docker", "inspect", "--format", "{{.State.Running}}", name);
      Process p = pb.start();
      StringBuilder out = new StringBuilder();
      try (var reader =
          new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
        String line;
        while ((line = reader.readLine()) != null) out.append(line);
      }
      return p.waitFor() == 0 && "true".equals(out.toString().trim());
    } catch (Exception e) {
      return false;
    }
  }
}
