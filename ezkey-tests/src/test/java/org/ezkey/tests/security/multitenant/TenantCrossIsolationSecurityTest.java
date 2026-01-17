/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: TenantCrossIsolationSecurityTest
 * Description: Tests cross-tenant isolation - TenantAdmin A cannot access Tenant B resources
 */

package org.ezkey.tests.security.multitenant;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.ezkey.tests.util.RestAssuredTestConfig.configureForAdminApi;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.ezkey.tests.security.AbstractSecurityTest;
import org.ezkey.tests.tags.TestTags;
import org.ezkey.tests.util.DatabaseHelper;
import org.ezkey.tests.util.TenantAdminTestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Tests cross-tenant isolation security.
 *
 * <p>Validates that TenantAdmin from one tenant cannot access resources belonging to another
 * tenant:
 *
 * <ul>
 *   <li>TenantAdmin A cannot list/read/modify integrations of Tenant B
 *   <li>TenantAdmin A cannot list/read/modify enrollments of Tenant B
 *   <li>TenantAdmin A cannot list/read/modify API keys of Tenant B
 *   <li>TenantAdmin A listings only show Tenant A resources
 * </ul>
 *
 * <p>These are P0 tests - critical isolation boundaries that prevent data leakage between tenants.
 *
 * <p><b>Multi-Tenant Philosophy:</b> See {@code reference/MULTI_TENANT.md} for complete context.
 *
 * <ul>
 *   <li><b>Key Principle:</b> "No Impersonation" - TenantAdmin cannot access other tenant resources
 *   <li><b>Read Operations:</b> TenantAdmin sees ONLY own tenant (automatic filtering)
 *   <li><b>Write Operations:</b> TenantAdmin creates ONLY in own tenant (automatic assignment)
 *   <li><b>Cross-Tenant Access:</b> Returns 403 Forbidden or 404 Not Found
 * </ul>
 *
 * <p><b>Test Strategy:</b>
 *
 * <ul>
 *   <li>Create two tenants (A and B) with separate TenantAdmins
 *   <li>Create resources in each tenant (integrations, enrollments, API keys)
 *   <li>Validate TenantAdmin A cannot access Tenant B resources
 *   <li>Validate list endpoints filter automatically (TenantAdmin A sees only Tenant A)
 * </ul>
 *
 * <p><b>Expected Behavior:</b>
 *
 * <table>
 * <tr>
 * <th>Operation</th>
 * <th>TenantAdmin A</th>
 * <th>TenantAdmin B</th>
 * <th>GlobalAdmin</th>
 * </tr>
 * <tr>
 * <td>GET /integrations</td>
 * <td>Only Tenant A</td>
 * <td>Only Tenant B</td>
 * <td>All tenants</td>
 * </tr>
 * <tr>
 * <td>GET /integrations/{B}</td>
 * <td>403/404</td>
 * <td>200 OK</td>
 * <td>200 OK</td>
 * </tr>
 * <tr>
 * <td>GET /api-keys</td>
 * <td>Only Tenant A</td>
 * <td>Only Tenant B</td>
 * <td>All tenants</td>
 * </tr>
 * <tr>
 * <td>GET /api-keys/{B}</td>
 * <td>403/404</td>
 * <td>200 OK</td>
 * <td>200 OK</td>
 * </tr>
 * </table>
 *
 * @see org.ezkey.tests.security.tenant.MultiTenantGlobalAdminTest
 * @see <a href="../../../reference/MULTI_TENANT.md">Multi-Tenant Philosophy</a>
 * @see <a href="../../AGENTS.md">Agent Quick Reference</a>
 * @since 2025
 */
@Tag(TestTags.MULTI_TENANT)
@Tag(TestTags.SECURITY)
@DisplayName("Cross-Tenant Isolation Security Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TenantCrossIsolationSecurityTest extends AbstractSecurityTest {

  private TenantAdminTestHelper tenantAdminTestHelper;
  private String globalAdminToken;
  private String uniqueSuffix;
  private final DatabaseHelper databaseHelper = new DatabaseHelper();
  private Integer tenantAId;
  private Integer tenantBId;
  private String tenantAdminAToken;
  private String tenantAdminBToken;
  private Integer integrationAId;
  private Integer integrationBId;
  private Integer enrollmentAId;
  private Integer enrollmentBId;

  @SuppressWarnings("unused")
  private String apiKeyACredentials;

  @SuppressWarnings("unused")
  private String apiKeyBCredentials;

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
      uniqueSuffix = String.valueOf(System.currentTimeMillis());
      tenantAId = testDataFactory.findOrCreateTenant("Tenant A " + uniqueSuffix, globalAdminToken);
      tenantBId = testDataFactory.findOrCreateTenant("Tenant B " + uniqueSuffix, globalAdminToken);

      // Create TenantAdmins with device simulation and login
      tenantAdminAToken =
          tenantAdminTestHelper.createAndLoginTenantAdmin(
              "admin-a-" + uniqueSuffix, tenantAId, globalAdminToken);
      tenantAdminBToken =
          tenantAdminTestHelper.createAndLoginTenantAdmin(
              "admin-b-" + uniqueSuffix, tenantBId, globalAdminToken);

      // Create integrations for both tenants (using TenantAdmin tokens so
      // integrations are assigned to correct tenant)
      integrationAId =
          testDataFactory.createIntegrationForTenant(
              "Integration A " + uniqueSuffix, tenantAId, tenantAdminAToken);
      integrationBId =
          testDataFactory.createIntegrationForTenant(
              "Integration B " + uniqueSuffix, tenantBId, tenantAdminBToken);

      // Create enrollments for both integrations (using GlobalAdmin token)
      enrollmentAId = testDataFactory.createEnrollment(integrationAId);
      enrollmentBId = testDataFactory.createEnrollment(integrationBId);

      // Create API keys for both integrations (using GlobalAdmin token)
      apiKeyACredentials =
          testDataFactory.createApiKeyForIntegration(integrationAId, globalAdminToken);
      apiKeyBCredentials =
          testDataFactory.createApiKeyForIntegration(integrationBId, globalAdminToken);

    } catch (IllegalStateException e) {
      org.junit.jupiter.api.Assumptions.assumeTrue(
          false,
          "Test setup failed - admin token or device simulation not available: " + e.getMessage());
    }
  }

  // ========== INTEGRATIONS ISOLATION TESTS ==========

  @Test
  @Order(1)
  @DisplayName("TenantAdmin A can list only Tenant A integrations")
  public void testTenantAdminACanListOnlyOwnIntegrations() {
    configureForAdminApi(dockerStackConfig);

    Response response =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + tenantAdminAToken)
            .when()
            .get("/integrations")
            .then()
            .extract()
            .response();

    assertThat(response.getStatusCode()).isEqualTo(200);

    List<Map<String, Object>> integrations = response.jsonPath().getList("content");
    assertThat(integrations).isNotNull();

    log.info("TenantAdmin A received {} integrations", integrations.size());
    log.info(
        "Expected integrationAId: {}, should NOT see integrationBId: {}",
        integrationAId,
        integrationBId);

    for (Map<String, Object> integration : integrations) {
      log.info("Found integration: id={}", integration.get("id"));
    }

    // Verify integration A is present
    boolean hasIntegrationA =
        integrations.stream().anyMatch(i -> integrationAId.equals(i.get("id")));
    assertThat(hasIntegrationA).as("TenantAdmin A should see integration A").isTrue();

    // Verify integration B is NOT present (cross-tenant isolation)
    boolean hasIntegrationB =
        integrations.stream().anyMatch(i -> integrationBId.equals(i.get("id")));
    assertThat(hasIntegrationB)
        .as("TenantAdmin A should NOT see integration B (belongs to tenant B)")
        .isFalse();

    log.info("✅ Isolation verified: TenantAdmin A sees only their own integrations");
  }

  @Test
  @Order(2)
  @DisplayName("TenantAdmin A cannot get Tenant B integration by ID (403)")
  public void testTenantAdminACannotGetTenantBIntegration() {
    configureForAdminApi(dockerStackConfig);

    Response response =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + tenantAdminAToken)
            .when()
            .get("/integrations/" + integrationBId)
            .then()
            .extract()
            .response();

    assertThat(response.getStatusCode()).isIn(403, 404);
  }

  /**
   * Validates that TenantAdmin cannot delete integrations belonging to other tenants.
   *
   * <p>This test ensures proper tenant isolation at the deletion boundary. When a TenantAdmin
   * attempts to delete an integration from a different tenant, the API must reject the operation.
   * The expected behavior is 404 (Not Found) to avoid leaking information about the existence of
   * resources in other tenants.
   *
   * <p><b>Security Invariant:</b> DELETE /integrations/{id} must validate tenant ownership before
   * performing deletion. TenantAdmin can only delete integrations in their own tenant.
   *
   * <p><b>Test Setup:</b> Integration B was created by TenantAdmin B (in Tenant B). TenantAdmin A
   * attempts to delete it using their token.
   *
   * @see org.ezkey.admin.controller.IntegrationController#delete(Integer)
   */
  @Test
  @Order(3)
  @DisplayName("TenantAdmin A cannot delete Tenant B integration (404)")
  public void testTenantAdminACannotDeleteTenantBIntegration() {
    configureForAdminApi(dockerStackConfig);

    Response response =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + tenantAdminAToken)
            .when()
            .delete("/integrations/" + integrationBId)
            .then()
            .extract()
            .response();

    assertThat(response.getStatusCode()).isEqualTo(404);

    // Verify integration B still exists (using global admin)
    Response verifyResponse =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + globalAdminToken)
            .when()
            .get("/integrations/" + integrationBId)
            .then()
            .extract()
            .response();

    assertThat(verifyResponse.getStatusCode()).isEqualTo(200);
  }

  // ========== ENROLLMENTS ISOLATION TESTS ==========

  @Test
  @Order(5)
  @DisplayName("TenantAdmin A can list only Tenant A enrollments")
  public void testTenantAdminACanListOnlyOwnEnrollments() {
    configureForAdminApi(dockerStackConfig);

    Response response =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + tenantAdminAToken)
            .when()
            .get("/enrollments")
            .then()
            .extract()
            .response();

    assertThat(response.getStatusCode()).isEqualTo(200);

    List<Map<String, Object>> enrollments = response.jsonPath().getList("content");
    assertThat(enrollments).isNotNull();

    // Verify enrollment B is not in the list
    boolean hasEnrollmentB =
        enrollments.stream().anyMatch(e -> enrollmentBId.equals(e.get("enrollmentId")));
    assertThat(hasEnrollmentB).as("TenantAdmin A should not see Tenant B enrollments").isFalse();
  }

  @Test
  @Order(6)
  @DisplayName("TenantAdmin A cannot get Tenant B enrollment by ID (403/404)")
  public void testTenantAdminACannotGetTenantBEnrollment() {
    configureForAdminApi(dockerStackConfig);

    Response response =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + tenantAdminAToken)
            .when()
            .get("/enrollments/" + enrollmentBId)
            .then()
            .extract()
            .response();

    assertThat(response.getStatusCode()).isIn(403, 404);
  }

  @Test
  @Order(7)
  @DisplayName("TenantAdmin A cannot create enrollment for Tenant B integration (400/403)")
  public void testTenantAdminACannotCreateEnrollmentForTenantBIntegration() {
    configureForAdminApi(dockerStackConfig);

    Map<String, Object> request = new HashMap<>();
    request.put("integrationId", integrationBId); // Tenant B integration
    request.put("name", "Malicious Enrollment");
    request.put("authAttemptChallengeRequired", false);

    Response response =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + tenantAdminAToken)
            .body(request)
            .when()
            .post("/enrollments")
            .then()
            .extract()
            .response();

    assertThat(response.getStatusCode()).isIn(400, 403);
  }

  @Test
  @Order(8)
  @DisplayName("TenantAdmin A cannot delete Tenant B enrollment (403/404)")
  public void testTenantAdminACannotDeleteTenantBEnrollment() {
    configureForAdminApi(dockerStackConfig);

    Response response =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + tenantAdminAToken)
            .when()
            .delete("/enrollments/" + enrollmentBId)
            .then()
            .extract()
            .response();

    assertThat(response.getStatusCode()).isEqualTo(404);

    // Verify enrollment B still exists (using global admin)
    Response verifyResponse =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + globalAdminToken)
            .when()
            .get("/enrollments/" + enrollmentBId)
            .then()
            .extract()
            .response();

    assertThat(verifyResponse.getStatusCode()).isEqualTo(200);
  }

  /**
   * Validates that TenantAdmin cannot delete enrollments belonging to other tenants.
   *
   * <p>This test ensures proper tenant isolation at the enrollment deletion boundary. When a
   * TenantAdmin attempts to delete an enrollment that belongs to an integration from a different
   * tenant, the API must reject the operation. The expected behavior is 404 (Not Found) to avoid
   * leaking information about the existence of resources in other tenants.
   *
   * <p><b>Security Invariant:</b> DELETE /enrollments/{id} must validate tenant ownership before
   * performing deletion. TenantAdmin can only delete enrollments belonging to integrations in their
   * own tenant. This validation uses AccessControlService.canAccessEnrollment() which checks the
   * tenant_id of the integration associated with the enrollment.
   *
   * <p><b>Test Setup:</b> Enrollment B was created for Integration B (owned by TenantAdmin B in
   * Tenant B). TenantAdmin A attempts to delete it using their token.
   *
   * @see org.ezkey.admin.controller.EnrollmentController#delete(Integer, HttpServletRequest)
   * @see org.ezkey.admin.security.AccessControlService#canAccessEnrollment(Authentication, Integer)
   */
  // ========== API KEYS ISOLATION TESTS ==========

  @Test
  @Order(9)
  @DisplayName("TenantAdmin A can list only Tenant A API keys")
  public void testTenantAdminACanListOnlyOwnApiKeys() {
    configureForAdminApi(dockerStackConfig);

    Response response =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + tenantAdminAToken)
            .when()
            .get("/api-keys")
            .then()
            .extract()
            .response();

    System.out.println("Response Status: " + response.getStatusCode());
    System.out.println("Response Body: " + response.getBody().asString());

    assertThat(response.getStatusCode()).isEqualTo(200);

    // Our endpoint returns List directly, not wrapped in "content"
    List<Map<String, Object>> apiKeys = response.jsonPath().getList("$");
    assertThat(apiKeys).isNotNull();

    // Verify all API keys belong to Tenant A integrations
    for (Map<String, Object> apiKey : apiKeys) {
      Integer integrationId = (Integer) apiKey.get("integrationId");
      // Should not be integration B
      assertThat(integrationId)
          .as("TenantAdmin A should not see Tenant B API keys")
          .isNotEqualTo(integrationBId);
    }
  }

  @Test
  @Order(10)
  @DisplayName("TenantAdmin A cannot create API key for Tenant B integration (400/403)")
  public void testTenantAdminACannotCreateApiKeyForTenantBIntegration() {
    configureForAdminApi(dockerStackConfig);

    Map<String, Object> request = new HashMap<>();
    request.put("integrationId", integrationBId); // Tenant B integration
    request.put("description", "Malicious API Key");

    Response response =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + tenantAdminAToken)
            .body(request)
            .when()
            .post("/api-keys")
            .then()
            .extract()
            .response();

    assertThat(response.getStatusCode()).isIn(400, 403);
  }

  // ========== BIDIRECTIONAL ISOLATION TESTS ==========

  @Test
  @Order(11)
  @DisplayName("TenantAdmin B cannot access Tenant A resources (bidirectional check)")
  public void testTenantAdminBCannotAccessTenantAResources() {
    configureForAdminApi(dockerStackConfig);

    // Try to get Tenant A integration
    Response integrationResponse =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + tenantAdminBToken)
            .when()
            .get("/integrations/" + integrationAId)
            .then()
            .extract()
            .response();

    assertThat(integrationResponse.getStatusCode()).isIn(403, 404);

    // Try to get Tenant A enrollment
    Response enrollmentResponse =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + tenantAdminBToken)
            .when()
            .get("/enrollments/" + enrollmentAId)
            .then()
            .extract()
            .response();

    assertThat(enrollmentResponse.getStatusCode()).isIn(403, 404);
  }

  // ========== GLOBALADMIN CROSS-TENANT ACCESS TESTS ==========

  @Test
  @Order(20)
  @DisplayName("GlobalAdmin can access Tenant A integration (cross-tenant read)")
  public void testGlobalAdminCanAccessTenantAIntegration() {
    configureForAdminApi(dockerStackConfig);

    Response response =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + globalAdminToken)
            .when()
            .get("/integrations/" + integrationAId)
            .then()
            .extract()
            .response();

    assertThat(response.getStatusCode()).isEqualTo(200);
    assertThat(response.jsonPath().getInt("id")).isEqualTo(integrationAId);
  }

  @Test
  @Order(21)
  @DisplayName("GlobalAdmin can access Tenant B integration (cross-tenant read)")
  public void testGlobalAdminCanAccessTenantBIntegration() {
    configureForAdminApi(dockerStackConfig);

    Response response =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + globalAdminToken)
            .when()
            .get("/integrations/" + integrationBId)
            .then()
            .extract()
            .response();

    assertThat(response.getStatusCode()).isEqualTo(200);
    assertThat(response.jsonPath().getInt("id")).isEqualTo(integrationBId);
  }

  @Test
  @Order(22)
  @DisplayName("GlobalAdmin can list all integrations (all tenants visible)")
  public void testGlobalAdminCanListAllIntegrations() {
    configureForAdminApi(dockerStackConfig);

    Response response =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + globalAdminToken)
            // Explicit pagination/sorting to keep test resilient as data accumulates
            // (production-like).
            .queryParam("page", 0)
            .queryParam("size", 100)
            .queryParam("sort", "id,ASC")
            // Scope to this test run's unique data (independence without cleanup).
            .queryParam("integrationName", uniqueSuffix)
            .when()
            .get("/integrations")
            .then()
            .statusCode(200)
            .extract()
            .response();

    List<Map<String, Object>> integrations = response.jsonPath().getList("content");
    assertThat(integrations).isNotNull();

    // Validate Page<T> metadata (Spring Data pagination contract).
    Integer pageNumber = response.jsonPath().getObject("number", Integer.class);
    if (pageNumber == null) {
      pageNumber = response.jsonPath().getObject("pageable.pageNumber", Integer.class);
    }
    if (pageNumber == null) {
      pageNumber = response.jsonPath().getObject("page.number", Integer.class);
    }
    Integer pageSize = response.jsonPath().getObject("size", Integer.class);
    if (pageSize == null) {
      pageSize = response.jsonPath().getObject("pageable.pageSize", Integer.class);
    }
    if (pageSize == null) {
      pageSize = response.jsonPath().getObject("page.size", Integer.class);
    }
    Number totalElements = response.jsonPath().getObject("totalElements", Number.class);
    if (totalElements == null) {
      totalElements = response.jsonPath().getObject("page.totalElements", Number.class);
    }
    Number totalPages = response.jsonPath().getObject("totalPages", Number.class);
    if (totalPages == null) {
      totalPages = response.jsonPath().getObject("page.totalPages", Number.class);
    }

    assertThat(pageNumber).as("Expected page number metadata").isEqualTo(0);
    assertThat(pageSize).as("Expected page size metadata").isEqualTo(100);
    assertThat(totalElements).as("Expected totalElements metadata").isNotNull();
    assertThat(totalElements.longValue()).isGreaterThanOrEqualTo(2L);
    assertThat(totalPages).as("Expected totalPages metadata").isNotNull();
    assertThat(totalPages.longValue()).isGreaterThanOrEqualTo(1L);

    log.info("GlobalAdmin received {} integrations", integrations.size());
    log.info(
        "Expected to see integrationAId: {} and integrationBId: {}",
        integrationAId,
        integrationBId);

    for (Map<String, Object> integration : integrations) {
      log.info(
          "Found integration: id={}, tenantId={}",
          integration.get("id"),
          integration.get("tenantId"));
    }

    // Extract integration IDs from the list
    List<Integer> integrationIds = integrations.stream().map(i -> (Integer) i.get("id")).toList();

    // GlobalAdmin should see integrations from both tenants
    assertThat(integrationIds)
        .as("GlobalAdmin should see integrations from all tenants")
        .contains(integrationAId, integrationBId);
  }

  @Test
  @Order(23)
  @DisplayName("GlobalAdmin can list all API keys (all tenants visible)")
  public void testGlobalAdminCanListAllApiKeys() {
    configureForAdminApi(dockerStackConfig);

    Response response =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + globalAdminToken)
            .when()
            .get("/api-keys")
            .then()
            .statusCode(200)
            .extract()
            .response();

    List<Map<String, Object>> apiKeys = response.jsonPath().getList("$");
    assertThat(apiKeys).isNotNull();

    // Extract integration IDs from API keys
    List<Integer> integrationIds =
        apiKeys.stream().map(apiKey -> (Integer) apiKey.get("integrationId")).toList();

    // GlobalAdmin should see API keys from both tenants
    assertThat(integrationIds)
        .as("GlobalAdmin should see API keys from all tenants")
        .contains(integrationAId, integrationBId);
  }

  @Test
  @Order(24)
  @DisplayName("GlobalAdmin can access Tenant A enrollment (cross-tenant read)")
  public void testGlobalAdminCanAccessTenantAEnrollment() {
    configureForAdminApi(dockerStackConfig);

    Response response =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + globalAdminToken)
            .when()
            .get("/enrollments/" + enrollmentAId)
            .then()
            .statusCode(200)
            .extract()
            .response();

    Integer responseEnrollmentId = response.jsonPath().getInt("enrollmentId");
    assertThat(responseEnrollmentId).isEqualTo(enrollmentAId);
  }

  @Test
  @Order(25)
  @DisplayName("GlobalAdmin can access Tenant B enrollment (cross-tenant read)")
  public void testGlobalAdminCanAccessTenantBEnrollment() {
    configureForAdminApi(dockerStackConfig);

    Response response =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + globalAdminToken)
            .when()
            .get("/enrollments/" + enrollmentBId)
            .then()
            .statusCode(200)
            .extract()
            .response();

    Integer responseEnrollmentId = response.jsonPath().getInt("enrollmentId");
    assertThat(responseEnrollmentId).isEqualTo(enrollmentBId);
  }

  /**
   * Validates that GlobalAdmin can list enrollments across all tenants.
   *
   * <p><b>Strategy:</b> Request a single record (`size=1`) and validate the pagination <code>
   * totalElements</code> matches the database count.
   *
   * <p><b>Important assumption / limitation:</b> This test assumes the Admin API list endpoint
   * returns the same logical set as <code>SELECT COUNT(*) FROM ezkey_enrollment</code> for a
   * GlobalAdmin (i.e., no implicit filtering such as soft-delete, inactive-only exclusion, or other
   * visibility rules). If a future feature introduces such filtering, this test is expected to fail
   * and should then be refined to compare against the correct DB subset (matching the API
   * semantics).
   */
  @Test
  @Order(26)
  @DisplayName("GlobalAdmin can list enrollments from all tenants (totalElements matches DB)")
  public void testGlobalAdminCanListAllEnrollments() {
    configureForAdminApi(dockerStackConfig);

    Response response =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + globalAdminToken)
            .queryParam("page", 0)
            .queryParam("size", 1)
            .queryParam("sort", "enrollmentId,ASC")
            .when()
            .get("/enrollments")
            .then()
            .statusCode(200)
            .extract()
            .response();

    // We create at least two enrollments in setup (A and B).
    String dbCountRaw =
        databaseHelper.executeQuerySingleValue("SELECT COUNT(*) FROM ezkey_enrollment;");
    assertThat(dbCountRaw).as("DB enrollment count should be available").isNotNull();
    long dbCount = Long.parseLong(dbCountRaw.trim());
    assertThat(dbCount).isGreaterThanOrEqualTo(2L);

    // Support multiple common Page<> JSON shapes (Spring may nest pagination differently).
    Number totalElements = response.jsonPath().getObject("totalElements", Number.class);
    if (totalElements == null) {
      totalElements = response.jsonPath().getObject("page.totalElements", Number.class);
    }
    assertThat(totalElements).as("Expected totalElements pagination metadata").isNotNull();
    assertThat(totalElements.longValue()).isEqualTo(dbCount);

    // Basic sanity: content list should exist and contain at most 1 record because size=1.
    List<Map<String, Object>> content = response.jsonPath().getList("content");
    assertThat(content).as("Expected 'content' array in paged response").isNotNull();
    assertThat(content.size()).isLessThanOrEqualTo(1);
  }
}
