/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test Class: AuditReasonJustificationTest
 * Description: Functional tests verifying that the optional `reason` justification field is
 *     persisted in audit log entries and that the @Size constraint rejects too-short values.
 */

package org.ezkey.tests.security.audit;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.ezkey.tests.security.AbstractSecurityTest;
import org.ezkey.tests.tags.TestTags;
import org.ezkey.tests.util.RestAssuredTestConfig;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Functional tests verifying SOC 2 CC6.3 / CC8.1 audit reason justification field.
 *
 * <p><b>Scenarios covered:</b>
 *
 * <ol>
 *   <li><b>Revoke with reason:</b> The {@code reason} param is persisted in the {@code
 *       API_KEY_REVOKED} audit log entry and returned by the query endpoint.
 *   <li><b>Revoke without reason:</b> The {@code reason} field is {@code null} when no param is
 *       supplied.
 *   <li><b>Short reason rejected (400):</b> A {@code reason} shorter than 10 characters triggers
 *       the {@code @Size} constraint and returns HTTP 400 via {@code ConstraintViolationException}
 *       handler.
 * </ol>
 *
 * <p><b>Prerequisites:</b> Docker stack must be running with the Admin API accessible.
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Tag(TestTags.FAST)
@Tag(TestTags.AUDIT_INTEGRITY)
@DisplayName("Audit Log Reason Justification Field")
public class AuditReasonJustificationTest extends AbstractSecurityTest {

  private static final Logger log = LoggerFactory.getLogger(AuditReasonJustificationTest.class);

  private String adminToken;

  @Override
  @BeforeEach
  public void setUp() {
    super.setUp();
    try {
      RestAssuredTestConfig.configureForAdminApi(dockerStackConfig);
      adminToken = authTokenManager.getAdminToken();
      log.info("AuditReasonJustificationTest setup complete");
    } catch (Exception e) {
      Assumptions.assumeTrue(false, "Test setup failed: " + e.getMessage());
    }
  }

  // -----------------------------------------------------------------------
  // Test 1 – Revoke with reason → audit log persists the reason
  // -----------------------------------------------------------------------

  @Test
  @DisplayName("Revoke API key with reason – audit log entry contains the reason field")
  public void testRevokeWithReason_auditLogContainsReason() {
    RestAssuredTestConfig.configureForAdminApi(dockerStackConfig);

    // Arrange: create integration + API key to revoke
    Integer integrationId = testDataFactory.createIntegration();
    Integer apiKeyId = createApiKey(integrationId);
    log.info("Created API key {} for integration {}", apiKeyId, integrationId);

    String reason = "Security incident identified by SOC team";

    // Act: revoke with reason
    given()
        .header("Authorization", "Bearer " + adminToken)
        .queryParam("reason", reason)
        .when()
        .delete("/api-keys/" + apiKeyId)
        .then()
        .statusCode(204);

    // Assert: audit log must carry the reason
    Map<String, Object> entry = findLatestAuditEntry("API_KEY_REVOKED", "SUCCESS", apiKeyId);
    assertThat(entry).as("API_KEY_REVOKED audit entry must exist for key %d", apiKeyId).isNotNull();
    assertThat(entry.get("reason"))
        .as("Audit entry must carry the justification reason")
        .isEqualTo(reason);
    log.info("Verified: audit entry reason = '{}'", entry.get("reason"));
  }

  // -----------------------------------------------------------------------
  // Test 2 – Revoke without reason → audit log reason is null
  // -----------------------------------------------------------------------

  @Test
  @DisplayName("Revoke API key without reason – audit log entry reason is null")
  public void testRevokeWithoutReason_auditLogReasonIsNull() {
    RestAssuredTestConfig.configureForAdminApi(dockerStackConfig);

    // Arrange
    Integer integrationId = testDataFactory.createIntegration();
    Integer apiKeyId = createApiKey(integrationId);
    log.info("Created API key {} for no-reason revoke test", apiKeyId);

    // Act: revoke without any reason param
    given()
        .header("Authorization", "Bearer " + adminToken)
        .when()
        .delete("/api-keys/" + apiKeyId)
        .then()
        .statusCode(204);

    // Assert: reason must be absent (null) in the audit entry
    Map<String, Object> entry = findLatestAuditEntry("API_KEY_REVOKED", "SUCCESS", apiKeyId);
    assertThat(entry).as("API_KEY_REVOKED audit entry must exist for key %d", apiKeyId).isNotNull();
    assertThat(entry.get("reason"))
        .as("Audit entry reason must be null when no reason is provided")
        .isNull();
    log.info("Verified: audit entry reason is null (no reason supplied)");
  }

  // -----------------------------------------------------------------------
  // Test 3 – Short reason rejected with 400
  // -----------------------------------------------------------------------

  @Test
  @DisplayName("Revoke API key with short reason (< 10 chars) – returns 400 VALIDATION_ERROR")
  public void testRevokeWithShortReason_returns400() {
    RestAssuredTestConfig.configureForAdminApi(dockerStackConfig);

    // @Size validation fires in Spring AOP before the controller method body is
    // entered,
    // so we do not need a real key for this test — any key ID will surface the 400.
    Response response =
        given()
            .header("Authorization", "Bearer " + adminToken)
            .queryParam("reason", "short")
            .when()
            .delete("/api-keys/999")
            .then()
            .extract()
            .response();

    assertThat(response.getStatusCode())
        .as("Short reason must be rejected with HTTP 400")
        .isEqualTo(400);
    assertThat(response.jsonPath().getString("code"))
        .as("Error code must be VALIDATION_ERROR")
        .isEqualTo("VALIDATION_ERROR");
    log.info("Verified: short reason returns 400 VALIDATION_ERROR");
  }

  // -----------------------------------------------------------------------
  // Helpers
  // -----------------------------------------------------------------------

  /**
   * Creates a new API key for the given integration and returns its numeric {@code apiKeyId}.
   *
   * @param integrationId the integration to attach the key to
   * @return the new API key's integer ID
   */
  private Integer createApiKey(Integer integrationId) {
    Map<String, Object> request = new HashMap<>();
    request.put("integrationId", integrationId);
    request.put("description", "Reason test key " + System.currentTimeMillis());

    return given()
        .contentType(ContentType.JSON)
        .header("Authorization", "Bearer " + adminToken)
        .body(request)
        .when()
        .post("/api-keys")
        .then()
        .statusCode(201)
        .extract()
        .response()
        .jsonPath()
        .getInt("apiKeyId");
  }

  /**
   * Queries the audit log endpoint for entries matching the given event type and status, then finds
   * the entry whose {@code eventDetails} references the specified API key ID.
   *
   * @param eventType the event type string (e.g., {@code "API_KEY_REVOKED"})
   * @param eventStatus the event status string (e.g., {@code "SUCCESS"})
   * @param apiKeyId the API key ID to locate within {@code eventDetails}
   * @return the matching audit log entry map, or {@code null} if not found
   */
  private Map<String, Object> findLatestAuditEntry(
      String eventType, String eventStatus, Integer apiKeyId) {
    Response response =
        given()
            .header("Authorization", "Bearer " + adminToken)
            .queryParam("eventType", eventType)
            .queryParam("eventStatus", eventStatus)
            .queryParam("page", 0)
            .queryParam("size", 50)
            .queryParam("sort", "createdAt,DESC")
            .when()
            .get("/audit-logs")
            .then()
            .statusCode(200)
            .extract()
            .response();

    List<Map<String, Object>> content = response.jsonPath().getList("content");
    if (content == null || content.isEmpty()) {
      return null;
    }

    String keyMarker = "API key ID: " + apiKeyId;
    return content.stream()
        .filter(
            e -> {
              Object details = e.get("eventDetails");
              return details != null && details.toString().contains(keyMarker);
            })
        .findFirst()
        .orElse(null);
  }
}
