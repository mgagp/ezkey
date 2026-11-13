-- Lab-only: induce a real per-entry HMAC mismatch for Integrity cut-3 QA.
-- Applied by scripts/lab/seed-integrity-cut3-qa.sh
--
-- Mutates `reason` (HMAC canonical field 15) and leaves `entry_hmac` unchanged.
-- Avoids mutating `event_details` JSON used by KEY_* GIN indexes.
-- POST /api/v1/audit-logs/integrity-validation/run (raiseAlert=true) can then raise
-- a real OPEN AUDIT_INTEGRITY_RUPTURE. Do not use seed-alerts-ui-review for reconcile.

UPDATE ezkey_audit_log
SET reason =
  CASE
    WHEN reason IS NULL OR btrim(reason) = ''
      THEN 'LAB_INTEGRITY_TAMPER'
    WHEN reason LIKE '%LAB_INTEGRITY_TAMPER%'
      THEN reason
    ELSE left(reason || ' LAB_INTEGRITY_TAMPER', 500)
  END
WHERE audit_log_id = (
  SELECT audit_log_id
  FROM ezkey_audit_log
  WHERE entry_hmac IS NOT NULL
    AND btrim(entry_hmac) <> ''
  ORDER BY created_at ASC, audit_log_id ASC
  LIMIT 1
);

SELECT
  audit_log_id,
  event_type,
  created_at,
  reason,
  LEFT(entry_hmac, 24) AS entry_hmac_prefix
FROM ezkey_audit_log
WHERE reason LIKE '%LAB_INTEGRITY_TAMPER%'
ORDER BY audit_log_id;
