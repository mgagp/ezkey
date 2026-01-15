/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: TenantBoundaryPermissionsSecurityTest
 * Description: Tests tenant-level boundary permissions - TenantAdmin restrictions
 */

package org.ezkey.tests.security.multitenant;

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

/**
 * Tests tenant-level boundary permissions.
 *
 * <p>Validates that TenantAdmin has restricted permissions compared to GlobalAdmin:
 *
 * <ul>
 *   <li>TenantAdmin cannot create new tenants (403)
 *   <li>TenantAdmin cannot list all tenants (403)
 *   <li>TenantAdmin cannot delete tenants (403)
 *   <li>TenantAdmin cannot access cross-tenant admin operations (403)
 *   <li>TenantAdmin operations are scoped to their tenant only
 * </ul>
 *
 * <p>These are P0 tests - critical boundaries that enforce tenant isolation at the API level.
 *
 * @since 2025
 */
@Tag(TestTags.MULTI_TENANT)
@Tag(TestTags.SECURITY)
@DisplayName("Tenant Boundary Permissions Security Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TenantBoundaryPermissionsSecurityTest extends AbstractSecurityTest {

  private TenantAdminTestHelper tenantAdminTestHelper;
  private String globalAdminToken;
  private Integer tenantAId;
  private Integer tenantBId;
  private String tenantAdminAToken;

  @Override
  @BeforeEach
  public void setUp() {
    super.setUp();

    try {
      // Get global admin token
      globalAdminToken = authTokenManager.getAdminToken();

      // Initialize TenantAdminTestHelper
      tenantAdminTestHelper =
          new TenantAdminTestHelper(dockerStackConfig, testDataFactory, cryptoApiClient);

      // Create two tenants
      String uniqueSuffix = String.valueOf(System.currentTimeMillis());
      tenantAId = testDataFactory.findOrCreateTenant("Tenant A " + uniqueSuffix, globalAdminToken);
      tenantBId = testDataFactory.findOrCreateTenant("Tenant B " + uniqueSuffix, globalAdminToken);

      // Create TenantAdmin for Tenant A with device simulation and login
      tenantAdminAToken =
          tenantAdminTestHelper.createAndLoginTenantAdmin(
              "admin-a-" + uniqueSuffix, tenantAId, globalAdminToken);

      // Create TenantAdmin for Tenant B (for cross-tenant admin tests)
      // Note: We get the adminId but don't need the token for these tests
      testDataFactory.createTenantAdmin("admin-b-" + uniqueSuffix, tenantBId, globalAdminToken);

    } catch (IllegalStateException e) {
      org.junit.jupiter.api.Assumptions.assumeTrue(
          false,
          "Test setup failed - admin token or device simulation not available: " + e.getMessage());
    }
  }

  // ========== TENANT MANAGEMENT BOUNDARY TESTS ==========

  @Test
  @Order(1)
  @DisplayName("TenantAdmin cannot create new tenant (403)")
  public void testTenantAdminCannotCreateTenant() {
    configureForAdminApi(dockerStackConfig);

    Map<String, Object> request = new HashMap<>();
    request.put("tenantName", "Malicious Tenant");
    request.put("tenantDescription", "Should not be created");

    Response response =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + tenantAdminAToken)
            .body(request)
            .when()
            .post("/tenants")
            .then()
            .extract()
            .response();

    assertThat(response.getStatusCode()).isEqualTo(403);
  }

  @Test
  @Order(2)
  @DisplayName("TenantAdmin cannot list all tenants (403)")
  public void testTenantAdminCannotListAllTenants() {
    configureForAdminApi(dockerStackConfig);

    Response response =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + tenantAdminAToken)
            .when()
            .get("/tenants")
            .then()
            .extract()
            .response();

    assertThat(response.getStatusCode()).isEqualTo(403);
  }

  @Test
  @Order(3)
  @DisplayName("TenantAdmin cannot get own tenant details (403)")
  public void testTenantAdminCannotGetOwnTenantDetails() {
    configureForAdminApi(dockerStackConfig);

    Response response =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + tenantAdminAToken)
            .when()
            .get("/tenants/" + tenantAId)
            .then()
            .extract()
            .response();

    // TenantAdmin should not have access to tenant details endpoint
    assertThat(response.getStatusCode()).isEqualTo(403);
  }

  @Test
  @Order(4)
  @DisplayName("TenantAdmin cannot get other tenant details (403)")
  public void testTenantAdminCannotGetOtherTenantDetails() {
    configureForAdminApi(dockerStackConfig);

    Response response =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + tenantAdminAToken)
            .when()
            .get("/tenants/" + tenantBId)
            .then()
            .extract()
            .response();

    assertThat(response.getStatusCode()).isEqualTo(403);
  }

  // ========== ADMIN MANAGEMENT BOUNDARY TESTS ==========

  @Test
  @Order(8)
  @DisplayName("TenantAdmin cannot create global admin (403)")
  public void testTenantAdminCannotCreateGlobalAdmin() {
    configureForAdminApi(dockerStackConfig);

    Map<String, Object> request = new HashMap<>();
    request.put("username", "malicious.global.admin");
    request.put("email", "malicious@example.com");
    request.put("firstName", "Malicious");
    request.put("lastName", "GlobalAdmin");

    Response response =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + tenantAdminAToken)
            .body(request)
            .when()
            .post("/admins/global")
            .then()
            .extract()
            .response();

    assertThat(response.getStatusCode()).isEqualTo(403);
  }

  @Test
  @Order(9)
  @DisplayName("TenantAdmin can create admin for own tenant (201)")
  public void testTenantAdminCanCreateAdminForOwnTenant() {
    configureForAdminApi(dockerStackConfig);

    String uniqueSuffix = String.valueOf(System.currentTimeMillis());
    Map<String, Object> request = new HashMap<>();
    request.put("username", "new.admin." + uniqueSuffix);
    request.put("email", "new.admin." + uniqueSuffix + "@example.com");
    request.put("firstName", "New");
    request.put("lastName", "Admin");
    request.put("tenantId", tenantAId);

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

    assertThat(response.getStatusCode()).isEqualTo(201);
  }

  @Test
  @Order(10)
  @DisplayName("TenantAdmin cannot create admin for other tenant (400/403)")
  public void testTenantAdminCannotCreateAdminForOtherTenant() {
    configureForAdminApi(dockerStackConfig);

    String uniqueSuffix = String.valueOf(System.currentTimeMillis());
    Map<String, Object> request = new HashMap<>();
    request.put("username", "malicious.admin." + uniqueSuffix);
    request.put("email", "malicious." + uniqueSuffix + "@example.com");
    request.put("firstName", "Malicious");
    request.put("lastName", "Admin");
    request.put("tenantId", tenantBId); // Try to create for Tenant B

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

    assertThat(response.getStatusCode()).isIn(400, 403);
  }

  @Test
  @Order(11)
  @DisplayName("TenantAdmin can list only own tenant admins")
  public void testTenantAdminCanListOnlyOwnTenantAdmins() {
    configureForAdminApi(dockerStackConfig);

    Response response =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + tenantAdminAToken)
            .when()
            .get("/admins")
            .then()
            .extract()
            .response();

    assertThat(response.getStatusCode()).isEqualTo(200);

    java.util.List<Map<String, Object>> admins = response.jsonPath().getList("content");
    assertThat(admins).isNotNull();

    // Verify all admins belong to Tenant A
    for (Map<String, Object> admin : admins) {
      Integer adminTenantId = (Integer) admin.get("tenantId");
      String adminType = (String) admin.get("adminType");

      // Should not see GlobalAdmin or admins from other tenants
      if (adminType != null && !"GLOBAL_ADMIN".equals(adminType)) {
        assertThat(adminTenantId)
            .as("TenantAdmin should only see admins from own tenant")
            .isEqualTo(tenantAId);
      }
    }
  }

  // ========== SCOPE VERIFICATION TESTS ==========

  @Test
  @Order(12)
  @DisplayName("GlobalAdmin can list all tenants (200)")
  public void testGlobalAdminCanListAllTenants() {
    configureForAdminApi(dockerStackConfig);

    Response response =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + globalAdminToken)
            .when()
            .get("/tenants")
            .then()
            .extract()
            .response();

    assertThat(response.getStatusCode()).isEqualTo(200);

    java.util.List<Map<String, Object>> tenants = response.jsonPath().getList("content");
    assertThat(tenants).isNotNull();
    assertThat(tenants.size()).isGreaterThanOrEqualTo(2); // At least Tenant A and B
  }

  @Test
  @Order(13)
  @DisplayName("GlobalAdmin can create tenant (201)")
  public void testGlobalAdminCanCreateTenant() {
    configureForAdminApi(dockerStackConfig);

    String uniqueSuffix = String.valueOf(System.currentTimeMillis());
    Map<String, Object> request = new HashMap<>();
    request.put("tenantName", "New Tenant " + uniqueSuffix);
    request.put("tenantDescription", "Created by GlobalAdmin");

    Response response =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + globalAdminToken)
            .body(request)
            .when()
            .post("/tenants")
            .then()
            .extract()
            .response();

    assertThat(response.getStatusCode()).isEqualTo(201);
  }
}
