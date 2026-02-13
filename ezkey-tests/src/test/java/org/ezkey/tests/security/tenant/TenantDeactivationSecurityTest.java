/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: TenantDeactivationSecurityTest
 * Description: Security tests for tenant deactivation integrity rules and cascading effects.
 */

package org.ezkey.tests.security.tenant;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.ezkey.tests.util.RestAssuredTestConfig.configureForAdminApi;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.HashMap;
import java.util.Map;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Security tests for tenant deactivation integrity rules.
 *
 * <p>Validates the cascading security effects when a GlobalAdmin deactivates a tenant:
 *
 * <ul>
 *   <li>Tenant is marked inactive via REST API
 *   <li>All active bearer tokens for tenant administrators are revoked
 *   <li>TenantAdmin login is blocked after deactivation
 *   <li>System tenant cannot be deactivated (safety rule)
 *   <li>Deactivation is idempotent (re-deactivating returns 204)
 *   <li>TenantAdmin cannot invoke the deactivation endpoint (authorization)
 * </ul>
 *
 * <p><b>Security Impact:</b> Tenant deactivation is a P0 security operation. When a tenant is
 * compromised or needs to be suspended, the GlobalAdmin must be able to instantly cut all access.
 * This test validates the full kill chain end-to-end.
 *
 * <p><b>Test Strategy:</b>
 *
 * <ul>
 *   <li>Tests 1-4 use the same tenant (created and deactivated in order)
 *   <li>Tests 5-7 are independent scenarios (system tenant, idempotence, authz)
 *   <li>Option A: Token revocation (401) proves E2E blocking — service-level guards are covered by
 *       unit tests
 *   <li>No cleanup: deactivated tenant remains inactive (data accumulation by design)
 * </ul>
 *
 * <p><b>Prerequisites:</b> Docker stack must be running with Admin API on port 9080.
 *
 * @author Ezkey contributors
 * @since 2025
 * @see org.ezkey.tests.security.tenant.TenantBasicOperationsTest
 * @see org.ezkey.tests.security.multitenant.TenantCrossIsolationSecurityTest
 */
@Tag(TestTags.SECURITY)
@Tag(TestTags.MULTI_TENANT)
@Tag(TestTags.ADMIN)
@DisplayName("Tenant Deactivation Security Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TenantDeactivationSecurityTest extends AbstractSecurityTest {

  private static final Logger log = LoggerFactory.getLogger(TenantDeactivationSecurityTest.class);

  /** Unique suffix for test data isolation across runs. */
  private String uniqueSuffix;

  /** GlobalAdmin bearer token. */
  private String globalAdminToken;

  /** Helper for TenantAdmin device simulation and login. */
  private TenantAdminTestHelper tenantAdminTestHelper;

  /**
   * ID of the tenant created for deactivation tests (tests 1-4). Shared across ordered tests via
   * static field to survive {@code @BeforeEach} resets.
   */
  private static Integer deactivationTenantId;

  /**
   * TenantAdmin bearer token obtained before deactivation. Used to verify token revocation in test
   * 3.
   */
  private static String tenantAdminTokenBeforeDeactivation;

  /**
   * Username of the TenantAdmin created for this test suite. Used for login attempt after
   * deactivation in test 4.
   */
  private static String tenantAdminUsername;

  @Override
  @BeforeEach
  public void setUp() {
    super.setUp();

    try {
      globalAdminToken = authTokenManager.getAdminToken();

      tenantAdminTestHelper =
          new TenantAdminTestHelper(dockerStackConfig, testDataFactory, cryptoApiClient);

      if (uniqueSuffix == null) {
        uniqueSuffix = String.valueOf(System.currentTimeMillis());
      }

    } catch (IllegalStateException e) {
      org.junit.jupiter.api.Assumptions.assumeTrue(
          false, "Test setup failed - admin token not available: " + e.getMessage());
    }
  }

  // ==================== Tests 1-4: Deactivation cascade chain
  // ====================

  /**
   * Test 1: GlobalAdmin can deactivate an application tenant.
   *
   * <p>Creates a tenant and a TenantAdmin with full device enrollment, then deactivates the tenant.
   * Stores the tenant ID and TenantAdmin token for subsequent tests.
   */
  @Test
  @Order(1)
  @DisplayName("Test 1: GlobalAdmin deactivates application tenant → 204")
  void test01_globalAdmin_can_deactivate_tenant() {
    log.info("=== Test 1: Deactivate application tenant ===");

    // Arrange: Create a dedicated tenant for deactivation
    configureForAdminApi(dockerStackConfig);

    String tenantName = "Deact " + uniqueSuffix;
    deactivationTenantId = testDataFactory.createTenant(tenantName, globalAdminToken);
    log.info("Created tenant '{}' with ID: {}", tenantName, deactivationTenantId);

    // Create a TenantAdmin with full device enrollment and obtain token
    tenantAdminUsername = "ta-d-" + uniqueSuffix;
    tenantAdminTokenBeforeDeactivation =
        tenantAdminTestHelper.createAndLoginTenantAdmin(
            tenantAdminUsername, deactivationTenantId, globalAdminToken);
    log.info("TenantAdmin '{}' enrolled and logged in", tenantAdminUsername);

    // Verify the token works before deactivation
    configureForAdminApi(dockerStackConfig);
    Response preCheck =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + tenantAdminTokenBeforeDeactivation)
            .when()
            .get("/integrations")
            .then()
            .extract()
            .response();
    assertThat(preCheck.statusCode())
        .as("TenantAdmin token should be valid before deactivation")
        .isEqualTo(200);
    log.info("✅ TenantAdmin token verified as working before deactivation");

    // Act: Deactivate the tenant
    configureForAdminApi(dockerStackConfig);
    Response response =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + globalAdminToken)
            .when()
            .post("/tenants/" + deactivationTenantId + "/deactivate")
            .then()
            .extract()
            .response();

    // Assert
    log.info("Deactivation response status: {}", response.statusCode());
    assertThat(response.statusCode())
        .as("Deactivation should return 204 No Content")
        .isEqualTo(204);

    log.info("✅ Test 1 PASSED: Tenant {} deactivated successfully", deactivationTenantId);
  }

  /**
   * Test 2: Verify tenant is marked inactive via GET.
   *
   * <p>After deactivation, the tenant's {@code active} field should be {@code false}.
   */
  @Test
  @Order(2)
  @DisplayName("Test 2: Deactivated tenant shows active=false via GET")
  void test02_deactivated_tenant_shows_inactive() {
    log.info("=== Test 2: Verify tenant is inactive ===");

    // Guard: ensure test 1 ran
    assertThat(deactivationTenantId).as("Tenant ID from Test 1 should be available").isNotNull();

    // Act
    configureForAdminApi(dockerStackConfig);
    Response response =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + globalAdminToken)
            .when()
            .get("/tenants/" + deactivationTenantId)
            .then()
            .extract()
            .response();

    // Assert
    log.info("Response status: {}", response.statusCode());
    log.info("Response body: {}", response.asString());

    assertThat(response.statusCode()).as("GET tenant should return 200").isEqualTo(200);

    Boolean active = response.jsonPath().getBoolean("active");
    assertThat(active).as("Deactivated tenant should have active=false").isFalse();

    log.info("✅ Test 2 PASSED: Tenant {} has active=false", deactivationTenantId);
  }

  /**
   * Test 3: TenantAdmin bearer token is revoked after deactivation.
   *
   * <p>The token that was valid before deactivation should now be rejected with 401. This validates
   * the token revocation cascade.
   */
  @Test
  @Order(3)
  @DisplayName("Test 3: TenantAdmin token revoked after deactivation → 401")
  void test03_tenantAdmin_token_revoked_after_deactivation() {
    log.info("=== Test 3: Verify token revocation ===");

    // Guard
    assertThat(tenantAdminTokenBeforeDeactivation)
        .as("TenantAdmin token from Test 1 should be available")
        .isNotNull();

    // Act: Try to use the revoked token
    configureForAdminApi(dockerStackConfig);
    Response response =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + tenantAdminTokenBeforeDeactivation)
            .when()
            .get("/integrations")
            .then()
            .extract()
            .response();

    // Assert
    log.info("Response status with revoked token: {}", response.statusCode());
    assertThat(response.statusCode())
        .as("Revoked TenantAdmin token should return 401 Unauthorized")
        .isEqualTo(401);

    log.info("✅ Test 3 PASSED: TenantAdmin token correctly rejected after deactivation");
  }

  /**
   * Test 4: TenantAdmin login is blocked after tenant deactivation.
   *
   * <p>Attempting to login with a TenantAdmin whose tenant has been deactivated should fail with
   * HTTP 403 Forbidden (RFC 9457). The auth service detects the inactive tenant and throws
   * TenantInactiveException, which GlobalExceptionHandler maps to 403.
   */
  @Test
  @Order(4)
  @DisplayName("Test 4: TenantAdmin login blocked after deactivation → 403")
  void test04_tenantAdmin_login_blocked_after_deactivation() {
    log.info("=== Test 4: Verify login blocked ===");

    // Guard
    assertThat(tenantAdminUsername)
        .as("TenantAdmin username from Test 1 should be available")
        .isNotNull();

    // Act: Attempt login with the TenantAdmin whose tenant is deactivated
    configureForAdminApi(dockerStackConfig);

    Map<String, Object> loginRequest = new HashMap<>();
    loginRequest.put("username", tenantAdminUsername);
    loginRequest.put("challengeRequested", true);

    Response response =
        given()
            .contentType(ContentType.JSON)
            .body(loginRequest)
            .when()
            .post("/admin/auth/login")
            .then()
            .extract()
            .response();

    // Assert: Login should be rejected
    log.info("Login response status: {}", response.statusCode());
    log.info("Login response body: {}", response.asString());

    // RFC 9457: TenantInactiveException → HTTP 403 Forbidden with ProblemDetail
    assertThat(response.statusCode())
        .as("Login for deactivated tenant should be rejected (403 Forbidden - RFC 9457)")
        .isEqualTo(403);

    log.info("✅ Test 4 PASSED: TenantAdmin login correctly blocked after deactivation");
  }

  // ==================== Tests 5-7: Independent scenarios ====================

  /**
   * Test 5: System tenant cannot be deactivated (RFC 9457 ProblemDetail).
   *
   * <p>The system tenant (ID 1, {@code isSystemTenant=true}) is protected against deactivation. The
   * API returns 400 Bad Request with RFC 9457 ProblemDetail.
   */
  @Test
  @Order(5)
  @DisplayName("Test 5: System tenant deactivation blocked → 400 RFC 9457")
  void test05_system_tenant_deactivation_blocked() {
    log.info("=== Test 5: System tenant protection ===");

    // Act: Attempt to deactivate the system tenant (ID 1)
    configureForAdminApi(dockerStackConfig);
    Response response =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + globalAdminToken)
            .when()
            .post("/tenants/1/deactivate")
            .then()
            .extract()
            .response();

    // Assert
    log.info("Response status: {}", response.statusCode());
    log.info("Response body: {}", response.asString());

    assertThat(response.statusCode())
        .as("System tenant deactivation should return 400")
        .isEqualTo(400);

    // Validate RFC 9457 ProblemDetail structure
    String type = response.jsonPath().getString("type");
    assertThat(type)
        .as("ProblemDetail type should indicate tenant-not-allowed")
        .isEqualTo("https://ezkey.io/problems/tenant-not-allowed");

    String title = response.jsonPath().getString("title");
    assertThat(title)
        .as("ProblemDetail title should describe the error")
        .isEqualTo("Tenant Operation Not Allowed");

    Integer status = response.jsonPath().getInt("status");
    assertThat(status).as("ProblemDetail status should be 400").isEqualTo(400);

    String detail = response.jsonPath().getString("detail");
    assertThat(detail)
        .as("ProblemDetail detail should mention system tenant")
        .containsIgnoringCase("system tenant");

    log.info("✅ Test 5 PASSED: System tenant protected with RFC 9457 ProblemDetail");
  }

  /**
   * Test 6: Deactivation is idempotent — deactivating an already inactive tenant returns 204.
   *
   * <p>Uses the tenant deactivated in test 1. Re-deactivating should succeed silently.
   */
  @Test
  @Order(6)
  @DisplayName("Test 6: Idempotent deactivation of already inactive tenant → 204")
  void test06_idempotent_deactivation() {
    log.info("=== Test 6: Idempotent deactivation ===");

    // Guard: ensure test 1 ran and tenant is already inactive
    assertThat(deactivationTenantId).as("Tenant ID from Test 1 should be available").isNotNull();

    // Act: Deactivate again
    configureForAdminApi(dockerStackConfig);
    Response response =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + globalAdminToken)
            .when()
            .post("/tenants/" + deactivationTenantId + "/deactivate")
            .then()
            .extract()
            .response();

    // Assert
    log.info("Response status: {}", response.statusCode());
    assertThat(response.statusCode())
        .as("Re-deactivation should return 204 (idempotent)")
        .isEqualTo(204);

    log.info("✅ Test 6 PASSED: Idempotent deactivation returns 204");
  }

  /**
   * Test 7: TenantAdmin cannot deactivate any tenant (authorization boundary).
   *
   * <p>Creates a separate active tenant with a TenantAdmin, then verifies that the TenantAdmin
   * cannot call the deactivation endpoint — it requires GlobalAdmin role.
   */
  @Test
  @Order(7)
  @DisplayName("Test 7: TenantAdmin cannot deactivate tenant → 403")
  void test07_tenantAdmin_cannot_deactivate_tenant() {
    log.info("=== Test 7: TenantAdmin authorization boundary ===");

    // Arrange: Create a separate active tenant with TenantAdmin
    configureForAdminApi(dockerStackConfig);
    String authzSuffix = String.valueOf(System.currentTimeMillis());
    Integer authzTenantId = testDataFactory.createTenant("AuthZ " + authzSuffix, globalAdminToken);

    String authzTenantAdminToken =
        tenantAdminTestHelper.createAndLoginTenantAdmin(
            "ta-z-" + authzSuffix, authzTenantId, globalAdminToken);
    log.info("Created TenantAdmin for authorization test in tenant {}", authzTenantId);

    // Act: TenantAdmin attempts to deactivate the tenant
    configureForAdminApi(dockerStackConfig);
    Response response =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + authzTenantAdminToken)
            .when()
            .post("/tenants/" + authzTenantId + "/deactivate")
            .then()
            .extract()
            .response();

    // Assert
    log.info("Response status: {}", response.statusCode());
    assertThat(response.statusCode())
        .as("TenantAdmin should not be able to deactivate a tenant (403)")
        .isEqualTo(403);

    // Verify the tenant is still active (deactivation did not happen)
    configureForAdminApi(dockerStackConfig);
    Response verifyResponse =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + globalAdminToken)
            .when()
            .get("/tenants/" + authzTenantId)
            .then()
            .extract()
            .response();

    Boolean stillActive = verifyResponse.jsonPath().getBoolean("active");
    assertThat(stillActive)
        .as("Tenant should still be active after failed TenantAdmin deactivation attempt")
        .isTrue();

    log.info("✅ Test 7 PASSED: TenantAdmin correctly denied deactivation access");
  }
}
