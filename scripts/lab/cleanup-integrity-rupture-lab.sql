-- Lab-only: undo seed-integrity-rupture-lab.sql marker on reason.
-- Safe to re-run.

UPDATE ezkey_audit_log
SET reason =
  CASE
    WHEN reason = 'LAB_INTEGRITY_TAMPER' THEN NULL
    WHEN reason LIKE '% LAB_INTEGRITY_TAMPER'
      THEN regexp_replace(reason, ' LAB_INTEGRITY_TAMPER$', '')
    WHEN reason LIKE '%LAB_INTEGRITY_TAMPER%'
      THEN regexp_replace(reason, ' ?LAB_INTEGRITY_TAMPER', '', 'g')
    ELSE reason
  END
WHERE reason LIKE '%LAB_INTEGRITY_TAMPER%';

SELECT COUNT(*) AS remaining_lab_tampers
FROM ezkey_audit_log
WHERE reason LIKE '%LAB_INTEGRITY_TAMPER%';
