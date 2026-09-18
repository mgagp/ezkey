-- Lab-only: remove OPEN integrity landmines left by cut-3 exploratory QA.
-- Applied by scripts/lab/cleanup-integrity-cut3-qa.sh (default / unless --keep-alerts).
--
-- Deletes:
--   - OPEN AUDIT_INTEGRITY_RUPTURE (real seed alerts from Run validation)
--   - OPEN AUDIT_CHAIN_HEARTBEAT_STALE (blocks reconcile; often from seed-alerts-ui-review)
--   - known seed-alerts-ui-review dedupe keys (OPEN or RESOLVED polish rows)
-- Skips rupture rows still referenced by entry-integrity conciliation (FK).

DELETE FROM ezkey_alert
WHERE dedupe_key IN (
  'AUDIT_CHAIN_GAP_PENDING:99901',
  'AUDIT_CHAIN_HEARTBEAT_STALE',
  'AUDIT_INTEGRITY_RUPTURE:lab-ui-001',
  'AUDIT_CHAIN_GAP_PENDING:88888'
);

DELETE FROM ezkey_alert a
WHERE a.status = 'OPEN'
  AND a.alert_type = 'AUDIT_INTEGRITY_RUPTURE'
  AND NOT EXISTS (
    SELECT 1
    FROM ezkey_audit_entry_integrity_conciliation c
    WHERE c.source_alert_id = a.alert_id
  );

DELETE FROM ezkey_alert
WHERE status = 'OPEN'
  AND alert_type = 'AUDIT_CHAIN_HEARTBEAT_STALE';

SELECT alert_id, alert_type, status, LEFT(dedupe_key, 60) AS dedupe_key
FROM ezkey_alert
ORDER BY alert_id;
