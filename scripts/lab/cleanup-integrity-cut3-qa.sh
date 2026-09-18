#!/usr/bin/env bash
# Lab-only cleanup for seed-integrity-cut3-qa.sh
#
#   ./scripts/lab/cleanup-integrity-cut3-qa.sh                 # untamper only
#   ./scripts/lab/cleanup-integrity-cut3-qa.sh --disable-archival
#   ./scripts/lab/cleanup-integrity-cut3-qa.sh --all
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SQL_CLEAN="${SCRIPT_DIR}/cleanup-integrity-rupture-lab.sql"
COMPOSE_LAB_OVERRIDE="${SCRIPT_DIR}/docker-compose.lab-external-archival.yml"

CONTAINER="${EZKEY_POSTGRES_CONTAINER:-ezkey-postgres}"
DB_USER="${EZKEY_DB_USER:-postgres}"
DB_NAME="${EZKEY_DB_NAME:-ezkey_db}"
ADMIN_API_CONTAINER="${EZKEY_ADMIN_API_CONTAINER:-ezkey-admin-api}"

DO_UNTAMPER=1
DO_DISABLE_ARCHIVAL=0

for arg in "$@"; do
  case "$arg" in
    --disable-archival) DO_DISABLE_ARCHIVAL=1 ;;
    --archival-only) DO_UNTAMPER=0; DO_DISABLE_ARCHIVAL=1 ;;
    --all) DO_UNTAMPER=1; DO_DISABLE_ARCHIVAL=1 ;;
    -h|--help)
      sed -n '2,10p' "$0" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
    *)
      echo "Unknown argument: $arg" >&2
      exit 1
      ;;
  esac
done

if [[ "$DO_UNTAMPER" -eq 1 ]]; then
  if ! docker inspect "$CONTAINER" >/dev/null 2>&1; then
    echo "Container '$CONTAINER' not found; skip untamper." >&2
  else
    echo "==> Removing LAB_INTEGRITY_TAMPER markers from audit reason..."
    docker exec -i "$CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 \
      < "$SQL_CLEAN"
  fi
fi

if [[ "$DO_DISABLE_ARCHIVAL" -eq 1 ]]; then
  if ! docker inspect "$ADMIN_API_CONTAINER" >/dev/null 2>&1; then
    echo "Container '$ADMIN_API_CONTAINER' not found; skip archival disable." >&2
    exit 0
  fi
  files="$(docker inspect "$ADMIN_API_CONTAINER" \
    --format '{{index .Config.Labels "com.docker.compose.project.config_files"}}')"
  working="$(docker inspect "$ADMIN_API_CONTAINER" \
    --format '{{index .Config.Labels "com.docker.compose.project.working_dir"}}')"
  if [[ -z "$files" || -z "$working" ]]; then
    echo "Could not read compose labels; recreate admin-api without the lab override manually." >&2
    exit 1
  fi

  # Drop the lab override if present, then recreate with the remaining files only.
  local_files=()
  IFS=','
  # shellcheck disable=SC2086
  for f in $files; do
    if [[ "$f" == "$COMPOSE_LAB_OVERRIDE" ]]; then
      continue
    fi
    # Also skip if path ends with the lab override name (absolute vs relative).
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

echo "Done. OPEN AUDIT_INTEGRITY_RUPTURE alerts (if any) are left for operator reconcile or manual resolve."
echo "Sealed ARCHIVE_SEAL checkpoints (if any) are left as-is."
