/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Utility: DemoDeviceEnrollmentWriter
 * Description: Writes enrollment JSON files to demo-device container for manual testing and demos
 */

package org.ezkey.tests.util;

import static io.restassured.RestAssured.given;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Instant;
import java.util.Map;
import org.ezkey.tests.config.DockerStackConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class for writing enrollment JSON files to the demo-device container.
 *
 * <p>This utility creates enrollment files in the demo-device container's data directory, making
 * enrollments immediately available for manual testing and demos without requiring manual
 * enrollment through the web UI.
 *
 * <p><b>Usage Context:</b> Called after successful bootstrap to sync the initial global admin
 * enrollment to the demo-device application.
 *
 * <p><b>File Format:</b> Creates JSON files matching {@code EnrollmentStoreService.Record}
 * structure at {@code /app/data/enrollments/{enrollmentId}.json} inside the demo-device container.
 *
 * @since 2025
 */
public class DemoDeviceEnrollmentWriter {

  private static final Logger log = LoggerFactory.getLogger(DemoDeviceEnrollmentWriter.class);

  private static final String DEMO_DEVICE_CONTAINER_STANDARD = "ezkey-demo-device";
  private static final String DEMO_DEVICE_CONTAINER_HA = "ezkey-demo-device-ha";
  private static final String ENROLLMENTS_DIR = "/app/data/enrollments";

  private final DockerStackConfig dockerStackConfig;
  private final ObjectMapper objectMapper;

  /**
   * Creates a new DemoDeviceEnrollmentWriter.
   *
   * @param dockerStackConfig Docker stack configuration
   */
  public DemoDeviceEnrollmentWriter(DockerStackConfig dockerStackConfig) {
    this.dockerStackConfig = dockerStackConfig;
    this.objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
  }

  /**
   * Detects which demo-device container to use (standard vs HA).
   *
   * @return demo-device container name
   */
  private String detectDemoDeviceContainer() {
    if (containerExists(DEMO_DEVICE_CONTAINER_HA)) {
      log.debug("HA mode detected: Using demo-device container {}", DEMO_DEVICE_CONTAINER_HA);
      return DEMO_DEVICE_CONTAINER_HA;
    }
    return DEMO_DEVICE_CONTAINER_STANDARD;
  }

  /**
   * Checks if a Docker container exists.
   *
   * @param containerName container name to check
   * @return true if container exists, false otherwise
   */
  private boolean containerExists(String containerName) {
    try {
      ProcessBuilder processBuilder = new ProcessBuilder("docker", "inspect", containerName);
      processBuilder.redirectErrorStream(true);
      Process process = processBuilder.start();

      // Consume output to avoid blocking.
      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(process.getInputStream()))) {
        while (reader.readLine() != null) {
          // no-op
        }
      }

      int exitCode = process.waitFor();
      return exitCode == 0;
    } catch (Exception e) {
      log.debug("Container {} does not exist: {}", containerName, e.getMessage());
      return false;
    }
  }

  /**
   * Writes enrollment JSON file to demo-device container after successful bootstrap.
   *
   * <p>Retrieves enrollment and integration details from Admin API, constructs JSON matching
   * EnrollmentStoreService.Record format, and writes to demo-device container using docker exec.
   *
   * <p><b>Throws exception if write fails</b> - bootstrap must fail if demo-device sync fails.
   *
   * @param enrollmentId Enrollment ID
   * @param devicePublicKey Base64-encoded device public key
   * @param devicePrivateKey Base64-encoded device private key
   * @param enrollmentProofToken Enrollment proof token
   * @param adminToken Admin bearer token for API calls
   * @throws IllegalStateException if enrollment file cannot be written
   */
  public void writeEnrollmentFile(
      Integer enrollmentId,
      String devicePublicKey,
      String devicePrivateKey,
      String enrollmentProofToken,
      String adminToken) {
    log.info("═══════════════════════════════════════════════════════════════");
    log.info("Writing enrollment file to demo-device container...");
    log.info("═══════════════════════════════════════════════════════════════");
    log.info("   Enrollment ID: {}", enrollmentId);

    try {
      // Step 1: Get enrollment details from Admin API
      log.info("   Fetching enrollment details from Admin API...");
      RestAssuredTestConfig.configureForAdminApi(dockerStackConfig);
      Response enrollmentResponse =
          given()
              .contentType(ContentType.JSON)
              .header("Authorization", "Bearer " + adminToken)
              .when()
              .get("/enrollments/" + enrollmentId)
              .then()
              .extract()
              .response();

      if (enrollmentResponse.getStatusCode() != 200) {
        throw new IllegalStateException(
            "Failed to fetch enrollment details. Status: "
                + enrollmentResponse.getStatusCode()
                + ", Response: "
                + enrollmentResponse.asString());
      }

      String enrollmentName = enrollmentResponse.jsonPath().getString("enrollmentName");
      Boolean authAttemptChallengeRequired =
          enrollmentResponse.jsonPath().getBoolean("authAttemptChallengeRequired");
      Integer integrationId = enrollmentResponse.jsonPath().getInt("integrationId");
      String integrationPublicKey = enrollmentResponse.jsonPath().getString("integrationPublicKey");

      log.info("   Enrollment Name: {}", enrollmentName);
      log.info("   Integration ID: {}", integrationId);
      log.info("   Challenge Required: {}", authAttemptChallengeRequired);

      // Step 2: Get integration details if integrationId is present
      String integrationName = null;
      String integrationDescription = null;
      String integrationLogo = null;

      if (integrationId != null && integrationId > 0) {
        log.info("   Fetching integration details from Admin API...");
        Response integrationResponse =
            given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + adminToken)
                .when()
                .get("/integrations/" + integrationId)
                .then()
                .extract()
                .response();

        if (integrationResponse.getStatusCode() == 200) {
          // Extract i18n fields for language "en"
          var i18nList = integrationResponse.jsonPath().getList("i18n");
          if (i18nList != null && !i18nList.isEmpty()) {
            // Find first i18n entry (or filter by language "en" if available)
            var firstI18n = i18nList.get(0);
            if (firstI18n instanceof Map) {
              @SuppressWarnings("unchecked")
              Map<String, Object> i18nMap = (Map<String, Object>) firstI18n;
              integrationName = (String) i18nMap.get("name");
              integrationDescription = (String) i18nMap.get("description");
              integrationLogo = (String) i18nMap.get("logo");
            }
          }
          log.info("   Integration Name: {}", integrationName);
        } else {
          log.warn(
              "   Failed to fetch integration details. Status: {}",
              integrationResponse.getStatusCode());
        }
      }

      // Step 3: Construct JSON matching EnrollmentStoreService.Record format
      ObjectNode enrollmentJson = objectMapper.createObjectNode();
      enrollmentJson.put("enrollmentId", enrollmentId);
      enrollmentJson.putNull("integrationId");
      enrollmentJson.put("enrollmentName", enrollmentName != null ? enrollmentName : "");
      enrollmentJson.putNull("enrollmentUrl");
      enrollmentJson.put(
          "integrationPublicKey", integrationPublicKey != null ? integrationPublicKey : "");
      enrollmentJson.put("enrollmentProofToken", enrollmentProofToken);
      enrollmentJson.put("devicePublicKey", devicePublicKey);
      enrollmentJson.put("devicePrivateKey", devicePrivateKey);
      enrollmentJson.put(
          "authAttemptChallengeRequired",
          authAttemptChallengeRequired != null ? authAttemptChallengeRequired : true);
      enrollmentJson.put("deviceLabel", "Device");
      enrollmentJson.put("createdAt", Instant.now().toString());
      enrollmentJson.putNull("integrationName");
      enrollmentJson.putNull("integrationDescription");
      enrollmentJson.putNull("integrationLogo");

      // Override integration fields if integration details were fetched
      if (integrationId != null && integrationId > 0) {
        enrollmentJson.put("integrationId", integrationId);
        if (integrationName != null) {
          enrollmentJson.put("integrationName", integrationName);
        }
        if (integrationDescription != null) {
          enrollmentJson.put("integrationDescription", integrationDescription);
        }
        if (integrationLogo != null) {
          enrollmentJson.put("integrationLogo", integrationLogo);
        }
      }

      String jsonContent = objectMapper.writeValueAsString(enrollmentJson);
      log.debug("   Generated JSON content: {}", jsonContent);

      // Step 4: Write file to demo-device container using docker exec
      String fileName = enrollmentId + ".json";
      String filePath = ENROLLMENTS_DIR + "/" + fileName;
      log.info("   Writing file to container: {}", filePath);

      // Check if file already exists (idempotence - bootstrap-init may have already created it)
      if (fileExistsInContainer(filePath)) {
        log.info(
            "   ⏭️  Enrollment file already exists (likely created by bootstrap-init) - Skipping");
        log.info("   ✅ Enrollment file already present in demo-device container");
        return;
      }

      // Ensure directory exists
      ensureDirectoryExists();

      // Write JSON file using docker exec
      writeFileToContainer(filePath, jsonContent);

      log.info("✅ Enrollment file written successfully to demo-device container");
      log.info("   File: {}", filePath);
    } catch (Exception e) {
      log.error("❌ Failed to write enrollment file to demo-device container", e);
      throw new IllegalStateException(
          "Failed to write enrollment file to demo-device container: " + e.getMessage(), e);
    }
  }

  /**
   * Checks if a file exists in the demo-device container.
   *
   * <p>This method is used for idempotence - to check if bootstrap-init has already created the
   * enrollment file.
   *
   * @param filePath Path to file inside container
   * @return true if file exists, false otherwise
   */
  private boolean fileExistsInContainer(String filePath) {
    try {
      String containerName = detectDemoDeviceContainer();
      String escapedPath = filePath.replace("'", "'\\''");
      String command = "test -f " + escapedPath;
      ProcessBuilder processBuilder =
          new ProcessBuilder("docker", "exec", containerName, "sh", "-c", command);
      processBuilder.redirectErrorStream(true);

      Process process = processBuilder.start();

      // Consume output to avoid blocking
      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(process.getInputStream()))) {
        while (reader.readLine() != null) {
          // Consume output
        }
      }

      int exitCode = process.waitFor();
      return exitCode == 0;
    } catch (Exception e) {
      log.debug("Failed to check if file exists in container: {}", e.getMessage());
      return false;
    }
  }

  /**
   * Ensures the enrollments directory exists in the demo-device container.
   *
   * <p>Creates directory with correct permissions (spring:spring user) to match the application
   * runtime user.
   *
   * @throws IllegalStateException if directory creation fails
   */
  private void ensureDirectoryExists() {
    try {
      String containerName = detectDemoDeviceContainer();
      ProcessBuilder processBuilder =
          new ProcessBuilder(
              "docker",
              "exec",
              containerName,
              "sh",
              "-c",
              "su-exec spring:spring mkdir -p " + ENROLLMENTS_DIR);
      processBuilder.redirectErrorStream(true);

      Process process = processBuilder.start();

      StringBuilder output = new StringBuilder();
      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(process.getInputStream()))) {
        String line;
        while ((line = reader.readLine()) != null) {
          output.append(line).append("\n");
        }
      }

      int exitCode = process.waitFor();
      if (exitCode != 0) {
        log.warn(
            "Directory creation command returned exit code: {} (container: {})",
            exitCode,
            containerName);
        // Continue anyway - directory might already exist
      }
    } catch (Exception e) {
      log.warn("Failed to ensure directory exists (may already exist): {}", e.getMessage());
      // Continue anyway - directory might already exist
    }
  }

  /**
   * Writes JSON content to a file in the demo-device container using docker exec.
   *
   * <p>Uses stdin redirection: writes JSON to stdin of a shell command that creates the file. Uses
   * su-exec to write file as spring user (matching application runtime user) to ensure correct
   * permissions.
   *
   * @param filePath Path to file inside container
   * @param jsonContent JSON content to write
   * @throws IllegalStateException if file write fails
   */
  private void writeFileToContainer(String filePath, String jsonContent) {
    try {
      String containerName = detectDemoDeviceContainer();
      // Use su-exec to write file as spring user (matching application runtime user)
      // Simple approach: use double quotes for outer shell, single quotes for inner shell path
      // Escape any single quotes in filePath by replacing ' with '\''
      String escapedPath = filePath.replace("'", "'\\''");
      String command = "su-exec spring:spring sh -c 'cat > " + escapedPath + "'";
      log.debug("Executing command: {}", command);
      ProcessBuilder processBuilder =
          new ProcessBuilder("docker", "exec", "-i", containerName, "sh", "-c", command);
      processBuilder.redirectErrorStream(true);

      Process process = processBuilder.start();

      // Write JSON content to stdin
      try (var writer = process.getOutputStream()) {
        writer.write(jsonContent.getBytes());
        writer.flush();
      }

      // Read output for debugging
      StringBuilder output = new StringBuilder();
      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(process.getInputStream()))) {
        String line;
        while ((line = reader.readLine()) != null) {
          output.append(line).append("\n");
        }
      }

      int exitCode = process.waitFor();
      if (exitCode != 0) {
        log.error(
            "File write command failed with exit code: {} (container: {})",
            exitCode,
            containerName);
        log.error("Output: {}", output);
        throw new IllegalStateException(
            "Failed to write file to demo-device container. Exit code: "
                + exitCode
                + ", Output: "
                + output);
      }

      log.debug("File written successfully. Output: {}", output);
    } catch (IOException | InterruptedException e) {
      log.error("Failed to write file to demo-device container", e);
      throw new IllegalStateException(
          "Failed to write file to demo-device container: " + e.getMessage(), e);
    }
  }
}
