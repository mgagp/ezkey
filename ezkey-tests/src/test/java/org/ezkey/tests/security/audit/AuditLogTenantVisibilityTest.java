/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test Class: AuditLogTenantVisibilityTest
 * Description: Functional tests verifying tenant_id population in audit logs and
 *     tenant-scoped visibility for the audit log query endpoint.
 */

package org.ezkey.tests.security.audit;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.ezkey.tests.util.RestAssuredTestConfig.configureForAuthApi;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.ezkey.tests.security.AbstractSecurityTest;
import org.ezkey.tests.tags.TestTags;
import org.ezkey.tests.util.CryptoApiClient.EcP256KeyPair;
import org.ezkey.tests.util.RestAssuredTestConfig;
import org.ezkey.tests.util.TenantAdminTestHelper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Functional tests for audit log tenant visibility.
 *
 * <p>Validates that the {@code tenant_id} field is correctly populated in audit log entries created
 * by admin operations (specifically auth attempt cancellation) and that the audit log query
 * endpoint enforces proper tenant-scoped visibility.
 *
 * <p><b>Test Scenarios:</b>
 *
 * <ul>
 *   <li><b>tenant_id population (SUCCESS):</b> Cancelling an auth attempt produces an audit log
 *       entry with the correct {@code tenant_id} derived from the enrollment's integration.
 *   <li><b>tenant_id population (FAILURE):</b> Cancelling an already-cancelled auth attempt
 *       produces a FAILURE audit log entry that still carries the correct {@code tenant_id},
 *       validating that the resolution happens before the try block.
 *   <li><b>Tenant isolation:</b> TenantAdmin B cannot see Tenant A's audit log entries.
 *   <li><b>GlobalAdmin visibility:</b> GlobalAdmin sees all audit logs and can optionally filter by
 *       {@code tenantId}.
 * </ul>
 *
 * <p><b>Prerequisites:</b> Docker stack must be running with admin API and auth API accessible.
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Tag(TestTags.MULTI_TENANT)
@Tag(TestTags.SECURITY)
@Tag(TestTags.FAST)
@DisplayName("Audit Log Tenant Visibility")
public class AuditLogTenantVisibilityTest extends AbstractSecurityTest {

  private static final Logger log = LoggerFactory.getLogger(AuditLogTenantVisibilityTest.class);

  private TenantAdminTestHelper tenantAdminTestHelper;
  private String globalAdminToken;
  private Integer tenantAId;
  private Integer tenantBId;
  private String tenantAdminAToken;
  private String tenantAdminBToken;
  private Integer integrationAId;
  private Integer enrollmentAId;
  private String uniqueSuffix;

  @Override
  @BeforeEach
  public void setUp() {
    super.setUp();

    try {
      globalAdminToken = authTokenManager.getAdminToken();

      tenantAdminTestHelper =
          new TenantAdminTestHelper(dockerStackConfig, testDataFactory, cryptoApiClient);

      uniqueSuffix = String.valueOf(System.currentTimeMillis());

      tenantAId = testDataFactory.findOrCreateTenant("AuditVisA " + uniqueSuffix, globalAdminToken);
      tenantBId = testDataFactory.findOrCreateTenant("AuditVisB " + uniqueSuffix, globalAdminToken);

      tenantAdminAToken =
          tenantAdminTestHelper.createAndLoginTenantAdmin(
              "audit-a-" + uniqueSuffix, tenantAId, globalAdminToken);

      tenantAdminBToken =
          tenantAdminTestHelper.createAndLoginTenantAdmin(
              "audit-b-" + uniqueSuffix, tenantBId, globalAdminToken);

      RestAssuredTestConfig.configureForAdminApi(dockerStackConfig);

      integrationAId =
          testDataFactory.createIntegrationForTenant(
              "IntAuditVis " + uniqueSuffix, tenantAId, tenantAdminAToken);

      enrollmentAId = createVerifiedEnrollment(integrationAId, tenantAdminAToken);

      log.info(
          "Test setup complete: tenantA={}, tenantB={}, integrationA={}, enrollmentA={}",
          tenantAId,
          tenantBId,
          integrationAId,
          enrollmentAId);

    } catch (IllegalStateException e) {
      Assumptions.assumeTrue(false, "Test setup failed: " + e.getMessage());
    }
  }

  /**
   * Verifies that a successful auth attempt cancellation produces an audit log entry with the
   * correct {@code tenant_id}, and that this entry is visible to the TenantAdmin who owns the
   * resource.
   */
  @Test
  @DisplayName("Cancel SUCCESS audit log has tenant_id and is visible to TenantAdmin (200)")
  public void testCancelSuccessAuditLogHasTenantId() {
    RestAssuredTestConfig.configureForAdminApi(dockerStackConfig);

    Integer authAttemptId = createAuthAttemptWithToken(enrollmentAId, tenantAdminAToken);
    log.info("Created auth attempt {} for enrollment {}", authAttemptId, enrollmentAId);

    Response cancelResponse =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + tenantAdminAToken)
            .when()
            .post("/auth-attempts/" + authAttemptId + "/cancel")
            .then()
            .extract()
            .response();

    assertThat(cancelResponse.getStatusCode()).as("Cancel should succeed with 200").isEqualTo(200);

    Response auditResponse =
        given()
            .header("Authorization", "Bearer " + tenantAdminAToken)
            .queryParam("eventType", "AUTH_ATTEMPT_CANCELLED")
            .queryParam("eventStatus", "SUCCESS")
            .queryParam("page", 0)
            .queryParam("size", 100)
            .queryParam("sort", "createdAt,DESC")
            .when()
            .get("/audit-logs")
            .then()
            .statusCode(200)
            .extract()
            .response();

    List<Map<String, Object>> content = auditResponse.jsonPath().getList("content");
    assertThat(content).as("Audit log content should not be empty").isNotEmpty();

    Map<String, Object> entry = findAuditEntryByAuthAttemptId(content, authAttemptId);
    assertThat(entry).as("Audit entry for auth attempt %d should exist", authAttemptId).isNotNull();
    assertThat(entry.get("tenantId"))
        .as("Audit entry tenant_id should match Tenant A")
        .isEqualTo(tenantAId);
    assertThat(entry.get("eventStatus"))
        .as("Audit entry status should be SUCCESS")
        .isEqualTo("SUCCESS");
    assertThat(entry.get("eventType"))
        .as("Audit entry type should be AUTH_ATTEMPT_CANCELLED")
        .isEqualTo("AUTH_ATTEMPT_CANCELLED");

    log.info(
        "Verified: SUCCESS audit entry for authAttempt={} has tenantId={}",
        authAttemptId,
        entry.get("tenantId"));
  }

  /**
   * Verifies that cancelling an already-cancelled auth attempt produces a FAILURE audit log entry
   * that still carries the correct {@code tenant_id}. This validates that the tenant resolution
   * happens before the try block, making it available in error handlers.
   */
  @Test
  @DisplayName("Cancel FAILURE audit log has tenant_id (already cancelled → 409)")
  public void testCancelFailureAuditLogHasTenantId() {
    RestAssuredTestConfig.configureForAdminApi(dockerStackConfig);

    Integer authAttemptId = createAuthAttemptWithToken(enrollmentAId, tenantAdminAToken);
    log.info("Created auth attempt {} for double-cancel test", authAttemptId);

    Response firstCancel =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + tenantAdminAToken)
            .when()
            .post("/auth-attempts/" + authAttemptId + "/cancel")
            .then()
            .extract()
            .response();
    assertThat(firstCancel.getStatusCode()).as("First cancel should succeed").isEqualTo(200);

    Response secondCancel =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + tenantAdminAToken)
            .when()
            .post("/auth-attempts/" + authAttemptId + "/cancel")
            .then()
            .extract()
            .response();
    assertThat(secondCancel.getStatusCode())
        .as("Second cancel should fail with 409 (already in final state)")
        .isEqualTo(409);

    Response auditResponse =
        given()
            .header("Authorization", "Bearer " + tenantAdminAToken)
            .queryParam("eventType", "AUTH_ATTEMPT_CANCELLED")
            .queryParam("eventStatus", "FAILURE")
            .queryParam("page", 0)
            .queryParam("size", 100)
            .queryParam("sort", "createdAt,DESC")
            .when()
            .get("/audit-logs")
            .then()
            .statusCode(200)
            .extract()
            .response();

    List<Map<String, Object>> content = auditResponse.jsonPath().getList("content");
    assertThat(content).as("Audit log content should not be empty").isNotEmpty();

    Map<String, Object> failEntry = findAuditEntryByAuthAttemptId(content, authAttemptId);
    assertThat(failEntry)
        .as("FAILURE audit entry for auth attempt %d should exist", authAttemptId)
        .isNotNull();
    assertThat(failEntry.get("tenantId"))
        .as("FAILURE audit entry tenant_id should match Tenant A")
        .isEqualTo(tenantAId);
    assertThat(failEntry.get("eventStatus"))
        .as("Audit entry status should be FAILURE")
        .isEqualTo("FAILURE");

    log.info(
        "Verified: FAILURE audit entry for authAttempt={} has tenantId={}",
        authAttemptId,
        failEntry.get("tenantId"));
  }

  /**
   * Verifies cross-tenant audit log isolation: TenantAdmin B must not see any audit log entries
   * belonging to Tenant A.
   */
  @Test
  @DisplayName("TenantAdmin B cannot see Tenant A's audit logs (isolation)")
  public void testTenantAdminBCannotSeeTenantAAuditLogs() {
    RestAssuredTestConfig.configureForAdminApi(dockerStackConfig);

    Integer authAttemptId = createAuthAttemptWithToken(enrollmentAId, tenantAdminAToken);
    given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + tenantAdminAToken)
        .when()
        .post("/auth-attempts/" + authAttemptId + "/cancel")
        .then()
        .statusCode(200);

    Response auditResponseB =
        given()
            .header("Authorization", "Bearer " + tenantAdminBToken)
            .queryParam("eventType", "AUTH_ATTEMPT_CANCELLED")
            .queryParam("page", 0)
            .queryParam("size", 100)
            .queryParam("sort", "createdAt,DESC")
            .when()
            .get("/audit-logs")
            .then()
            .statusCode(200)
            .extract()
            .response();

    List<Map<String, Object>> contentB = auditResponseB.jsonPath().getList("content");

    if (contentB != null && !contentB.isEmpty()) {
      for (Map<String, Object> entry : contentB) {
        Integer entryTenantId = (Integer) entry.get("tenantId");
        assertThat(entryTenantId)
            .as(
                "TenantAdmin B should not see Tenant A audit logs (entry auditLogId=%s)",
                entry.get("auditLogId"))
            .isNotEqualTo(tenantAId);
      }
    }

    log.info(
        "Verified: TenantAdmin B sees {} audit entries, none from Tenant A ({})",
        contentB != null ? contentB.size() : 0,
        tenantAId);
  }

  /**
   * Verifies that GlobalAdmin can see audit logs from all tenants and can optionally narrow the
   * results by applying a {@code tenantId} filter.
   */
  @Test
  @DisplayName("GlobalAdmin sees all audit logs and can filter by tenantId (200)")
  public void testGlobalAdminSeesAllAndCanFilterByTenant() {
    RestAssuredTestConfig.configureForAdminApi(dockerStackConfig);

    Integer authAttemptId = createAuthAttemptWithToken(enrollmentAId, tenantAdminAToken);
    given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + tenantAdminAToken)
        .when()
        .post("/auth-attempts/" + authAttemptId + "/cancel")
        .then()
        .statusCode(200);

    Response unfilteredResponse =
        given()
            .header("Authorization", "Bearer " + globalAdminToken)
            .queryParam("eventType", "AUTH_ATTEMPT_CANCELLED")
            .queryParam("page", 0)
            .queryParam("size", 100)
            .queryParam("sort", "createdAt,DESC")
            .when()
            .get("/audit-logs")
            .then()
            .statusCode(200)
            .extract()
            .response();

    List<Map<String, Object>> allContent = unfilteredResponse.jsonPath().getList("content");
    assertThat(allContent).as("GlobalAdmin should see audit entries").isNotEmpty();

    Map<String, Object> targetEntry = findAuditEntryByAuthAttemptId(allContent, authAttemptId);
    assertThat(targetEntry).as("GlobalAdmin should see Tenant A's cancel audit entry").isNotNull();
    assertThat(targetEntry.get("tenantId"))
        .as("Entry should have Tenant A's tenantId")
        .isEqualTo(tenantAId);

    Response filteredResponse =
        given()
            .header("Authorization", "Bearer " + globalAdminToken)
            .queryParam("eventType", "AUTH_ATTEMPT_CANCELLED")
            .queryParam("tenantId", tenantAId)
            .queryParam("page", 0)
            .queryParam("size", 100)
            .queryParam("sort", "createdAt,DESC")
            .when()
            .get("/audit-logs")
            .then()
            .statusCode(200)
            .extract()
            .response();

    List<Map<String, Object>> filteredContent = filteredResponse.jsonPath().getList("content");
    assertThat(filteredContent).as("Filtered results should not be empty").isNotEmpty();

    for (Map<String, Object> entry : filteredContent) {
      assertThat(entry.get("tenantId"))
          .as(
              "All filtered entries should belong to Tenant A (auditLogId=%s)",
              entry.get("auditLogId"))
          .isEqualTo(tenantAId);
    }

    assertThat(filteredContent.size())
        .as("Filtered count should be <= unfiltered count")
        .isLessThanOrEqualTo(allContent.size());

    log.info(
        "Verified: GlobalAdmin sees {} unfiltered entries, {} filtered for tenantA={}",
        allContent.size(),
        filteredContent.size(),
        tenantAId);
  }

  // ---------------------------------------------------------------------------
  // Helper methods
  // ---------------------------------------------------------------------------

  /**
   * Creates a fully verified enrollment (bind + verify) for use in auth-attempt tests.
   *
   * <p>Auth attempts require a VERIFIED enrollment per the enrollment lifecycle security gate.
   *
   * @param integrationId integration ID
   * @param bearerToken bearer token for Admin API (tenant admin with access to the integration)
   * @return Enrollment ID of the verified enrollment
   */
  private Integer createVerifiedEnrollment(Integer integrationId, String bearerToken) {
    Integer enrollmentId =
        testDataFactory.createEnrollment(integrationId, "AuditVis " + uniqueSuffix, false);

    Response enrollmentResponse =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + bearerToken)
            .when()
            .get("/enrollments/" + enrollmentId)
            .then()
            .statusCode(200)
            .extract()
            .response();

    String enrollmentProofToken = enrollmentResponse.jsonPath().getString("enrollmentProofToken");
    Integer challengeCode = enrollmentResponse.jsonPath().getInt("enrollmentChallenge");
    assertThat(enrollmentProofToken).isNotNull().isNotEmpty();

    EcP256KeyPair deviceKeyPair = cryptoApiClient.generateKeyPair();
    configureForAuthApi(dockerStackConfig);

    Map<String, Object> bindRequest = new HashMap<>();
    bindRequest.put("enrollmentId", enrollmentId);
    bindRequest.put("enrollmentProofToken", enrollmentProofToken);

    Response bindResponse =
        given()
            .contentType(ContentType.JSON)
            .body(bindRequest)
            .when()
            .post("/enrollments/bind")
            .then()
            .statusCode(200)
            .extract()
            .response();

    String bindProofToken = bindResponse.jsonPath().getString("enrollmentProofToken");
    assertThat(bindProofToken).isNotNull().isNotEmpty();

    String signature = cryptoApiClient.signData(bindProofToken, deviceKeyPair.privateKey());
    configureForAuthApi(dockerStackConfig);

    Map<String, Object> verifyRequest = new HashMap<>();
    verifyRequest.put("enrollmentId", enrollmentId);
    verifyRequest.put("challengeResponse", challengeCode);
    verifyRequest.put("devicePublicKey", deviceKeyPair.publicKey());
    verifyRequest.put("enrollmentProofTokenSigned", signature);

    given()
        .contentType(ContentType.JSON)
        .body(verifyRequest)
        .when()
        .post("/enrollments/verify")
        .then()
        .statusCode(200);

    RestAssuredTestConfig.configureForAdminApi(dockerStackConfig);
    return enrollmentId;
  }

  /**
   * Creates an auth attempt using the specified bearer token.
   *
   * @param enrollmentId the enrollment ID to create the auth attempt for
   * @param bearerToken the bearer token for authentication
   * @return the created auth attempt ID
   */
  private Integer createAuthAttemptWithToken(Integer enrollmentId, String bearerToken) {
    Map<String, Object> request = new HashMap<>();
    request.put("enrollmentId", enrollmentId);
    request.put("challengeRequested", false);

    Response response =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + bearerToken)
            .body(request)
            .when()
            .post("/auth-attempts")
            .then()
            .statusCode(201)
            .extract()
            .response();

    return response.jsonPath().getInt("authAttemptId");
  }

  /**
   * Finds an audit log entry matching the given auth attempt ID within a list of audit entries.
   *
   * @param content the list of audit log entries from the API response
   * @param authAttemptId the auth attempt ID to search for
   * @return the matching entry, or {@code null} if not found
   */
  private Map<String, Object> findAuditEntryByAuthAttemptId(
      List<Map<String, Object>> content, Integer authAttemptId) {
    if (content == null) {
      return null;
    }
    return content.stream()
        .filter(e -> e.get("authAttemptId") != null && authAttemptId.equals(e.get("authAttemptId")))
        .findFirst()
        .orElse(null);
  }
}
