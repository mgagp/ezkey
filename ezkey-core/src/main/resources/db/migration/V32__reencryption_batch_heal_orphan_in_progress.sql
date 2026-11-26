-- ============================================================================
-- Ezkey Migration V32: Heal orphan IN_PROGRESS re-encryption batches (#1431)
-- ============================================================================
-- Description: A re-encryption batch runs in one database transaction, so a
--              live batch never commits IN_PROGRESS. Before #1431, a run that
--              stopped early (interrupted at shutdown, or no row left to fetch
--              while old-key rows remained) returned normally and committed
--              IN_PROGRESS. No automatic path picks such a row up, it blocks new
--              batches for its (target, old key) and keeps the old key from
--              draining. The code now writes FAILED on that path instead.
--
--              This one-shot heal moves every existing IN_PROGRESS row to
--              FAILED with the classified reason 'Orphaned' (#785: classified
--              reasons only). retry_count is reset (the orphan was a code defect,
--              not batch failures) so the scheduler resumes it. last_record_id and
--              the counters are cleared: rows left behind may sit below the
--              cursor, and the fetch filters on the old key's key-id column, so a
--              rescan only touches rows still to migrate (each re-checked under a
--              row lock); rows migrated earlier are reconciled as skipped.
--
-- Safe during a rolling deploy: an old node running a batch has not committed
-- IN_PROGRESS (single transaction), so no live batch matches.
--
-- Author: Ezkey contributors
-- Date:   2026-11
-- ============================================================================

UPDATE ezkey_reencryption_batch
SET status        = 'FAILED',
    error_message = 'Orphaned',
    error_count   = error_count + 1,
    retry_count   = 0,
    last_record_id = NULL,
    records_done   = 0,
    records_failed = 0,
    records_skipped = 0,
    progress_pct   = 0,
    completed_at  = COALESCE(completed_at, NOW()),
    updated_at    = NOW()
WHERE status = 'IN_PROGRESS';
