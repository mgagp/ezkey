/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test Class: TenantDeactivationSecurityTest
 * Description: Functional tests for tenant deactivation validation, specifically testing the
 * system tenant protection mechanism.
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
 * Test suite for tenant deactivation validation.
 *
 * <p>These tests validate that the system tenant protection mechanism works correctly and prevents
 * deactivation of the system tenant while allowing deactivation of application tenants.
 *
 * <p><b>Test Scope:</b>
 *
 * <ul>
 *   <li>Verify system tenant (tenant_id=1) cannot be deactivated
 *   <li>Verify application tenants can be deactivated successfully
 *   <li>Validate error messages and HTTP status codes
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
@DisplayName("Tenant Deactivation Security (GlobalAdmin)")
public class TenantDeactivationSecurityTest extends AbstractSecurityTest {

  private static final Logger log = LoggerFactory.getLogger(TenantDeactivationSecurityTest.class);

  private Integer applicationTenantId;

  @BeforeEach
  public void setUp() {
    super.setUp();
    log.info("Configuring for Admin API");
    RestAssuredTestConfig.configureForAdminApi(dockerStackConfig);
  }

  /**
   * Test 1: Create Application Tenant for Testing
   *
   * <p>Creates an application tenant that will be used for testing successful deactivation.
   */
  @Test
  @Order(1)
  @DisplayName("Test 1: Create application tenant for deactivation testing")
  void test01_create_application_tenant() {
    log.info("=== Test 1: Create Application Tenant ===");

    // Arrange: Prepare tenant creation request
    String uniqueTenantName = "Test Tenant Deactivation " + System.currentTimeMillis();
    Map<String, Object> request = new HashMap<>();
    request.put("tenantName", uniqueTenantName);
    request.put("tenantDescription", "Test tenant for deactivation validation");

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

    // Store tenant ID for next tests
    applicationTenantId = response.jsonPath().getInt("tenantId");
    assertThat(applicationTenantId).as("Tenant ID should be present").isNotNull().isGreaterThan(0);

    log.info("✅ Test 1 PASSED: Application tenant created with ID {}", applicationTenantId);
  }

  /**
   * Test 2: Verify System Tenant Cannot Be Deactivated
   *
   * <p>Validates that attempting to deactivate the system tenant (tenant_id=1) results in HTTP 400
   * Bad Request with an appropriate error message.
   */
  @Test
  @Order(2)
  @DisplayName("Test 2: System tenant (ID=1) cannot be deactivated")
  void test02_system_tenant_cannot_be_deactivated() {
    log.info("=== Test 2: Attempt to Deactivate System Tenant ===");

    // Act: Attempt to deactivate system tenant (tenant_id=1)
    Response response =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + authTokenManager.getAdminToken())
            .when()
            .post("/tenants/1/deactivate")
            .then()
            .extract()
            .response();

    // Assert: Verify response
    log.info("Response status: {}", response.statusCode());
    log.info("Response body: {}", response.asString());

    assertThat(response.statusCode())
        .as("Expected HTTP 400 Bad Request for system tenant deactivation")
        .isEqualTo(400);

    // Verify error response structure
    String errorCode = response.jsonPath().getString("code");
    assertThat(errorCode)
        .as("Error code should indicate system tenant deactivation not allowed")
        .isEqualTo("SYSTEM_TENANT_DEACTIVATION_NOT_ALLOWED");

    String errorMessage = response.jsonPath().getString("message");
    assertThat(errorMessage)
        .as("Error message should explain system tenant cannot be deactivated")
        .contains("Cannot deactivate system tenant");

    log.info("✅ Test 2 PASSED: System tenant deactivation properly blocked with error: {}", errorMessage);
  }

  /**
   * Test 3: Verify Application Tenant Can Be Deactivated
   *
   * <p>Validates that application tenants (non-system tenants) can be deactivated successfully.
   */
  @Test
  @Order(3)
  @DisplayName("Test 3: Application tenant can be deactivated successfully")
  void test03_application_tenant_can_be_deactivated() {
    log.info("=== Test 3: Deactivate Application Tenant ===");

    // Arrange: Ensure we have an application tenant from Test 1
    if (applicationTenantId == null) {
      log.warn("applicationTenantId is null, creating tenant first");
      test01_create_application_tenant();
    }

    assertThat(applicationTenantId)
        .as("Application tenant ID from Test 1 should be available")
        .isNotNull();

    // Act: Deactivate application tenant
    Response response =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + authTokenManager.getAdminToken())
            .when()
            .post("/tenants/" + applicationTenantId + "/deactivate")
            .then()
            .extract()
            .response();

    // Assert: Verify response
    log.info("Response status: {}", response.statusCode());

    assertThat(response.statusCode())
        .as("Expected HTTP 204 No Content for successful deactivation")
        .isEqualTo(204);

    log.info("✅ Test 3 PASSED: Application tenant deactivated successfully");

    // Verify tenant is actually deactivated by fetching it
    Response getTenantResponse =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + authTokenManager.getAdminToken())
            .when()
            .get("/tenants/" + applicationTenantId)
            .then()
            .extract()
            .response();

    log.info("Get tenant response status: {}", getTenantResponse.statusCode());
    log.info("Get tenant response body: {}", getTenantResponse.asString());

    assertThat(getTenantResponse.statusCode()).as("Expected HTTP 200 OK").isEqualTo(200);

    Boolean isActive = getTenantResponse.jsonPath().getBoolean("active");
    assertThat(isActive).as("Tenant should be inactive after deactivation").isFalse();

    log.info("✅ Verified: Tenant active flag is now false");
  }
}
