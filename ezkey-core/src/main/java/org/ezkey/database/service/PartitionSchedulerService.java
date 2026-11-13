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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.ezkey.alert.domain.AlertResolutionReason;
import org.ezkey.alert.domain.AlertSeverity;
import org.ezkey.alert.domain.AlertType;
import org.ezkey.alert.service.AlertService;
import org.ezkey.audit.integrity.ScheduledJobKey;
import org.ezkey.audit.integrity.ScheduledJobLastRunService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Creates monthly partitions for {@code ezkey_auth_attempt} and {@code ezkey_audit_log}.
 *
 * <p>The horizon is the current month on the injected clock plus {@code months-ahead} future months
 * (default {@value #DEFAULT_MONTHS_AHEAD}). Production supplies a UTC clock, so that selection is a
 * UTC month. The bounds sent to {@code create_monthly_partition} are a zone-less {@link
 * LocalDateTime} ({@code atStartOfDay}) and follow the JVM/JDBC session time zone. This service
 * does not set that zone. The buffer is why a stretch of failed runs (for example EXECUTE grants
 * drifting off {@code create_monthly_partition}) does not become an MFA outage on the first day of
 * the next month. Flyway still creates the current and next month at migrate time; this service
 * re-checks the same horizon at startup and once a day.
 *
 * <p>There is no DEFAULT partition. A default child would accept rows outside the planned months,
 * so a later month drop or audit purge could not tell those rows from the months operators intend
 * to keep. {@code ezkey_audit_log} is RANGE then LIST({@code api_name}); a default child would need
 * its own list sub-partitions or inserts would still fail. The horizon plus an operator alert is
 * the safety net.
 *
 * <p>Creation uses the PostgreSQL SECURITY DEFINER function {@code create_monthly_partition}. The
 * application role needs EXECUTE, not CREATE TABLE. The function returns {@code true} when it
 * created the partition and {@code false} when it already existed.
 *
 * <p>Each partition call runs in its own transaction. One failure does not stop the remaining
 * targets. Any failure records {@link ScheduledJobKey#PARTITION_CREATION} as FAILED and raises
 * {@link AlertType#PARTITION_CREATION_FAILED}. A later clean run records SUCCESS and resolves that
 * alert. The registry stores the exception class name only, not the driver message.
 *
 * <p><b>HA:</b> the daily run keeps {@code @SchedulerLock} name {@value #LOCK_NAME}. Startup takes
 * the same lock through {@link LockProvider}. If another instance holds it, startup skips and does
 * not record a failure. If the lock store itself errors, startup still creates partitions: the
 * function is idempotent, and skipping would recreate the outage the lock is meant to protect.
 *
 * <p><b>Configuration:</b>
 *
 * <ul>
 *   <li>{@code ezkey.database.partition.scheduler.enabled} (default {@code true})
 *   <li>{@code ezkey.database.partition.scheduler.cron} (default {@code 0 0 1 * * ?})
 *   <li>{@code ezkey.database.partition.scheduler.months-ahead} (default {@value
 *       #DEFAULT_MONTHS_AHEAD}, clamped to {@value #MIN_MONTHS_AHEAD}–{@value #MAX_MONTHS_AHEAD})
 *   <li>{@code ezkey.database.partition.scheduler.startup-enabled} (default {@code true})
 * </ul>
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Component
@ConditionalOnProperty(
    name = "ezkey.database.partition.scheduler.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class PartitionSchedulerService {

  private static final Logger logger = LoggerFactory.getLogger(PartitionSchedulerService.class);

  /** ShedLock name shared by the daily job and the startup pass. */
  public static final String LOCK_NAME = "DB_PARTITION_CREATION";

  /** Singleton dedupe key for the partition-creation alert. */
  public static final String ALERT_DEDUPE_KEY = "PARTITION_CREATION_FAILED";

  /**
   * Future months beyond the current one. Three months means a full month of failed daily runs
   * still leaves the next two months already created.
   */
  public static final int DEFAULT_MONTHS_AHEAD = 3;

  /** At least the next month, which is the previous scheduler's only target. */
  static final int MIN_MONTHS_AHEAD = 1;

  /** Caps a bad configuration value so a typo cannot create a year of empty partitions. */
  static final int MAX_MONTHS_AHEAD = 12;

  private static final Duration LOCK_AT_MOST = Duration.ofMinutes(10);

  private static final DateTimeFormatter PARTITION_SUFFIX = DateTimeFormatter.ofPattern("yyyy_MM");

  private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("yyyy-MM");

  private static final String AUTH_ATTEMPT = "ezkey_auth_attempt";

  private static final String AUDIT_LOG = "ezkey_audit_log";

  private static final String FUNCTION_CALL =
      "SELECT create_monthly_partition(:tableName, :partitionName, :startDate, :endDate)";

  private final EntityManager entityManager;

  private final ScheduledJobLastRunService jobLastRunService;

  private final AlertService alertService;

  private final ObjectProvider<LockProvider> lockProvider;

  private final Clock clock;

  private final int monthsAhead;

  private final boolean startupEnabled;

  private final TransactionTemplate partitionTransaction;

  /**
   * Constructs the scheduler.
   *
   * @param entityManager persistence context used for the partition function call
   * @param jobLastRunService last-run registry
   * @param alertService operator alert raise and resolve
   * @param transactionManager commits each partition call on its own
   * @param lockProvider ShedLock provider; absent only outside Admin API
   * @param clock clock that selects the current month; production supplies UTC. Bounds are not
   *     taken from this clock
   * @param monthsAhead future months beyond the current one
   * @param startupEnabled when false, the ready event does not create partitions
   */
  public PartitionSchedulerService(
      EntityManager entityManager,
      ScheduledJobLastRunService jobLastRunService,
      AlertService alertService,
      PlatformTransactionManager transactionManager,
      ObjectProvider<LockProvider> lockProvider,
      Clock clock,
      @Value("${ezkey.database.partition.scheduler.months-ahead:" + DEFAULT_MONTHS_AHEAD + "}")
          int monthsAhead,
      @Value("${ezkey.database.partition.scheduler.startup-enabled:true}") boolean startupEnabled) {
    this.entityManager = entityManager;
    this.jobLastRunService = jobLastRunService;
    this.alertService = alertService;
    this.lockProvider = lockProvider;
    this.clock = clock;
    this.monthsAhead = clampMonthsAhead(monthsAhead);
    this.startupEnabled = startupEnabled;
    this.partitionTransaction = new TransactionTemplate(transactionManager);
    this.partitionTransaction.setPropagationBehavior(
        TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    if (monthsAhead != this.monthsAhead) {
      logger.warn(
          "ezkey.database.partition.scheduler.months-ahead={} is outside {}-{}; using {}",
          monthsAhead,
          MIN_MONTHS_AHEAD,
          MAX_MONTHS_AHEAD,
          this.monthsAhead);
    }
  }

  /**
   * Daily pass. ShedLock allows one Admin API instance to run it.
   *
   * <p>The method itself is not transactional: each partition commits on its own, so a failure
   * cannot mark the rest of the horizon rollback-only.
   */
  @Scheduled(cron = "${ezkey.database.partition.scheduler.cron:0 0 1 * * ?}")
  @SchedulerLock(name = LOCK_NAME, lockAtMostFor = "PT10M")
  public void createNextMonthPartitions() {
    ensurePartitionHorizon();
  }

  /**
   * Creates or verifies the horizon once the application is ready to use the database.
   *
   * <p>Uses {@value #LOCK_NAME}, the same name as the daily job, so startup does not race another
   * instance. A held lock skips this pass without a failure record.
   */
  @EventListener(ApplicationReadyEvent.class)
  public void ensurePartitionsOnStartup() {
    if (!startupEnabled) {
      logger.info("Partition creation at startup is disabled");
      return;
    }
    LockProvider provider = lockProvider.getIfAvailable();
    if (provider == null) {
      logger.warn(
          "ShedLock LockProvider is absent; creating partitions at startup without {}", LOCK_NAME);
      ensurePartitionHorizon();
      return;
    }
    Optional<SimpleLock> acquired = tryLock(provider);
    if (acquired == null) {
      ensurePartitionHorizon();
      return;
    }
    if (acquired.isEmpty()) {
      logger.info("Skipping partition creation at startup; another instance holds {}", LOCK_NAME);
      return;
    }
    SimpleLock held = acquired.get();
    try {
      ensurePartitionHorizon();
    } finally {
      held.unlock();
    }
  }

  /**
   * Creates the horizon without waiting for the daily schedule.
   *
   * <p>Does not take the distributed lock. Prefer the scheduled method in a multi-instance
   * deployment.
   */
  public void createPartitionsManually() {
    logger.info("Manually triggering partition creation");
    ensurePartitionHorizon();
  }

  /** Creates the current month and the configured future months for both partitioned tables. */
  void ensurePartitionHorizon() {
    LocalDate currentMonth = LocalDate.now(clock).withDayOfMonth(1);
    LocalDate lastMonth = currentMonth.plusMonths(monthsAhead);
    String fromLabel = currentMonth.format(MONTH_LABEL);
    String toLabel = lastMonth.format(MONTH_LABEL);
    List<String> errorNames = new ArrayList<>();
    int targets = 0;
    logger.info(
        "Starting partition horizon check UTC {}..{} monthsAhead={}",
        fromLabel,
        toLabel,
        monthsAhead);
    for (int offset = 0; offset <= monthsAhead; offset++) {
      LocalDate start = currentMonth.plusMonths(offset);
      LocalDate end = start.plusMonths(1);
      String suffix = start.format(PARTITION_SUFFIX);
      targets +=
          createPartitionIfNotExists(
              AUTH_ATTEMPT, AUTH_ATTEMPT + "_" + suffix, start, end, errorNames);
      targets +=
          createPartitionIfNotExists(AUDIT_LOG, AUDIT_LOG + "_" + suffix, start, end, errorNames);
    }
    String scope = "UTC " + fromLabel + ".." + toLabel + " monthsAhead=" + monthsAhead;
    if (errorNames.isEmpty()) {
      recordOutcome(true, scope, null, fromLabel, toLabel, 0);
      logger.info("Partition horizon check completed");
      return;
    }
    String failedScope = scope + " failed=" + errorNames.size() + "/" + targets;
    recordOutcome(false, failedScope, errorNames.get(0), fromLabel, toLabel, errorNames.size());
  }

  private int createPartitionIfNotExists(
      String tableName,
      String partitionName,
      LocalDate startDate,
      LocalDate endDate,
      List<String> errorNames) {
    try {
      Boolean created =
          partitionTransaction.execute(
              _ -> invokeCreateFunction(tableName, partitionName, startDate, endDate));
      if (created == null) {
        errorNames.add("IllegalStateException");
        logger.error("Partition function returned no result for {}", partitionName);
        return 1;
      }
      if (created) {
        logger.info("Created partition: {} for table: {}", partitionName, tableName);
      } else {
        logger.debug("Partition {} already exists for table: {}", partitionName, tableName);
      }
    } catch (Exception ex) { // CHECKSTYLE IGNORE IllegalCatch
      errorNames.add(safeErrorName(ex));
      logger.error(
          "Error creating partition {} for table {}: {}",
          partitionName,
          tableName,
          safeErrorName(ex),
          ex);
    }
    return 1;
  }

  private Boolean invokeCreateFunction(
      String tableName, String partitionName, LocalDate startDate, LocalDate endDate) {
    // Zone-less. PostgreSQL reads these in the JDBC session time zone, not from the clock.
    LocalDateTime startDateTime = startDate.atStartOfDay();
    LocalDateTime endDateTime = endDate.atStartOfDay();
    Object result =
        entityManager
            .createNativeQuery(FUNCTION_CALL)
            .setParameter("tableName", tableName)
            .setParameter("partitionName", partitionName)
            .setParameter("startDate", startDateTime)
            .setParameter("endDate", endDateTime)
            .getSingleResult();
    if (result instanceof Boolean created) {
      return created;
    }
    throw new IllegalStateException("Unexpected partition function result");
  }

  private void recordOutcome(
      boolean success,
      String scope,
      String errorName,
      String fromLabel,
      String toLabel,
      int failed) {
    if (success) {
      try {
        jobLastRunService.recordSuccess(ScheduledJobKey.PARTITION_CREATION, scope);
      } catch (Exception ex) { // CHECKSTYLE IGNORE IllegalCatch
        logger.error("Could not record partition job success: {}", safeErrorName(ex), ex);
      }
      try {
        alertService.resolveByDedupeKey(
            ALERT_DEDUPE_KEY, AlertResolutionReason.PARTITION_HORIZON_RESTORED, null);
      } catch (Exception ex) { // CHECKSTYLE IGNORE IllegalCatch
        logger.error("Could not resolve partition creation alert: {}", safeErrorName(ex), ex);
      }
      return;
    }
    try {
      jobLastRunService.recordFailure(ScheduledJobKey.PARTITION_CREATION, scope, errorName);
    } catch (Exception ex) { // CHECKSTYLE IGNORE IllegalCatch
      logger.error("Could not record partition job outcome: {}", safeErrorName(ex), ex);
    }
    try {
      String payload =
          "{\"monthsAhead\":"
              + monthsAhead
              + ",\"from\":\""
              + fromLabel
              + "\",\"to\":\""
              + toLabel
              + "\",\"failedTargets\":"
              + failed
              + ",\"error\":\""
              + errorName
              + "\"}";
      alertService.raiseOrTouch(
          AlertType.PARTITION_CREATION_FAILED, AlertSeverity.CRITICAL, ALERT_DEDUPE_KEY, payload);
    } catch (Exception ex) { // CHECKSTYLE IGNORE IllegalCatch
      logger.error("Could not raise partition creation alert: {}", safeErrorName(ex), ex);
    }
  }

  /**
   * Tries to take {@value #LOCK_NAME}.
   *
   * @param provider ShedLock provider
   * @return the lock when acquired, empty when another instance holds it, or null when the lock
   *     store failed and the caller should run without a lock
   */
  private Optional<SimpleLock> tryLock(LockProvider provider) {
    LockConfiguration configuration =
        new LockConfiguration(Instant.now(), LOCK_NAME, LOCK_AT_MOST, Duration.ZERO);
    try {
      return provider.lock(configuration);
    } catch (Exception ex) { // CHECKSTYLE IGNORE IllegalCatch
      logger.error(
          "Could not acquire {} at startup; creating partitions without the lock because the"
              + " function is idempotent",
          LOCK_NAME,
          ex);
      return null;
    }
  }

  private static int clampMonthsAhead(int configured) {
    if (configured < MIN_MONTHS_AHEAD) {
      return MIN_MONTHS_AHEAD;
    }
    if (configured > MAX_MONTHS_AHEAD) {
      return MAX_MONTHS_AHEAD;
    }
    return configured;
  }

  /**
   * Root exception class name. Driver messages can contain SQL text and must not land in the
   * registry or the alert payload.
   *
   * @param error failure from the partition call or the registry
   * @return simple class name of the root cause
   */
  static String safeErrorName(Throwable error) {
    Throwable root = error;
    while (root.getCause() != null && root.getCause() != root) {
      root = root.getCause();
    }
    String name = root.getClass().getSimpleName();
    if (name == null || name.isBlank()) {
      return "Exception";
    }
    return name;
  }
}
