#!/usr/bin/env bash
#
# One-shot operator flow: build backend images (optional), export/load to Lightsail (optional),
# optional no-wipe sync of Caddy/Compose to the VM and remote docker compose up (optional),
# remote clean-start (optional, destructive), deploy Admin UI to Cloudflare (optional).
#
# Usage (from repository root, Git Bash):
#   ./experimental-hybrid/scripts/full-exp-environment-upgrade.sh
#   ./experimental-hybrid/scripts/full-exp-environment-upgrade.sh full
#   ./experimental-hybrid/scripts/full-exp-environment-upgrade.sh rolling
#   ./experimental-hybrid/scripts/full-exp-environment-upgrade.sh --help
#
# With no arguments, or with the "full" subcommand first, runs the destructive "everything"
# preset (build + export + clean-start + remove remote tars + deploy UI production + build).
#
# Subcommand "rolling" (EXPerimental / Lightsail, no database wipe):
#   --build --export --include-demo-acme is available via --include-demo-acme
#   default: build migration + all APIs, export all images including migration, --remove-remote-tars,
#   --sync-operator-files, --remote-up on LIGHTSAIL_SSH_HOST (Caddy + Compose from repo, then
#   docker compose up -d; Postgres and named volumes are preserved; Flyway runs from new image).
#
# Environment:
#   LIGHTSAIL_SSH_HOST   default ezkey; set only if your SSH Host alias differs (experimental hybrid).
#   EXPORT_DIR           passed through to export script (default: docker/export)
#   Root .env            CLOUDFLARE_* and VITE_API_BASE_URL for UI deploy (see scripts/cloudflare/*.sh)
#   PREVIEW_BRANCH       for preview UI deploy (default: timestamped); set e.g. exp1-admin-ui-preview
#   CLOUDFLARE_PAGES_PROJECT  default ezkey-admin-ui
#
set -euo pipefail

export LIGHTSAIL_SSH_HOST="${LIGHTSAIL_SSH_HOST:-ezkey}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
EXPORT_SCRIPT="${SCRIPT_DIR}/export-backend-images-to-lightsail.sh"
PREVIEW_DEPLOY="${REPO_ROOT}/scripts/cloudflare/deploy-admin-ui-preview.sh"
PROD_DEPLOY="${REPO_ROOT}/scripts/cloudflare/deploy-admin-ui-production.sh"

DO_BUILD=""
DO_EXPORT=""
DO_CLEAN_START=""
DO_DEPLOY_UI=""
UI_MODE="preview"
APIS_ONLY=""
REMOVE_REMOTE_TARS=""
DO_SYNC_OP=""
DO_REMOTE_UP=""
INCLUDE_DEMO_ACME=""

usage() {
  cat <<EOF
Usage: $0 [full|rolling] [options]

  full             Destructive preset: --build --export --clean-start --remove-remote-tars
                   --deploy-ui --ui-production --ui-build (same as no arguments).
  (no arguments)   Same as: $0 full
  rolling          No-wipe Lightsail stack upgrade: --build --export --remove-remote-tars
                   --sync-operator-files --remote-up (Caddy, Compose, migration + backends + demo
                   if --include-demo-acme, then remote docker compose up -d). Does NOT run clean-start.

  After "full" or "rolling", option flags can override or add, e.g.:
    $0 full --ui-preview
    $0 rolling --include-demo-acme
    $0 --build --export --sync-operator-files --remote-up

  --build            Run docker compose -f docker/docker-compose.yml build (see preset / flags).
  --export           Run export-backend-images-to-lightsail.sh
  --apis-only        Pass --apis-only to export (skip migration image; use when Flyway unchanged)
  --sync-operator-files  Pass to export: scp lightsail docker-compose.yml, Caddyfile, clean-start.sh
  --remote-up        Pass to export: ssh to VM and run docker compose up -d (rolling apply, DB kept)
  --include-demo-acme  Pass to export and add demo-app-acme to the local docker compose build
  --clean-start      Pass to export (DESTRUCTIVE: remote clean-start.sh). Incompatible with rolling preset.
  --remove-remote-tars  Pass to export
  --deploy-ui        Deploy Admin UI to Cloudflare (requires CLOUDFLARE_API_TOKEN, CLOUDFLARE_ACCOUNT_ID)
  --ui-preview|production|...   (unchanged, see below)

  --ui-preview      Use deploy-admin-ui-preview.sh (default when not using preset full)
  --ui-production   Use deploy-admin-ui-production.sh
  --ui-build        Pass --build to the deploy script

Preset "full" enables:
  --build --export --clean-start --remove-remote-tars --deploy-ui --ui-production --ui-build

Examples:
  $0
  $0 full
  $0 rolling
  $0 rolling --include-demo-acme
  $0 --build --export --sync-operator-files --remote-up --remove-remote-tars
  $0 --export --clean-start
  $0 --deploy-ui --ui-build
EOF
}

UI_BUILD=""

PRESET_FULL=""
PRESET_ROLLING=""

if [[ $# -gt 0 && "$1" == "rolling" ]]; then
  PRESET_ROLLING=1
  shift
elif [[ $# -eq 0 ]]; then
  PRESET_FULL=1
elif [[ "${1:-}" == "full" ]]; then
  PRESET_FULL=1
  shift
fi

if [[ -n "$PRESET_FULL" ]]; then
  DO_BUILD="1"
  DO_EXPORT="1"
  DO_CLEAN_START="1"
  REMOVE_REMOTE_TARS="1"
  DO_DEPLOY_UI="1"
  UI_MODE="production"
  UI_BUILD="--build"
fi

if [[ -n "$PRESET_ROLLING" ]]; then
  DO_BUILD="1"
  DO_EXPORT="1"
  REMOVE_REMOTE_TARS="1"
  DO_SYNC_OP="1"
  DO_REMOTE_UP="1"
fi

while [[ $# -gt 0 ]]; do
  case "$1" in
    --build) DO_BUILD="1" ;;
    --export) DO_EXPORT="1" ;;
    --apis-only) APIS_ONLY="1" ;;
    --sync-operator-files) DO_SYNC_OP="1" ;;
    --remote-up) DO_REMOTE_UP="1" ;;
    --include-demo-acme) INCLUDE_DEMO_ACME="1" ;;
    --clean-start) DO_CLEAN_START="1" ;;
    --remove-remote-tars) REMOVE_REMOTE_TARS="1" ;;
    --deploy-ui) DO_DEPLOY_UI="1" ;;
    --ui-preview) UI_MODE="preview" ;;
    --ui-production) UI_MODE="production" ;;
    --ui-build) UI_BUILD="--build" ;;
    -h|--help|help) usage; exit 0 ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
  shift
done

if [[ -n "$PRESET_ROLLING" && -n "$DO_CLEAN_START" ]]; then
  echo "error: preset 'rolling' is for no-wipe upgrades. Do not pass --clean-start." >&2
  echo "  Use subcommand or flags 'full' / --clean-start for a destructive reset." >&2
  exit 1
fi

cd "${REPO_ROOT}"

echo "=========================================="
echo "  Full experimental environment upgrade"
echo "  Repo: ${REPO_ROOT}"
echo "  LIGHTSAIL_SSH_HOST: ${LIGHTSAIL_SSH_HOST}"
if [[ -n "${PRESET_FULL:-}" ]]; then
  echo "  Preset: full (destructive clean-start on export)"
fi
if [[ -n "${PRESET_ROLLING:-}" ]]; then
  echo "  Preset: rolling (no DB wipe: sync Caddy/Compose, load images, remote compose up)"
fi
echo "=========================================="
echo ""

if [[ -n "$DO_BUILD" ]]; then
  BUILD_TARGETS=(migration admin-api auth-api integration-api)
  if [[ -n "$INCLUDE_DEMO_ACME" ]]; then
    BUILD_TARGETS+=(demo-app-acme)
  fi
  echo "→ docker compose build ${BUILD_TARGETS[*]} ..."
  docker compose -f docker/docker-compose.yml build "${BUILD_TARGETS[@]}"
  echo ""
fi

if [[ -n "$DO_EXPORT" ]]; then
  ex_args=()
  [[ -n "$APIS_ONLY" ]] && ex_args+=(--apis-only)
  [[ -n "$DO_CLEAN_START" ]] && ex_args+=(--clean-start)
  [[ -n "${REMOVE_REMOTE_TARS:-}" ]] && ex_args+=(--remove-remote-tars)
  [[ -n "$INCLUDE_DEMO_ACME" ]] && ex_args+=(--include-demo-acme)
  [[ -n "$DO_SYNC_OP" ]] && ex_args+=(--sync-operator-files)
  [[ -n "$DO_REMOTE_UP" ]] && ex_args+=(--remote-up)
  bash "${EXPORT_SCRIPT}" "${ex_args[@]}"
  echo ""
fi

if [[ -n "$DO_DEPLOY_UI" ]]; then
  if [[ "$UI_MODE" == "production" ]]; then
    echo "→ Cloudflare Pages (production)..."
    bash "${PROD_DEPLOY}" ${UI_BUILD:+$UI_BUILD}
  else
    echo "→ Cloudflare Pages (preview)..."
    bash "${PREVIEW_DEPLOY}" ${UI_BUILD:+$UI_BUILD}
  fi
  echo ""
fi

echo "=========================================="
echo "  Finished."
echo "=========================================="
