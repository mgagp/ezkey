-- ============================================================================
-- Ezkey Migration V25: Integrity async job — trigger, counters, nightly terminal rows
-- ============================================================================
-- Description: Integrity cut 3 PR 1. Adds job trigger (MANUAL/AUTO/NIGHTLY), nullable
--              chain counters + chain_status, allows nightly rows without admin id,
--              heals legacy INTERRUPTED orphans missing abandoned_at (one-shot), and
--              rewrites the table comment (verdict history; operator slot excludes NIGHTLY).
--
-- Invariants (DB CHECK):
--   - nightly never RUNNING (never takes the operator slot)
--   - started_by_admin_id IS NULL iff trigger = NIGHTLY
--   - INTERRUPTED is always written with abandoned_at at boot (see markInterruptedOnStartup);
--     this migration clears historical orphans so the heal loop can be removed.
--
-- Note: column name "trigger" is quoted — TRIGGER is a PostgreSQL reserved keyword.
--
-- Author: Ezkey contributors
-- Date:   2026-10
-- ============================================================================

-- Trigger: NOT NULL DEFAULT so legacy rows stay visible in trigger <> 'NIGHTLY' filters.
ALTER TABLE ezkey_integrity_async_job
    ADD COLUMN IF NOT EXISTS "trigger" VARCHAR(16) NOT NULL DEFAULT 'MANUAL';

ALTER TABLE ezkey_integrity_async_job
    DROP CONSTRAINT IF EXISTS chk_integrity_async_job_trigger;
ALTER TABLE ezkey_integrity_async_job
    ADD CONSTRAINT chk_integrity_async_job_trigger
        CHECK ("trigger" IN ('MANUAL', 'AUTO', 'NIGHTLY'));

-- Nightly terminal rows only (never occupy the RUNNING slot).
ALTER TABLE ezkey_integrity_async_job
    DROP CONSTRAINT IF EXISTS chk_integrity_async_job_nightly_terminal;
ALTER TABLE ezkey_integrity_async_job
    ADD CONSTRAINT chk_integrity_async_job_nightly_terminal
        CHECK ("trigger" <> 'NIGHTLY' OR status IN ('SUCCEEDED', 'FAILED'));

-- Author: nightly has null admin id; operator rows must keep an author.
ALTER TABLE ezkey_integrity_async_job
    ALTER COLUMN started_by_admin_id DROP NOT NULL;

ALTER TABLE ezkey_integrity_async_job
    DROP CONSTRAINT IF EXISTS chk_integrity_async_job_nightly_admin;
ALTER TABLE ezkey_integrity_async_job
    ADD CONSTRAINT chk_integrity_async_job_nightly_admin
        CHECK (("trigger" = 'NIGHTLY') = (started_by_admin_id IS NULL));

-- Structured chain counters (nullable; no backfill of legacy rows).
ALTER TABLE ezkey_integrity_async_job
    ADD COLUMN IF NOT EXISTS chain_status VARCHAR(64);
ALTER TABLE ezkey_integrity_async_job
    ADD COLUMN IF NOT EXISTS total_checkpoints INT;
ALTER TABLE ezkey_integrity_async_job
    ADD COLUMN IF NOT EXISTS valid_checkpoints INT;
ALTER TABLE ezkey_integrity_async_job
    ADD COLUMN IF NOT EXISTS invalid_checkpoints INT;
ALTER TABLE ezkey_integrity_async_job
    ADD COLUMN IF NOT EXISTS archived_checkpoints INT;
ALTER TABLE ezkey_integrity_async_job
    ADD COLUMN IF NOT EXISTS undeclared_gaps INT;
ALTER TABLE ezkey_integrity_async_job
    ADD COLUMN IF NOT EXISTS entry_hmac_violations INT;

ALTER TABLE ezkey_integrity_async_job
    DROP CONSTRAINT IF EXISTS chk_integrity_async_job_chain_status;
ALTER TABLE ezkey_integrity_async_job
    ADD CONSTRAINT chk_integrity_async_job_chain_status
        CHECK (
            chain_status IS NULL
            OR chain_status IN (
                'OK',
                'UNDECLARED_GAP_DETECTED',
                'CHAIN_INTEGRITY_VIOLATION_DETECTED',
                'NOT_VERIFIED'
            )
        );

-- One-shot: heal INTERRUPTED rows that lack abandoned_at (pre–crash-posture).
-- Invariant going forward: INTERRUPTED is always written with abandoned_at in
-- markInterruptedOnStartup; never Escape-sticky.
UPDATE ezkey_integrity_async_job
SET abandoned_at = COALESCE(finished_at, CURRENT_TIMESTAMP)
WHERE status = 'INTERRUPTED'
  AND abandoned_at IS NULL;

COMMENT ON TABLE ezkey_integrity_async_job IS
'Integrity verdict history; only rows with trigger<>''NIGHTLY'' participate in the operator slot. '
'One RUNNING operator row for the whole deployment (partial unique on slot_key). '
'Nightly terminal rows (trigger=NIGHTLY) are SUCCEEDED/FAILED only and never take the slot.';

COMMENT ON COLUMN ezkey_integrity_async_job."trigger" IS
'MANUAL|AUTO|NIGHTLY. NIGHTLY ≡ RetroactiveIntegrityValidationTriggerSource.SCHEDULED. '
'Client-declared on POST (AUTO|MANUAL); NIGHTLY rejected on POST.';

COMMENT ON COLUMN ezkey_integrity_async_job.chain_status IS
'OK | UNDECLARED_GAP_DETECTED | CHAIN_INTEGRITY_VIOLATION_DETECTED | NOT_VERIFIED '
'(HMAC inactive / no checkpoints / skipped). Null for VERIFY_ENTRY and legacy rows.';
