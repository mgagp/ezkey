/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: ScheduledJobLastRunService
 * Description: Records last-run metadata for scheduled jobs.
 */

package org.ezkey.audit.integrity;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Updates {@code ezkey_scheduled_job_last_run} rows after scheduled job executions.
 *
 * @since 2026
 */
@Service
public class ScheduledJobLastRunService {

  private final ScheduledJobLastRunRepository repository;

  /**
   * Constructs the service.
   *
   * @param repository job last-run repository
   */
  public ScheduledJobLastRunService(ScheduledJobLastRunRepository repository) {
    this.repository = repository;
  }

  /**
   * Records a successful job run.
   *
   * @param jobKey stable job identifier
   * @param scope human-readable scope of the run (may be null)
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordSuccess(ScheduledJobKey jobKey, String scope) {
    ScheduledJobLastRun row = loadOrThrow(jobKey);
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    row.setLastExecutionAt(now);
    row.setLastStatus(ScheduledJobLastRunStatus.SUCCESS);
    row.setLastRunScope(scope);
    row.setLastErrorSummary(null);
    row.setUpdatedAt(now);
    repository.save(row);
  }

  /**
   * Records a failed job run (infrastructure/execution failure, not integrity rupture).
   *
   * @param jobKey stable job identifier
   * @param scope human-readable scope attempted (may be null)
   * @param errorSummary safe operator-facing summary (may be null)
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordFailure(ScheduledJobKey jobKey, String scope, String errorSummary) {
    ScheduledJobLastRun row = loadOrThrow(jobKey);
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    row.setLastExecutionAt(now);
    row.setLastStatus(ScheduledJobLastRunStatus.FAILED);
    row.setLastRunScope(scope);
    row.setLastErrorSummary(truncate(errorSummary, 512));
    row.setUpdatedAt(now);
    repository.save(row);
  }

  private ScheduledJobLastRun loadOrThrow(ScheduledJobKey jobKey) {
    return repository
        .findById(jobKey)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Missing scheduled job registry row for key: " + jobKey.name()));
  }

  private static String truncate(String value, int maxLen) {
    if (value == null) {
      return null;
    }
    if (value.length() <= maxLen) {
      return value;
    }
    return value.substring(0, maxLen);
  }
}
