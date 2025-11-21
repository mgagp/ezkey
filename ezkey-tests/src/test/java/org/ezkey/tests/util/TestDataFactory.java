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

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.ezkey.tests.config.DockerStackConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;

/**
 * Factory class for creating test data via REST API calls.
 *
 * <p>Provides helper methods to create test entities (integrations, enrollments, auth attempts,
 * etc.) through the Admin API and Auth API. These methods simplify test setup by encapsulating
 * common creation patterns.
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
   * @param logo Logo URL (optional)
   * @return Integration ID
   */
  public Integer createIntegration(String name, String description, String logo) {
    log.debug("Creating integration: {}", name);

    RestAssuredTestConfig.configureForAdminApi(dockerStackConfig);

    Map<String, Object> i18n = new HashMap<>();
    i18n.put("lang", "en");
    i18n.put("name", name);
    i18n.put("description", description);

    Map<String, Object> request = new HashMap<>();
    request.put("logo", logo != null ? logo : "https://example.com/logo.png");
    request.put("i18n", new Object[] {i18n});

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

    Integer integrationId = response.jsonPath().getInt("integrationId");
    log.debug("Created integration with ID: {}", integrationId);

    return integrationId;
  }

  /**
   * Creates a test integration with default values.
   *
   * @return Integration ID
   */
  public Integer createIntegration() {
    return createIntegration("Test Integration", "Test Description", null);
  }

  /**
   * Creates a test enrollment via Admin API.
   *
   * @param integrationId Integration ID
   * @param name Enrollment name
   * @param challengeRequired Whether challenge is required
   * @return Enrollment ID
   */
  public Integer createEnrollment(
      Integer integrationId, String name, Boolean challengeRequired) {
    log.debug("Creating enrollment: {} for integration: {}", name, integrationId);

    RestAssuredTestConfig.configureForAdminApi(dockerStackConfig);

    Map<String, Object> request = new HashMap<>();
    request.put("integrationId", integrationId);
    request.put("name", name);
    request.put("authAttemptChallengeRequired", challengeRequired != null ? challengeRequired : false);

    Response response =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + authTokenManager.getAdminToken())
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
    log.debug("Creating auth attempt for enrollment: {}", enrollmentId);

    RestAssuredTestConfig.configureForAdminApi(dockerStackConfig);

    Map<String, Object> request = new HashMap<>();
    request.put("enrollmentId", enrollmentId);
    request.put("challengeRequested", challengeRequested != null ? challengeRequested : false);

    Response response =
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + authTokenManager.getAdminToken())
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
}

