-- Lab-only: reset ARCHIVE_SEAL / sealed-export lifecycle left by cut-3 path B.
-- Applied by scripts/lab/cleanup-integrity-cut3-qa.sh (default / unless --keep-seals).
--
-- Warm lab stacks do not auto-seal (retention P12M). Exceptional Seal Archive during
-- path B leaves ARCHIVE_SEAL rows that make reconcile reject with
-- "checkpoint … not REGULAR (type=ARCHIVE_SEAL)" when an OPEN rupture window overlaps.
-- Does NOT touch MANIPULATION_CONCILIATION (path A success evidence).

UPDATE ezkey_audit_chain_checkpoint
SET
  checkpoint_type = 'REGULAR',
  lifecycle_state = 'ACTIVE',
  sealed_at = NULL,
  sealed_by_admin_id = NULL,
  exported_at = NULL,
  exported_by_admin_id = NULL,
  export_bundle_digest = NULL,
  notes = NULL
WHERE checkpoint_type = 'ARCHIVE_SEAL'
   OR lifecycle_state IN ('SEALED', 'EXPORTED', 'PURGEABLE');

SELECT
  checkpoint_id,
  checkpoint_type,
  lifecycle_state,
  entry_count,
  window_start,
  window_end
FROM ezkey_audit_chain_checkpoint
WHERE checkpoint_type <> 'REGULAR'
   OR lifecycle_state <> 'ACTIVE'
ORDER BY checkpoint_id;
