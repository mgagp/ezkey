/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Utility: BootstrapCredentialsExtractor
 * Description: Extracts bootstrap credentials from Docker container logs
 */

package org.ezkey.tests.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for extracting bootstrap credentials from Docker container logs.
 *
 * <p>This class reads the Admin API Docker container logs and extracts the initial global admin
 * enrollment credentials that are logged during bootstrap. The credentials are saved to a local
 * JSON file for use in tests.
 *
 * <p><b>Usage:</b>
 *
 * <pre>
 * BootstrapCredentialsExtractor extractor = new BootstrapCredentialsExtractor();
 * BootstrapCredentials credentials = extractor.extractCredentials();
 * // credentials.enrollmentId, credentials.enrollmentProofToken, etc.
 * </pre>
 *
 * <p><b>Log Format Parsed:</b>
 *
 * <pre>
 * 📱 GLOBAL ADMIN PASSWORDLESS ENROLLMENT - SAVE THESE CREDENTIALS NOW!
 * 🔐 ENROLLMENT CREDENTIALS:
 *    Enrollment ID: 123
 *    Enrollment Proof Token: EZK-ABC123-DEF456
 *    Enrollment Challenge Code: 123456
 * 🔑 RECOVERY CODES (SAVE SECURELY - SINGLE USE ONLY):
 *    1. CODE1
 *    2. CODE2
 *    ...
 * </pre>
 *
 * @since 2025
 */
public class BootstrapCredentialsExtractor {

  private static final Logger log = LoggerFactory.getLogger(BootstrapCredentialsExtractor.class);

  private static final String DOCKER_CONTAINER_NAME_STANDARD = "ezkey-admin-api";
  private static final String DOCKER_CONTAINER_NAME_HA_1 = "ezkey-admin-api-1";
  private static final String DOCKER_CONTAINER_NAME_HA_2 = "ezkey-admin-api-2";
  private static final String CREDENTIALS_FILE_PATH = ".ezkey-test/bootstrap-credentials.json";
  private static final String SHEDLOCK_LOCK_NAME = "ADMIN_STARTUP_BOOTSTRAP";

  // Patterns for parsing logs
  private static final Pattern ENROLLMENT_ID_PATTERN = Pattern.compile("Enrollment ID:\\s*(\\d+)");
  // Proof token format: Base64 URL-safe parts separated by dots (e.g.,
  // "randomPart.timestamp.saltPart")
  // Base64 URL-safe includes: A-Z, a-z, 0-9, -, _ (no padding with withoutPadding())
  // Capture everything after "Enrollment Proof Token: " until end of line (non-greedy to stop at
  // newline)
  // Token format: ~43chars.~13digits.~22chars = ~80 chars total
  private static final Pattern ENROLLMENT_PROOF_TOKEN_PATTERN =
      Pattern.compile("Enrollment Proof Token:\\s*([^\\r\\n]+)", Pattern.MULTILINE);
  private static final Pattern ENROLLMENT_CHALLENGE_PATTERN =
      Pattern.compile("Enrollment Challenge Code:\\s*(\\d+)");
  private static final Pattern RECOVERY_CODE_PATTERN =
      Pattern.compile("\\s+\\d+\\.\\s+([A-Z0-9\\-]+)");

  /**
   * Represents bootstrap credentials extracted from logs.
   *
   * @param enrollmentId Enrollment ID
   * @param enrollmentProofToken Enrollment proof token
   * @param enrollmentChallengeCode Enrollment challenge code (6 digits)
   * @param recoveryCodes List of recovery codes
   */
  public record BootstrapCredentials(
      Integer enrollmentId,
      String enrollmentProofToken,
      Integer enrollmentChallengeCode,
      List<String> recoveryCodes) {}

  /**
   * Extracts bootstrap credentials from Docker container logs.
   *
   * <p>Reads logs from the Admin API container, parses the bootstrap credentials section, and
   * returns the extracted information.
   *
   * @return BootstrapCredentials with extracted information
   * @throws IllegalStateException if credentials cannot be extracted
   */
  public BootstrapCredentials extractCredentials() {
    log.info("Extracting bootstrap credentials from Docker container logs...");

    try {
      // Read logs from Docker container
      String logs = readDockerLogs();

      // Parse credentials from logs
      BootstrapCredentials credentials = parseCredentials(logs);

      // Save to file for future use
      saveCredentialsToFile(credentials);

      log.info("✅ Bootstrap credentials extracted successfully");
      log.debug("  Enrollment ID: {}", credentials.enrollmentId());
      log.debug(
          "  Enrollment Proof Token: {}...",
          credentials
              .enrollmentProofToken()
              .substring(0, Math.min(20, credentials.enrollmentProofToken().length())));

      return credentials;
    } catch (Exception e) {
      throw new IllegalStateException(
          "Failed to extract bootstrap credentials from Docker logs: " + e.getMessage(), e);
    }
  }

  /**
   * Loads bootstrap credentials from saved file if available.
   *
   * <p>Checks if credentials file exists and loads it, otherwise extracts from logs. Validates that
   * the token has the correct format (3 parts separated by dots).
   *
   * @return BootstrapCredentials from file or logs
   */
  public BootstrapCredentials loadOrExtractCredentials() {
    Path credentialsPath = Path.of(CREDENTIALS_FILE_PATH);

    if (Files.exists(credentialsPath)) {
      try {
        log.info("Loading bootstrap credentials from file: {}", CREDENTIALS_FILE_PATH);
        BootstrapCredentials credentials = loadCredentialsFromFile(credentialsPath);

        // Validate token format before using cached credentials
        String token = credentials.enrollmentProofToken();
        if (token != null) {
          String[] parts = token.split("\\.");
          if (parts.length != 3) {
            log.warn(
                "Cached token has invalid format (expected 3 parts, got {}). Re-extracting from"
                    + " logs.",
                parts.length);
            log.warn(
                "Token (first 100 chars): {}",
                token.length() > 100 ? token.substring(0, 100) + "..." : token);
            // Delete invalid cache file and re-extract
            try {
              Files.delete(credentialsPath);
              log.info("Deleted invalid credentials cache file");
            } catch (IOException e) {
              log.warn("Failed to delete invalid cache file: {}", e.getMessage());
            }
            return extractCredentials();
          }
        }

        return credentials;
      } catch (Exception e) {
        log.warn("Failed to load credentials from file, extracting from logs: {}", e.getMessage());
      }
    }

    return extractCredentials();
  }

  /**
   * Detects which Admin API container to use based on environment (standard or HA mode).
   *
   * @return Container name to use for reading logs
   * @throws IllegalStateException if no valid container is found
   */
  private String detectAdminApiContainer() throws IOException, InterruptedException {
    // Check if HA mode containers exist
    boolean haContainer1Exists = containerExists(DOCKER_CONTAINER_NAME_HA_1);
    boolean haContainer2Exists = containerExists(DOCKER_CONTAINER_NAME_HA_2);

    if (haContainer1Exists && haContainer2Exists) {
      log.info(
          "HA mode detected: Found containers {} and {}",
          DOCKER_CONTAINER_NAME_HA_1,
          DOCKER_CONTAINER_NAME_HA_2);
      // Find which instance created the admin global
      return findInstanceWithBootstrapLogs();
    }

    // Standard mode
    if (containerExists(DOCKER_CONTAINER_NAME_STANDARD)) {
      log.debug("Standard mode detected: Using container {}", DOCKER_CONTAINER_NAME_STANDARD);
      return DOCKER_CONTAINER_NAME_STANDARD;
    }

    throw new IllegalStateException(
        "No Admin API container found. Expected one of: "
            + DOCKER_CONTAINER_NAME_STANDARD
            + ", "
            + DOCKER_CONTAINER_NAME_HA_1
            + ", or "
            + DOCKER_CONTAINER_NAME_HA_2);
  }

  /**
   * Checks if a Docker container exists.
   *
   * @param containerName Container name to check
   * @return true if container exists, false otherwise
   */
  private boolean containerExists(String containerName) {
    try {
      ProcessBuilder processBuilder = new ProcessBuilder("docker", "inspect", containerName);
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
      log.debug("Container {} does not exist: {}", containerName, e.getMessage());
      return false;
    }
  }

  /**
   * Finds which HA instance created the bootstrap credentials by checking logs or ShedLock table.
   *
   * @return Container name of the instance that created the admin
   * @throws IllegalStateException if instance cannot be identified
   */
  private String findInstanceWithBootstrapLogs() throws IOException, InterruptedException {
    // Strategy 1: Try to query ShedLock table to find which instance holds/held the lock
    String instanceFromShedLock = findInstanceFromShedLock();
    if (instanceFromShedLock != null) {
      log.info("Found bootstrap instance hint from ShedLock: {}", instanceFromShedLock);

      // Validate the hint: the enrollment banner must exist in logs, otherwise fall back.
      try {
        String logs = readDockerLogs(instanceFromShedLock);
        if (logs.contains("GLOBAL ADMIN PASSWORDLESS ENROLLMENT")) {
          log.info("Validated bootstrap banner present in logs of: {}", instanceFromShedLock);
          return instanceFromShedLock;
        }
        log.warn(
            "ShedLock-selected instance '{}' does not contain bootstrap credentials banner. Will"
                + " scan both instances.",
            instanceFromShedLock);
      } catch (Exception e) {
        log.warn(
            "Failed to read logs from ShedLock-selected instance '{}': {}. Will scan both"
                + " instances.",
            instanceFromShedLock,
            e.getMessage());
      }
    }

    // Strategy 2: Read logs from both instances and find the one with bootstrap credentials
    log.info("ShedLock query failed or returned no result, checking logs of both instances...");
    for (String container : new String[] {DOCKER_CONTAINER_NAME_HA_1, DOCKER_CONTAINER_NAME_HA_2}) {
      try {
        String logs = readDockerLogs(container);
        if (logs.contains("GLOBAL ADMIN PASSWORDLESS ENROLLMENT")) {
          log.info("Found bootstrap credentials in logs of container: {}", container);
          return container;
        }
      } catch (Exception e) {
        log.debug("Failed to read logs from {}: {}", container, e.getMessage());
      }
    }

    throw new IllegalStateException(
        "Could not identify which HA instance created the admin global. "
            + "Bootstrap credentials not found in logs of either "
            + DOCKER_CONTAINER_NAME_HA_1
            + " or "
            + DOCKER_CONTAINER_NAME_HA_2);
  }

  /**
   * Queries ShedLock table to find which instance holds or held the bootstrap lock.
   *
   * @return Container name (e.g., "ezkey-admin-api-1") or null if not found
   */
  private String findInstanceFromShedLock() {
    try {
      // Query ShedLock table for ADMIN_STARTUP_BOOTSTRAP lock
      // locked_by column contains host identifier (often Docker container ID prefix)
      ProcessBuilder processBuilder =
          new ProcessBuilder(
              "docker",
              "exec",
              "ezkey-postgres-ha",
              "psql",
              "-U",
              "postgres",
              "-d",
              "ezkey_db",
              "-t",
              "-A",
              "-c",
              "SELECT locked_by FROM ezkey_shedlock WHERE name = '"
                  + SHEDLOCK_LOCK_NAME
                  + "' ORDER BY locked_at DESC LIMIT 1;");
      processBuilder.redirectErrorStream(true);

      Process process = processBuilder.start();

      StringBuilder output = new StringBuilder();
      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(process.getInputStream()))) {
        String line;
        while ((line = reader.readLine()) != null) {
          String trimmed = line.trim();
          if (!trimmed.isEmpty()) {
            output.append(trimmed);
          }
        }
      }

      int exitCode = process.waitFor();
      if (exitCode != 0) {
        log.debug("ShedLock query failed with exit code: {}", exitCode);
        return null;
      }

      String lockedBy = output.toString().trim();
      if (lockedBy.isEmpty()) {
        log.debug("No lock found in ShedLock table for {}", SHEDLOCK_LOCK_NAME);
        return null;
      }

      log.debug("Found locked_by in ShedLock: {}", lockedBy);

      // Map locked_by to a specific container by comparing with Docker container ID prefix.
      // In Docker, the container hostname is often the first 12 chars of the container ID.
      String ha1IdPrefix = getDockerContainerIdPrefix(DOCKER_CONTAINER_NAME_HA_1);
      String ha2IdPrefix = getDockerContainerIdPrefix(DOCKER_CONTAINER_NAME_HA_2);

      if (ha1IdPrefix != null && lockedBy.equalsIgnoreCase(ha1IdPrefix)) {
        return DOCKER_CONTAINER_NAME_HA_1;
      }
      if (ha2IdPrefix != null && lockedBy.equalsIgnoreCase(ha2IdPrefix)) {
        return DOCKER_CONTAINER_NAME_HA_2;
      }

      // Some environments may store a different locked_by (e.g., custom instance id). Handle that
      // too.
      if (lockedBy.contains("admin-api-1")) {
        return DOCKER_CONTAINER_NAME_HA_1;
      }
      if (lockedBy.contains("admin-api-2")) {
        return DOCKER_CONTAINER_NAME_HA_2;
      }

      log.warn(
          "Unexpected locked_by value: '{}' (admin-api-1 idPrefix='{}', admin-api-2 idPrefix='{}');"
              + " cannot map to container reliably.",
          lockedBy,
          ha1IdPrefix,
          ha2IdPrefix);
      return null;
    } catch (Exception e) {
      log.debug("Failed to query ShedLock table: {}", e.getMessage());
      return null;
    }
  }

  /**
   * Returns the Docker container ID prefix (first 12 hex chars) for a given container.
   *
   * <p>This is used to map ShedLock's {@code locked_by} (often hostname == container ID prefix) to
   * the correct HA instance.
   *
   * @param containerName Docker container name (e.g., {@code ezkey-admin-api-1})
   * @return 12-char container ID prefix, or null if not available
   */
  private String getDockerContainerIdPrefix(String containerName) {
    try {
      ProcessBuilder processBuilder =
          new ProcessBuilder("docker", "inspect", "-f", "{{.Id}}", containerName);
      processBuilder.redirectErrorStream(true);
      Process process = processBuilder.start();

      StringBuilder output = new StringBuilder();
      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(process.getInputStream()))) {
        String line;
        while ((line = reader.readLine()) != null) {
          String trimmed = line.trim();
          if (!trimmed.isEmpty()) {
            output.append(trimmed);
          }
        }
      }

      int exitCode = process.waitFor();
      if (exitCode != 0) {
        log.debug("docker inspect failed for {} with exit code {}", containerName, exitCode);
        return null;
      }

      String fullId = output.toString().trim();
      if (fullId.length() < 12) {
        return null;
      }
      return fullId.substring(0, 12);
    } catch (Exception e) {
      log.debug("Failed to inspect docker container id for {}: {}", containerName, e.getMessage());
      return null;
    }
  }

  /**
   * Reads Docker container logs.
   *
   * @return Log content as string
   * @throws IOException if log reading fails
   * @throws InterruptedException if process is interrupted
   */
  private String readDockerLogs() throws IOException, InterruptedException {
    String containerName = detectAdminApiContainer();
    return readDockerLogs(containerName);
  }

  /**
   * Reads Docker container logs from a specific container.
   *
   * @param containerName Container name to read logs from
   * @return Log content as string
   * @throws IOException if log reading fails
   * @throws InterruptedException if process is interrupted
   */
  private String readDockerLogs(String containerName) throws IOException, InterruptedException {
    log.debug("Reading logs from Docker container: {}", containerName);

    ProcessBuilder processBuilder = new ProcessBuilder("docker", "logs", containerName);
    processBuilder.redirectErrorStream(true);

    Process process = processBuilder.start();

    StringBuilder logs = new StringBuilder();
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(process.getInputStream()))) {
      String line;
      while ((line = reader.readLine()) != null) {
        logs.append(line).append("\n");
      }
    }

    int exitCode = process.waitFor();
    if (exitCode != 0) {
      throw new IOException(
          "Docker logs command failed with exit code: "
              + exitCode
              + " for container: "
              + containerName);
    }

    return logs.toString();
  }

  /**
   * Parses credentials from log content.
   *
   * @param logs Log content to parse
   * @return BootstrapCredentials with extracted information
   * @throws IllegalStateException if required information cannot be found
   */
  private BootstrapCredentials parseCredentials(String logs) {
    log.debug("Parsing credentials from logs...");

    // Find the bootstrap credentials section
    int credentialsStart = logs.indexOf("GLOBAL ADMIN PASSWORDLESS ENROLLMENT");
    if (credentialsStart == -1) {
      throw new IllegalStateException(
          "Bootstrap credentials section not found in logs. "
              + "Ensure Admin API has completed bootstrap.");
    }

    // Extract the credentials section (next 200 lines should be enough)
    String credentialsSection =
        logs.substring(credentialsStart, Math.min(credentialsStart + 5000, logs.length()));

    // Extract enrollment ID
    Matcher enrollmentIdMatcher = ENROLLMENT_ID_PATTERN.matcher(credentialsSection);
    if (!enrollmentIdMatcher.find()) {
      throw new IllegalStateException("Enrollment ID not found in logs");
    }
    Integer enrollmentId = Integer.parseInt(enrollmentIdMatcher.group(1));

    // Extract enrollment proof token
    Matcher tokenMatcher = ENROLLMENT_PROOF_TOKEN_PATTERN.matcher(credentialsSection);
    if (!tokenMatcher.find()) {
      log.error("Failed to find enrollment proof token in logs. Credentials section preview:");
      log.error(credentialsSection.substring(0, Math.min(500, credentialsSection.length())));
      throw new IllegalStateException("Enrollment Proof Token not found in logs");
    }
    String enrollmentProofToken = tokenMatcher.group(1).trim(); // Remove any trailing whitespace

    // Debug: Log the raw matched string to see if it's complete
    String rawMatch = tokenMatcher.group(0);
    log.debug("Raw regex match: '{}'", rawMatch);
    log.debug("Extracted token (group 1): '{}'", enrollmentProofToken);
    log.debug("Extracted enrollment proof token length: {} chars", enrollmentProofToken.length());
    log.debug(
        "Extracted enrollment proof token (first 50): {}...",
        enrollmentProofToken.length() > 50
            ? enrollmentProofToken.substring(0, 50)
            : enrollmentProofToken);
    log.debug(
        "Extracted enrollment proof token (last 30): ...{}",
        enrollmentProofToken.length() > 30
            ? enrollmentProofToken.substring(enrollmentProofToken.length() - 30)
            : enrollmentProofToken);

    // Validate token format: should have 1 dot (2 parts: randomPart.saltPart)
    String[] parts = enrollmentProofToken.split("\\.");
    if (parts.length != 2) {
      log.error(
          "Invalid proof token format: expected 2 parts separated by dots, got {} parts",
          parts.length);
      log.error("Token parts: {}", java.util.Arrays.toString(parts));
      throw new IllegalStateException(
          "Invalid proof token format: expected format 'randomPart.saltPart', got: "
              + enrollmentProofToken.substring(0, Math.min(100, enrollmentProofToken.length())));
    }

    // Extract enrollment challenge code
    Matcher challengeMatcher = ENROLLMENT_CHALLENGE_PATTERN.matcher(credentialsSection);
    if (!challengeMatcher.find()) {
      throw new IllegalStateException("Enrollment Challenge Code not found in logs");
    }
    Integer enrollmentChallengeCode = Integer.parseInt(challengeMatcher.group(1));

    // Extract recovery codes
    List<String> recoveryCodes = new ArrayList<>();
    int recoveryStart = credentialsSection.indexOf("RECOVERY CODES");
    if (recoveryStart != -1) {
      String recoverySection =
          credentialsSection.substring(
              recoveryStart, Math.min(recoveryStart + 1000, credentialsSection.length()));
      Matcher recoveryMatcher = RECOVERY_CODE_PATTERN.matcher(recoverySection);
      while (recoveryMatcher.find()) {
        recoveryCodes.add(recoveryMatcher.group(1));
      }
    }

    if (recoveryCodes.isEmpty()) {
      log.warn("No recovery codes found in logs (may be normal if already bound)");
    }

    return new BootstrapCredentials(
        enrollmentId, enrollmentProofToken, enrollmentChallengeCode, recoveryCodes);
  }

  /**
   * Saves credentials to JSON file.
   *
   * @param credentials Credentials to save
   * @throws IOException if file writing fails
   */
  private void saveCredentialsToFile(BootstrapCredentials credentials) throws IOException {
    Path credentialsPath = Path.of(CREDENTIALS_FILE_PATH);
    Path parentDir = credentialsPath.getParent();

    // Create directory if it doesn't exist
    if (parentDir != null && !Files.exists(parentDir)) {
      Files.createDirectories(parentDir);
    }

    ObjectMapper mapper = new ObjectMapper();
    ObjectNode jsonNode = mapper.createObjectNode();
    jsonNode.put("enrollmentId", credentials.enrollmentId());
    jsonNode.put("enrollmentProofToken", credentials.enrollmentProofToken());
    jsonNode.put("enrollmentChallengeCode", credentials.enrollmentChallengeCode());
    jsonNode
        .putArray("recoveryCodes")
        .addAll(
            credentials.recoveryCodes().stream()
                .map(code -> mapper.getNodeFactory().textNode(code))
                .toList());

    mapper.writerWithDefaultPrettyPrinter().writeValue(credentialsPath.toFile(), jsonNode);

    log.debug("Credentials saved to: {}", CREDENTIALS_FILE_PATH);
  }

  /**
   * Loads credentials from JSON file.
   *
   * @param credentialsPath Path to credentials file
   * @return BootstrapCredentials loaded from file
   * @throws IOException if file reading fails
   */
  private BootstrapCredentials loadCredentialsFromFile(Path credentialsPath) throws IOException {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode jsonNode = (ObjectNode) mapper.readTree(credentialsPath.toFile());

    Integer enrollmentId = jsonNode.get("enrollmentId").asInt();
    String enrollmentProofToken = jsonNode.get("enrollmentProofToken").asText();
    Integer enrollmentChallengeCode = jsonNode.get("enrollmentChallengeCode").asInt();

    List<String> recoveryCodes = new ArrayList<>();
    if (jsonNode.has("recoveryCodes")) {
      jsonNode.get("recoveryCodes").forEach(code -> recoveryCodes.add(code.asText()));
    }

    return new BootstrapCredentials(
        enrollmentId, enrollmentProofToken, enrollmentChallengeCode, recoveryCodes);
  }
}
