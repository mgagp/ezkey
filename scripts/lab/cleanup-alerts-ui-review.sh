#!/usr/bin/env bash
# Remove lab-only alert rows inserted by seed-alerts-ui-review.sh
set -euo pipefail

CONTAINER="${EZKEY_POSTGRES_CONTAINER:-ezkey-postgres}"
DB_USER="${EZKEY_DB_USER:-postgres}"
DB_NAME="${EZKEY_DB_NAME:-ezkey_db}"

LAB_DEDUPE_KEYS=(
  'AUDIT_CHAIN_GAP_PENDING:99901'
  'AUDIT_CHAIN_HEARTBEAT_STALE'
  'AUDIT_INTEGRITY_RUPTURE:lab-ui-001'
  'AUDIT_CHAIN_GAP_PENDING:88888'
)

for key in "${LAB_DEDUPE_KEYS[@]}"; do
  docker exec "$CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 -c \
    "DELETE FROM ezkey_alert WHERE dedupe_key = '${key}';"
done

echo "Lab alert rows removed."
