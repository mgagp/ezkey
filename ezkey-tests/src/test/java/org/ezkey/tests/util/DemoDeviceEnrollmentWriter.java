/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Utility: DemoDeviceEnrollmentWriter
 * Description: Writes enrollment JSON files to demo-device container for manual testing and demos
 */

package org.ezkey.tests.util;

import static io.restassured.RestAssured.given;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Instant;
import org.ezkey.tests.config.DockerStackConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

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
    ObjectMapper mapper = new ObjectMapper();
    mapper.writerWithDefaultPrettyPrinter();
    this.objectMapper = mapper;
  }

  /**
   * Detects which demo-device container to use (standard vs HA).
   *
   * @return demo-device container name
   * @throws IllegalStateException if no running demo-device container is found
   */
  private String detectDemoDeviceContainer() {
    // Check HA mode first
    if (containerIsRunning(DEMO_DEVICE_CONTAINER_HA)) {
      log.debug("HA mode detected: Using demo-device container {}", DEMO_DEVICE_CONTAINER_HA);
      return DEMO_DEVICE_CONTAINER_HA;
    }

    // Check standard mode
    if (containerIsRunning(DEMO_DEVICE_CONTAINER_STANDARD)) {
      log.debug(
          "Standard mode detected: Using demo-device container {}", DEMO_DEVICE_CONTAINER_STANDARD);
      return DEMO_DEVICE_CONTAINER_STANDARD;
    }

    // Container exists but is not running, or doesn't exist
    String errorMessage =
        String.format(
            "No running demo-device container found. "
                + "Expected one of: %s or %s. "
                + "Make sure the Docker stack is running and demo-device container is healthy.",
            DEMO_DEVICE_CONTAINER_STANDARD, DEMO_DEVICE_CONTAINER_HA);
    log.error(errorMessage);
    throw new IllegalStateException(errorMessage);
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
   * Checks if a Docker container is running (not just exists).
   *
   * @param containerName container name to check
   * @return true if container exists and is running, false otherwise
   */
  private boolean containerIsRunning(String containerName) {
    try {
      ProcessBuilder processBuilder =
          new ProcessBuilder("docker", "inspect", "--format", "{{.State.Running}}", containerName);
      processBuilder.redirectErrorStream(true);

      Process process = processBuilder.start();

      StringBuilder output = new StringBuilder();
      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(process.getInputStream()))) {
        String line;
        while ((line = reader.readLine()) != null) {
          output.append(line);
        }
      }

      int exitCode = process.waitFor();
      if (exitCode != 0) {
        log.debug("Container {} does not exist or cannot be inspected", containerName);
        return false;
      }

      String state = output.toString().trim();
      boolean isRunning = "true".equals(state);
      if (!isRunning) {
        log.warn("Container {} exists but is not running. State: {}", containerName, state);
      }
      return isRunning;
    } catch (Exception e) {
      log.debug("Failed to check if container '{}' is running: {}", containerName, e.getMessage());
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
    log.info(
        "   Device Public Key: {}...",
        devicePublicKey != null && devicePublicKey.length() > 30
            ? devicePublicKey.substring(0, 30) + "..."
            : devicePublicKey);
    log.info(
        "   Proof Token: {}...",
        enrollmentProofToken != null && enrollmentProofToken.length() > 30
            ? enrollmentProofToken.substring(0, 30) + "..."
            : enrollmentProofToken);
    log.info("   Admin Token present: {}", adminToken != null && !adminToken.isEmpty());

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
          integrationName = integrationResponse.jsonPath().getString("name");
          integrationDescription = integrationResponse.jsonPath().getString("description");
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

      // Override integration fields if integration details were fetched
      if (integrationId != null && integrationId > 0) {
        enrollmentJson.put("integrationId", integrationId);
        if (integrationName != null) {
          enrollmentJson.put("integrationName", integrationName);
        }
        if (integrationDescription != null) {
          enrollmentJson.put("integrationDescription", integrationDescription);
        }
      }

      String jsonContent = objectMapper.writeValueAsString(enrollmentJson);
      log.debug("   Generated JSON content: {}", jsonContent);

      // Step 4: Write file to demo-device container using docker exec
      String fileName = enrollmentId + ".json";
      String filePath = ENROLLMENTS_DIR + "/" + fileName;
      log.info("   Writing file to container: {}", filePath);

      // Check if file already exists (idempotence - bootstrap-init may have already
      // created it)
      if (fileExistsInContainer(filePath)) {
        log.info(
            "   ⏭️  Enrollment file already exists (likely created by bootstrap-init) -"
                + " Overwriting");
        log.info("   ⚠️  Overwriting existing file to ensure synchronization");
        // Continue to overwrite - we want to ensure the file is up-to-date
      }

      // Ensure directory exists
      ensureDirectoryExists();

      // Write JSON file using docker exec
      log.info("   Writing JSON content to file: {}", filePath);
      log.debug("   JSON content length: {} bytes", jsonContent.length());
      writeFileToContainer(filePath, jsonContent);

      log.info("✅ Enrollment file written successfully to demo-device container");
      log.info("   File: {}", filePath);
      log.info("   DemoDevice should now be able to authenticate with this enrollment");
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
   * runtime user. Fixes permissions on parent directory if needed (volume may have been created
   * with root ownership).
   *
   * @throws IllegalStateException if directory creation fails
   */
  private void ensureDirectoryExists() {
    try {
      String containerName = detectDemoDeviceContainer();

      // First, ensure parent directory /app/data exists and has correct ownership
      // This is needed because the volume may have been created with root ownership
      String fixParentPermissionsCommand =
          "mkdir -p /app/data && chown -R spring:spring /app/data && chmod 755 /app/data";
      ProcessBuilder fixParentBuilder =
          new ProcessBuilder(
              "docker", "exec", containerName, "sh", "-c", fixParentPermissionsCommand);
      fixParentBuilder.redirectErrorStream(true);

      Process fixParentProcess = fixParentBuilder.start();
      StringBuilder fixParentOutput = new StringBuilder();
      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(fixParentProcess.getInputStream()))) {
        String line;
        while ((line = reader.readLine()) != null) {
          fixParentOutput.append(line).append("\n");
        }
      }

      int fixParentExitCode = fixParentProcess.waitFor();
      if (fixParentExitCode != 0) {
        log.warn(
            "Failed to fix parent directory permissions (exit code: {}). Output: {}",
            fixParentExitCode,
            fixParentOutput);
        // Continue anyway - might still work
      } else {
        log.debug("Parent directory permissions fixed successfully");
      }

      // Create the enrollments directory (container already runs as spring user)
      ProcessBuilder processBuilder =
          new ProcessBuilder(
              "docker", "exec", containerName, "sh", "-c", "mkdir -p " + ENROLLMENTS_DIR);
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
        log.error(
            "Directory creation command returned exit code: {} (container: {}). Output: {}",
            exitCode,
            containerName,
            output);
        throw new IllegalStateException(
            "Failed to create enrollments directory. Exit code: "
                + exitCode
                + ", Output: "
                + output);
      }

      log.debug("Enrollments directory created successfully: {}", ENROLLMENTS_DIR);
    } catch (Exception e) {
      log.error("Failed to ensure directory exists: {}", e.getMessage(), e);
      throw new IllegalStateException(
          "Failed to ensure enrollments directory exists: " + e.getMessage(), e);
    }
  }

  /**
   * Writes JSON content to a file in the demo-device container using docker exec.
   *
   * <p>Uses stdin redirection: writes JSON to stdin of a shell command that creates the file. The
   * container runs directly as the spring user, so no privilege escalation is needed.
   *
   * @param filePath Path to file inside container
   * @param jsonContent JSON content to write
   * @throws IllegalStateException if file write fails
   */
  private void writeFileToContainer(String filePath, String jsonContent) {
    try {
      String containerName = detectDemoDeviceContainer();
      // Write file directly (container already runs as spring user)
      // Escape any single quotes in filePath by replacing ' with '\''
      String escapedPath = filePath.replace("'", "'\\''");
      String command = "sh -c 'cat > " + escapedPath + "'";
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
