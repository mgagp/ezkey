/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: TenantSqlIsolationOracleSecurityTest
 * Description: Live probes for assessment-curated SQL-ISO tenant-isolation oracles
 */
package org.ezkey.tests.security.multitenant;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.ezkey.tests.util.RestAssuredTestConfig.configureForAdminApi;
import static org.ezkey.tests.util.RestAssuredTestConfig.configureForIntegrationApi;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.ezkey.tests.security.AbstractSecurityTest;
import org.ezkey.tests.tags.TestTags;
import org.ezkey.tests.util.TenantAdminTestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Live probes for the 2026-09 tenant SQL isolation assessment.
 *
 * <p>These tests prove that Tenant Admin / Integration API key callers <b>cannot consume</b>
 * foreign-tenant rows (fail-closed authorization). They also capture Problem details so the
 * assessment register can record hide-existence oracles (SQL-ISO-001, SQL-ISO-002, SQL-ISO-003)
 * without locking those dialects into CI. When a finding is remediated, keep the rejection
 * assertions; expect the two details/status pairs to converge.
 *
 * Register: {@code docs/java-tenant-sql-isolation-assessment-2026-09.md}.
 *
 * @since 2026
 */
@Tag(TestTags.FAST)
@Tag(TestTags.SECURITY)
@Tag(TestTags.MULTI_TENANT)
@DisplayName("Tenant SQL isolation oracles (assessment-curated)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TenantSqlIsolationOracleSecurityTest extends AbstractSecurityTest {

  private static final int UNKNOWN_ENROLLMENT_ID = 2_000_000_000;
  private static final int UNKNOWN_ADMIN_ID = 2_000_000_000;

  private String globalAdminToken;
  private String tenantAdminAToken;
  private Integer tenantAId;
  private Integer enrollmentBId;
  private String apiKeyACredentials;
  private Integer globalAdminId;
  private String globalAdminUsername;

  @Override
  @BeforeEach
  public void setUp() {
    super.setUp();

    try {
      globalAdminToken = authTokenManager.getAdminToken();
      configureForAdminApi(dockerStackConfig);

      String suffix = UUID.randomUUID().toString().substring(0, 8);
      tenantAId = testDataFactory.createTenant("SQLISO Tenant A " + suffix, globalAdminToken);
      Integer tenantBId =
          testDataFactory.createTenant("SQLISO Tenant B " + suffix, globalAdminToken);

      TenantAdminTestHelper helper =
          new TenantAdminTestHelper(dockerStackConfig, testDataFactory, cryptoApiClient);
      tenantAdminAToken =
          helper.createAndLoginTenantAdmin("sqliso_a_" + suffix, tenantAId, globalAdminToken);
      String tenantAdminBToken =
          helper.createAndLoginTenantAdmin("sqliso_b_" + suffix, tenantBId, globalAdminToken);

      Integer integrationAId =
          testDataFactory.createIntegrationForTenant(
              "SQLISO Integration A " + suffix, tenantAId, tenantAdminAToken);
      Integer integrationBId =
          testDataFactory.createIntegrationForTenant(
              "SQLISO Integration B " + suffix, tenantBId, tenantAdminBToken);

      testDataFactory.createEnrollment(integrationAId, "SQLISO Device A", false, tenantAdminAToken);
      enrollmentBId =
          testDataFactory.createEnrollment(
              integrationBId, "SQLISO Device B", false, tenantAdminBToken);

      apiKeyACredentials =
          testDataFactory.createApiKeyForIntegration(integrationAId, tenantAdminAToken);

      resolveGlobalAdminIdentity();
    } catch (IllegalStateException e) {
      org.junit.jupiter.api.Assumptions.assumeTrue(
          false,
          "Test setup failed - admin token or device simulation not available: " + e.getMessage());
    }
  }

  @Test
  @Order(1)
  @DisplayName("SQL-ISO-001: API key A cannot create an auth attempt for Tenant B enrollment")
  public void apiKeyACannotCreateAuthAttemptForEnrollmentB() {
    Response foreign = postIntegrationAuthAttempt(enrollmentBId);
    Response missing = postIntegrationAuthAttempt(UNKNOWN_ENROLLMENT_ID);

    log.info(
        "SQL-ISO-001 foreign enrollment {} → HTTP {} detail={}",
        enrollmentBId,
        foreign.getStatusCode(),
        foreign.jsonPath().getString("detail"));
    log.info(
        "SQL-ISO-001 unknown enrollment {} → HTTP {} detail={}",
        UNKNOWN_ENROLLMENT_ID,
        missing.getStatusCode(),
        missing.jsonPath().getString("detail"));

    assertThat(foreign.getStatusCode()).isIn(400, 403, 404);
    assertThat(missing.getStatusCode()).isIn(400, 403, 404);
    assertThat(foreign.getBody().asString()).doesNotContain("SQLISO Tenant B");
    assertThat(foreign.getBody().asString()).doesNotContain("SQLISO Device B");
    assertThat(foreign.getBody().asString()).doesNotContain("SQLISO Integration B");
  }

  @Test
  @Order(2)
  @DisplayName("SQL-ISO-002: TenantAdmin A cannot read Global Admin by id")
  public void tenantAdminACannotGetGlobalAdminById() {
    configureForAdminApi(dockerStackConfig);

    Response globalAdmin =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + tenantAdminAToken)
            .when()
            .get("/admins/" + globalAdminId)
            .then()
            .extract()
            .response();

    Response missing =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + tenantAdminAToken)
            .when()
            .get("/admins/" + UNKNOWN_ADMIN_ID)
            .then()
            .extract()
            .response();

    log.info(
        "SQL-ISO-002 GET /admins/{} (GA) → HTTP {}", globalAdminId, globalAdmin.getStatusCode());
    log.info(
        "SQL-ISO-002 GET /admins/{} (missing) → HTTP {}",
        UNKNOWN_ADMIN_ID,
        missing.getStatusCode());

    assertThat(globalAdmin.getStatusCode()).isIn(403, 404);
    assertThat(globalAdmin.getBody().asString()).doesNotContain(globalAdminUsername);
    assertThat(missing.getStatusCode()).isEqualTo(404);
  }

  @Test
  @Order(3)
  @DisplayName("SQL-ISO-003: TenantAdmin A cannot provision a peer using the Global Admin username")
  public void tenantAdminACannotReuseGlobalAdminUsername() {
    configureForAdminApi(dockerStackConfig);

    Map<String, Object> request = new HashMap<>();
    request.put("username", globalAdminUsername);
    request.put("email", "sqliso-collision-" + UUID.randomUUID() + "@example.com");
    request.put("firstName", "Collision");
    request.put("lastName", "Probe");
    request.put("tenantId", tenantAId);
    request.put("onboardingMode", "ACTIVATION_CODE");

    Response response =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + tenantAdminAToken)
            .body(request)
            .when()
            .post("/admins/tenant")
            .then()
            .extract()
            .response();

    log.info(
        "SQL-ISO-003 POST /admins/tenant username={} → HTTP {} detail={}",
        globalAdminUsername,
        response.getStatusCode(),
        response.jsonPath().getString("detail"));

    assertThat(response.getStatusCode()).isIn(400, 403, 409);
    assertThat(response.getStatusCode()).isNotEqualTo(201);
  }

  private Response postIntegrationAuthAttempt(Integer enrollmentId) {
    String[] parts = apiKeyACredentials.split(":", 2);
    Map<String, Object> body = new HashMap<>();
    body.put("enrollmentId", enrollmentId);
    body.put("challengeRequested", false);

    configureForIntegrationApi(dockerStackConfig);
    return given()
        .contentType(ContentType.JSON)
        .header("Authorization", basicApiKeyHeader(parts[0], parts[1]))
        .body(body)
        .when()
        .post("/auth-attempts")
        .then()
        .extract()
        .response();
  }

  private void resolveGlobalAdminIdentity() {
    configureForAdminApi(dockerStackConfig);
    Response me =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + globalAdminToken)
            .when()
            .get("/admin/auth/me")
            .then()
            .statusCode(200)
            .extract()
            .response();

    Object id = me.jsonPath().get("adminId");
    if (id instanceof Number number) {
      globalAdminId = number.intValue();
    } else {
      throw new IllegalStateException("GET /admin/auth/me did not return adminId");
    }
    globalAdminUsername = me.jsonPath().getString("username");
    assertThat(globalAdminUsername).isNotBlank();
    assertThat(me.jsonPath().getString("adminType")).isEqualTo("GLOBAL_ADMIN");
  }

  private static String basicApiKeyHeader(String integrationKey, String secretKey) {
    String credentials = integrationKey + ":" + secretKey;
    return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());
  }
}
