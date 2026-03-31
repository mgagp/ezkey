/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: ReencryptionFullTriggerConcurrentActivityElectiveTest
 * Description: Full re-encryption trigger under concurrent enrollment row updates (optimistic-lock stress).
 */

package org.ezkey.tests.security.crypto;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.ezkey.tests.util.RestAssuredTestConfig.configureForAdminApi;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.ezkey.tests.security.AbstractSecurityTest;
import org.ezkey.tests.tags.TestTags;
import org.ezkey.tests.util.DatabaseHelper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Elective regression for {@code ObjectOptimisticLockingFailureException} during full re-encryption
 * when other work updates {@code ezkey_enrollment} rows (version bumps) concurrently.
 *
 * <p>Reproduces the failure mode described for long {@code triggerFullReencryption} runs under
 * operational churn: background threads issue SQL updates that increment {@code version} on rows
 * still targeted by sequential column batches.
 *
 * <p>Run: {@code mvn test -pl ezkey-tests -P elective-tests} against a healthy Docker stack.
 *
 * @since 2026
 */
@Tag(TestTags.ELECTIVE)
@Tag(TestTags.SLOW)
@Tag(TestTags.TIME_DEPENDENT)
@Tag(TestTags.ENCRYPTION)
@Tag(TestTags.DATABASE)
@DisplayName("Re-encryption full trigger under concurrent activity (elective)")
public class ReencryptionFullTriggerConcurrentActivityElectiveTest extends AbstractSecurityTest {

  private static final Logger log =
      LoggerFactory.getLogger(ReencryptionFullTriggerConcurrentActivityElectiveTest.class);

  private final DatabaseHelper databaseHelper = new DatabaseHelper();

  @Test
  @DisplayName("POST reencrypt/trigger succeeds while background tasks bump enrollment versions")
  public void fullReencryptionTrigger_underConcurrentEnrollmentVersionBumps_succeeds()
      throws Exception {
    String adminToken;
    try {
      adminToken = authTokenManager.getAdminToken();
    } catch (IllegalStateException e) {
      Assumptions.abort("Admin token not available. Skipping test.");
      return;
    }

    configureForAdminApi(dockerStackConfig);

    Long primaryBefore = getCurrentPrimaryKeyId();
    assertThat(primaryBefore).as("PRIMARY key must exist").isNotNull();

    // Create enrollments encrypted with the current PRIMARY before rotation (they will retain
    // old-key ciphertext until re-encrypted).
    for (int i = 0; i < 4; i++) {
      Integer integrationId = testDataFactory.createIntegration();
      testDataFactory.createEnrollment(integrationId);
    }

    Long pendingKeyId = getPendingKeyId();
    if (pendingKeyId == null) {
      Response rotateResponse =
          given()
              .contentType(ContentType.JSON)
              .header("Authorization", "Bearer " + adminToken)
              .when()
              .post("/encryption-keys/rotate")
              .then()
              .extract()
              .response();
      if (rotateResponse.getStatusCode() != 200) {
        Assumptions.abort("Key rotation not available: HTTP " + rotateResponse.getStatusCode());
        return;
      }
      pendingKeyId = rotateResponse.jsonPath().getLong("newPrimaryKeyId");
    }

    log.info("Waiting for PENDING key {} to be promoted...", pendingKeyId);
    int waitedSeconds = 0;
    while ("PENDING".equals(getKeyStatus(pendingKeyId)) && waitedSeconds < 60) {
      Thread.sleep(5000);
      waitedSeconds += 5;
    }
    assertThat(getKeyStatus(pendingKeyId))
        .as("PENDING key should become PRIMARY")
        .isEqualTo("PRIMARY");

    Long oldKeyId = primaryBefore;

    AtomicBoolean running = new AtomicBoolean(true);
    ExecutorService pool = Executors.newFixedThreadPool(4);
    List<Future<?>> futures = new ArrayList<>();
    Random rng = new Random();

    for (int t = 0; t < 4; t++) {
      futures.add(
          pool.submit(
              () -> {
                while (running.get() && !Thread.currentThread().isInterrupted()) {
                  List<Integer> ids = listEnrollmentIdsWithOldKeyPrefix(oldKeyId);
                  if (ids.isEmpty()) {
                    sleepQuiet(100);
                    continue;
                  }
                  int id = ids.get(rng.nextInt(ids.size()));
                  String sql =
                      "UPDATE ezkey_enrollment SET last_used_at = NOW(), version = version + 1"
                          + " WHERE enrollment_id = "
                          + id;
                  databaseHelper.executeUpdate(sql);
                  sleepQuiet(40 + rng.nextInt(80));
                }
              }));
    }

    sleepQuiet(1500);

    Response triggerResponse =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + adminToken)
            .when()
            .post("/encryption-keys/reencrypt/trigger")
            .then()
            .extract()
            .response();

    running.set(false);
    pool.shutdown();
    assertThat(pool.awaitTermination(120, TimeUnit.SECONDS))
        .as("Background bump tasks should finish")
        .isTrue();

    for (Future<?> f : futures) {
      try {
        f.get(5, TimeUnit.SECONDS);
      } catch (Exception e) {
        log.warn("Background task ended with: {}", e.getMessage());
      }
    }

    assertThat(triggerResponse.getStatusCode())
        .as("Full re-encryption trigger should return HTTP 200")
        .isEqualTo(200);

    int batchesFailed = triggerResponse.jsonPath().getInt("batchesFailed");
    assertThat(batchesFailed)
        .as("No re-encryption batches should fail (check admin-api logs if non-zero)")
        .isZero();
  }

  private Long getCurrentPrimaryKeyId() {
    String result =
        databaseHelper.executeQuerySingleValue(
            "SELECT key_id FROM ezkey_encryption_key WHERE key_status = 'PRIMARY' LIMIT 1");
    return result != null ? Long.parseLong(result.trim()) : null;
  }

  private Long getPendingKeyId() {
    String result =
        databaseHelper.executeQuerySingleValue(
            "SELECT key_id FROM ezkey_encryption_key WHERE key_status = 'PENDING' LIMIT 1");
    return result != null ? Long.parseLong(result.trim()) : null;
  }

  private String getKeyStatus(Long keyId) {
    String result =
        databaseHelper.executeQuerySingleValue(
            "SELECT key_status FROM ezkey_encryption_key WHERE key_id = " + keyId);
    return result != null ? result.trim() : null;
  }

  private List<Integer> listEnrollmentIdsWithOldKeyPrefix(long oldKeyId) {
    String prefix = "ENC:" + oldKeyId + ":%";
    String sql =
        "SELECT enrollment_id::text FROM ezkey_enrollment WHERE enrollment_proof_token LIKE '"
            + prefix.replace("'", "''")
            + "' OR integration_private_key LIKE '"
            + prefix.replace("'", "''")
            + "' LIMIT 200";
    List<String> rows = databaseHelper.executeQuery(sql);
    List<Integer> ids = new ArrayList<>();
    for (String row : rows) {
      if (row == null || row.isBlank()) {
        continue;
      }
      try {
        ids.add(Integer.parseInt(row.trim()));
      } catch (NumberFormatException ignored) {
        // skip
      }
    }
    return ids;
  }

  private static void sleepQuiet(int ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
