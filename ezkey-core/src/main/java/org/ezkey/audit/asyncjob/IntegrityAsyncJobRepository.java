/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Repository: IntegrityAsyncJobRepository
 * Description: Persistence for Integrity async job slot rows.
 */

package org.ezkey.audit.asyncjob;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Persistence for {@link IntegrityAsyncJob} rows.
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
   * Most recent job by start time.
   *
   * @return latest job when any exist
   */
  Optional<IntegrityAsyncJob> findFirstByOrderByStartedAtDesc();

  /**
   * Recent jobs newest-first (banner / abandon resolves the latest non-abandoned).
   *
   * @return jobs ordered by started_at descending
   */
  List<IntegrityAsyncJob> findAllByOrderByStartedAtDesc();
}
