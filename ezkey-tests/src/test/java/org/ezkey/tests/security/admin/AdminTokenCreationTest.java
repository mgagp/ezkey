/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AdminTokenCreationTest
 * Description: Building block test for creating admin tokens (idempotent and independent)
 */

package org.ezkey.tests.security.admin;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.ezkey.tests.util.RestAssuredTestConfig.configureForAdminApi;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.ezkey.tests.security.AbstractSecurityTest;
import org.ezkey.tests.util.AdminBootstrapService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Building block test for creating admin tokens.
 *
 * <p>This test represents the core capability of obtaining a valid admin token. It is designed to
 * be:
 *
 * <ul>
 *   <li><b>Independent:</b> Can be run in any order, does not depend on other tests
 *   <li><b>Idempotent:</b> Can be run multiple times with the same result
 *   <li><b>Efficient:</b> Reuses cached tokens and device credentials when available
 * </ul>
 *
 * <p>The test follows a three-tier strategy:
 *
 * <ol>
 *   <li>Reuse cached token from previous run (fastest)
 *   <li>Reuse device credentials to create new token (fast)
 *   <li>Perform initial bootstrap if needed (slower, one-time)
 * </ol>
 *
 * <p>This test validates:
 *
 * <ul>
 *   <li>Token creation (via bootstrap or reuse)
 *   <li>Token validity by accessing protected endpoints
 * </ul>
 *
 * <p><b>Usage:</b> This test can be used as a dependency for other tests that require an admin
 * token. It ensures a valid token is available without manual intervention.
 *
 * @since 2025
 */
@DisplayName("Admin Token Creation Test")
public class AdminTokenCreationTest extends AbstractSecurityTest {

  @Test
  @DisplayName("Create admin token (reuses cache or device credentials when available)")
  public void testCreateAdminToken() {
    // Create bootstrap service
    AdminBootstrapService bootstrapService =
        new AdminBootstrapService(
            dockerStackConfig, bootstrapCredentialsExtractor, cryptoApiClient);

    // Obtain token (reuses cache, device credentials, or performs bootstrap as needed)
    String adminToken = bootstrapService.ensureAdminToken();

    // Verify token is not null or empty
    assertThat(adminToken).isNotNull().isNotEmpty();

    // Verify token is valid by accessing a protected endpoint
    configureForAdminApi(dockerStackConfig);
    Response response =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + adminToken)
            .when()
            .get("/integrations")
            .then()
            .statusCode(200)
            .extract()
            .response();

    // Verify response is successful
    assertThat(response.getStatusCode()).isEqualTo(200);
  }
}
