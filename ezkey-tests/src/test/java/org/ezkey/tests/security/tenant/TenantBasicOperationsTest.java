/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test Class: TenantBasicOperationsTest
 * Description: Basic CRUD tests for tenant management using GlobalAdmin authentication.
 */

package org.ezkey.tests.security.tenant;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.HashMap;
import java.util.Map;
import org.ezkey.tests.security.AbstractSecurityTest;
import org.ezkey.tests.tags.TestTags;
import org.ezkey.tests.util.RestAssuredTestConfig;
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
 * Test suite for basic tenant CRUD operations.
 *
 * <p>These tests validate the fundamental tenant management API endpoints using GlobalAdmin
 * authentication. This is Phase 1 testing focusing on data model validation and basic CRUD
 * operations.
 *
 * <p><b>Test Scope (Phase 1):</b>
 *
 * <ul>
 *   <li>Tenant creation with GlobalAdmin token
 *   <li>Tenant retrieval by ID
 *   <li>Tenant listing
 *   <li>Response field validation
 * </ul>
 *
 * <p><b>Not Tested (Phase 3):</b>
 *
 * <ul>
 *   <li>Cross-tenant access denial (requires TenantAdmin auth)
 *   <li>Authorization boundaries
 *   <li>TenantAdmin RBAC
 * </ul>
 *
 * <p><b>Authentication:</b> Uses GlobalAdmin token from AuthTokenManager
 *
 * <p><b>Prerequisites:</b> Docker stack must be running with admin API on port 9080
 *
 * @author Ezkey contributors
 * @since 2025
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Tag(TestTags.ADMIN)
@DisplayName("Tenant Basic Operations (GlobalAdmin)")
public class TenantBasicOperationsTest extends AbstractSecurityTest {

  private static final Logger log = LoggerFactory.getLogger(TenantBasicOperationsTest.class);

  private Integer createdTenantId;

  @BeforeEach
  public void setUp() {
    super.setUp();
    log.info("Configuring for Admin API");
    RestAssuredTestConfig.configureForAdminApi(dockerStackConfig);
  }

  /**
   * Test 1: Create Tenant and Validate Response
   *
   * <p>Validates that a GlobalAdmin can create a new tenant and receives a proper response with:
   *
   * <ul>
   *   <li>HTTP 201 Created status
   *   <li>Location header with tenant URI
   *   <li>Response body containing tenantId
   *   <li>Response body containing all expected fields (tenantName, tenantDescription, createdAt,
   *       active)
   * </ul>
   */
  @Test
  @Order(1)
  @DisplayName("Test 1: GlobalAdmin can create tenant and response contains tenantId")
  void test01_create_tenant_returns_valid_response() {
    log.info("=== Test 1: Create Tenant ===");

    // Arrange: Prepare tenant creation request
    String uniqueTenantName = "Test Tenant " + System.currentTimeMillis();
    Map<String, Object> request = new HashMap<>();
    request.put("tenantName", uniqueTenantName);
    request.put("tenantDescription", "Test tenant for functional validation");

    // Act: Create tenant using GlobalAdmin token
    Response response =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + authTokenManager.getAdminToken())
            .body(request)
            .when()
            .post("/tenants")
            .then()
            .extract()
            .response();

    // Assert: Verify response
    log.info("Response status: {}", response.statusCode());
    log.info("Response body: {}", response.asString());

    assertThat(response.statusCode()).as("Expected HTTP 201 Created").isEqualTo(201);

    // Verify Location header
    String locationHeader = response.getHeader("Location");
    assertThat(locationHeader).as("Location header should be present").isNotNull();
    log.info("Location header: {}", locationHeader);

    // Verify response body contains tenantId
    Integer tenantId = response.jsonPath().getInt("tenantId");
    assertThat(tenantId).as("Response should contain tenantId").isNotNull().isGreaterThan(0);
    log.info("Created tenant ID: {}", tenantId);

    // Store for next test
    createdTenantId = tenantId;

    // Verify other fields
    String responseTenantName = response.jsonPath().getString("tenantName");
    assertThat(responseTenantName)
        .as("Tenant name should match request")
        .isEqualTo(uniqueTenantName);

    String responseTenantDescription = response.jsonPath().getString("tenantDescription");
    assertThat(responseTenantDescription)
        .as("Tenant description should match request")
        .isEqualTo("Test tenant for functional validation");

    Boolean active = response.jsonPath().getBoolean("active");
    assertThat(active).as("New tenant should be active by default").isTrue();

    String createdAt = response.jsonPath().getString("createdAt");
    assertThat(createdAt).as("createdAt timestamp should be present").isNotNull();

    log.info("✅ Test 1 PASSED: Tenant created successfully with all expected fields");
  }

  /**
   * Test 2: Retrieve Tenant by ID
   *
   * <p>Validates that a GlobalAdmin can retrieve a tenant by ID and the response matches the
   * created tenant.
   */
  @Test
  @Order(2)
  @DisplayName("Test 2: GlobalAdmin can retrieve tenant by ID")
  void test02_get_tenant_by_id() {
    log.info("=== Test 2: Get Tenant by ID ===");

    // Arrange: Use tenant created in Test 1
    // If Test 1 didn't run, create a tenant now
    if (createdTenantId == null) {
      log.warn("createdTenantId is null, creating tenant first");
      test01_create_tenant_returns_valid_response();
    }

    assertThat(createdTenantId).as("Tenant ID from Test 1 should be available").isNotNull();

    // Act: Retrieve tenant by ID
    Response response =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + authTokenManager.getAdminToken())
            .when()
            .get("/tenants/" + createdTenantId)
            .then()
            .extract()
            .response();

    // Assert: Verify response
    log.info("Response status: {}", response.statusCode());
    log.info("Response body: {}", response.asString());

    assertThat(response.statusCode()).as("Expected HTTP 200 OK").isEqualTo(200);

    // Verify tenantId matches
    Integer retrievedTenantId = response.jsonPath().getInt("tenantId");
    assertThat(retrievedTenantId)
        .as("Retrieved tenant ID should match created tenant ID")
        .isEqualTo(createdTenantId);

    // Verify all fields are present
    assertThat(response.jsonPath().getString("tenantName")).isNotNull();
    assertThat(response.jsonPath().getString("tenantDescription")).isNotNull();
    assertThat(response.jsonPath().getString("createdAt")).isNotNull();
    assertThat(response.jsonPath().getBoolean("active")).isNotNull();

    log.info("✅ Test 2 PASSED: Tenant retrieved successfully");
  }

  /**
   * Test 3: List All Tenants
   *
   * <p>Validates that a GlobalAdmin can list all tenants and the response is a valid array
   * containing tenant objects.
   */
  @Test
  @Order(3)
  @DisplayName("Test 3: GlobalAdmin can list all tenants")
  void test03_list_all_tenants() {
    log.info("=== Test 3: List All Tenants ===");

    // Act: List all tenants
    Response response =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + authTokenManager.getAdminToken())
            .when()
            .get("/tenants")
            .then()
            .extract()
            .response();

    // Assert: Verify response
    log.info("Response status: {}", response.statusCode());
    log.info(
        "Response body (first 500 chars): {}",
        response.asString().substring(0, Math.min(500, response.asString().length())));

    assertThat(response.statusCode()).as("Expected HTTP 200 OK").isEqualTo(200);

    // Verify response is an array
    assertThat(response.jsonPath().getList("$"))
        .as("Response should be an array of tenants")
        .isNotNull()
        .isNotEmpty();

    // Verify the created tenant is in the list
    if (createdTenantId != null) {
      boolean foundCreatedTenant =
          response.jsonPath().getList("tenantId", Integer.class).contains(createdTenantId);
      assertThat(foundCreatedTenant).as("Created tenant should appear in the list").isTrue();
    }

    log.info("✅ Test 3 PASSED: Tenants listed successfully");
  }
}
