/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: ApiKeyUpdateSecurityTest
 * Description: Integration tests for PATCH /api/v1/api-keys/{keyId} (API key config update).
 */

package org.ezkey.tests.security.apikey;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.ezkey.tests.util.RestAssuredTestConfig.configureForAdminApi;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.HashMap;
import java.util.Map;
import org.ezkey.tests.security.AbstractSecurityTest;
import org.ezkey.tests.tags.TestTags;
import org.ezkey.tests.util.DatabaseHelper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for API key PATCH endpoint.
 *
 * <p>Validates PATCH /api/v1/api-keys/{keyId} including:
 *
 * <ul>
 *   <li>Happy path: partial update returns 200 with updated fields
 *   <li>Optimistic locking: stale version returns 409 Conflict
 * </ul>
 *
 * @since 2025
 */
@Tag(TestTags.API_KEY)
@Tag(TestTags.INTEGRATION)
@DisplayName("API Key Update (PATCH) Security Tests")
public class ApiKeyUpdateSecurityTest extends AbstractSecurityTest {

  /**
   * Creates an API key for an integration and returns the API key ID.
   *
   * @param integrationId Integration ID
   * @param adminToken Admin bearer token
   * @return API key ID
   */
  private Integer createApiKeyAndGetId(Integer integrationId, String adminToken) {
    Map<String, Object> request = new HashMap<>();
    request.put("integrationId", integrationId);
    request.put("description", "Test API Key for PATCH");

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
  @DisplayName("PATCH API key - happy path returns 200 with updated fields")
  void patchApiKey_happyPath_returns200() {
    String adminToken;
    try {
      adminToken = authTokenManager.getAdminToken();
    } catch (IllegalStateException e) {
      Assumptions.assumeTrue(false, "Admin token not available. Run bootstrap-init first.");
      return;
    }

    configureForAdminApi(dockerStackConfig);
    Integer integrationId = testDataFactory.createIntegration();
    Integer apiKeyId = createApiKeyAndGetId(integrationId, adminToken);

    // Get current API key and version
    Response getResponse =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + adminToken)
            .when()
            .get("/api-keys/" + apiKeyId)
            .then()
            .extract()
            .response();

    Assumptions.assumeTrue(
        getResponse.getStatusCode() == 200, "API key " + apiKeyId + " not found");
    Long version = getResponse.jsonPath().getLong("version");
    Assumptions.assumeTrue(version != null, "API key response missing version field");

    // PATCH with new description and ipWhitelist
    String newDescription = "PatchTest-" + System.currentTimeMillis();
    Map<String, Object> patchBody = new HashMap<>();
    patchBody.put("version", version);
    patchBody.put("description", newDescription);
    patchBody.put("ipWhitelist", new String[] {"192.168.1.0/24"});

    Response patchResponse =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + adminToken)
            .body(patchBody)
            .when()
            .patch("/api-keys/" + apiKeyId)
            .then()
            .extract()
            .response();

    assertThat(patchResponse.getStatusCode()).isEqualTo(200);
    assertThat(patchResponse.jsonPath().getString("description")).isEqualTo(newDescription);
    assertThat(patchResponse.jsonPath().getList("ipWhitelist")).containsExactly("192.168.1.0/24");
    assertThat(patchResponse.jsonPath().getLong("version")).isGreaterThan(version);
  }

  @Test
  @DisplayName("PATCH API key - stale version returns 409 Conflict")
  void patchApiKey_staleVersion_returns409() {
    String adminToken;
    try {
      adminToken = authTokenManager.getAdminToken();
    } catch (IllegalStateException e) {
      Assumptions.assumeTrue(false, "Admin token not available. Run bootstrap-init first.");
      return;
    }

    configureForAdminApi(dockerStackConfig);
    Integer integrationId = testDataFactory.createIntegration();
    Integer apiKeyId = createApiKeyAndGetId(integrationId, adminToken);

    // Get current API key and version
    Response getResponse =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + adminToken)
            .when()
            .get("/api-keys/" + apiKeyId)
            .then()
            .extract()
            .response();

    Assumptions.assumeTrue(
        getResponse.getStatusCode() == 200, "API key " + apiKeyId + " not found");
    Long staleVersion = getResponse.jsonPath().getLong("version");

    // Simulate concurrent update: bump version in DB directly
    DatabaseHelper db = new DatabaseHelper();
    boolean success =
        db.executeUpdate(
            "UPDATE ezkey_api_key SET version = version + 1 WHERE api_key_id = " + apiKeyId + ";");
    Assumptions.assumeTrue(success, "Failed to bump version in DB");

    // PATCH with stale version
    Map<String, Object> patchBody = Map.of("version", staleVersion, "description", "StaleUpdate");

    Response patchResponse =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + adminToken)
            .body(patchBody)
            .when()
            .patch("/api-keys/" + apiKeyId)
            .then()
            .extract()
            .response();

    assertThat(patchResponse.getStatusCode()).isEqualTo(409);
  }
}
