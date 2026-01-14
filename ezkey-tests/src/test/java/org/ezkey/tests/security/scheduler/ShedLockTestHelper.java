/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Utility: ShedLockTestHelper
 * Description: Helper for querying ShedLock table in PostgreSQL for HA testing.
 */

package org.ezkey.tests.security.scheduler;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class for querying ShedLock distributed locks table in PostgreSQL.
 *
 * <p>Provides methods to query the `ezkey_shedlock` table directly via docker exec to verify distributed
 * locking behavior in HA deployments. This is suitable for functional E2E tests validating ShedLock
 * exclusion mutuelle.
 *
 * <p><b>Usage Context:</b> Use for:
 *
 * <ul>
 *   <li>Verifying that only one instance holds a lock for a scheduled job
 *   <li>Checking lock expiration times
 *   <li>Validating lock ownership (locked_by column)
 *   <li>Monitoring lock acquisition/release cycles
 * </ul>
 *
 * <p><b>Database Configuration:</b> Assumes PostgreSQL container named "ezkey-postgres-ha" with
 * database "ezkey_db" and user "postgres".
 *
 * @since 2025
 */
public class ShedLockTestHelper {

  private static final Logger log = LoggerFactory.getLogger(ShedLockTestHelper.class);

  // Container names - auto-detect based on environment
  private static final String DOCKER_CONTAINER_STANDARD = "ezkey-postgres";
  private static final String DOCKER_CONTAINER_HA = "ezkey-postgres-ha";
  private static final String DATABASE = "ezkey_db";
  private static final String USER = "postgres";

  // Detected container name (lazy initialization)
  private String dockerContainer = null;

  /**
   * Represents a ShedLock entry from the database.
   *
   * @param name lock name (job identifier)
   * @param lockedBy instance identifier that holds the lock
   * @param lockedAt when the lock was acquired
   * @param lockUntil when the lock expires
   * @param isActive whether the lock is currently active (lock_until > NOW())
   */
  public record ShedLockEntry(
      String name,
      String lockedBy,
      OffsetDateTime lockedAt,
      OffsetDateTime lockUntil,
      boolean isActive) {}

  /**
   * Verifies that the ezkey_shedlock table exists in the database.
   *
   * @return true if table exists, false otherwise
   */
  public boolean verifyShedLockTableExists() {
    String sqlQuery =
        "SELECT EXISTS ("
            + "SELECT FROM information_schema.tables "
            + "WHERE table_schema = 'public' "
            + "AND table_name = 'ezkey_shedlock'"
            + ");";

    List<String> results = executeQuery(sqlQuery);
    if (results.isEmpty()) {
      log.warn("Could not verify if ezkey_shedlock table exists (query returned no results)");
      return false;
    }

    String exists = results.get(0).trim();
    boolean tableExists = "t".equals(exists) || "true".equalsIgnoreCase(exists);
    if (!tableExists) {
      log.warn(
          "ezkey_shedlock table does not exist in database. "
              + "ShedLock should create it automatically when the first scheduled job runs. "
              + "Make sure scheduled jobs are enabled and at least one job has executed.");
    } else {
      log.debug("ezkey_shedlock table exists in database");
    }

    return tableExists;
  }

  /**
   * Detects which PostgreSQL container to use based on environment (standard or HA mode).
   *
   * @return Container name to use for database operations
   */
  private String detectPostgresContainer() {
    if (dockerContainer != null) {
      return dockerContainer;
    }

    // Check if HA mode container exists
    if (containerExists(DOCKER_CONTAINER_HA)) {
      log.debug("HA mode detected: Using PostgreSQL container {}", DOCKER_CONTAINER_HA);
      dockerContainer = DOCKER_CONTAINER_HA;
      return dockerContainer;
    }

    // Fallback to standard mode
    if (containerExists(DOCKER_CONTAINER_STANDARD)) {
      log.debug("Standard mode detected: Using PostgreSQL container {}", DOCKER_CONTAINER_STANDARD);
      dockerContainer = DOCKER_CONTAINER_STANDARD;
      return dockerContainer;
    }

    // Default to HA (will fail with clear error if container doesn't exist)
    log.warn(
        "Neither {} nor {} found, defaulting to {}",
        DOCKER_CONTAINER_HA,
        DOCKER_CONTAINER_STANDARD,
        DOCKER_CONTAINER_HA);
    dockerContainer = DOCKER_CONTAINER_HA;
    return dockerContainer;
  }

  /**
   * Checks if a Docker container exists.
   *
   * @param containerName the container name to check
   * @return true if container exists, false otherwise
   */
  private boolean containerExists(String containerName) {
    try {
      ProcessBuilder processBuilder =
          new ProcessBuilder("docker", "inspect", "--format", "{{.State.Running}}", containerName);
      processBuilder.redirectErrorStream(true);

      Process process = processBuilder.start();

      StringBuilder output = new StringBuilder();
      try (java.io.BufferedReader reader =
          new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
        String line;
        while ((line = reader.readLine()) != null) {
          output.append(line);
        }
      }

      int exitCode = process.waitFor();
      if (exitCode != 0) {
        return false;
      }

      String state = output.toString().trim();
      return "true".equals(state);
    } catch (Exception e) {
      log.debug("Failed to check container '{}': {}", containerName, e.getMessage());
      return false;
    }
  }

  /**
   * Verifies that the PostgreSQL container exists and is accessible.
   *
   * @return true if container exists and is accessible, false otherwise
   */
  public boolean verifyContainerAccessible() {
    String container = detectPostgresContainer();
    if (container == null) {
      log.warn(
          "No PostgreSQL container found. Make sure Docker stack is running (standard:"
              + " ./docker/start.sh or HA: ./docker/start-ha.sh)");
      return false;
    }

    boolean exists = containerExists(container);
    if (exists) {
      log.debug("Container '{}' is accessible and running", container);
    } else {
      log.warn(
          "Container '{}' does not exist or is not running. "
              + "Make sure the Docker stack is running.",
          container);
    }
    return exists;
  }

  /**
   * Checks if we are running in HA mode (multiple instances).
   *
   * @return true if HA mode detected, false if standard mode
   */
  public boolean isHaMode() {
    String container = detectPostgresContainer();
    return DOCKER_CONTAINER_HA.equals(container);
  }

  /**
   * Executes a SQL query and returns the result as a list of strings (one per row).
   *
   * @param sqlQuery SQL query to execute
   * @return List of result rows (trimmed strings), empty list if no results or error
   */
  private List<String> executeQuery(String sqlQuery) {
    log.debug("Executing SQL query: {}", sqlQuery);

    String container = detectPostgresContainer();
    try {
      ProcessBuilder processBuilder =
          new ProcessBuilder(
              "docker", "exec", container, "psql", "-U", USER, "-d", DATABASE, "-t", "-A", "-c",
              sqlQuery);
      processBuilder.redirectErrorStream(true);

      Process process = processBuilder.start();

      List<String> results = new ArrayList<>();
      List<String> errorOutput = new ArrayList<>();
      try (java.io.BufferedReader reader =
          new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
        String line;
        while ((line = reader.readLine()) != null) {
          String trimmed = line.trim();
          if (!trimmed.isEmpty()) {
            // Check if this looks like an error message
            if (trimmed.toLowerCase().contains("error")
                || trimmed.toLowerCase().contains("fatal")
                || trimmed.toLowerCase().contains("could not")
                || trimmed.toLowerCase().contains("does not exist")) {
              errorOutput.add(trimmed);
            }
            results.add(trimmed);
          }
        }
      }

      int exitCode = process.waitFor();
      if (exitCode != 0) {
        log.warn(
            "SQL query failed with exit code: {} (container: {}). Output:\n{}",
            exitCode,
            container,
            String.join("\n", results.isEmpty() ? errorOutput : results));
        return new ArrayList<>();
      }

      log.debug("Query returned {} rows", results.size());
      return results;
    } catch (Exception e) {
      log.warn(
          "Failed to execute SQL query: {} (container: {}). Error: {}",
          sqlQuery,
          container,
          e.getMessage(),
          e);
      return new ArrayList<>();
    }
  }

  /**
   * Gets all active locks from the ezkey_shedlock table.
   *
   * @return List of active lock entries (where lock_until > NOW())
   */
  public List<ShedLockEntry> getActiveLocks() {
    String sqlQuery =
        "SELECT name, locked_by, locked_at, lock_until, "
            + "CASE WHEN lock_until > NOW() THEN 'true' ELSE 'false' END as is_active "
            + "FROM ezkey_shedlock "
            + "WHERE lock_until > NOW() "
            + "ORDER BY locked_at DESC;";

    List<String> rows = executeQuery(sqlQuery);
    List<ShedLockEntry> locks = new ArrayList<>();

    for (String row : rows) {
      // Parse pipe-separated values (psql -A format)
      String[] parts = row.split("\\|");
      if (parts.length >= 5) {
        try {
          String name = parts[0].trim();
          String lockedBy = parts[1].trim();
          OffsetDateTime lockedAt = parseTimestamp(parts[2].trim());
          OffsetDateTime lockUntil = parseTimestamp(parts[3].trim());
          boolean isActive = "true".equalsIgnoreCase(parts[4].trim());

          locks.add(new ShedLockEntry(name, lockedBy, lockedAt, lockUntil, isActive));
        } catch (Exception e) {
          log.warn("Failed to parse lock entry: {}", row, e);
        }
      }
    }

    return locks;
  }

  /**
   * Gets a specific lock by name.
   *
   * @param lockName the lock name (job identifier)
   * @return Lock entry if found, null otherwise
   */
  public ShedLockEntry getLock(String lockName) {
    String sqlQuery =
        String.format(
            "SELECT name, locked_by, locked_at, lock_until, "
                + "CASE WHEN lock_until > NOW() THEN 'true' ELSE 'false' END as is_active "
                + "FROM ezkey_shedlock "
                + "WHERE name = '%s' "
                + "ORDER BY locked_at DESC "
                + "LIMIT 1;",
            lockName.replace("'", "''")); // SQL injection protection

    List<String> rows = executeQuery(sqlQuery);
    if (rows.isEmpty()) {
      return null;
    }

    String row = rows.get(0);
    String[] parts = row.split("\\|");
    if (parts.length >= 5) {
      try {
        String name = parts[0].trim();
        String lockedBy = parts[1].trim();
        OffsetDateTime lockedAt = parseTimestamp(parts[2].trim());
        OffsetDateTime lockUntil = parseTimestamp(parts[3].trim());
        boolean isActive = "true".equalsIgnoreCase(parts[4].trim());

        return new ShedLockEntry(name, lockedBy, lockedAt, lockUntil, isActive);
      } catch (Exception e) {
        log.warn("Failed to parse lock entry: {}", row, e);
      }
    }

    return null;
  }

  /**
   * Gets all locks (active and expired) from the ezkey_shedlock table.
   *
   * @return List of all lock entries
   */
  public List<ShedLockEntry> getAllLocks() {
    String sqlQuery =
        "SELECT name, locked_by, locked_at, lock_until, "
            + "CASE WHEN lock_until > NOW() THEN 'true' ELSE 'false' END as is_active "
            + "FROM ezkey_shedlock "
            + "ORDER BY locked_at DESC;";

    List<String> rows = executeQuery(sqlQuery);
    List<ShedLockEntry> locks = new ArrayList<>();

    for (String row : rows) {
      String[] parts = row.split("\\|");
      if (parts.length >= 5) {
        try {
          String name = parts[0].trim();
          String lockedBy = parts[1].trim();
          OffsetDateTime lockedAt = parseTimestamp(parts[2].trim());
          OffsetDateTime lockUntil = parseTimestamp(parts[3].trim());
          boolean isActive = "true".equalsIgnoreCase(parts[4].trim());

          locks.add(new ShedLockEntry(name, lockedBy, lockedAt, lockUntil, isActive));
        } catch (Exception e) {
          log.warn("Failed to parse lock entry: {}", row, e);
        }
      }
    }

    return locks;
  }

  /**
   * Counts active locks for a specific job name.
   *
   * @param lockName the lock name (job identifier)
   * @return Number of active locks (should be 0 or 1)
   */
  public int countActiveLocks(String lockName) {
    String sqlQuery =
        String.format(
            "SELECT COUNT(*) FROM ezkey_shedlock WHERE name = '%s' AND lock_until > NOW();",
            lockName.replace("'", "''"));

    List<String> results = executeQuery(sqlQuery);
    if (results.isEmpty()) {
      return 0;
    }

    try {
      return Integer.parseInt(results.get(0).trim());
    } catch (NumberFormatException e) {
      log.warn("Failed to parse count result: {}", results.get(0));
      return 0;
    }
  }

  /**
   * Gets lock history for a specific job (all lock acquisitions).
   *
   * @param lockName the lock name (job identifier)
   * @return List of lock entries ordered by acquisition time (most recent first)
   */
  public List<ShedLockEntry> getLockHistory(String lockName) {
    String sqlQuery =
        String.format(
            "SELECT name, locked_by, locked_at, lock_until, "
                + "CASE WHEN lock_until > NOW() THEN 'true' ELSE 'false' END as is_active "
                + "FROM ezkey_shedlock "
                + "WHERE name = '%s' "
                + "ORDER BY locked_at DESC;",
            lockName.replace("'", "''"));

    List<String> rows = executeQuery(sqlQuery);
    List<ShedLockEntry> locks = new ArrayList<>();

    for (String row : rows) {
      String[] parts = row.split("\\|");
      if (parts.length >= 5) {
        try {
          String name = parts[0].trim();
          String lockedBy = parts[1].trim();
          OffsetDateTime lockedAt = parseTimestamp(parts[2].trim());
          OffsetDateTime lockUntil = parseTimestamp(parts[3].trim());
          boolean isActive = "true".equalsIgnoreCase(parts[4].trim());

          locks.add(new ShedLockEntry(name, lockedBy, lockedAt, lockUntil, isActive));
        } catch (Exception e) {
          log.warn("Failed to parse lock entry: {}", row, e);
        }
      }
    }

    return locks;
  }

  /**
   * Waits for a lock to be acquired by any instance.
   *
   * @param lockName the lock name to wait for
   * @param timeoutSeconds maximum time to wait in seconds
   * @return Lock entry if acquired within timeout, null otherwise
   */
  public ShedLockEntry waitForLockAcquisition(String lockName, int timeoutSeconds) {
    log.info("Waiting for lock '{}' to be acquired (timeout: {}s)...", lockName, timeoutSeconds);

    long startTime = System.currentTimeMillis();
    long timeoutMs = timeoutSeconds * 1000L;

    while (System.currentTimeMillis() - startTime < timeoutMs) {
      ShedLockEntry lock = getLock(lockName);
      if (lock != null && lock.isActive()) {
        log.info("Lock '{}' acquired by instance '{}'", lockName, lock.lockedBy());
        return lock;
      }

      try {
        Thread.sleep(500); // Check every 500ms
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return null;
      }
    }

    log.warn("Timeout waiting for lock '{}' to be acquired", lockName);
    return null;
  }

  /**
   * Parses a PostgreSQL TIMESTAMPTZ string to OffsetDateTime.
   *
   * @param timestampStr PostgreSQL timestamp string
   * @return Parsed OffsetDateTime
   */
  private OffsetDateTime parseTimestamp(String timestampStr) {
    if (timestampStr == null || timestampStr.isBlank()) {
      return OffsetDateTime.now();
    }

    String raw = timestampStr.trim();

    /*
     * Why do we normalize/accept multiple formats here?
     *
     * These timestamps come from `psql` output (via `docker exec ... psql -t -A -c "SELECT ..."`),
     * not from the Java APIs. Postgres stores TIMESTAMPTZ consistently, but its *text rendering*
     * (and `psql` formatting) is not guaranteed to be strict ISO-8601.
     *
     * Examples observed in practice:
     * - "2025-12-22 16:04:50.14368+00"   (space separator, variable fractional precision, "+00")
     * - "2025-12-22 16:04:50.143680+00" (6 digits)
     * - "2025-12-22T16:04:50.14368Z"    (already ISO)
     *
     * So we normalize the string into an ISO_OFFSET_DATE_TIME-compatible form before parsing.
     * This keeps tests focused on lock semantics (instants/ordering) rather than `psql` formatting.
     */

    // Normalize common PostgreSQL formats to ISO_OFFSET_DATE_TIME.
    // Examples from psql output:
    // - "2025-12-22 16:04:50.14368+00"      -> "2025-12-22T16:04:50.14368+00:00"
    // - "2025-12-22 16:04:50.143680+00"    -> "2025-12-22T16:04:50.143680+00:00"
    // - "2025-12-22 16:04:50+00"           -> "2025-12-22T16:04:50+00:00"
    // - "2025-12-22T16:04:50.14368Z"       -> unchanged
    String normalized = raw;
    if (normalized.length() >= 19 && normalized.charAt(10) == ' ') {
      normalized = normalized.substring(0, 10) + "T" + normalized.substring(11);
    }

    // Convert timezone offsets like +00 or -05 to +00:00 / -05:00 for ISO parsing.
    normalized = normalized.replaceAll("([+-]\\d{2})$", "$1:00");
    normalized = normalized.replaceAll("([+-]\\d{2})(\\d{2})$", "$1:$2");

    try {
      return OffsetDateTime.parse(normalized, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    } catch (Exception ignored) {
      // Fall back to a tolerant parser (treat as UTC if no offset present).
    }

    try {
      DateTimeFormatter localTolerant =
          new DateTimeFormatterBuilder()
              .appendPattern("yyyy-MM-dd['T'][' ']HH:mm:ss")
              .optionalStart()
              .appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true)
              .optionalEnd()
              .toFormatter();
      return java.time.LocalDateTime.parse(raw, localTolerant).atOffset(ZoneOffset.UTC);
    } catch (Exception e2) {
      log.warn("Failed to parse timestamp: {}", timestampStr, e2);
      return OffsetDateTime.now(); // Fallback
    }
  }

  /**
   * Gets statistics about lock distribution across instances.
   *
   * @return Map of instance ID to number of active locks held
   */
  public Map<String, Integer> getLockDistribution() {
    String sqlQuery =
        "SELECT locked_by, COUNT(*) as lock_count "
            + "FROM ezkey_shedlock "
            + "WHERE lock_until > NOW() "
            + "GROUP BY locked_by "
            + "ORDER BY lock_count DESC;";

    List<String> rows = executeQuery(sqlQuery);
    Map<String, Integer> distribution = new HashMap<>();

    for (String row : rows) {
      String[] parts = row.split("\\|");
      if (parts.length >= 2) {
        try {
          String instanceId = parts[0].trim();
          int count = Integer.parseInt(parts[1].trim());
          distribution.put(instanceId, count);
        } catch (Exception e) {
          log.warn("Failed to parse distribution entry: {}", row, e);
        }
      }
    }

    return distribution;
  }
}
