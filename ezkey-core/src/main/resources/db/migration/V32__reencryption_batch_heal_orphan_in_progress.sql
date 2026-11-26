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
--              reasons only). retry_count is left as is, so the scheduler resumes
--              the batch while retries remain; the resume re-checks every row
--              (prefix check under a row lock), so rows already migrated are
--              skipped.
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
    completed_at  = COALESCE(completed_at, NOW()),
    updated_at    = NOW()
WHERE status = 'IN_PROGRESS';
