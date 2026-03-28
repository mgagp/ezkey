/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Utility: TestDataFactory
 * Description: Helper methods for creating test entities via REST API
 */

package org.ezkey.tests.util;

import static io.restassured.RestAssured.given;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.HashMap;
import java.util.Map;
import org.ezkey.tests.config.DockerStackConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory class for creating test data via REST API calls.
 *
 * <p>Provides helper methods to create test entities (integrations, enrollments, auth attempts,
 * etc.) through the Admin API and Auth API. These methods simplify test setup by encapsulating
 * common creation patterns.
 *
 * <p>Supports opportunistic reuse: Some methods (findOrCreate*) will search for existing entities
 * matching the criteria before creating new ones, reducing overhead in repeated test runs.
 *
 * @since 2025
 */
public class TestDataFactory {

  private static final Logger log = LoggerFactory.getLogger(TestDataFactory.class);

  private final DockerStackConfig dockerStackConfig;
  private final AuthTokenManager authTokenManager;

  /**
   * Creates a new TestDataFactory.
   *
   * @param dockerStackConfig Docker stack configuration
   * @param authTokenManager Token manager for authentication
   */
  public TestDataFactory(DockerStackConfig dockerStackConfig, AuthTokenManager authTokenManager) {
    this.dockerStackConfig = dockerStackConfig;
    this.authTokenManager = authTokenManager;
  }

  /**
   * Creates a test integration via Admin API.
   *
   * @param name Integration name
   * @param description Integration description
   * @return Integration ID
   */
  public Integer createIntegration(String name, String description) {
    log.debug("Creating integration: {}", name);

    RestAssuredTestConfig.configureForAdminApi(dockerStackConfig);

    // Generate unique code using UUID to prevent conflicts across test runs
    String code = "test-" + java.util.UUID.randomUUID().toString().substring(0, 8);

    Map<String, Object> request = new HashMap<>();
    request.put("code", code);
    request.put("name", name);
    request.put("description", description);

    Response response =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + authTokenManager.getAdminToken())
            .body(request)
            .when()
            .post("/integrations")
            .then()
            .statusCode(201)
            .extract()
            .response();

    Integer integrationId = response.jsonPath().getInt("id");
    log.debug("Created integration with ID: {}", integrationId);

    return integrationId;
  }

  /**
   * Creates a test integration with default values.
   *
   * @return Integration ID
   */
  public Integer createIntegration() {
    return createIntegration("Test Integration", "Test Description");
  }

  /**
   * Creates a test enrollment via Admin API.
   *
   * @param integrationId Integration ID
   * @param name Enrollment name
   * @param challengeRequired Whether challenge is required
   * @return Enrollment ID
   */
  public Integer createEnrollment(Integer integrationId, String name, Boolean challengeRequired) {
    return createEnrollment(
        integrationId, name, challengeRequired, authTokenManager.getAdminToken());
  }

  /**
   * Creates a test enrollment using an explicit admin bearer token (e.g. Tenant Admin for
   * operational churn).
   *
   * @param integrationId Integration ID
   * @param name Enrollment name
   * @param challengeRequired Whether challenge is required
   * @param bearerToken Admin API bearer token
   * @return Enrollment ID
   */
  public Integer createEnrollment(
      Integer integrationId, String name, Boolean challengeRequired, String bearerToken) {
    log.debug("Creating enrollment: {} for integration: {}", name, integrationId);

    RestAssuredTestConfig.configureForAdminApi(dockerStackConfig);

    Map<String, Object> request = new HashMap<>();
    request.put("integrationId", integrationId);
    request.put("name", name);
    request.put(
        "authAttemptChallengeRequired", challengeRequired != null ? challengeRequired : false);

    Response response =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + bearerToken)
            .body(request)
            .when()
            .post("/enrollments")
            .then()
            .statusCode(201)
            .extract()
            .response();

    Integer enrollmentId = response.jsonPath().getInt("enrollmentId");
    log.debug("Created enrollment with ID: {}", enrollmentId);

    return enrollmentId;
  }

  /**
   * Creates a test enrollment with default values.
   *
   * @param integrationId Integration ID
   * @return Enrollment ID
   */
  public Integer createEnrollment(Integer integrationId) {
    return createEnrollment(integrationId, "Test Device", false);
  }

  /**
   * Creates a test auth attempt via Admin API.
   *
   * @param enrollmentId Enrollment ID
   * @param challengeRequested Whether challenge is requested
   * @return Auth attempt ID
   */
  public Integer createAuthAttempt(Integer enrollmentId, Boolean challengeRequested) {
    return createAuthAttempt(enrollmentId, challengeRequested, authTokenManager.getAdminToken());
  }

  /**
   * Creates a test auth attempt using an explicit admin bearer token.
   *
   * @param enrollmentId Enrollment ID
   * @param challengeRequested Whether challenge is requested
   * @param bearerToken Admin API bearer token
   * @return Auth attempt ID
   */
  public Integer createAuthAttempt(
      Integer enrollmentId, Boolean challengeRequested, String bearerToken) {
    log.debug("Creating auth attempt for enrollment: {}", enrollmentId);

    RestAssuredTestConfig.configureForAdminApi(dockerStackConfig);

    Map<String, Object> request = new HashMap<>();
    request.put("enrollmentId", enrollmentId);
    request.put("challengeRequested", challengeRequested != null ? challengeRequested : false);

    Response response =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + bearerToken)
            .body(request)
            .when()
            .post("/auth-attempts")
            .then()
            .statusCode(201)
            .extract()
            .response();

    Integer authAttemptId = response.jsonPath().getInt("authAttemptId");
    log.debug("Created auth attempt with ID: {}", authAttemptId);

    return authAttemptId;
  }

  /**
   * Creates a test auth attempt with default values.
   *
   * @param enrollmentId Enrollment ID
   * @return Auth attempt ID
   */
  public Integer createAuthAttempt(Integer enrollmentId) {
    return createAuthAttempt(enrollmentId, false);
  }

  /**
   * Creates a tenant via Admin API.
   *
   * <p>Creates a new tenant with a unique name to ensure idempotence across test runs.
   *
   * @param tenantName Tenant name
   * @param adminToken Admin bearer token (must be GlobalAdmin)
   * @return Tenant ID
   */
  public Integer createTenant(String tenantName, String adminToken) {
    log.debug("Creating tenant: {}", tenantName);

    RestAssuredTestConfig.configureForAdminApi(dockerStackConfig);

    Map<String, Object> request = new HashMap<>();
    request.put("tenantName", tenantName);
    request.put("tenantDescription", "Test tenant: " + tenantName);

    Response response =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + adminToken)
            .body(request)
            .when()
            .post("/tenants")
            .then()
            .statusCode(201)
            .extract()
            .response();

    Integer tenantId = response.jsonPath().getInt("tenantId");
    log.debug("Created tenant with ID: {}", tenantId);

    return tenantId;
  }

  /**
   * Creates a tenant admin via Admin API (without full authentication).
   *
   * <p>Creates a new tenant admin record but does NOT complete the enrollment flow. The returned
   * token is null - tests should use the globalAdminToken instead for operations that require
   * authentication.
   *
   * <p>This simple approach avoids complex enrollment binding and device key generation during test
   * setup while still creating the required admin records.
   *
   * @param username Admin username
   * @param tenantId Tenant ID
   * @param adminToken Admin bearer token (must be GlobalAdmin or TenantAdmin of same tenant)
   * @return null - use globalAdminToken for authenticated API calls instead
   */
  public String createTenantAdmin(String username, Integer tenantId, String adminToken) {
    log.debug("Creating tenant admin: {} for tenant: {}", username, tenantId);

    RestAssuredTestConfig.configureForAdminApi(dockerStackConfig);

    Map<String, Object> request = new HashMap<>();
    request.put("username", username);
    request.put("email", username + "@example.com");
    request.put("firstName", "Test");
    request.put("lastName", "Admin");
    request.put("tenantId", tenantId);

    Response createResponse =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + adminToken)
            .body(request)
            .when()
            .post("/admins/tenant")
            .then()
            .statusCode(201)
            .extract()
            .response();

    Integer adminId = createResponse.jsonPath().getInt("adminId");

    log.debug("Created tenant admin with ID: {}", adminId);

    // Return null - caller should use global admin token for authentication
    // Full enrollment flow requires device keys which are handled separately
    return null;
  }

  /**
   * Creates an integration for a specific tenant via Admin API.
   *
   * <p>Creates a new integration assigned to the specified tenant.
   *
   * @param name Integration name
   * @param tenantId Tenant ID
   * @param adminToken Admin bearer token (must have access to tenant)
   * @return Integration ID
   */
  public Integer createIntegrationForTenant(String name, Integer tenantId, String adminToken) {
    log.debug("Creating integration: {} for tenant: {}", name, tenantId);

    RestAssuredTestConfig.configureForAdminApi(dockerStackConfig);

    // Generate unique code using UUID to prevent conflicts across test runs
    String code = "test-" + java.util.UUID.randomUUID().toString().substring(0, 8);

    Map<String, Object> request = new HashMap<>();
    request.put("code", code);
    request.put("name", name);
    request.put("description", "Test integration for tenant: " + tenantId);

    Response response =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + adminToken)
            .body(request)
            .when()
            .post("/integrations")
            .then()
            .statusCode(201)
            .extract()
            .response();

    Integer integrationId = response.jsonPath().getInt("id");
    log.debug("Created integration with ID: {} for tenant: {}", integrationId, tenantId);

    return integrationId;
  }

  /**
   * Creates an API key for an integration via Admin API.
   *
   * <p>Creates a new API key with a unique description to ensure idempotence.
   *
   * @param integrationId Integration ID
   * @param adminToken Admin bearer token
   * @return API key credentials as "integrationKey:secretKey"
   */
  public String createApiKeyForIntegration(Integer integrationId, String adminToken) {
    log.debug("Creating API key for integration: {}", integrationId);

    RestAssuredTestConfig.configureForAdminApi(dockerStackConfig);

    String uniqueSuffix = String.valueOf(System.currentTimeMillis());
    Map<String, Object> request = new HashMap<>();
    request.put("integrationId", integrationId);
    request.put("description", "Test API Key " + uniqueSuffix);

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

    String integrationKey = response.jsonPath().getString("integrationKey");
    String secretKey = response.jsonPath().getString("secretKey");

    String credentials = integrationKey + ":" + secretKey;
    log.debug("Created API key for integration: {}", integrationId);

    return credentials;
  }

  /**
   * Finds or creates a tenant by name.
   *
   * <p>Searches for existing tenant with matching name first. If found, returns its ID. Otherwise
   * creates a new tenant.
   *
   * <p>This is useful for test idempotence - tests can reference tenants by name and reuse existing
   * ones across runs.
   *
   * @param tenantName Tenant name
   * @param adminToken Admin bearer token (must be GlobalAdmin)
   * @return Tenant ID (existing or newly created)
   */
  public Integer findOrCreateTenant(String tenantName, String adminToken) {
    log.debug("Finding or creating tenant: {}", tenantName);

    RestAssuredTestConfig.configureForAdminApi(dockerStackConfig);

    // Try to find existing tenant (paginated list; request enough to find by name)
    Response listResponse =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + adminToken)
            .queryParam("page", 0)
            .queryParam("size", 100)
            .when()
            .get("/tenants")
            .then()
            .statusCode(200)
            .extract()
            .response();

    // Check if tenant with this name exists in first page
    java.util.List<Map<String, Object>> tenants = listResponse.jsonPath().getList("content");
    if (tenants != null) {
      for (Map<String, Object> tenant : tenants) {
        if (tenant != null && tenantName.equals(tenant.get("tenantName"))) {
          Integer tenantId = (Integer) tenant.get("tenantId");
          log.debug("Found existing tenant: {} with ID: {}", tenantName, tenantId);
          return tenantId;
        }
      }
    }

    // Not found, create new tenant
    log.debug("Tenant not found, creating new: {}", tenantName);
    return createTenant(tenantName, adminToken);
  }

  /**
   * Finds or creates an integration by name and tenant.
   *
   * <p>Searches for existing integration with matching name in the specified tenant. If found,
   * returns its ID. Otherwise creates a new integration.
   *
   * <p>This is useful for test idempotence - tests can reference integrations by name and reuse
   * existing ones across runs.
   *
   * @param name Integration name
   * @param tenantId Tenant ID (null for system tenant)
   * @param adminToken Admin bearer token
   * @return Integration ID (existing or newly created)
   */
  public Integer findOrCreateIntegration(String name, Integer tenantId, String adminToken) {
    log.debug("Finding or creating integration: {} for tenant: {}", name, tenantId);

    RestAssuredTestConfig.configureForAdminApi(dockerStackConfig);

    // Try to find existing integration
    Response listResponse =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + adminToken)
            .when()
            .get("/integrations")
            .then()
            .statusCode(200)
            .extract()
            .response();

    // Check if integration with this name exists for this tenant
    java.util.List<Map<String, Object>> integrations = listResponse.jsonPath().getList("content");
    if (integrations != null) {
      for (Map<String, Object> integration : integrations) {
        if (integration == null) {
          continue;
        }
        String integrationName = (String) integration.get("name");
        Integer integrationTenantId = (Integer) integration.get("tenantId");

        // Match name and tenant
        boolean tenantMatch =
            (tenantId == null && integrationTenantId == null)
                || (tenantId != null && tenantId.equals(integrationTenantId));

        if (name.equals(integrationName) && tenantMatch) {
          Integer integrationId = (Integer) integration.get("id");
          log.debug("Found existing integration: {} with ID: {}", name, integrationId);
          return integrationId;
        }
      }
    }

    // Not found, create new integration
    log.debug("Integration not found, creating new: {}", name);
    if (tenantId != null) {
      return createIntegrationForTenant(name, tenantId, adminToken);
    } else {
      return createIntegration(name, "Test integration");
    }
  }
}
