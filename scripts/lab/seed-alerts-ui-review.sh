#!/usr/bin/env bash
# Lab-only: seed ezkey_alert rows for Admin UI /alerts list polish validation (Wave C).
# Safe to re-run: removes lab dedupe keys first, then inserts fresh rows.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SQL_FILE="${SCRIPT_DIR}/seed-alerts-ui-review.sql"

CONTAINER="${EZKEY_POSTGRES_CONTAINER:-ezkey-postgres}"
DB_USER="${EZKEY_DB_USER:-postgres}"
DB_NAME="${EZKEY_DB_NAME:-ezkey_db}"

LAB_DEDUPE_KEYS=(
  'AUDIT_CHAIN_GAP_PENDING:99901'
  'AUDIT_CHAIN_HEARTBEAT_STALE'
  'AUDIT_INTEGRITY_RUPTURE:lab-ui-001'
  'AUDIT_CHAIN_GAP_PENDING:88888'
)

echo "==> Removing prior lab alert rows (if any)..."
for key in "${LAB_DEDUPE_KEYS[@]}"; do
  docker exec "$CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 -c \
    "DELETE FROM ezkey_alert WHERE dedupe_key = '${key}';"
done

echo "==> Inserting lab OPEN alerts (one per family) + one RESOLVED row..."
docker exec -i "$CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 \
  < "$SQL_FILE"

echo "==> Current lab alerts:"
docker exec "$CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -c \
  "SELECT alert_id, alert_type, status, severity, occurrence_count, dedupe_key
   FROM ezkey_alert
   ORDER BY status, alert_id;"

echo "Done. Open Admin UI /alerts as Global Admin (default filter: Open → 3 rows)."
