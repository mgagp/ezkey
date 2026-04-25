#!/usr/bin/env bash
#
# Export Ezkey backend images (docker save), copy to Lightsail (scp), docker load on the VM.
# LIGHTSAIL_SSH_HOST defaults to ezkey (experimental hybrid). Override if your SSH Host name differs.
#
# Prerequisites: built images; ssh/scp to LIGHTSAIL_SSH_HOST.
#
# With --clean-start, syncs lightsail operator files to the VM then runs clean-start.sh (destructive).
# With --sync-operator-files, copies docker-compose.yml, Caddyfile, and clean-start.sh to the VM only
# (no clean-start) so Caddy and Compose stay aligned with the repo; preserves existing volumes/DB.
# With --remote-up, after load (and after --sync-operator-files if set), runs docker compose up -d on
# the VM so migration + backends + Caddy pick up the loaded images (no volume wipe).
# Do not combine --clean-start with --sync-operator-files or --remote-up (incompatible; see script check).
# Override remote path with LIGHTSAIL_REMOTE_DIR if needed.
#
# Usage (from repository root):
#   ./experimental-hybrid/scripts/export-backend-images-to-lightsail.sh
#   ./experimental-hybrid/scripts/export-backend-images-to-lightsail.sh --apis-only
#   ./experimental-hybrid/scripts/export-backend-images-to-lightsail.sh --include-demo-acme
#   ./experimental-hybrid/scripts/export-backend-images-to-lightsail.sh --sync-operator-files --remote-up
#   ./experimental-hybrid/scripts/export-backend-images-to-lightsail.sh --clean-start
#
# Options:
#   --apis-only            Export only admin-api, auth-api, integration-api (no migration image).
#   --include-demo-acme    Also export ezkey-demo-app-acme (after the API images; requires a local
#                          build, see below).
#   --sync-operator-files  After load, scp lightsail docker-compose.yml, Caddyfile, clean-start.sh
#                          to LIGHTSAIL_REMOTE_DIR (no DB wipe; use with --remote-up for a full rolling refresh).
#   --remote-up            After load (and optional sync), ssh to the VM and run
#                          docker compose up -d in LIGHTSAIL_REMOTE_DIR (picks up new images; Postgres
#                          data and named volumes are retained; migration re-runs from the new image).
#   --clean-start          After load, sync lightsail files and run clean-start.sh
#                          (DESTRUCTIVE: wipes volumes). Incompatible with --sync-operator-files and
#                          --remote-up.
#   --remove-remote-tars   After successful load, rm the tar files from the remote home directory.
#   --dry-run              Print commands only.
#
# Build prerequisite for --include-demo-acme (from repository root):
#   docker build -f docker/Dockerfile --target demo-app-acme -t ezkey-demo-app-acme:latest .
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
COMPOSE_FILE="${REPO_ROOT}/docker/docker-compose.yml"
EXPORT_DIR="${EXPORT_DIR:-${REPO_ROOT}/docker/export}"

SSH_HOST="${LIGHTSAIL_SSH_HOST:-ezkey}"
LIGHTSAIL_REMOTE_DIR="${LIGHTSAIL_REMOTE_DIR:-~/ezkey/experimental-hybrid/lightsail}"
LIGHTSAIL_LOCAL_DIR="${REPO_ROOT}/experimental-hybrid/lightsail"

INCLUDE_MIGRATION="1"
INCLUDE_DEMO_ACME=""
DO_CLEAN_START=""
REMOVE_REMOTE_TARS=""
DRY_RUN=""
SYNC_OPERATOR_FILES=""
REMOTE_UP=""

usage() {
  cat <<'EOF'
Export Ezkey backend images → Lightsail: docker save, scp, remote docker load.

Usage (from repository root):
  ./experimental-hybrid/scripts/export-backend-images-to-lightsail.sh [options]

Options:
  --apis-only            Export only admin-api, auth-api, integration-api (no migration image).
  --include-demo-acme    Also export ezkey-demo-app-acme (after API tars; build image first).
  --sync-operator-files  After load, scp docker-compose.yml, Caddyfile, clean-start.sh (no volume wipe).
  --remote-up            After load/sync, on the VM: docker compose up -d (rolling apply; DB preserved).
  --clean-start          After load, sync and run clean-start.sh (DESTRUCTIVE). Not with --sync-operator-files
                         or --remote-up.
  --remove-remote-tars   After successful load, remove tar files on the remote home directory.
  --dry-run              Print commands only.
  -h, --help             Show this help.

Build demo image when using --include-demo-acme (from repository root):
  docker build -f docker/Dockerfile --target demo-app-acme -t ezkey-demo-app-acme:latest .
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --apis-only) INCLUDE_MIGRATION="" ;;
    --include-demo-acme) INCLUDE_DEMO_ACME="1" ;;
    --sync-operator-files) SYNC_OPERATOR_FILES="1" ;;
    --remote-up) REMOTE_UP="1" ;;
    --clean-start) DO_CLEAN_START="1" ;;
    --remove-remote-tars) REMOVE_REMOTE_TARS="1" ;;
    --dry-run) DRY_RUN="1" ;;
    -h|--help) usage; exit 0 ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
  shift
done

if [[ -n "$DO_CLEAN_START" ]] && { [[ -n "$SYNC_OPERATOR_FILES" ]] || [[ -n "$REMOTE_UP" ]]; }; then
  echo "error: --clean-start cannot be combined with --sync-operator-files or --remote-up." >&2
  echo "  For a no-wipe upgrade (Caddy, Compose, images, migration), use --sync-operator-files and/or --remote-up only." >&2
  echo "  For a disposable full reset, use --clean-start alone." >&2
  exit 1
fi

IMAGES=()
TARS=()
if [[ -n "$INCLUDE_MIGRATION" ]]; then
  IMAGES+=(ezkey-migration:latest)
  TARS+=(ezkey-migration.tar)
fi
IMAGES+=(ezkey-admin-api:latest ezkey-auth-api:latest ezkey-integration-api:latest)
TARS+=(ezkey-admin-api.tar ezkey-auth-api.tar ezkey-integration-api.tar)
if [[ -n "$INCLUDE_DEMO_ACME" ]]; then
  IMAGES+=(ezkey-demo-app-acme:latest)
  TARS+=(ezkey-demo-app-acme.tar)
fi

run() {
  if [[ -n "$DRY_RUN" ]]; then
    printf '[dry-run]'
    printf ' %q' "$@"
    echo
    return 0
  fi
  "$@"
}

echo "=========================================="
echo "  Export backend images → Lightsail"
echo "  SSH: ${SSH_HOST}"
if [[ -n "$INCLUDE_DEMO_ACME" ]]; then
  echo "  (including demo-app-acme)"
fi
echo "=========================================="
echo ""

if [[ ! -f "$COMPOSE_FILE" ]]; then
  echo "error: compose file not found: ${COMPOSE_FILE}" >&2
  exit 1
fi

mkdir -p "$EXPORT_DIR"

for i in "${!IMAGES[@]}"; do
  img="${IMAGES[$i]}"
  tar_name="${TARS[$i]}"
  out="${EXPORT_DIR}/${tar_name}"
  echo "→ docker save ${img} → ${out}"
  if ! docker image inspect "$img" >/dev/null 2>&1; then
    echo "error: image not found locally: ${img}" >&2
    if [[ "$img" == "ezkey-demo-app-acme:latest" ]]; then
      echo "  Build first, e.g. from repo root:" >&2
      echo "    docker build -f docker/Dockerfile --target demo-app-acme -t ezkey-demo-app-acme:latest ." >&2
    else
      echo "  Build first, e.g. from repo root:" >&2
      echo "    docker compose -f docker/docker-compose.yml build migration admin-api auth-api integration-api" >&2
    fi
    exit 1
  fi
  run docker save -o "$out" "$img"
done

echo ""
echo "→ scp → ${SSH_HOST}:~/"
for tar_name in "${TARS[@]}"; do
  run scp "${EXPORT_DIR}/${tar_name}" "${SSH_HOST}:~/"
done

echo ""
echo "→ docker load on ${SSH_HOST}"
for tar_name in "${TARS[@]}"; do
  run ssh "${SSH_HOST}" "docker load -i ~/${tar_name}"
done

if [[ -n "$REMOVE_REMOTE_TARS" ]]; then
  echo ""
  echo "→ remove remote tars in ~/"
  remote_paths=""
  for tar_name in "${TARS[@]}"; do
    remote_paths+=" ~/${tar_name}"
  done
  run ssh "${SSH_HOST}" "rm -f${remote_paths}"
fi

if [[ -n "$SYNC_OPERATOR_FILES" && -z "$DO_CLEAN_START" ]]; then
  echo ""
  echo "→ sync operator files (no clean-start) → ${SSH_HOST}:${LIGHTSAIL_REMOTE_DIR}"
  run ssh "${SSH_HOST}" "mkdir -p ${LIGHTSAIL_REMOTE_DIR}"
  for f in docker-compose.yml Caddyfile clean-start.sh; do
    src="${LIGHTSAIL_LOCAL_DIR}/${f}"
    if [[ ! -f "$src" ]]; then
      echo "error: missing ${src}" >&2
      exit 1
    fi
    run scp "$src" "${SSH_HOST}:${LIGHTSAIL_REMOTE_DIR}/${f}"
  done
fi

if [[ -n "$REMOTE_UP" && -z "$DO_CLEAN_START" ]]; then
  echo ""
  echo "→ remote rolling stack (docker compose up -d) on ${SSH_HOST}"
  # Recreate containers so :latest and updated Compose/Caddy take effect; Postgres keeps postgres-data.
  run ssh "${SSH_HOST}" "set -euo pipefail; cd ${LIGHTSAIL_REMOTE_DIR} && docker compose up -d --remove-orphans"
fi

if [[ -n "$DO_CLEAN_START" ]]; then
  echo ""
  echo "→ sync lightsail operator files → ${SSH_HOST}:${LIGHTSAIL_REMOTE_DIR}"
  # Let the remote login shell expand ~ (do not wrap the path in single quotes on the remote).
  run ssh "${SSH_HOST}" "mkdir -p ${LIGHTSAIL_REMOTE_DIR}"
  for f in docker-compose.yml Caddyfile clean-start.sh; do
    src="${LIGHTSAIL_LOCAL_DIR}/${f}"
    if [[ ! -f "$src" ]]; then
      echo "error: missing ${src}" >&2
      exit 1
    fi
    run scp "$src" "${SSH_HOST}:${LIGHTSAIL_REMOTE_DIR}/${f}"
  done
  echo ""
  echo "→ remote clean-start (destructive: docker compose down -v, keygen, up)"
  run ssh "${SSH_HOST}" "set -euo pipefail; cd ${LIGHTSAIL_REMOTE_DIR} && bash clean-start.sh"
fi

echo ""
echo "Done."
if [[ -n "$DO_CLEAN_START" ]]; then
  :
elif [[ -n "$REMOTE_UP" ]]; then
  echo "Rolling steps finished: operator files are synced (if --sync-operator-files) and the stack was restarted. Postgres and named app volumes are unchanged; the migration service applies Flyway from the new image on up."
else
  echo "Tip: for a one-pass, no-wipe upgrade (Caddy, Compose, images, migration), use --sync-operator-files and --remote-up, or: experimental-hybrid/scripts/full-exp-environment-upgrade.sh rolling"
  echo "     See also experimental-hybrid/BACKEND_ROLLING_UPDATE.md"
fi
