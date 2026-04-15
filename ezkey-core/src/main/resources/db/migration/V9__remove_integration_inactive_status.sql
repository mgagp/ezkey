-- V9: Remove INACTIVE from integration lifecycle status
-- Migrates any INACTIVE integrations to RETIRED and drops the now-invalid CHECK constraint.
--
-- Background: INACTIVE was a transitional state that is no longer part of the lifecycle model.
-- Only ACTIVE and RETIRED remain. Any INACTIVE row is treated as permanently retired.

-- Step 1: Promote all INACTIVE rows to RETIRED (data is preserved, nothing is hard-deleted)
UPDATE ezkey_integration
  SET integration_lifecycle_status = 'RETIRED'
WHERE integration_lifecycle_status = 'INACTIVE';

-- Step 2: Drop the inline CHECK constraint that still allows 'INACTIVE'
-- PostgreSQL auto-names inline column CHECK constraints as {table}_{column}_check.
DO $$
  DECLARE
    v_conname TEXT;
  BEGIN
    SELECT c.conname INTO v_conname
    FROM pg_constraint c
      JOIN pg_class t ON c.conrelid = t.oid
    WHERE t.relname = 'ezkey_integration'
      AND c.contype = 'c'
      AND pg_get_constraintdef(c.oid) LIKE '%integration_lifecycle_status%';

    IF v_conname IS NOT NULL THEN
      EXECUTE format('ALTER TABLE ezkey_integration DROP CONSTRAINT %I', v_conname);
    END IF;
  END
$$;

-- Step 3: Add the replacement CHECK constraint allowing only ACTIVE and RETIRED
ALTER TABLE ezkey_integration
  ADD CONSTRAINT ezkey_integration_lifecycle_status_check
  CHECK (integration_lifecycle_status IN ('ACTIVE', 'RETIRED'));

-- Step 4: Update the column comment to reflect the new model
COMMENT ON COLUMN ezkey_integration.integration_lifecycle_status IS
  'Explicit lifecycle state for the integration: ACTIVE or RETIRED. '
  'ACTIVE = integration accepts new API keys and enrollments. '
  'RETIRED = permanently removed from normal operations while preserving historical data.';
