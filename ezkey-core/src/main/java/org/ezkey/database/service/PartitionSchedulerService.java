/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: PartitionSchedulerService
 * Description: Scheduled service for automatic creation of monthly partitions for partitioned tables.
 */

package org.ezkey.database.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled service for automatic creation of monthly partitions.
 *
 * <p>This service automatically creates partitions for the next month to ensure seamless data
 * insertion. It runs daily to check if the next month's partition exists and creates it if needed.
 *
 * <p><b>Security Design:</b>
 *
 * <p>This service uses a PostgreSQL SECURITY DEFINER function ({@code create_monthly_partition}) to
 * create partitions without requiring DDL privileges on the application role. This maintains
 * security best practices:
 *
 * <ul>
 *   <li><b>Role Separation:</b> Application role (EZKEY_app) only needs EXECUTE privilege, not
 *       CREATE TABLE
 *   <li><b>Owner Privileges:</b> Function executes with owner role (EZKEY_owner) privileges via
 *       SECURITY DEFINER
 *   <li><b>Compliance:</b> Maintains SOC2 separation of duties (DDL vs DML privileges)
 * </ul>
 *
 * <p><b>Function Return Value:</b>
 *
 * <p>The {@code create_monthly_partition} function returns a BOOLEAN indicating the operation
 * result:
 *
 * <ul>
 *   <li>{@code true} - Partition was created successfully
 *   <li>{@code false} - Partition already existed (idempotent operation)
 * </ul>
 *
 * <p><b>Supported Tables:</b>
 *
 * <ul>
 *   <li>ezkey_auth_attempt - Partitioned by created_at (monthly)
 *   <li>ezkey_audit_log - Partitioned by created_at (monthly)
 * </ul>
 *
 * <p><b>Default Schedule:</b> Daily at 1 AM (configurable via cron expression)
 *
 * <p><b>Configuration Properties:</b>
 *
 * <ul>
 *   <li>ezkey.database.partition.scheduler.enabled - Enable/disable scheduler (default: true)
 *   <li>ezkey.database.partition.scheduler.cron - Cron expression for schedule (default: 0 0 1 * *
 *       ?)
 * </ul>
 *
 * <p><b>Partition Naming Convention:</b>
 *
 * <ul>
 *   <li>ezkey_auth_attempt_YYYY_MM (e.g., ezkey_auth_attempt_2025_01)
 *   <li>ezkey_audit_log_YYYY_MM (e.g., ezkey_audit_log_2025_01)
 * </ul>
 *
 * <p><b>Production Setup:</b>
 *
 * <p>In production environments with separated database roles:
 *
 * <ol>
 *   <li>Function must be owned by owner role (EZKEY_owner) - set in migration or manually
 *   <li>Application role (EZKEY_app) must have EXECUTE privilege on function
 *   <li>Function uses SECURITY DEFINER to execute with owner privileges
 * </ol>
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see <a href="https://www.postgresql.org/docs/current/sql-createfunction.html">PostgreSQL
 *     SECURITY DEFINER</a>
 */
@Component
@ConditionalOnProperty(
    name = "ezkey.database.partition.scheduler.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class PartitionSchedulerService {

  private static final Logger logger = LoggerFactory.getLogger(PartitionSchedulerService.class);

  private static final DateTimeFormatter PARTITION_DATE_FORMAT =
      DateTimeFormatter.ofPattern("yyyy_MM");

  @PersistenceContext // Checkstyle rule
  private EntityManager entityManager;

  @Value("${ezkey.database.partition.scheduler.cron:0 0 1 * * ?}")
  private String scheduleCron;

  /**
   * Scheduled job to create partitions for the next month.
   *
   * <p>Runs daily to ensure the next month's partition exists. Creates partitions for both
   * ezkey_auth_attempt and ezkey_audit_log tables if they don't already exist.
   *
   * <p>This job is idempotent - it checks if partitions exist before creating them, so it's safe to
   * run multiple times.
   *
   * <p><b>HA Safety:</b> Uses distributed locking to ensure only one instance executes this job at
   * a time.
   */
  @Scheduled(cron = "${ezkey.database.partition.scheduler.cron:0 0 1 * * ?}")
  @SchedulerLock(name = "DB_PARTITION_CREATION", lockAtMostFor = "PT10M")
  @Transactional
  public void createNextMonthPartitions() {
    try {
      logger.info("🔧 Starting partition creation check for next month");

      LocalDate nextMonth = LocalDate.now().plusMonths(1);
      LocalDate nextMonthStart = nextMonth.withDayOfMonth(1);
      LocalDate nextMonthEnd = nextMonthStart.plusMonths(1);

      String partitionSuffix = nextMonthStart.format(PARTITION_DATE_FORMAT);

      // Create partition for ezkey_auth_attempt
      createPartitionIfNotExists(
          "ezkey_auth_attempt",
          "ezkey_auth_attempt_" + partitionSuffix,
          nextMonthStart,
          nextMonthEnd);

      // Create partition for ezkey_audit_log
      createPartitionIfNotExists(
          "ezkey_audit_log", "ezkey_audit_log_" + partitionSuffix, nextMonthStart, nextMonthEnd);

      logger.info("✅ Partition creation check completed");

    } catch (Exception e) {
      logger.error("❌ Error creating partitions: {}", e.getMessage(), e);
    }
  }

  /**
   * Creates a partition for a table if it doesn't already exist.
   *
   * <p>Uses PostgreSQL SECURITY DEFINER function to create partitions without requiring DDL
   * privileges on the application role. This maintains security best practices by separating DDL
   * privileges (owner role) from DML privileges (application role).
   *
   * <p>The function returns a BOOLEAN indicating whether the partition was created:
   *
   * <ul>
   *   <li>{@code true} - Partition was created successfully
   *   <li>{@code false} - Partition already existed (idempotent operation)
   * </ul>
   *
   * @param tableName the name of the partitioned table
   * @param partitionName the name of the partition to create
   * @param startDate the start date for the partition (inclusive)
   * @param endDate the end date for the partition (exclusive)
   */
  private void createPartitionIfNotExists(
      String tableName, String partitionName, LocalDate startDate, LocalDate endDate) {

    try {
      // Use SECURITY DEFINER function to create partition
      // Function executes with owner privileges, application role only needs EXECUTE
      LocalDateTime startDateTime = startDate.atStartOfDay();
      LocalDateTime endDateTime = endDate.atStartOfDay();

      String functionCall =
          "SELECT create_monthly_partition(:tableName, :partitionName, :startDate, :endDate)";

      Object result =
          entityManager
              .createNativeQuery(functionCall)
              .setParameter("tableName", tableName)
              .setParameter("partitionName", partitionName)
              .setParameter("startDate", startDateTime)
              .setParameter("endDate", endDateTime)
              .getSingleResult();

      // Function returns BOOLEAN: true if created, false if already existed
      Boolean wasCreated = (Boolean) result;
      if (wasCreated) {
        logger.info("✅ Created partition: {} for table: {}", partitionName, tableName);
      } else {
        logger.debug("ℹ️ Partition {} already exists for table: {}", partitionName, tableName);
      }

    } catch (Exception e) {
      logger.error(
          "❌ Error creating partition {} for table {}: {}",
          partitionName,
          tableName,
          e.getMessage(),
          e);
      throw e;
    }
  }

  /**
   * Manually trigger partition creation (useful for testing or manual operations).
   *
   * <p>This method can be called programmatically to create partitions without waiting for the
   * scheduled job.
   */
  @Transactional
  public void createPartitionsManually() {
    logger.info("🔧 Manually triggering partition creation");
    createNextMonthPartitions();
  }
}
