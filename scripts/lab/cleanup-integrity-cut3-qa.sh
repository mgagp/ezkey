#!/usr/bin/env bash
# Lab-only cleanup for seed-integrity-cut3-qa.sh / Integrity cut-3 exploratory QA.
#
# Default is FULL reset for Isabelle/Marc re-runs (avoid silent landmines):
#   untamper + OPEN lab alerts + ARCHIVE_SEAL/EXPORTED reset + drop archival override
#
#   ./scripts/lab/cleanup-integrity-cut3-qa.sh
#   ./scripts/lab/cleanup-integrity-cut3-qa.sh --keep-alerts
#   ./scripts/lab/cleanup-integrity-cut3-qa.sh --keep-seals
#   ./scripts/lab/cleanup-integrity-cut3-qa.sh --keep-tamper
#   ./scripts/lab/cleanup-integrity-cut3-qa.sh --keep-archival-override
#   ./scripts/lab/cleanup-integrity-cut3-qa.sh --keep-evidence   # keep alerts+seals+tamper+override
#   ./scripts/lab/cleanup-integrity-cut3-qa.sh --alerts-seals-only
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SQL_CLEAN_TAMPER="${SCRIPT_DIR}/cleanup-integrity-rupture-lab.sql"
SQL_CLEAN_ALERTS="${SCRIPT_DIR}/cleanup-integrity-cut3-alerts.sql"
SQL_CLEAN_SEALS="${SCRIPT_DIR}/cleanup-integrity-cut3-seals.sql"
COMPOSE_LAB_OVERRIDE="${SCRIPT_DIR}/docker-compose.lab-external-archival.yml"

CONTAINER="${EZKEY_POSTGRES_CONTAINER:-ezkey-postgres}"
DB_USER="${EZKEY_DB_USER:-postgres}"
DB_NAME="${EZKEY_DB_NAME:-ezkey_db}"
ADMIN_API_CONTAINER="${EZKEY_ADMIN_API_CONTAINER:-ezkey-admin-api}"

DO_UNTAMPER=1
DO_ALERTS=1
DO_SEALS=1
DO_DISABLE_ARCHIVAL=1

usage() {
  sed -n '2,16p' "$0" | sed 's/^# \{0,1\}//'
  exit "${1:-0}"
}

for arg in "$@"; do
  case "$arg" in
    -h|--help) usage 0 ;;
    --keep-tamper) DO_UNTAMPER=0 ;;
    --keep-alerts) DO_ALERTS=0 ;;
    --keep-seals) DO_SEALS=0 ;;
    --keep-archival-override) DO_DISABLE_ARCHIVAL=0 ;;
    --keep-evidence)
      DO_UNTAMPER=0
      DO_ALERTS=0
      DO_SEALS=0
      DO_DISABLE_ARCHIVAL=0
      ;;
    --alerts-seals-only)
      DO_UNTAMPER=0
      DO_ALERTS=1
      DO_SEALS=1
      DO_DISABLE_ARCHIVAL=0
      ;;
    --disable-archival)
      DO_UNTAMPER=0
      DO_ALERTS=0
      DO_SEALS=0
      DO_DISABLE_ARCHIVAL=1
      ;;
    --archival-only)
      DO_UNTAMPER=0
      DO_ALERTS=0
      DO_SEALS=0
      DO_DISABLE_ARCHIVAL=1
      ;;
    --all)
      DO_UNTAMPER=1
      DO_ALERTS=1
      DO_SEALS=1
      DO_DISABLE_ARCHIVAL=1
      ;;
    *)
      echo "Unknown argument: $arg" >&2
      usage 1
      ;;
  esac
done

run_sql() {
  local file="$1"
  docker exec -i "$CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 < "$file"
}

if [[ "$DO_UNTAMPER" -eq 1 || "$DO_ALERTS" -eq 1 || "$DO_SEALS" -eq 1 ]]; then
  if ! docker inspect "$CONTAINER" >/dev/null 2>&1; then
    echo "Container '$CONTAINER' not found; skip DB cleanup steps." >&2
  else
    if [[ "$DO_UNTAMPER" -eq 1 ]]; then
      echo "==> Removing LAB_INTEGRITY_TAMPER markers from audit reason..."
      run_sql "$SQL_CLEAN_TAMPER"
    fi
    if [[ "$DO_ALERTS" -eq 1 ]]; then
      echo "==> Removing OPEN lab integrity / heartbeat landmine alerts..."
      run_sql "$SQL_CLEAN_ALERTS"
    fi
    if [[ "$DO_SEALS" -eq 1 ]]; then
      echo "==> Resetting lab ARCHIVE_SEAL / SEALED / EXPORTED checkpoints to REGULAR/ACTIVE..."
      run_sql "$SQL_CLEAN_SEALS"
    fi
  fi
fi

if [[ "$DO_DISABLE_ARCHIVAL" -eq 1 ]]; then
  if ! docker inspect "$ADMIN_API_CONTAINER" >/dev/null 2>&1; then
    echo "Container '$ADMIN_API_CONTAINER' not found; skip archival disable." >&2
  else
    files="$(docker inspect "$ADMIN_API_CONTAINER" \
      --format '{{index .Config.Labels "com.docker.compose.project.config_files"}}')"
    working="$(docker inspect "$ADMIN_API_CONTAINER" \
      --format '{{index .Config.Labels "com.docker.compose.project.working_dir"}}')"
    if [[ -z "$files" || -z "$working" ]]; then
      echo "Could not read compose labels; recreate admin-api without the lab override manually." >&2
      exit 1
    fi

    local_files=()
    IFS=','
    # shellcheck disable=SC2086
    for f in $files; do
      if [[ "$f" == "$COMPOSE_LAB_OVERRIDE" ]]; then
        continue
      fi
      if [[ "$(basename "$f")" == "$(basename "$COMPOSE_LAB_OVERRIDE")" \
        && "$f" == *scripts/lab/docker-compose.lab-external-archival.yml ]]; then
        continue
      fi
      local_files+=(-f "$f")
    done
    unset IFS

    echo "==> Recreating admin-api without lab external-archival override..."
    (
      cd "$working"
      docker compose "${local_files[@]}" up -d --no-deps admin-api
    )
    echo "    External archival returns to compose/profile default (false)."
  fi
fi

echo "Done. Default cleanup clears tamper, OPEN lab alerts, and ARCHIVE_SEAL/EXPORTED landmines."
echo "MANIPULATION_CONCILIATION checkpoints (successful reconcile) are left intact."
echo "Re-run path A or B only after this cleanup (or use separate stacks). Prefer A before B."
