#!/usr/bin/env bash
#
# Export Ezkey backend images (docker save), copy to Lightsail (scp), docker load on the VM.
# Intended to run from a dev machine with Docker; the VM uses SSH host alias (default: ezkey).
#
# Prerequisites:
#   - Images already built locally (e.g. docker compose build …) unless you use a wrapper script
#   - ssh / scp to LIGHTSAIL_SSH_HOST
#
# Operator files (docker-compose.yml, Caddyfile, clean-start.sh) under experimental-hybrid/lightsail/
# are copied to the VM before --clean-start so the remote stack matches the repo (images alone do not
# update compose). Override path with LIGHTSAIL_REMOTE_DIR if your tree differs.
#
# Usage (from repository root):
#   ./experimental-hybrid/scripts/export-backend-images-to-lightsail.sh
#   LIGHTSAIL_SSH_HOST=ezkey ./experimental-hybrid/scripts/export-backend-images-to-lightsail.sh --clean-start
#
# Options:
#   --apis-only       Export only admin-api, auth-api, integration-api (no migration image).
#   --clean-start     After load, run ~/ezkey/experimental-hybrid/lightsail/clean-start.sh (DESTRUCTIVE: wipes volumes).
#   --remove-remote-tars  After successful load, rm the tar files from the remote home directory.
#   --dry-run         Print commands only.
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
DO_CLEAN_START=""
REMOVE_REMOTE_TARS=""
DRY_RUN=""

usage() {
  sed -n '2,25p' "$0" | tail -n +2
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --apis-only) INCLUDE_MIGRATION="" ;;
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

IMAGES=()
TARS=()
if [[ -n "$INCLUDE_MIGRATION" ]]; then
  IMAGES+=(ezkey-migration:latest)
  TARS+=(ezkey-migration.tar)
fi
IMAGES+=(ezkey-admin-api:latest ezkey-auth-api:latest ezkey-integration-api:latest)
TARS+=(ezkey-admin-api.tar ezkey-auth-api.tar ezkey-integration-api.tar)

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
    echo "  Build first, e.g. from repo root:" >&2
    echo "    docker compose -f docker/docker-compose.yml build migration admin-api auth-api integration-api" >&2
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
if [[ -z "$DO_CLEAN_START" ]]; then
  echo "Tip: after load, recreate services if you did not wipe volumes — see experimental-hybrid/BACKEND_ROLLING_UPDATE.md"
fi
