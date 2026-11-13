/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Repository: IntegrityAsyncJobRepository
 * Description: Persistence for Integrity async job slot / verdict rows.
 */

package org.ezkey.audit.asyncjob;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Persistence for {@link IntegrityAsyncJob} rows.
 *
 * <p>Operator-slot readers exclude {@link IntegrityAsyncJobTrigger#NIGHTLY}. The only reader that
 * includes nightly rows is {@link #findLatestVerdict()}. Unfiltered "latest job" finders are
 * intentionally absent so a future nightly row cannot mask Escape-sticky or banner state.
 *
 * @since 2026
 */
@Repository
public interface IntegrityAsyncJobRepository extends JpaRepository<IntegrityAsyncJob, UUID> {

  /**
   * Finds the single RUNNING job for the global slot, if any.
   *
   * @param slotKey slot key (normally {@link IntegrityAsyncJob#GLOBAL_SLOT_KEY})
   * @param status expected status ({@link IntegrityAsyncJobStatus#RUNNING})
   * @return running job when present
   */
  Optional<IntegrityAsyncJob> findBySlotKeyAndStatus(
      String slotKey, IntegrityAsyncJobStatus status);

  /**
   * Whether a RUNNING job occupies the slot.
   *
   * @param slotKey slot key
   * @param status status to match
   * @return true when a matching row exists
   */
  boolean existsBySlotKeyAndStatus(String slotKey, IntegrityAsyncJobStatus status);

  /**
   * Latest operator job (any status), including already-abandoned rows (for abandon idempotence).
   *
   * @return latest non-NIGHTLY job when any exist
   */
  @Query(
      """
      SELECT j FROM IntegrityAsyncJob j
      WHERE j.trigger <> org.ezkey.audit.asyncjob.IntegrityAsyncJobTrigger.NIGHTLY
      ORDER BY j.startedAt DESC
      LIMIT 1
      """)
  Optional<IntegrityAsyncJob> findLatestOperatorJob();

  /**
   * Latest non-abandoned operator job (banner / Escape-sticky / current).
   *
   * @return latest active operator job when present
   */
  @Query(
      """
      SELECT j FROM IntegrityAsyncJob j
      WHERE j.trigger <> org.ezkey.audit.asyncjob.IntegrityAsyncJobTrigger.NIGHTLY
        AND j.abandonedAt IS NULL
      ORDER BY j.startedAt DESC
      LIMIT 1
      """)
  Optional<IntegrityAsyncJob> findLatestActiveOperatorJob();

  /**
   * Latest verdict-bearing row (operator or nightly).
   *
   * <p>Verdict-bearing = SUCCEEDED, type VERIFY_CHAIN_RANGE or RUN_VALIDATION, {@code chainStatus}
   * present and not {@link IntegrityChainStatus#NOT_VERIFIED}.
   *
   * @return latest usable chain verdict when present
   */
  @Query(
      """
      SELECT j FROM IntegrityAsyncJob j
      WHERE j.status = org.ezkey.audit.asyncjob.IntegrityAsyncJobStatus.SUCCEEDED
        AND j.jobType IN (
            org.ezkey.audit.asyncjob.IntegrityAsyncJobType.VERIFY_CHAIN_RANGE,
            org.ezkey.audit.asyncjob.IntegrityAsyncJobType.RUN_VALIDATION)
        AND j.chainStatus IS NOT NULL
        AND j.chainStatus <> org.ezkey.audit.asyncjob.IntegrityChainStatus.NOT_VERIFIED
      ORDER BY j.finishedAt DESC NULLS LAST, j.startedAt DESC
      LIMIT 1
      """)
  Optional<IntegrityAsyncJob> findLatestVerdict();
}
