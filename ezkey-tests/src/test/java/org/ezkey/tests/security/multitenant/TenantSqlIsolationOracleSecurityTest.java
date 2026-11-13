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
 * foreign-tenant rows (fail-closed authorization), and they lock hide-existence for the remediated
 * findings (SQL-ISO-001, SQL-ISO-002, SQL-ISO-004): a row that exists outside the caller's scope
 * and a row that does not exist share one client outcome, HTTP 404. SQL-ISO-003 is suppressed
 * (accepted posture: usernames are instance-global because login is); its test only keeps the
 * rejection.
 *
 * <p>Register: {@code docs/java-tenant-sql-isolation-assessment-2026-09.md}.
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
  private static final String NOT_FOUND_TYPE = "https://ezkey.io/problems/admin/resource-not-found";

  private String globalAdminToken;
  private String tenantAdminAToken;
  private Integer tenantAId;
  private Integer tenantAdminAId;
  private Integer tenantAdminBId;
  private Integer integrationBId;
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
      integrationBId =
          testDataFactory.createIntegrationForTenant(
              "SQLISO Integration B " + suffix, tenantBId, tenantAdminBToken);

      testDataFactory.createEnrollment(integrationAId, "SQLISO Device A", false, tenantAdminAToken);
      enrollmentBId =
          testDataFactory.createEnrollment(
              integrationBId, "SQLISO Device B", false, tenantAdminBToken);

      apiKeyACredentials =
          testDataFactory.createApiKeyForIntegration(integrationAId, tenantAdminAToken);

      resolveGlobalAdminIdentity();
      tenantAdminAId = resolveAdminId(tenantAdminAToken);
      tenantAdminBId = resolveAdminId(tenantAdminBToken);
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

    // Hide existence: an enrollment of another integration answers exactly like an unknown id
    assertThat(foreign.getStatusCode()).isEqualTo(404);
    assertThat(missing.getStatusCode()).isEqualTo(404);
    assertThat(foreign.jsonPath().getString("type"))
        .isEqualTo("https://ezkey.io/problems/resource/not-found");
    assertThat(missing.jsonPath().getString("type"))
        .isEqualTo(foreign.jsonPath().getString("type"));
    assertThat(foreign.jsonPath().getString("detail")).doesNotContainIgnoringCase("belong");
    assertThat(foreign.getBody().asString()).doesNotContain("SQLISO Tenant B");
    assertThat(foreign.getBody().asString()).doesNotContain("SQLISO Device B");
    assertThat(foreign.getBody().asString()).doesNotContain("SQLISO Integration B");
  }

  @Test
  @Order(2)
  @DisplayName("SQL-ISO-002: out-of-tenant admin ids answer exactly like a missing id (404)")
  public void tenantAdminACannotTellForeignAdminFromMissing() {
    configureForAdminApi(dockerStackConfig);
    Map<String, Object> patchBody = Map.of("firstName", "Probe");

    String[][] routes = {
      {"GET", ""},
      {"PATCH", ""},
      {"GET", "/onboarding"},
      {"GET", "/onboarding/qrcode"},
      {"POST", "/recovery-codes/regenerate"},
      {"POST", "/recovery-codes/issue-initial"},
    };

    for (String[] route : routes) {
      String method = route[0];
      String suffix = route[1];
      Response missing = adminRoute(method, UNKNOWN_ADMIN_ID, suffix, patchBody);
      Response globalAdmin = adminRoute(method, globalAdminId, suffix, patchBody);
      Response otherTenant = adminRoute(method, tenantAdminBId, suffix, patchBody);

      log.info(
          "SQL-ISO-002 {} /admins/{{id}}{} → GA {} / other tenant {} / missing {}",
          method,
          suffix,
          globalAdmin.getStatusCode(),
          otherTenant.getStatusCode(),
          missing.getStatusCode());

      String label = method + " /admins/{id}" + suffix;
      assertThat(missing.getStatusCode()).as(label + " missing").isEqualTo(404);
      assertThat(globalAdmin.getStatusCode()).as(label + " Global Admin").isEqualTo(404);
      assertThat(otherTenant.getStatusCode()).as(label + " other tenant").isEqualTo(404);
      assertThat(problemType(missing)).as(label + " type").isEqualTo(NOT_FOUND_TYPE);
      assertThat(problemType(globalAdmin)).as(label + " type").isEqualTo(problemType(missing));
      assertThat(problemType(otherTenant)).as(label + " type").isEqualTo(problemType(missing));
      assertThat(globalAdmin.getBody().asString()).doesNotContain(globalAdminUsername);
      assertThat(globalAdmin.getBody().asString()).doesNotContainIgnoringCase("tenant admin");
      assertThat(otherTenant.getBody().asString()).doesNotContain("sqliso_b_");
    }

    // Control: the same caller still reads an admin of their own tenant
    assertThat(adminRoute("GET", tenantAdminAId, "", patchBody).getStatusCode()).isEqualTo(200);
  }

  @Test
  @Order(4)
  @DisplayName(
      "SQL-ISO-004: userIdentifier lookup on a foreign integration answers like a missing one")
  public void tenantAdminACannotProbeUserIdentifiersOfForeignIntegration() {
    Response foreign = postAdminAuthAttemptByUserIdentifier(integrationBId, "sqliso-probe-user");
    Response missing =
        postAdminAuthAttemptByUserIdentifier(UNKNOWN_ENROLLMENT_ID, "sqliso-probe-user");

    log.info(
        "SQL-ISO-004 userIdentifier on foreign integration {} → HTTP {} / unknown integration →"
            + " HTTP {}",
        integrationBId,
        foreign.getStatusCode(),
        missing.getStatusCode());

    // The integration check runs before the lookup, so a hit and a miss on Tenant B cannot differ:
    // both stop here. The "lookup never runs" half is unit-tested in admin-api
    // (AuthAttemptControllerOwnershipTest).
    assertThat(foreign.getStatusCode()).isEqualTo(404);
    assertThat(missing.getStatusCode()).isEqualTo(404);
    assertThat(problemType(foreign)).isEqualTo(NOT_FOUND_TYPE);
    assertThat(problemType(missing)).isEqualTo(NOT_FOUND_TYPE);
    assertThat(foreign.getBody().asString()).doesNotContain("SQLISO Integration B");
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

  private Response adminRoute(String method, Integer adminId, String suffix, Object body) {
    configureForAdminApi(dockerStackConfig);
    var request =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + tenantAdminAToken);
    String path = "/admins/" + adminId + suffix;
    return switch (method) {
      case "GET" -> request.when().get(path).then().extract().response();
      case "PATCH" -> request.body(body).when().patch(path).then().extract().response();
      default -> request.when().post(path).then().extract().response();
    };
  }

  private Response postAdminAuthAttemptByUserIdentifier(
      Integer integrationId, String userIdentifier) {
    Map<String, Object> body = new HashMap<>();
    body.put("integrationId", integrationId);
    body.put("userIdentifier", userIdentifier);
    body.put("challengeRequested", false);

    configureForAdminApi(dockerStackConfig);
    return given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + tenantAdminAToken)
        .body(body)
        .when()
        .post("/auth-attempts")
        .then()
        .extract()
        .response();
  }

  /** RFC 9457 {@code type}, or null when the response has no JSON body (empty 404). */
  private static String problemType(Response response) {
    String body = response.getBody().asString();
    return body == null || body.isBlank() || !body.trim().startsWith("{")
        ? null
        : response.jsonPath().getString("type");
  }

  private Integer resolveAdminId(String bearerToken) {
    configureForAdminApi(dockerStackConfig);
    Object id =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + bearerToken)
            .when()
            .get("/admin/auth/me")
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()
            .get("adminId");
    if (id instanceof Number number) {
      return number.intValue();
    }
    throw new IllegalStateException("GET /admin/auth/me did not return adminId");
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
