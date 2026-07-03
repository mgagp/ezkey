-- Lab-only rows for Admin UI /alerts list validation (Wave C).
-- Applied by scripts/lab/seed-alerts-ui-review.sh

INSERT INTO ezkey_alert (alert_type, severity, status, dedupe_key, payload, occurrence_count)
VALUES
  (
    'AUDIT_CHAIN_GAP_PENDING',
    'WARNING',
    'OPEN',
    'AUDIT_CHAIN_GAP_PENDING:99901',
    '{"anchorCheckpointId":99901,"gapStart":"2026-07-01T10:00:00Z","estimatedGapEnd":"2026-07-01T11:30:00Z","estimatedGapMinutes":90,"message":"Lab gap for UI review"}',
    3
  ),
  (
    'AUDIT_CHAIN_HEARTBEAT_STALE',
    'WARNING',
    'OPEN',
    'AUDIT_CHAIN_HEARTBEAT_STALE',
    '{"phase":"DEGRADED_SERVICE","anchorCheckpointId":42,"latestWindowEnd":"2026-07-03T14:00:00Z"}',
    1
  ),
  (
    'AUDIT_INTEGRITY_RUPTURE',
    'CRITICAL',
    'OPEN',
    'AUDIT_INTEGRITY_RUPTURE:lab-ui-001',
    '{"windowStart":"2026-07-02T00:00:00Z","windowEnd":"2026-07-02T23:59:59Z","chainStatus":"RUPTURE","violationCount":2,"entryHmacViolationCount":3,"failBoundary":"2026-07-02T00:00:00Z","resumeBoundary":"2026-07-02T23:59:59Z","entryViolations":{"items":[],"totalCount":3,"returnedCount":3,"truncated":false},"chainViolations":{"items":[],"totalCount":2,"returnedCount":2,"truncated":false},"message":"Lab rupture for UI review"}',
    1
  );

INSERT INTO ezkey_alert (
  alert_type,
  severity,
  status,
  dedupe_key,
  payload,
  occurrence_count,
  resolved_at,
  resolution_reason
)
VALUES (
  'AUDIT_CHAIN_GAP_PENDING',
  'WARNING',
  'RESOLVED',
  'AUDIT_CHAIN_GAP_PENDING:88888',
  '{"anchorCheckpointId":88888,"gapStart":"2026-06-28T08:00:00Z","estimatedGapEnd":"2026-06-28T08:15:00Z","estimatedGapMinutes":15,"message":"Resolved lab row for UI filter test"}',
  2,
  NOW() - INTERVAL '1 day',
  'GAP_DECLARED'
);
