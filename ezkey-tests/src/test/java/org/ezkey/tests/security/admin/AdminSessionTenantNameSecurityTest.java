/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AdminSessionTenantNameSecurityTest
 * Description: Tenant-scoped /me sessions expose tenant display name; global sessions omit it.
 */

package org.ezkey.tests.security.admin;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.ezkey.tests.util.RestAssuredTestConfig.configureForAdminApi;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.UUID;
import org.ezkey.tests.security.AbstractSecurityTest;
import org.ezkey.tests.tags.TestTags;
import org.ezkey.tests.util.TenantAdminTestHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Verifies {@code GET /api/v1/admin/auth/me} returns {@code tenantName} for tenant-scoped
 * administrators and omits it for global administrators.
 *
 * @since 2026
 */
@Tag(TestTags.FAST)
@Tag(TestTags.ADMIN)
@DisplayName("Admin session tenant display name")
public class AdminSessionTenantNameSecurityTest extends AbstractSecurityTest {

  @Test
  @DisplayName("GET /me includes tenantName for TENANT_ADMIN and omits it for GLOBAL_ADMIN")
  public void meIncludesTenantNameForTenantAdminOnly() {
    String globalToken = authTokenManager.getAdminToken();
    configureForAdminApi(dockerStackConfig);

    Response globalMe =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + globalToken)
            .when()
            .get("/admin/auth/me")
            .then()
            .extract()
            .response();

    assertThat(globalMe.getStatusCode()).as("global /me body: %s", globalMe.asString()).isEqualTo(200);
    assertThat(globalMe.jsonPath().getString("adminType")).isEqualTo("GLOBAL_ADMIN");
    assertThat(globalMe.jsonPath().getString("tenantName")).isNull();
    assertThat(globalMe.jsonPath().getObject("tenantId", Integer.class)).isNull();

    String suffix = UUID.randomUUID().toString().substring(0, 8);
    String tenantName = "Session Tenant " + suffix;
    Integer tenantId = testDataFactory.createTenant(tenantName, globalToken);
    String username = "session.tenant." + suffix;

    TenantAdminTestHelper tenantAdminHelper =
        new TenantAdminTestHelper(dockerStackConfig, testDataFactory, cryptoApiClient);
    String tenantToken =
        tenantAdminHelper.createAndLoginTenantAdmin(username, tenantId, globalToken);

    configureForAdminApi(dockerStackConfig);
    Response tenantMe =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + tenantToken)
            .when()
            .get("/admin/auth/me")
            .then()
            .extract()
            .response();

    assertThat(tenantMe.getStatusCode()).as("tenant /me body: %s", tenantMe.asString()).isEqualTo(200);
    assertThat(tenantMe.jsonPath().getString("adminType")).isEqualTo("TENANT_ADMIN");
    assertThat(tenantMe.jsonPath().getInt("tenantId")).isEqualTo(tenantId);
    assertThat(tenantMe.jsonPath().getString("tenantName")).isEqualTo(tenantName);
    assertThat(tenantMe.jsonPath().getString("username")).isEqualTo(username);
  }
}
