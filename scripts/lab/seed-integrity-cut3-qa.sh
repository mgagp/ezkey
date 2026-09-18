#!/usr/bin/env bash
# Lab-only seed helpers for Integrity cut-3 exploratory QA (PR #564 / Isabelle walk).
#
# Produces the two warm-stack preconditions that clean-start does not:
#   (A) a real entry-HMAC tamper so Integrity "Run validation" can raise
#       AUDIT_INTEGRITY_RUPTURE (Alerts → Integrity deep-link + reconcile)
#   (B) Admin API with external archival enabled so a SEALED ARCHIVE_SEAL tranche
#       can yield confirmationRequired=true (Confirm archived button)
#
# Safe for local/demo only. Does not invent a second journal or mock Integrity APIs.
#
# Usage (repo root, warm Docker stack):
#   ./scripts/lab/seed-integrity-cut3-qa.sh              # tamper + print next steps
#   ./scripts/lab/seed-integrity-cut3-qa.sh --enable-archival
#   ./scripts/lab/seed-integrity-cut3-qa.sh --tamper-only
#   ./scripts/lab/seed-integrity-cut3-qa.sh --archival-only
#
# Operator steps: docs/lab/INTEGRITY_CUT3_EXPLORATORY_QA.md
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
SQL_TAMPER="${SCRIPT_DIR}/seed-integrity-rupture-lab.sql"
COMPOSE_LAB_OVERRIDE="${SCRIPT_DIR}/docker-compose.lab-external-archival.yml"

CONTAINER="${EZKEY_POSTGRES_CONTAINER:-ezkey-postgres}"
DB_USER="${EZKEY_DB_USER:-postgres}"
DB_NAME="${EZKEY_DB_NAME:-ezkey_db}"
ADMIN_API_CONTAINER="${EZKEY_ADMIN_API_CONTAINER:-ezkey-admin-api}"

DO_TAMPER=1
DO_ARCHIVAL=0

usage() {
  sed -n '2,22p' "$0" | sed 's/^# \{0,1\}//'
  exit "${1:-0}"
}

for arg in "$@"; do
  case "$arg" in
    -h|--help) usage 0 ;;
    --tamper-only) DO_TAMPER=1; DO_ARCHIVAL=0 ;;
    --archival-only) DO_TAMPER=0; DO_ARCHIVAL=1 ;;
    --enable-archival) DO_ARCHIVAL=1 ;;
    --all) DO_TAMPER=1; DO_ARCHIVAL=1 ;;
    *)
      echo "Unknown argument: $arg" >&2
      usage 1
      ;;
  esac
done

require_container() {
  local name="$1"
  if ! docker inspect "$name" >/dev/null 2>&1; then
    echo "Container '$name' not found. Start the local stack (e.g. ezkey-tests/clean-start.sh) first." >&2
    exit 1
  fi
}

compose_config_files() {
  docker inspect "$ADMIN_API_CONTAINER" \
    --format '{{index .Config.Labels "com.docker.compose.project.config_files"}}' 2>/dev/null \
    || true
}

compose_working_dir() {
  docker inspect "$ADMIN_API_CONTAINER" \
    --format '{{index .Config.Labels "com.docker.compose.project.working_dir"}}' 2>/dev/null \
    || true
}

enable_external_archival() {
  require_container "$ADMIN_API_CONTAINER"
  local files working
  files="$(compose_config_files)"
  working="$(compose_working_dir)"
  if [[ -z "$files" || -z "$working" ]]; then
    echo "Could not read compose labels from $ADMIN_API_CONTAINER." >&2
    echo "Recreate admin-api manually with -f scripts/lab/docker-compose.lab-external-archival.yml" >&2
    exit 1
  fi

  local -a compose_args=()
  local IFS=','
  # shellcheck disable=SC2086
  for f in $files; do
    compose_args+=(-f "$f")
  done
  unset IFS
  compose_args+=(-f "$COMPOSE_LAB_OVERRIDE")

  echo "==> Enabling ezkey.audit.archive.external-archival-enabled via lab compose override..."
  echo "    (brief Admin API recreate; other services stay up)"
  (
    cd "$working"
    docker compose "${compose_args[@]}" up -d --no-deps admin-api
  )

  echo "==> Waiting for Admin API health..."
  local i
  for i in $(seq 1 60); do
    if curl -sf "http://localhost:9081/actuator/health" >/dev/null 2>&1 \
      || curl -sf "http://localhost:9080/actuator/health" >/dev/null 2>&1; then
      echo "    Admin API healthy."
      return 0
    fi
    sleep 2
  done
  echo "Admin API did not become healthy in time; check docker logs $ADMIN_API_CONTAINER" >&2
  exit 1
}

print_tamper_summary() {
  docker exec "$CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -c \
    "SELECT audit_log_id, event_type, created_at, reason
     FROM ezkey_audit_log
     WHERE reason LIKE '%LAB_INTEGRITY_TAMPER%'
     ORDER BY audit_log_id;"
}

print_checkpoint_hint() {
  echo "==> Candidate REGULAR/ACTIVE checkpoints (prefer an empty window for Seal Archive):"
  docker exec "$CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -c \
    "SELECT checkpoint_id, window_start, window_end, entry_count, checkpoint_type, lifecycle_state
     FROM ezkey_audit_chain_checkpoint
     WHERE checkpoint_type = 'REGULAR' AND lifecycle_state = 'ACTIVE'
     ORDER BY checkpoint_id
     LIMIT 12;"
}

if [[ "$DO_TAMPER" -eq 1 ]]; then
  require_container "$CONTAINER"
  echo "==> Applying lab entry-HMAC tamper (reason field change, hmac untouched)..."
  docker exec -i "$CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 \
    < "$SQL_TAMPER"
  echo
  print_tamper_summary
  echo
fi

if [[ "$DO_ARCHIVAL" -eq 1 ]]; then
  enable_external_archival
  echo
  print_checkpoint_hint
  echo
fi

cat <<'EOF'
========================================================================
Next operator steps (Global Admin) — see docs/lab/INTEGRITY_CUT3_EXPLORATORY_QA.md
------------------------------------------------------------------------
(A) Real rupture alert → Alerts deep-link → Reconcile
  1. Do NOT use scripts/lab/seed-alerts-ui-review.sh for this path
     (fake payload; also seeds OPEN HEARTBEAT_STALE which blocks reconcile).
     If that seed was applied: ./scripts/lab/cleanup-alerts-ui-review.sh
  2. Admin UI → Integrity → set date range covering the tampered created_at
  3. Click "Run validation and raise alert if needed"
  4. Alerts → open the OPEN AUDIT_INTEGRITY_RUPTURE → Integrity deep-link
  5. On Integrity, use Reconcile (acknowledge live entry ids + justification)

(B) Confirm archived (confirmationRequired)
  1. Ensure archival flag is on: re-run with --enable-archival if needed
  2. Integrity → Lifecycle overview should show externalArchivalEnabled=yes
  3. Exceptional maintenance → Seal Archive on one empty REGULAR checkpoint
     (checkpoint ID mode; integrity must be intact in that window)
  4. confirmationRequired becomes Yes → Confirm archived appears
  5. Confirm with a lab exportBundleDigest (≥16 chars)

Cleanup: ./scripts/lab/cleanup-integrity-cut3-qa.sh
========================================================================
EOF
