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
import java.nio.file.Paths;
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

  private static final String DOCKER_CONTAINER_NAME = "ezkey-admin-api";
  private static final String CREDENTIALS_FILE_PATH = ".ezkey-test/bootstrap-credentials.json";

  // Patterns for parsing logs
  private static final Pattern ENROLLMENT_ID_PATTERN = Pattern.compile("Enrollment ID:\\s*(\\d+)");
  // Proof token format: Base64 URL-safe parts separated by dots (e.g., "randomPart.timestamp.saltPart")
  // Base64 URL-safe includes: A-Z, a-z, 0-9, -, _ (no padding with withoutPadding())
  // Capture everything after "Enrollment Proof Token: " until end of line (non-greedy to stop at newline)
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
   * <p>Checks if credentials file exists and loads it, otherwise extracts from logs.
   *
   * @return BootstrapCredentials from file or logs
   */
  public BootstrapCredentials loadOrExtractCredentials() {
    Path credentialsPath = Paths.get(CREDENTIALS_FILE_PATH);

    if (Files.exists(credentialsPath)) {
      try {
        log.info("Loading bootstrap credentials from file: {}", CREDENTIALS_FILE_PATH);
        return loadCredentialsFromFile(credentialsPath);
      } catch (Exception e) {
        log.warn("Failed to load credentials from file, extracting from logs: {}", e.getMessage());
      }
    }

    return extractCredentials();
  }

  /**
   * Reads Docker container logs.
   *
   * @return Log content as string
   * @throws IOException if log reading fails
   * @throws InterruptedException if process is interrupted
   */
  private String readDockerLogs() throws IOException, InterruptedException {
    log.debug("Reading logs from Docker container: {}", DOCKER_CONTAINER_NAME);

    ProcessBuilder processBuilder = new ProcessBuilder("docker", "logs", DOCKER_CONTAINER_NAME);
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
      throw new IOException("Docker logs command failed with exit code: " + exitCode);
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
    log.debug("Extracted enrollment proof token length: {} chars", enrollmentProofToken.length());
    log.debug("Extracted enrollment proof token (first 50): {}...", 
        enrollmentProofToken.length() > 50 
            ? enrollmentProofToken.substring(0, 50) 
            : enrollmentProofToken);
    log.debug("Extracted enrollment proof token (last 30): ...{}", 
        enrollmentProofToken.length() > 30 
            ? enrollmentProofToken.substring(enrollmentProofToken.length() - 30) 
            : enrollmentProofToken);
    
    // Validate token format: should have 2 dots (3 parts: random.timestamp.salt)
    String[] parts = enrollmentProofToken.split("\\.");
    if (parts.length != 3) {
      log.error("Invalid proof token format: expected 3 parts separated by dots, got {} parts", parts.length);
      log.error("Token parts: {}", java.util.Arrays.toString(parts));
      throw new IllegalStateException(
          "Invalid proof token format: expected format 'randomPart.timestamp.saltPart', got: " 
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
    Path credentialsPath = Paths.get(CREDENTIALS_FILE_PATH);
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
