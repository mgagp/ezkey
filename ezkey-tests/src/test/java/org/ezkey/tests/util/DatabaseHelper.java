/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Utility: DatabaseHelper
 * Description: Helper for opportunistic database access in E2E tests
 */

package org.ezkey.tests.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class for opportunistic database access in E2E tests.
 *
 * <p>Provides methods to query and update PostgreSQL database directly via docker exec, avoiding
 * API authentication requirements when appropriate. This is suitable for functional E2E tests in
 * clean room Docker environment.
 *
 * <p><b>Usage Context:</b> Use for:
 *
 * <ul>
 *   <li>State verification (faster than API calls)
 *   <li>Finding existing entities before creating new ones
 *   <li>Cleanup operations
 *   <li>Resetting state for test idempotence
 * </ul>
 *
 * <p><b>Database Configuration:</b> Assumes PostgreSQL container named "ezkey-postgres" with
 * database "ezkey_db" and user "postgres".
 *
 * @since 2025
 */
public class DatabaseHelper {

  private static final Logger log = LoggerFactory.getLogger(DatabaseHelper.class);

  private static final String DOCKER_CONTAINER = "ezkey-postgres";
  private static final String DATABASE = "ezkey_db";
  private static final String USER = "postgres";

  /**
   * Executes a SQL query and returns the result as a list of strings (one per row).
   *
   * @param sqlQuery SQL query to execute
   * @return List of result rows (trimmed strings), empty list if no results or error
   */
  public List<String> executeQuery(String sqlQuery) {
    log.debug("Executing SQL query: {}", sqlQuery);

    try {
      ProcessBuilder processBuilder =
          new ProcessBuilder(
              "docker",
              "exec",
              DOCKER_CONTAINER,
              "psql",
              "-U",
              USER,
              "-d",
              DATABASE,
              "-t",
              "-A",
              "-c",
              sqlQuery);
      processBuilder.redirectErrorStream(true);

      Process process = processBuilder.start();

      List<String> results = new ArrayList<>();
      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(process.getInputStream()))) {
        String line;
        while ((line = reader.readLine()) != null) {
          String trimmed = line.trim();
          if (!trimmed.isEmpty()) {
            results.add(trimmed);
          }
        }
      }

      int exitCode = process.waitFor();
      if (exitCode != 0) {
        log.warn("SQL query failed with exit code: {}", exitCode);
        return new ArrayList<>();
      }

      log.debug("Query returned {} rows", results.size());
      return results;
    } catch (Exception e) {
      log.warn("Failed to execute SQL query: {}", e.getMessage());
      return new ArrayList<>();
    }
  }

  /**
   * Executes a SQL query and returns the first result value as a string.
   *
   * @param sqlQuery SQL query to execute (should return single value)
   * @return First result value, or null if no results or error
   */
  public String executeQuerySingleValue(String sqlQuery) {
    List<String> results = executeQuery(sqlQuery);
    return results.isEmpty() ? null : results.get(0);
  }

  /**
   * Executes a SQL update/delete/insert statement.
   *
   * @param sqlStatement SQL statement to execute
   * @return true if successful, false otherwise
   */
  public boolean executeUpdate(String sqlStatement) {
    log.debug("Executing SQL update: {}", sqlStatement);

    try {
      ProcessBuilder processBuilder =
          new ProcessBuilder(
              "docker",
              "exec",
              DOCKER_CONTAINER,
              "psql",
              "-U",
              USER,
              "-d",
              DATABASE,
              "-c",
              sqlStatement);
      processBuilder.redirectErrorStream(true);

      Process process = processBuilder.start();

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
        log.error("SQL update failed with exit code: {}: {}", exitCode, output);
        return false;
      }

      log.debug("SQL update successful");
      return true;
    } catch (Exception e) {
      log.error("Failed to execute SQL update: {}", e.getMessage(), e);
      return false;
    }
  }

  /**
   * Checks enrollment status in database.
   *
   * @param enrollmentId Enrollment ID to check
   * @return Enrollment status (CREATED, BOUND, VERIFIED, INVALID) or null if not found
   */
  public String getEnrollmentStatus(Integer enrollmentId) {
    String sqlQuery =
        String.format(
            "SELECT enrollment_status FROM ezkey_enrollment WHERE enrollment_id = %d;",
            enrollmentId);
    return executeQuerySingleValue(sqlQuery);
  }

  /**
   * Finds integration ID by name (i18n).
   *
   * <p>Note: This is a simple lookup. For more complex queries, use executeQuery directly.
   *
   * @param integrationName Integration name to find
   * @return Integration ID, or null if not found
   */
  public Integer findIntegrationIdByName(String integrationName) {
    String sqlQuery =
        String.format(
            "SELECT i.integration_id FROM ezkey_integration i "
                + "JOIN ezkey_integration_i18n i18n ON i.integration_id = i18n.integration_id "
                + "WHERE i18n.integration_i18n_name = '%s' LIMIT 1;",
            integrationName.replace("'", "''")); // SQL injection protection
    String result = executeQuerySingleValue(sqlQuery);
    return result != null ? Integer.parseInt(result.trim()) : null;
  }

  /**
   * Resets enrollment to CREATED state (clears device binding).
   *
   * <p>This is appropriate for functional E2E tests in clean room Docker environment.
   *
   * @param enrollmentId Enrollment ID to reset
   * @throws IllegalStateException if reset fails
   */
  public void resetEnrollment(Integer enrollmentId) {
    log.info("Resetting enrollment {} in database to CREATED state...", enrollmentId);

    String sqlUpdate =
        String.format(
            "UPDATE ezkey_enrollment SET enrollment_status = 'CREATED', device_public_key = NULL,"
                + " device_public_key_hash = NULL WHERE enrollment_id = %d;",
            enrollmentId);

    boolean success = executeUpdate(sqlUpdate);
    if (!success) {
      log.error("Failed to reset enrollment {} in database", enrollmentId);
      throw new IllegalStateException(
          "Failed to reset enrollment " + enrollmentId + " in database");
    }
    log.info("✅ Enrollment {} reset successfully in database", enrollmentId);
  }

  /**
   * Deletes integration and all related data (cascade).
   *
   * <p><b>Warning:</b> This deletes all related enrollments, API keys, etc. Use with caution.
   *
   * @param integrationId Integration ID to delete
   * @return true if successful, false otherwise
   */
  public boolean deleteIntegration(Integer integrationId) {
    String sqlDelete =
        String.format("DELETE FROM ezkey_integration WHERE integration_id = %d;", integrationId);
    return executeUpdate(sqlDelete);
  }
}
