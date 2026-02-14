/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: MultiTenantGlobalAdminTest
 * Description: Phase 1 multi-tenant isolation tests using GlobalAdmin operations
 */

package org.ezkey.tests.security.tenant;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ezkey.tests.security.AbstractSecurityTest;
import org.ezkey.tests.tags.TestTags;
import org.ezkey.tests.util.RestAssuredTestConfig;
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

import io.restassured.http.ContentType;
import io.restassured.response.Response;

/**
 * Phase 1 Multi-Tenant Isolation Tests using GlobalAdmin.
 *
 * <p>
 * <b>Scope:</b> Tests GlobalAdmin visibility behavior. Validates that
 * GlobalAdmin can view all
 * resources across all tenants while respecting the "No Impersonation" rule for
 * resource creation.
 *
 * <p>
 * <b>Multi-Tenant Philosophy:</b> See {@code reference/MULTI_TENANT.md} for
 * complete context.
 *
 * <ul>
 * <li><b>Key Principle:</b> GlobalAdmin VIEWS all resources but CREATES only in
 * System Tenant
 * <li><b>Read Operations:</b> GlobalAdmin sees ALL tenants (no filtering)
 * <li><b>Write Operations:</b> GlobalAdmin creates in System Tenant (tenantId:
 * 1) ONLY
 * <li><b>No Impersonation:</b> Cannot create resources "on behalf of"
 * TenantAdmin
 * </ul>
 *
 * <p>
 * <b>Test Pattern:</b>
 *
 * <ol>
 * <li>Create multiple tenants (A, B, C)
 * <li>Create TenantAdmin accounts for each tenant
 * <li>TenantAdmins create resources in their respective tenants
 * <li>Verify GlobalAdmin can VIEW all resources across all tenants
 * <li>Verify tenant_id is properly set in responses
 * <li>Verify data isolation at query level (by listing resources)
 * </ol>
 *
 * <p>
 * <b>Expected GlobalAdmin Behavior:</b>
 *
 * <table>
 * <tr>
 * <th>Operation</th>
 * <th>GlobalAdmin</th>
 * <th>TenantAdmin A</th>
 * </tr>
 * <tr>
 * <td>GET /integrations</td>
 * <td>All tenants (A, B, C)</td>
 * <td>Only Tenant A</td>
 * </tr>
 * <tr>
 * <td>POST /integrations</td>
 * <td>System Tenant (ID: 1)</td>
 * <td>Tenant A</td>
 * </tr>
 * <tr>
 * <td>GET /api-keys</td>
 * <td>All tenants (A, B, C)</td>
 * <td>Only Tenant A</td>
 * </tr>
 * <tr>
 * <td>GET /tenants</td>
 * <td>All tenants</td>
 * <td>Own tenant only</td>
 * </tr>
 * </table>
 *
 * <p>
 * <b>Why GlobalAdmin Tests?</b> These tests validate:
 *
 * <ul>
 * <li>✅ Multi-tenant infrastructure correctly separates data by tenant_id
 * <li>✅ GlobalAdmin maintains full cross-tenant READ access
 * <li>✅ List endpoints return data from all tenants for GlobalAdmin
 * <li>✅ Tenant filtering works correctly (TenantAdmin path tested in {@link
 * org.ezkey.tests.security.multitenant.TenantCrossIsolationSecurityTest})
 * </ul>
 *
 * <p>
 * <b>Complementary Tests:</b> See {@link
 * org.ezkey.tests.security.multitenant.TenantCrossIsolationSecurityTest} for
 * TenantAdmin isolation
 * tests.
 *
 * @see org.ezkey.tests.security.multitenant.TenantCrossIsolationSecurityTest
 * @see <a href="../../../reference/MULTI_TENANT.md">Multi-Tenant Philosophy</a>
 * @see <a href="../../AGENTS.md">Agent Quick Reference</a>
 */
@DisplayName("Multi-Tenant Global Admin Tests - Phase 1")
@Tag(TestTags.ADMIN)
@Tag(TestTags.INTEGRATION)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MultiTenantGlobalAdminTest extends AbstractSecurityTest {

  private static final Logger log = LoggerFactory.getLogger(MultiTenantGlobalAdminTest.class);

  private String uniqueSuffix;
  private Integer tenantAId;
  private Integer tenantBId;
  private Integer tenantCId;

  private String tenantAdminAToken;
  private String tenantAdminBToken;
  private String tenantAdminCToken;

  private Integer integrationA1Id;
  private Integer integrationA2Id;
  private Integer integrationBId;
  private Integer integrationCId;

  @BeforeEach
  void setUpMultiTenantResources() {
    super.setUp();
    log.info("Setting up multi-tenant test resources");

    // Create three distinct tenants
    uniqueSuffix = String.valueOf(System.currentTimeMillis());

    tenantAId = createTenant("Tenant A " + uniqueSuffix);
    tenantBId = createTenant("Tenant B " + uniqueSuffix);
    tenantCId = createTenant("Tenant C " + uniqueSuffix);

    log.debug("Created tenants: A={}, B={}, C={}", tenantAId, tenantBId, tenantCId);

    // Create TenantAdmins for each tenant and get their tokens
    TenantAdminTestHelper tenantAdminHelper = new TenantAdminTestHelper(dockerStackConfig, testDataFactory,
        cryptoApiClient);
    String globalAdminToken = authTokenManager.getAdminToken();
    tenantAdminAToken = tenantAdminHelper.createAndLoginTenantAdmin(
        "admin-a-" + uniqueSuffix, tenantAId, globalAdminToken);
    tenantAdminBToken = tenantAdminHelper.createAndLoginTenantAdmin(
        "admin-b-" + uniqueSuffix, tenantBId, globalAdminToken);
    tenantAdminCToken = tenantAdminHelper.createAndLoginTenantAdmin(
        "admin-c-" + uniqueSuffix, tenantCId, globalAdminToken);

    log.debug("Created TenantAdmins with tokens for all three tenants");

    // Create integrations for each tenant using TenantAdmin tokens
    integrationA1Id = createIntegrationForTenant("Integration A1 " + uniqueSuffix, tenantAdminAToken);
    integrationA2Id = createIntegrationForTenant("Integration A2 " + uniqueSuffix, tenantAdminAToken);
    integrationBId = createIntegrationForTenant("Integration B " + uniqueSuffix, tenantAdminBToken);
    integrationCId = createIntegrationForTenant("Integration C " + uniqueSuffix, tenantAdminCToken);

    log.debug(
        "Created integrations: A1={}, A2={}, B={}, C={}",
        integrationA1Id,
        integrationA2Id,
        integrationBId,
        integrationCId);
  }

  // ============================================================================
  // P0 CRITICAL: Data Isolation - Verify tenant_id is properly set
  // ============================================================================

  @Test
  @Order(1)
  @DisplayName("P0: Integration for Tenant A has correct tenant_id")
  void integration_tenantA_has_correct_tenantId() {
    Response response = getIntegration(integrationA1Id);
    log.info("Integration response for A1: {}", response.asString());

    assertThat(response.statusCode()).isEqualTo(200);
    Integer tenantIdValue = response.jsonPath().getInt("tenantId");
    log.info("TenantId value: {}", tenantIdValue);
    assertThat(tenantIdValue).isEqualTo(tenantAId);
    assertThat(response.jsonPath().getInt("id")).isEqualTo(integrationA1Id);
  }

  @Test
  @Order(2)
  @DisplayName("P0: Integration for Tenant B has correct tenant_id")
  void integration_tenantB_has_correct_tenantId() {
    Response response = getIntegration(integrationBId);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.jsonPath().getInt("tenantId")).isEqualTo(tenantBId);
    assertThat(response.jsonPath().getInt("id")).isEqualTo(integrationBId);
  }

  @Test
  @Order(3)
  @DisplayName("P0: Integration for Tenant C has correct tenant_id")
  void integration_tenantC_has_correct_tenantId() {
    Response response = getIntegration(integrationCId);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.jsonPath().getInt("tenantId")).isEqualTo(tenantCId);
    assertThat(response.jsonPath().getInt("id")).isEqualTo(integrationCId);
  }

  // ============================================================================
  // P1: GlobalAdmin Access - Verify full access across all tenants
  // ============================================================================

  @Test
  @Order(10)
  @DisplayName("P1: GlobalAdmin can read all integrations across tenants")
  void globalAdmin_can_read_all_integrations() {
    // Production-like tests accumulate data over time, so never rely on default
    // pagination.
    // Scope this query to this test run via the unique suffix embedded in
    // integration names.
    RestAssuredTestConfig.configureForAdminApi(dockerStackConfig);

    Response response = given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + authTokenManager.getAdminToken())
        .queryParam("page", 0)
        .queryParam("size", 100)
        .queryParam("sort", "id,ASC")
        .queryParam("integrationName", uniqueSuffix)
        .when()
        .get("/integrations")
        .then()
        .statusCode(200)
        .extract()
        .response();

    List<Integer> integrationIds = response.jsonPath().getList("content.id", Integer.class);
    assertThat(integrationIds).isNotNull();
    assertThat(integrationIds)
        .contains(integrationA1Id, integrationA2Id, integrationBId, integrationCId);

    // Pagination contract sanity (exact shape may vary by JSON serializer).
    Number totalElements = response.jsonPath().getObject("totalElements", Number.class);
    if (totalElements == null) {
      totalElements = response.jsonPath().getObject("page.totalElements", Number.class);
    }
    assertThat(totalElements).isNotNull();
    assertThat(totalElements.longValue()).isGreaterThanOrEqualTo(4L);
  }

  @Test
  @Order(12)
  @DisplayName("P1: GlobalAdmin can delete integration from any tenant")
  void globalAdmin_can_delete_integration_from_any_tenant() {
    Integer integrationToDeleteId = createIntegrationForTenant("Temp Integration", tenantAdminAToken);

    deleteIntegration(integrationToDeleteId);

    Response response = given()
        .header("Authorization", "Bearer " + authTokenManager.getAdminToken())
        .get("/integrations/" + integrationToDeleteId);

    assertThat(response.statusCode()).isIn(404, 403);
  }

  // ============================================================================
  // P1: Tenant Isolation - Verify multiple integrations per tenant
  // ============================================================================

  @Test
  @Order(20)
  @DisplayName("P1: Tenant A has two integrations with correct tenant_id")
  void tenant_A_has_two_integrations_with_correct_tenant_ids() {
    Response response1 = getIntegration(integrationA1Id);
    Response response2 = getIntegration(integrationA2Id);

    assertThat(response1.jsonPath().getInt("tenantId")).isEqualTo(tenantAId);
    assertThat(response2.jsonPath().getInt("tenantId")).isEqualTo(tenantAId);

    // Verify both have correct IDs
    assertThat(response1.jsonPath().getInt("id")).isEqualTo(integrationA1Id);
    assertThat(response2.jsonPath().getInt("id")).isEqualTo(integrationA2Id);
  }

  @Test
  @Order(21)
  @DisplayName("P1: Different tenants have different integration IDs")
  void different_tenants_have_different_integration_ids() {
    Response responseA = getIntegration(integrationA1Id);
    Response responseB = getIntegration(integrationBId);
    Response responseC = getIntegration(integrationCId);

    Integer tenantIdA = responseA.jsonPath().getInt("tenantId");
    Integer tenantIdB = responseB.jsonPath().getInt("tenantId");
    Integer tenantIdC = responseC.jsonPath().getInt("tenantId");

    // Verify all three tenants are distinct
    assertThat(tenantIdA).isNotEqualTo(tenantIdB);
    assertThat(tenantIdB).isNotEqualTo(tenantIdC);
    assertThat(tenantIdA).isNotEqualTo(tenantIdC);
  }

  // ============================================================================
  // P1: Tenant CRUD - Verify tenant creation and listing
  // ============================================================================

  @Test
  @Order(30)
  @DisplayName("P1: Can create and retrieve tenant")
  void can_create_and_retrieve_tenant() {
    String tenantName = "New Tenant " + System.currentTimeMillis();

    Integer newTenantId = createTenant(tenantName);

    assertThat(newTenantId).isNotNull().isGreaterThan(0);

    Response response = getTenant(newTenantId);
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.jsonPath().getString("tenantName")).isEqualTo(tenantName);
  }

  // NOTE: Pagination test - pagination/sorting needs better debugging/fixing
  @Test
  @Order(31)
  @DisplayName("P1: GlobalAdmin can list all tenants")
  void globalAdmin_can_list_all_tenants() {
    // Note: /tenants returns a JSON array (List<TenantResponseDto>), not a paged
    // response.
    Response response = listTenants();
    assertThat(response.statusCode()).isEqualTo(200);

    List<Integer> tenantIds = response.jsonPath().getList("tenantId", Integer.class);
    if (tenantIds == null) {
      // Defensive fallback if the JSONPath extraction expects an explicit root
      // selector.
      tenantIds = response.jsonPath().getList("$..tenantId", Integer.class);
    }
    assertThat(tenantIds).isNotNull().contains(tenantAId, tenantBId, tenantCId);
  }

  // ============================================================================
  // Helper Methods
  // ============================================================================

  private Integer createTenant(String tenantName) {
    RestAssuredTestConfig.configureForAdminApi(dockerStackConfig);

    Map<String, Object> request = new HashMap<>();
    request.put("tenantName", tenantName);
    request.put("tenantDescription", "Test tenant: " + tenantName);

    Response response = given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + authTokenManager.getAdminToken())
        .body(request)
        .when()
        .post("/tenants")
        .then()
        .statusCode(201)
        .extract()
        .response();

    return response.jsonPath().getInt("tenantId");
  }

  private Response getTenant(Integer tenantId) {
    RestAssuredTestConfig.configureForAdminApi(dockerStackConfig);

    return given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + authTokenManager.getAdminToken())
        .when()
        .get("/tenants/" + tenantId);
  }

  private Response listTenants() {
    RestAssuredTestConfig.configureForAdminApi(dockerStackConfig);

    return given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + authTokenManager.getAdminToken())
        .queryParam("size", 100)
        .when()
        .get("/tenants");
  }

  private Integer createIntegrationForTenant(String integrationName, String tenantAdminToken) {
    RestAssuredTestConfig.configureForAdminApi(dockerStackConfig);

    // Generate unique code using UUID
    String code = "test-" + java.util.UUID.randomUUID().toString().substring(0, 8);

    Map<String, Object> i18n = new HashMap<>();
    i18n.put("language", "en");
    i18n.put("name", integrationName);
    i18n.put("description", "Test integration: " + integrationName);

    Map<String, Object> request = new HashMap<>();
    request.put("code", code);
    request.put("logo", "https://example.com/logo.png");
    request.put("i18n", new Object[] { i18n });

    Response response = given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + tenantAdminToken)
        .body(request)
        .when()
        .post("/integrations")
        .then()
        .statusCode(201)
        .extract()
        .response();

    Integer integrationId = response.jsonPath().getInt("id");
    log.debug("Created integration with ID: {} using TenantAdmin token", integrationId);
    return integrationId;
  }

  private Response getIntegration(Integer integrationId) {
    RestAssuredTestConfig.configureForAdminApi(dockerStackConfig);

    return given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + authTokenManager.getAdminToken())
        .when()
        .get("/integrations/" + integrationId);
  }

  @SuppressWarnings("unused")
  private Response listIntegrations() {
    RestAssuredTestConfig.configureForAdminApi(dockerStackConfig);

    return given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + authTokenManager.getAdminToken())
        .queryParam("page", 0)
        .queryParam("size", 100)
        .queryParam("sort", "createdAt,DESC")
        .when()
        .get("/integrations");
  }

  private void deleteIntegration(Integer integrationId) {
    RestAssuredTestConfig.configureForAdminApi(dockerStackConfig);

    given()
        .header("Authorization", "Bearer " + authTokenManager.getAdminToken())
        .when()
        .delete("/integrations/" + integrationId)
        .then()
        .statusCode(204);
  }
}
