/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 *
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.tests.connector;

import static io.restassured.RestAssured.given;
import static org.ezkey.tests.util.RestAssuredTestConfig.configureForAdminApi;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.ezkey.tests.security.AbstractSecurityTest;
import org.ezkey.tests.util.DockerCliLocator;

/** Shared calls for the QR delivery functional tests. */
abstract class AbstractQrDeliveryFunctionalTest extends AbstractSecurityTest {

  protected String adminToken() {
    try {
      return authTokenManager.getAdminToken();
    } catch (Exception e) { // CHECKSTYLE IGNORE IllegalCatch
      throw new IllegalStateException("Could not get an admin token", e);
    }
  }

  protected Integer newIntegration() {
    return testDataFactory.createIntegration("connector-" + shortId(), "QR delivery check");
  }

  protected Integer newEnrollment(Integer integrationId, String contactEmail) {
    Map<String, Object> body = new HashMap<>();
    body.put("integrationId", integrationId);
    body.put("name", "delivery-" + shortId());
    body.put("authAttemptChallengeRequired", false);
    if (contactEmail != null) {
      body.put("contactEmail", contactEmail);
    }
    configureForAdminApi(dockerStackConfig);
    return given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + adminToken())
        .body(body)
        .when()
        .post("/enrollments")
        .then()
        .statusCode(201)
        .extract()
        .jsonPath()
        .getInt("enrollmentId");
  }

  protected Response send(Integer enrollmentId, String locale) {
    configureForAdminApi(dockerStackConfig);
    return given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + adminToken())
        .body(Map.of("locale", locale))
        .when()
        .post("/enrollments/" + enrollmentId + "/qr-delivery")
        .then()
        .extract()
        .response();
  }

  protected Response get(String path) {
    configureForAdminApi(dockerStackConfig);
    return given()
        .header("Authorization", "Bearer " + adminToken())
        .when()
        .get(path)
        .then()
        .extract()
        .response();
  }

  protected Response capability() {
    return get("/enrollments/qr-delivery/capability");
  }

  /** Runs the Docker CLI and returns its output, stderr included. */
  protected static String docker(String... args) throws IOException, InterruptedException {
    Process process = DockerCliLocator.processBuilder(args).redirectErrorStream(true).start();
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    if (process.waitFor() != 0) {
      throw new IllegalStateException("docker " + args[0] + " failed");
    }
    return output;
  }

  /** Runs one read-only query on the stack's database and returns unaligned rows. */
  protected static String query(String sql) throws IOException, InterruptedException {
    return docker(
        "exec", "ezkey-postgres", "psql", "-U", "postgres", "-d", "ezkey_db", "-At", "-c", sql);
  }

  private static String shortId() {
    return UUID.randomUUID().toString().substring(0, 8);
  }
}
