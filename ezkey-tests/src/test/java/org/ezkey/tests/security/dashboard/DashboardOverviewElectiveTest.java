/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test Class: DashboardOverviewElectiveTest
 * Description: Elective spot checks for dashboard overview and pending-count. Cross-validates
 *     API aggregates against database counts via DatabaseHelper (opportunistic validation).
 */

package org.ezkey.tests.security.dashboard;

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
 * Elective spot checks for dashboard API: overview and pending-count.
 *
 * <p>Uses the project's opportunistic testing philosophy: call the dashboard endpoints, then
 * cross-validate selected metrics against direct database counts via {@link DatabaseHelper}. Does
 * not validate alerts (GAP_PENDING) as that would require an elaborate audit-chain scenario.
 *
 * <p><b>Scope:</b> Runs with the default admin token (initial global admin from bootstrap), so
 * dashboard returns instance-wide aggregates. DB spot checks use equivalent instance-wide counts
 * (no tenant filter).
 *
 * <p><b>Invocation:</b>
 *
 * <pre>{@code
 * mvn test -pl ezkey-tests -P elective-tests
 * }</pre>
 *
 * <p><b>Resilience / clean-start:</b> The test must pass on a freshly recreated DB (e.g. after
 * clean-start). Assertions are strict equalities between API and DB counts: when the DB has little
 * or no data, the API returns the same counts (e.g. zeros), so the test passes. It simply reflects
 * coherent reality between the dashboard response and the database — no skip, no special handling
 * for empty data.
 *
 * @since 2026
 */
@Tag(TestTags.ELECTIVE)
@DisplayName("Dashboard Overview Elective Spot Checks")
public class DashboardOverviewElectiveTest extends AbstractSecurityTest {

  private static final Logger logger = LoggerFactory.getLogger(DashboardOverviewElectiveTest.class);

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
  @DisplayName("Dashboard overview and pending-count match DB spot-check counts")
  void dashboardOverview_andPendingCount_matchDbSpotChecks() {
    logger.info("=== Elective: Dashboard Overview vs DB Spot Checks ===");

    Assumptions.assumeTrue(adminToken != null && !adminToken.isBlank(), "Admin token required");

    // --- 1) DB counts (instance-wide, matching Global Admin scope) ---
    long dbIntegrationsTotal =
        queryLong(
            "SELECT COUNT(*) FROM ezkey_integration "
                + "WHERE (is_system_integration IS NULL OR is_system_integration = false)");
    long dbEnrollmentsActive =
        queryLong("SELECT COUNT(*) FROM ezkey_enrollment WHERE enrollment_active = true");
    long dbAuth24h =
        queryLong(
            "SELECT COUNT(*) FROM ezkey_auth_attempt "
                + "WHERE created_at >= NOW() - INTERVAL '24 hours'");
    long dbPending =
        queryLong("SELECT COUNT(*) FROM ezkey_auth_attempt WHERE auth_attempt_status = 'PENDING'");

    logger.info(
        "DB snapshot: integrations={}, enrollments(active)={}, auth24h={}, pending={}",
        dbIntegrationsTotal,
        dbEnrollmentsActive,
        dbAuth24h,
        dbPending);
    if (dbIntegrationsTotal == 0 && dbAuth24h == 0 && dbPending == 0) {
      logger.info(
          "Clean-start / minimal data: API and DB counts will match (zeros or bootstrap-only)");
    }

    // --- 2) GET /dashboard/overview ---
    Response overviewResponse =
        given()
            .header("Authorization", "Bearer " + adminToken)
            .when()
            .get("/dashboard/overview")
            .then()
            .statusCode(200)
            .extract()
            .response();

    int apiIntTotal = overviewResponse.jsonPath().getInt("integrations.total");
    int apiIntActive = overviewResponse.jsonPath().getInt("integrations.active");
    int apiIntInactive = overviewResponse.jsonPath().getInt("integrations.inactive");
    int apiEnrTotal = overviewResponse.jsonPath().getInt("enrollments.total");
    int apiAuth24hTotal = overviewResponse.jsonPath().getInt("auth24h.total");
    // recentActivity and alerts: structure only, no DB cross-check (alerts not validated)

    logger.info(
        "API overview: integrations(total={}, active={}, inactive={}), enrollments(total={}),"
            + " auth24h(total={})",
        apiIntTotal,
        apiIntActive,
        apiIntInactive,
        apiEnrTotal,
        apiAuth24hTotal);

    assertThat(apiIntTotal)
        .as("Overview integrations.total must match DB count (non-system integrations)")
        .isEqualTo((int) dbIntegrationsTotal);
    assertThat(apiIntActive + apiIntInactive)
        .as("Overview active + inactive must equal total")
        .isEqualTo(apiIntTotal);
    assertThat(apiEnrTotal)
        .as("Overview enrollments.total must match DB count (active enrollments)")
        .isEqualTo((int) dbEnrollmentsActive);
    assertThat(apiAuth24hTotal)
        .as("Overview auth24h.total must match DB count (last 24h); small race allowed")
        .isEqualTo((int) dbAuth24h);

    // --- 3) GET /auth-attempts/pending-count ---
    Response pendingResponse =
        given()
            .header("Authorization", "Bearer " + adminToken)
            .when()
            .get("/auth-attempts/pending-count")
            .then()
            .statusCode(200)
            .extract()
            .response();

    long apiPending = pendingResponse.jsonPath().getLong("count");
    logger.info("API pending-count: {}", apiPending);

    assertThat(apiPending)
        .as("Pending-count must match DB count of PENDING auth attempts")
        .isEqualTo(dbPending);

    logger.info(
        "PASS: Dashboard overview and pending-count match DB spot checks (integrations,"
            + " enrollments, auth24h, pending)");
  }

  private long queryLong(String sql) {
    String result = databaseHelper.executeQuerySingleValue(sql);
    if (result == null || result.isBlank()) {
      return 0L;
    }
    return Long.parseLong(result.trim());
  }
}
