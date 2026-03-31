/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AdminInitialBootstrapTest
 * Description: Test for forcing initial bootstrap enrollment (one-time operation)
 */

package org.ezkey.tests.security.bootstrap;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.ezkey.tests.util.RestAssuredTestConfig.configureForAdminApi;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.ezkey.tests.security.AbstractSecurityTest;
import org.ezkey.tests.tags.TestTags;
import org.ezkey.tests.util.AdminBootstrapService;
import org.ezkey.tests.util.BootstrapCredentialsExtractor;
import org.ezkey.tests.util.DatabaseHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test for forcing initial bootstrap enrollment.
 *
 * <p>This test explicitly performs the initial bootstrap flow (device enrollment) even if device
 * credentials already exist. It is useful for:
 *
 * <ul>
 *   <li>Testing the bootstrap flow in isolation
 *   <li>Resetting device enrollment after Docker reset
 *   <li>Forcing a fresh enrollment when needed
 * </ul>
 *
 * <p><b>Note:</b> This test will delete existing device credentials to force a fresh bootstrap. For
 * normal test execution, use {@link org.ezkey.tests.security.admin.AdminTokenCreationTest} instead,
 * which is more efficient and idempotent.
 *
 * <p>This test validates:
 *
 * <ul>
 *   <li>Bootstrap credentials extraction
 *   <li>Device key pair generation
 *   <li>Device binding to enrollment
 *   <li>Enrollment verification
 *   <li>Device credentials persistence
 * </ul>
 *
 * @since 2025
 */
@Tag(TestTags.SLOW)
@Tag(TestTags.ADMIN)
@Tag(TestTags.DATABASE)
@DisplayName("Admin Initial Bootstrap Test")
public class AdminInitialBootstrapTest extends AbstractSecurityTest {

  private static final Logger log = LoggerFactory.getLogger(AdminInitialBootstrapTest.class);

  @Test
  @DisplayName("Force initial bootstrap enrollment (deletes existing device credentials)")
  public void testForceInitialBootstrap() throws IOException {
    // Load bootstrap credentials to get enrollment ID
    BootstrapCredentialsExtractor extractor = new BootstrapCredentialsExtractor();
    BootstrapCredentialsExtractor.BootstrapCredentials credentials =
        extractor.loadOrExtractCredentials();
    Integer enrollmentId = credentials.enrollmentId();

    // Reset enrollment in database to CREATED state to allow fresh bootstrap
    DatabaseHelper databaseHelper = new DatabaseHelper();

    String enrollmentStatus = databaseHelper.getEnrollmentStatus(enrollmentId);
    if ("VERIFIED".equals(enrollmentStatus) || "BOUND".equals(enrollmentStatus)) {
      log.info(
          "Enrollment {} is in {} state - Resetting to CREATED for fresh bootstrap",
          enrollmentId,
          enrollmentStatus);

      // For admin enrollments, AdminBootstrapService will handle the full rebind cycle
      // For other enrollments, just reset
      if (databaseHelper.isAdminEnrollment(enrollmentId)) {
        log.info(
            "Admin enrollment detected - AdminBootstrapService will perform full rebind cycle");
      }

      databaseHelper.resetEnrollment(enrollmentId);
      log.info("Enrollment {} reset to CREATED state", enrollmentId);
    }

    // Delete existing device credentials to force fresh bootstrap
    Path deviceCredentialsPath = Path.of(".ezkey-test/device-credentials.json");
    if (Files.exists(deviceCredentialsPath)) {
      Files.delete(deviceCredentialsPath);
      log.info("Deleted existing device credentials to force fresh bootstrap");
    }

    // Delete existing token to force fresh creation
    Path tokenPath = Path.of(".ezkey-test/admin-token.json");
    if (Files.exists(tokenPath)) {
      Files.delete(tokenPath);
      log.info("Deleted existing admin token to force fresh creation");
    }

    // Create bootstrap service
    AdminBootstrapService bootstrapService =
        new AdminBootstrapService(
            dockerStackConfig, bootstrapCredentialsExtractor, cryptoApiClient);

    // Perform bootstrap (will do initial bootstrap since credentials deleted)
    String adminToken = bootstrapService.ensureAdminToken();

    // Verify token is not null or empty
    assertThat(adminToken).isNotNull().isNotEmpty();

    // Verify device credentials were created
    assertThat(Files.exists(deviceCredentialsPath))
        .as("Device credentials should be created after bootstrap")
        .isTrue();

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
