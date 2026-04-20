/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test Class: AuditLifecycleArchiveEligibilityElectiveTest
 * Description: Elective spot check for the audit archive lifecycle eligibility contract on a live,
 *     data-bearing stack.
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
 * Elective spot check for the archive eligibility lifecycle contract.
 *
 * <p>This test is intended for a running stack that has accumulated enough history for archive
 * lifecycle progression to become meaningful. It is read-only and safe to run manually, but it is
 * excluded from standard builds because most fresh stacks will not yet have any {@code SEALED}
 * archive tranche.
 *
 * <p><b>What is verified when data exists:</b>
 *
 * <ul>
 *   <li>The endpoint returns 200 for a Global Admin token.
 *   <li>{@code sealedCheckpointCount} matches the database count of {@code SEALED + ARCHIVE_SEAL}
 *       checkpoints.
 *   <li>{@code checkpointIdFrom} and {@code checkpointIdTo} match the oldest and newest sealed
 *       tranche boundaries.
 *   <li>{@code confirmationRequired} remains aligned with the product contract: it is true only
 *       when external archival is enabled and at least one sealed tranche exists.
 * </ul>
 *
 * <p><b>Grace on insufficient data:</b> if no sealed archive checkpoints exist yet, the test is
 * skipped with an informational message. That is intentional: this is an elective operational spot
 * check, not a default functional test.
 *
 * @since 2026
 */
@Tag(TestTags.ELECTIVE)
@Tag(TestTags.AUDIT_INTEGRITY)
@DisplayName("Audit Lifecycle Archive Eligibility Elective Spot Check")
public class AuditLifecycleArchiveEligibilityElectiveTest extends AbstractSecurityTest {

  private static final Logger logger =
      LoggerFactory.getLogger(AuditLifecycleArchiveEligibilityElectiveTest.class);

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

  @Test
  @DisplayName("Archive eligibility matches the current sealed checkpoint tranche")
  void archiveEligibility_matchesCurrentSealedCheckpointTranche() {
    logger.info("=== Elective: Audit Archive Lifecycle Eligibility ===");

    long sealedCount =
        queryLong(
            "SELECT COUNT(*) FROM ezkey_audit_chain_checkpoint "
                + "WHERE lifecycle_state = 'SEALED' AND checkpoint_type = 'ARCHIVE_SEAL'");

    Assumptions.assumeTrue(
        sealedCount > 0,
        "No sealed archive checkpoints yet -- run the system longer or after lifecycle progression"
            + " has produced a sealed tranche");

    long oldestCheckpointId =
        queryLong(
            "SELECT checkpoint_id FROM ezkey_audit_chain_checkpoint "
                + "WHERE lifecycle_state = 'SEALED' AND checkpoint_type = 'ARCHIVE_SEAL' "
                + "ORDER BY window_start ASC, checkpoint_id ASC LIMIT 1");
    long newestCheckpointId =
        queryLong(
            "SELECT checkpoint_id FROM ezkey_audit_chain_checkpoint "
                + "WHERE lifecycle_state = 'SEALED' AND checkpoint_type = 'ARCHIVE_SEAL' "
                + "ORDER BY window_start DESC, checkpoint_id DESC LIMIT 1");

    Response response =
        given()
            .header("Authorization", "Bearer " + adminToken)
            .when()
            .get("/audit-logs/lifecycle/archive-eligibility")
            .then()
            .statusCode(200)
            .extract()
            .response();

    boolean externalArchivalEnabled = response.jsonPath().getBoolean("externalArchivalEnabled");
    boolean confirmationRequired = response.jsonPath().getBoolean("confirmationRequired");
    int responseSealedCount = response.jsonPath().getInt("sealedCheckpointCount");
    long responseCheckpointIdFrom = response.jsonPath().getLong("checkpointIdFrom");
    long responseCheckpointIdTo = response.jsonPath().getLong("checkpointIdTo");
    String oldestSealedWindowStart = response.jsonPath().getString("oldestSealedWindowStart");
    String newestSealedWindowEnd = response.jsonPath().getString("newestSealedWindowEnd");

    logger.info(
        "Archive eligibility response: externalArchivalEnabled={}, confirmationRequired={},"
            + " sealedCheckpointCount={}, checkpointIdFrom={}, checkpointIdTo={}",
        externalArchivalEnabled,
        confirmationRequired,
        responseSealedCount,
        responseCheckpointIdFrom,
        responseCheckpointIdTo);

    assertThat(responseSealedCount)
        .as("sealedCheckpointCount must match the number of sealed archive checkpoints in DB")
        .isEqualTo(Math.toIntExact(sealedCount));
    assertThat(responseCheckpointIdFrom)
        .as("checkpointIdFrom must expose the oldest sealed checkpoint")
        .isEqualTo(oldestCheckpointId);
    assertThat(responseCheckpointIdTo)
        .as("checkpointIdTo must expose the newest sealed checkpoint")
        .isEqualTo(newestCheckpointId);
    assertThat(oldestSealedWindowStart)
        .as("oldestSealedWindowStart must be present when a sealed tranche exists")
        .isNotBlank();
    assertThat(newestSealedWindowEnd)
        .as("newestSealedWindowEnd must be present when a sealed tranche exists")
        .isNotBlank();
    assertThat(confirmationRequired)
        .as(
            "confirmationRequired must only reflect whether external archival is enabled when a"
                + " sealed tranche exists")
        .isEqualTo(externalArchivalEnabled);
  }

  private long queryLong(String sql) {
    String result = databaseHelper.executeQuerySingleValue(sql);
    if (result == null || result.isBlank()) {
      return 0L;
    }
    return Long.parseLong(result.trim());
  }
}
