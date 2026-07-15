/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: ApiKeyObjectAuthorizationSecurityTest
 * Description: SEC-022 / SEC-023 — Tenant Admin must not get/list/revoke foreign-tenant API keys.
 */

package org.ezkey.tests.security.apikey;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.ezkey.tests.util.RestAssuredTestConfig.configureForAdminApi;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
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
 * SEC-022 / SEC-023 regression: object-level authorization on API key get, per-integration list,
 * and revoke.
 *
 * <p>Tenant Admin A must receive 403 for Tenant B key/integration targets; Tenant B's key must
 * remain active after a rejected cross-tenant revoke. Same-tenant revoke remains 204.
 *
 * @since 2026
 */
@Tag(TestTags.FAST)
@Tag(TestTags.SECURITY)
@Tag(TestTags.API_KEY)
@Tag(TestTags.MULTI_TENANT)
@DisplayName("SEC-022/023 API Key Object Authorization")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ApiKeyObjectAuthorizationSecurityTest extends AbstractSecurityTest {

  private String globalAdminToken;
  private String tenantAdminAToken;
  private String tenantAdminBToken;
  private Integer integrationAId;
  private Integer integrationBId;
  private Integer apiKeyAId;
  private Integer apiKeyBId;

  @Override
  @BeforeEach
  public void setUp() {
    super.setUp();

    try {
      globalAdminToken = authTokenManager.getAdminToken();
      configureForAdminApi(dockerStackConfig);

      String suffix = UUID.randomUUID().toString().substring(0, 8);
      Integer tenantAId =
          testDataFactory.createTenant("SEC022 Tenant A " + suffix, globalAdminToken);
      Integer tenantBId =
          testDataFactory.createTenant("SEC022 Tenant B " + suffix, globalAdminToken);

      TenantAdminTestHelper tenantAdminHelper =
          new TenantAdminTestHelper(dockerStackConfig, testDataFactory, cryptoApiClient);
      tenantAdminAToken =
          tenantAdminHelper.createAndLoginTenantAdmin(
              "sec022_a_" + suffix, tenantAId, globalAdminToken);
      tenantAdminBToken =
          tenantAdminHelper.createAndLoginTenantAdmin(
              "sec022_b_" + suffix, tenantBId, globalAdminToken);

      integrationAId =
          testDataFactory.createIntegrationForTenant(
              "SEC022 Integration A " + suffix, tenantAId, tenantAdminAToken);
      integrationBId =
          testDataFactory.createIntegrationForTenant(
              "SEC022 Integration B " + suffix, tenantBId, tenantAdminBToken);

      apiKeyAId = createApiKeyAndGetId(integrationAId, tenantAdminAToken);
      apiKeyBId = createApiKeyAndGetId(integrationBId, tenantAdminBToken);
    } catch (IllegalStateException e) {
      org.junit.jupiter.api.Assumptions.assumeTrue(
          false,
          "Test setup failed - admin token or device simulation not available: " + e.getMessage());
    }
  }

  private Integer createApiKeyAndGetId(Integer integrationId, String adminToken) {
    Map<String, Object> request = new HashMap<>();
    request.put("integrationId", integrationId);
    request.put("description", "SEC-022 object authz key");

    Response response =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + adminToken)
            .body(request)
            .when()
            .post("/api-keys")
            .then()
            .statusCode(201)
            .extract()
            .response();

    return response.jsonPath().getInt("apiKeyId");
  }

  @Test
  @Order(1)
  @DisplayName("SEC-023: TenantAdmin A cannot GET Tenant B API key by id (403)")
  public void tenantAdminACannotGetTenantBApiKey() {
    configureForAdminApi(dockerStackConfig);

    Response response =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + tenantAdminAToken)
            .when()
            .get("/api-keys/" + apiKeyBId)
            .then()
            .extract()
            .response();

    assertThat(response.getStatusCode())
        .as("Cross-tenant GET /api-keys/{keyId} must be denied")
        .isEqualTo(403);
  }

  @Test
  @Order(2)
  @DisplayName("SEC-023: TenantAdmin A cannot list API keys for Tenant B integration (403)")
  public void tenantAdminACannotListTenantBIntegrationApiKeys() {
    configureForAdminApi(dockerStackConfig);

    Response response =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + tenantAdminAToken)
            .queryParam("page", 0)
            .queryParam("size", 20)
            .when()
            .get("/api-keys/integration/" + integrationBId)
            .then()
            .extract()
            .response();

    assertThat(response.getStatusCode())
        .as("Cross-tenant GET /api-keys/integration/{id} must be denied")
        .isEqualTo(403);
  }

  @Test
  @Order(3)
  @DisplayName("SEC-022: TenantAdmin A cannot revoke Tenant B API key (403); key stays active")
  public void tenantAdminACannotRevokeTenantBApiKey() {
    configureForAdminApi(dockerStackConfig);

    Response revokeResponse =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + tenantAdminAToken)
            .queryParam("reason", "SEC-022 cross-tenant revoke probe")
            .when()
            .delete("/api-keys/" + apiKeyBId)
            .then()
            .extract()
            .response();

    assertThat(revokeResponse.getStatusCode())
        .as("Cross-tenant DELETE /api-keys/{keyId} must be denied")
        .isEqualTo(403);

    Response verifyResponse =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + globalAdminToken)
            .when()
            .get("/api-keys/" + apiKeyBId)
            .then()
            .extract()
            .response();

    assertThat(verifyResponse.getStatusCode()).isEqualTo(200);
    assertThat(verifyResponse.jsonPath().getBoolean("active"))
        .as("Tenant B key must remain active after rejected cross-tenant revoke")
        .isTrue();
  }

  @Test
  @Order(4)
  @DisplayName("Same-tenant revoke still succeeds (204)")
  public void tenantAdminACanRevokeOwnApiKey() {
    configureForAdminApi(dockerStackConfig);

    Response revokeResponse =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + tenantAdminAToken)
            .queryParam("reason", "SEC-022 same-tenant revoke smoke")
            .when()
            .delete("/api-keys/" + apiKeyAId)
            .then()
            .extract()
            .response();

    assertThat(revokeResponse.getStatusCode()).isEqualTo(204);

    Response verifyResponse =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + globalAdminToken)
            .when()
            .get("/api-keys/" + apiKeyAId)
            .then()
            .extract()
            .response();

    assertThat(verifyResponse.getStatusCode()).isEqualTo(200);
    assertThat(verifyResponse.jsonPath().getBoolean("active")).isFalse();
  }
}
