/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test Class: AuditIntegrityElectiveTest
 * Description: Elective on-demand spot checks for audit log HMAC integrity and chain checkpoint
 *     linkage. Run explicitly after a period of real system activity to verify the full
 *     tamper-evidence pipeline on live data.
 */

package org.ezkey.tests.security.audit;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import io.restassured.response.Response;
import org.ezkey.tests.security.AbstractSecurityTest;
import org.ezkey.tests.tags.TestTags;
import org.ezkey.tests.util.DatabaseHelper;
import org.ezkey.tests.util.RestAssuredTestConfig;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Elective on-demand spot checks for audit log integrity on a live, data-bearing system.
 *
 * <p>These tests are <b>never run automatically</b>. They are designed to be invoked explicitly by
 * a developer after the Docker stack has been running long enough to accumulate real audit logs and
 * chain checkpoints -- typically after 15+ minutes of activity, or after a full functional test
 * suite run.
 *
 * <p><b>What is verified:</b>
 *
 * <ol>
 *   <li><b>Per-entry HMAC integrity (full dataset)</b> -- calls {@code GET
 *       /api/v1/audit-logs/integrity-check} with a date range covering all data; every signed entry
 *       in the database is recomputed and compared against its stored HMAC. Any mismatch means the
 *       entry was tampered with after signing.
 *   <li><b>Chain checkpoint linkage</b> -- calls {@code GET /api/v1/audit-logs/chain-integrity}
 *       with a date range covering all checkpoints; every 5-minute checkpoint window is recomputed
 *       and its chain link to the previous checkpoint is validated. Detects entry insertion,
 *       deletion, or reordering between checkpoints.
 *   <li><b>Single-entry targeted check</b> -- picks the most-recently signed entry from the DB and
 *       verifies it via {@code GET /api/v1/audit-logs/{id}/integrity-check}. Demonstrates the
 *       forensic spot-check capability.
 * </ol>
 *
 * <p><b>Invocation:</b>
 *
 * <pre>{@code
 * mvn test -pl ezkey-tests -P elective-tests
 * }</pre>
 *
 * <p><b>Grace on empty data:</b> if the system has no signed entries or no chain checkpoints yet,
 * the relevant test is skipped with an informational message rather than failing. This makes it
 * safe to run on a freshly started stack.
 *
 * <p><b>Database cross-check:</b> each API assertion is complemented by a direct SQL count to
 * confirm the API totals match what is actually in the database, providing an additional layer of
 * confidence.
 *
 * @since 2026
 */
@Tag(TestTags.ELECTIVE)
@Tag(TestTags.AUDIT_INTEGRITY)
@DisplayName("Audit Integrity Elective Spot Checks")
public class AuditIntegrityElectiveTest extends AbstractSecurityTest {

  private static final Logger logger = LoggerFactory.getLogger(AuditIntegrityElectiveTest.class);

  private DatabaseHelper databaseHelper;
  private String adminToken;

  @BeforeEach
  @Override
  public void setUp() {
    super.setUp();
    RestAssuredTestConfig.configureForAdminApi(dockerStackConfig);
    databaseHelper = new DatabaseHelper();
    adminToken = authTokenManager.getAdminToken();
  }

  // -----------------------------------------------------------------------
  // Test 1 – Per-entry HMAC integrity (full dataset)
  // -----------------------------------------------------------------------

  @Test
  @DisplayName("All signed audit log entries must have a valid HMAC (full dataset)")
  void allSignedEntries_mustHaveValidHmac() {
    logger.info("=== Elective: Per-Entry HMAC Integrity Check ===");

    // DB cross-check: how many signed entries exist?
    long dbSignedCount =
        queryLong("SELECT COUNT(*) FROM ezkey_audit_log WHERE entry_hmac IS NOT NULL");
    long dbUnsignedCount =
        queryLong("SELECT COUNT(*) FROM ezkey_audit_log WHERE entry_hmac IS NULL");
    long dbTotal = dbSignedCount + dbUnsignedCount;

    logger.info(
        "Database snapshot: total={}, signed={}, unsigned={}",
        dbTotal,
        dbSignedCount,
        dbUnsignedCount);

    // Call the range integrity-check endpoint with a wide date range (covers full dataset)
    String from = "2000-01-01T00:00:00Z";
    String to = "2030-12-31T23:59:59Z";
    Response response =
        given()
            .header("Authorization", "Bearer " + adminToken)
            .queryParam("from", from)
            .queryParam("to", to)
            .when()
            .get("/audit-logs/integrity-check")
            .then()
            .statusCode(200)
            .extract()
            .response();

    String status = response.jsonPath().getString("status");
    long total = response.jsonPath().getLong("totalEntries");
    long valid = response.jsonPath().getLong("validEntries");
    long invalid = response.jsonPath().getLong("invalidEntries");
    long unsigned = response.jsonPath().getLong("unsignedEntries");
    boolean intact = response.jsonPath().getBoolean("intact");

    logger.info(
        "API response: status={}, total={}, valid={}, invalid={}, unsigned={}, intact={}",
        status,
        total,
        valid,
        invalid,
        unsigned,
        intact);

    // If HMAC signing was never activated, skip gracefully
    Assumptions.assumeTrue(
        !"HMAC signing is not active".equals(status),
        "HMAC signing is not active in this environment -- skipping elective test");

    // If no entries have been created yet, skip gracefully
    Assumptions.assumeTrue(
        total > 0, "No audit log entries found yet -- run some tests or operations first");

    // DB vs API consistency
    assertThat(total).as("API totalEntries must match DB row count").isEqualTo(dbTotal);

    // Core assertion: no HMAC mismatches
    assertThat(invalid)
        .as(
            "INTEGRITY VIOLATION: %d entr%s failed HMAC verification -- audit trail may be"
                + " compromised!",
            invalid, invalid == 1 ? "y" : "ies")
        .isZero();

    assertThat(intact).as("API must report intact=true when no violations are found").isTrue();

    assertThat(status).as("Status must be OK when all entries are intact").isEqualTo("OK");

    logger.info(
        "✅ PASS: {} signed entr{} verified intact, {} unsigned (pre-signing)",
        valid,
        valid == 1 ? "y" : "ies",
        unsigned);
  }

  // -----------------------------------------------------------------------
  // Test 2 – Chain checkpoint linkage (full dataset)
  // -----------------------------------------------------------------------

  @Test
  @DisplayName("All chain checkpoints must be linked and their entry digests must match")
  void allChainCheckpoints_mustBeIntact() {
    logger.info("=== Elective: Chain Checkpoint Integrity Check ===");

    // DB cross-check: how many checkpoints exist?
    long dbCheckpointCount = queryLong("SELECT COUNT(*) FROM ezkey_audit_chain_checkpoint");

    logger.info("Database snapshot: checkpoints={}", dbCheckpointCount);

    // Call the chain-integrity endpoint with a wide date range (covers full chain)
    String from = "2000-01-01T00:00:00Z";
    String to = "2030-12-31T23:59:59Z";
    Response response =
        given()
            .header("Authorization", "Bearer " + adminToken)
            .queryParam("from", from)
            .queryParam("to", to)
            .when()
            .get("/audit-logs/chain-integrity")
            .then()
            .statusCode(200)
            .extract()
            .response();

    String status = response.jsonPath().getString("status");
    int total = response.jsonPath().getInt("totalCheckpoints");
    int valid = response.jsonPath().getInt("validCheckpoints");
    int invalid = response.jsonPath().getInt("invalidCheckpoints");
    boolean intact = response.jsonPath().getBoolean("intact");

    logger.info(
        "API response: status={}, total={}, valid={}, invalid={}, intact={}",
        status,
        total,
        valid,
        invalid,
        intact);

    // If HMAC signing is not active, skip gracefully
    Assumptions.assumeTrue(
        !"HMAC signing is not active".equals(status),
        "HMAC signing is not active in this environment -- skipping elective test");

    // If the chain scheduler hasn't run yet (first 5 minutes after startup), skip gracefully
    Assumptions.assumeTrue(
        total > 0,
        "No chain checkpoints yet -- the scheduler runs every 5 minutes; wait a bit and retry");

    // DB vs API consistency
    assertThat((long) total)
        .as("API totalCheckpoints must match DB row count")
        .isEqualTo(dbCheckpointCount);

    // Core assertion: no checkpoint violations
    assertThat(invalid)
        .as(
            "CHAIN INTEGRITY VIOLATION: %d checkpoint%s failed verification!",
            invalid, invalid == 1 ? "" : "s")
        .isZero();

    assertThat(intact).as("API must report intact=true when all checkpoints are valid").isTrue();

    assertThat(status).as("Status must be OK").isEqualTo("OK");

    // Log the age of the latest checkpoint for observability
    String latestWindowStart =
        databaseHelper.executeQuerySingleValue(
            "SELECT window_start FROM ezkey_audit_chain_checkpoint ORDER BY window_start DESC LIMIT"
                + " 1");
    logger.info(
        "✅ PASS: {} checkpoint{} verified intact. Latest window: {}",
        total,
        total == 1 ? "" : "s",
        latestWindowStart);
  }

  // -----------------------------------------------------------------------
  // Test 3 – Single-entry targeted forensic check
  // -----------------------------------------------------------------------

  @Test
  @DisplayName("Most recently signed audit log entry must verify as intact by ID")
  void mostRecentSignedEntry_mustVerifyByIdAsIntact() {
    logger.info("=== Elective: Single-Entry Targeted Integrity Check ===");

    // Pick the most recently signed entry from the DB
    String idStr =
        databaseHelper.executeQuerySingleValue(
            "SELECT audit_log_id FROM ezkey_audit_log WHERE entry_hmac IS NOT NULL "
                + "ORDER BY audit_log_id DESC LIMIT 1");

    Assumptions.assumeTrue(
        idStr != null, "No signed audit log entries found -- run some operations first");

    long entryId = Long.parseLong(idStr.trim());
    logger.info("Verifying single entry: audit_log_id={}", entryId);

    // Call GET /audit-logs/{id}/integrity-check
    Response response =
        given()
            .header("Authorization", "Bearer " + adminToken)
            .when()
            .get("/audit-logs/" + entryId + "/integrity-check")
            .then()
            .statusCode(200)
            .extract()
            .response();

    String status = response.jsonPath().getString("status");
    long total = response.jsonPath().getLong("totalEntries");
    long valid = response.jsonPath().getLong("validEntries");
    long invalid = response.jsonPath().getLong("invalidEntries");
    boolean intact = response.jsonPath().getBoolean("intact");

    logger.info(
        "API response for id={}: status={}, total={}, valid={}, invalid={}, intact={}",
        entryId,
        status,
        total,
        valid,
        invalid,
        intact);

    assertThat(total).as("totalEntries must be 1 for a single-entry check").isEqualTo(1);
    assertThat(invalid).as("INTEGRITY VIOLATION for entry id=%d!", entryId).isZero();
    assertThat(intact).as("intact must be true for an unmodified entry").isTrue();
    assertThat(status).as("status must be OK").isEqualTo("OK");

    logger.info("✅ PASS: Entry audit_log_id={} is intact", entryId);
  }

  // -----------------------------------------------------------------------
  // Helper
  // -----------------------------------------------------------------------

  private long queryLong(String sql) {
    String result = databaseHelper.executeQuerySingleValue(sql);
    if (result == null || result.isBlank()) {
      return 0L;
    }
    return Long.parseLong(result.trim());
  }
}
