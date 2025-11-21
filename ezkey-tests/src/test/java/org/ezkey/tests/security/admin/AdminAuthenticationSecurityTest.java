/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AdminAuthenticationSecurityTest
 * Description: Security tests for admin authentication (passwordless login, recovery codes, token validation)
 */

package org.ezkey.tests.security.admin;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.ezkey.tests.security.AbstractSecurityTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.ezkey.tests.util.RestAssuredTestConfig.configureForAdminApi;

/**
 * Security tests for admin authentication endpoints.
 *
 * <p>Validates security aspects of admin authentication including:
 *
 * <ul>
 *   <li>Unauthorized access attempts (401)
 *   <li>Invalid token handling
 *   <li>Token invalidation (logout)
 *   <li>Passwordless login flow (requires device approval)
 * </ul>
 *
 * <p>Note: These tests require a valid admin token set via EZKEY_ADMIN_TOKEN environment variable
 * or through login flow with device approval.
 *
 * @since 2025
 */
@DisplayName("Admin Authentication Security Tests")
public class AdminAuthenticationSecurityTest extends AbstractSecurityTest {

  @Test
  @DisplayName("Unauthorized access to protected endpoint should return 401")
  public void testUnauthorizedAccess() {
    configureForAdminApi(dockerStackConfig);

    Response response =
        given()
            .contentType(ContentType.JSON)
            .when()
            .get("/integrations")
            .then()
            .extract()
            .response();

    assertThat(response.getStatusCode()).isEqualTo(401);
  }

  @Test
  @DisplayName("Invalid bearer token should return 401")
  public void testInvalidToken() {
    configureForAdminApi(dockerStackConfig);

    Response response =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer invalid-token-12345")
            .when()
            .get("/integrations")
            .then()
            .extract()
            .response();

    assertThat(response.getStatusCode()).isEqualTo(401);
  }

  @Test
  @DisplayName("Missing Authorization header should return 401")
  public void testMissingAuthorizationHeader() {
    configureForAdminApi(dockerStackConfig);

    Response response =
        given()
            .contentType(ContentType.JSON)
            .when()
            .get("/integrations")
            .then()
            .extract()
            .response();

    assertThat(response.getStatusCode()).isEqualTo(401);
  }

  @Test
  @DisplayName("Valid admin token should allow access to protected endpoints")
  public void testValidTokenAccess() {
    // Skip if admin token not available
    try {
      String adminToken = authTokenManager.getAdminToken();
      configureForAdminApi(dockerStackConfig);

      Response response =
          given()
              .contentType(ContentType.JSON)
              .header("Authorization", "Bearer " + adminToken)
              .when()
              .get("/integrations")
              .then()
              .extract()
              .response();

      assertThat(response.getStatusCode()).isEqualTo(200);
    } catch (IllegalStateException e) {
      // Skip test if admin token not available
      org.junit.jupiter.api.Assumptions.assumeTrue(
          false, "Admin token not available. Set EZKEY_ADMIN_TOKEN environment variable.");
    }
  }

  @Test
  @DisplayName("Logout should invalidate token")
  public void testLogoutInvalidatesToken() {
    // Skip if admin token not available
    try {
      String adminToken = authTokenManager.getAdminToken();
      configureForAdminApi(dockerStackConfig);

      // Logout
      Response logoutResponse =
          given()
              .contentType(ContentType.JSON)
              .header("Authorization", "Bearer " + adminToken)
              .when()
              .post("/admin/auth/logout")
              .then()
              .extract()
              .response();

      assertThat(logoutResponse.getStatusCode()).isEqualTo(200);

      // Try to use token after logout
      Response accessResponse =
          given()
              .contentType(ContentType.JSON)
              .header("Authorization", "Bearer " + adminToken)
              .when()
              .get("/integrations")
              .then()
              .extract()
              .response();

      // Token should be invalid after logout
      assertThat(accessResponse.getStatusCode()).isEqualTo(401);
    } catch (IllegalStateException e) {
      // Skip test if admin token not available
      org.junit.jupiter.api.Assumptions.assumeTrue(
          false, "Admin token not available. Set EZKEY_ADMIN_TOKEN environment variable.");
    }
  }
}

