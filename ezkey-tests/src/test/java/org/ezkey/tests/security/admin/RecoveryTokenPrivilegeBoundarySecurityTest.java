/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: RecoveryTokenPrivilegeBoundarySecurityTest
 * Description: SEC-021 — recovery tokens must not authenticate as full admin sessions.
 */

package org.ezkey.tests.security.admin;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.ezkey.tests.util.RestAssuredTestConfig.configureForAdminApi;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.ezkey.tests.security.AbstractSecurityTest;
import org.ezkey.tests.tags.TestTags;
import org.ezkey.tests.util.TenantAdminTestHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * SEC-021 regression: recovery bearer tokens are reset-only, not full administrator sessions.
 *
 * <p>Uses a disposable Tenant Admin (full enroll + login + initial recovery codes) so the bootstrap
 * Global Admin enrollment is never unbound.
 *
 * @since 2026
 */
@Tag(TestTags.FAST)
@Tag(TestTags.ADMIN)
@DisplayName("SEC-021 Recovery Token Privilege Boundary")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RecoveryTokenPrivilegeBoundarySecurityTest extends AbstractSecurityTest {

  private static String disposableUsername;
  private static Integer disposableAdminId;
  private static Integer disposableEnrollmentId;
  private static String disposableRecoveryToken;

  @Test
  @Order(1)
  @DisplayName("Recover issues ezkey_recovery_* token for disposable enrolled admin")
  public void recoverIssuesRecoveryToken() throws Exception {
    String sessionAdminToken = authTokenManager.getAdminToken();
    configureForAdminApi(dockerStackConfig);

    String suffix = UUID.randomUUID().toString().substring(0, 8);
    disposableUsername = "sec021_" + suffix;
    Integer tenantId = testDataFactory.createTenant("SEC021 Tenant " + suffix, sessionAdminToken);

    // Create + enroll + login so issue-initial recovery codes is eligible (lastLoginAt set).
    TenantAdminTestHelper tenantAdminHelper =
        new TenantAdminTestHelper(dockerStackConfig, testDataFactory, cryptoApiClient);
    tenantAdminHelper.createAndLoginTenantAdmin(disposableUsername, tenantId, sessionAdminToken);

    Path credentialsPath =
        Path.of(
            System.getProperty("ezkey.test.state.dir", ".ezkey-test"),
            "tenant-admin-" + tenantId + "-device-credentials.json");
    assertThat(Files.exists(credentialsPath))
        .as("Tenant admin device credentials file should exist: %s", credentialsPath)
        .isTrue();
    JsonNode credentialsJson = new ObjectMapper().readTree(credentialsPath.toFile());
    disposableAdminId = credentialsJson.get("adminId").asInt();
    disposableEnrollmentId = credentialsJson.get("enrollmentId").asInt();

    configureForAdminApi(dockerStackConfig);
    Response issueInitialResponse =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + sessionAdminToken)
            .when()
            .post("/admins/" + disposableAdminId + "/recovery-codes/issue-initial")
            .then()
            .extract()
            .response();

    assertThat(issueInitialResponse.getStatusCode())
        .as("issue-initial body: %s", issueInitialResponse.asString())
        .isEqualTo(200);

    List<String> recoveryCodes =
        issueInitialResponse.jsonPath().getList("recoveryCodes", String.class);
    assertThat(recoveryCodes).isNotEmpty();

    Map<String, Object> recoverRequest = new HashMap<>();
    recoverRequest.put("username", disposableUsername);
    recoverRequest.put("recoveryCode", recoveryCodes.get(0));

    Response recoverResponse =
        given()
            .contentType(ContentType.JSON)
            .body(recoverRequest)
            .when()
            .post("/admin/auth/recover")
            .then()
            .extract()
            .response();

    assertThat(recoverResponse.getStatusCode())
        .as("recover body: %s", recoverResponse.asString())
        .isEqualTo(200);
    assertThat(recoverResponse.jsonPath().getBoolean("success")).isTrue();

    disposableRecoveryToken = recoverResponse.jsonPath().getString("recoveryToken");
    assertThat(disposableRecoveryToken).isNotBlank().startsWith("ezkey_recovery_");
    log.info(
        "SEC-021 setup: username={}, adminId={}, enrollmentId={}",
        disposableUsername,
        disposableAdminId,
        disposableEnrollmentId);
  }

  @Test
  @Order(2)
  @DisplayName("Recovery token must receive 401 on ordinary Admin API routes")
  public void recoveryTokenDeniedOnOrdinaryAdminRoutes() {
    org.junit.jupiter.api.Assumptions.assumeTrue(
        disposableRecoveryToken != null, "Recovery token from Order(1) required");

    configureForAdminApi(dockerStackConfig);

    Response integrationsResponse =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + disposableRecoveryToken)
            .when()
            .get("/integrations")
            .then()
            .extract()
            .response();

    assertThat(integrationsResponse.getStatusCode())
        .as("SEC-021: recovery bearer must not authenticate as a session on GET /integrations")
        .isEqualTo(401);

    Response meResponse =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + disposableRecoveryToken)
            .when()
            .get("/admin/auth/me")
            .then()
            .extract()
            .response();

    assertThat(meResponse.getStatusCode())
        .as("SEC-021: recovery bearer must not authenticate as a session on GET /admin/auth/me")
        .isEqualTo(401);
  }

  @Test
  @Order(3)
  @DisplayName("Recovery token can still reset its own enrollment")
  public void recoveryTokenCanResetOwnEnrollment() {
    org.junit.jupiter.api.Assumptions.assumeTrue(
        disposableRecoveryToken != null && disposableEnrollmentId != null,
        "Recovery token and enrollment from Order(1) required");

    configureForAdminApi(dockerStackConfig);

    Map<String, Object> resetRequest = new HashMap<>();
    resetRequest.put("enrollmentId", disposableEnrollmentId);

    Response resetResponse =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + disposableRecoveryToken)
            .body(resetRequest)
            .when()
            .post("/admin/enrollments/reset")
            .then()
            .extract()
            .response();

    assertThat(resetResponse.getStatusCode())
        .as("reset body: %s", resetResponse.asString())
        .isEqualTo(200);
    assertThat(resetResponse.jsonPath().getBoolean("success")).isTrue();
    assertThat(resetResponse.jsonPath().getInt("enrollmentId")).isEqualTo(disposableEnrollmentId);
    assertThat(resetResponse.jsonPath().getString("enrollmentProofToken")).isNotBlank();
  }

  @Test
  @Order(4)
  @DisplayName("Session bearer token is rejected on enrollment reset")
  public void sessionTokenRejectedOnEnrollmentReset() {
    org.junit.jupiter.api.Assumptions.assumeTrue(
        disposableEnrollmentId != null, "Enrollment from Order(1) required");

    String adminToken = authTokenManager.getAdminToken();
    configureForAdminApi(dockerStackConfig);

    Map<String, Object> resetRequest = new HashMap<>();
    resetRequest.put("enrollmentId", disposableEnrollmentId);

    Response resetResponse =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + adminToken)
            .body(resetRequest)
            .when()
            .post("/admin/enrollments/reset")
            .then()
            .extract()
            .response();

    assertThat(resetResponse.getStatusCode()).isEqualTo(403);
  }

  @Test
  @Order(5)
  @DisplayName("Normal session token still accesses protected Admin API")
  public void normalSessionStillWorks() {
    String adminToken = authTokenManager.getAdminToken();
    configureForAdminApi(dockerStackConfig);

    Response response =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + adminToken)
            .when()
            .get("/integrations")
            .then()
            .extract()
            .response();

    assertThat(response.getStatusCode()).isEqualTo(200);
  }
}
