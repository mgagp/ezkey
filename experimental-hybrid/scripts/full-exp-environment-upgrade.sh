#!/usr/bin/env bash
#
# One-shot operator flow: build backend images (optional), export/load to Lightsail (optional),
# remote clean-start (optional), deploy Admin UI to Cloudflare (optional).
#
# Experimental / disposable Lightsail: assumes DB wipe on --clean-start is acceptable.
#
# Usage (from repository root, Git Bash):
#   ./experimental-hybrid/scripts/full-exp-environment-upgrade.sh
#   ./experimental-hybrid/scripts/full-exp-environment-upgrade.sh full
#   ./experimental-hybrid/scripts/full-exp-environment-upgrade.sh --help
#
# With no arguments, or with the "full" subcommand first, runs the destructive "everything"
# preset (same idea as --build --export --clean-start --remove-remote-tars --deploy-ui
# --ui-production --ui-build). Additional flags after "full" can narrow or adjust (e.g.
# --ui-preview, --apis-only).
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

usage() {
  cat <<EOF
Usage: $0 [full] [options]

  full             Shorthand for the full destructive refresh preset (see below). Optional if you
                   pass no arguments at all — same as running with zero args.
  (no arguments)   Same as: $0 full

  Preset "full" applies before option flags; flags listed after "full" can override parts, e.g.:
    $0 full --ui-preview
    $0 full --apis-only

  --build          Run: docker compose -f docker/docker-compose.yml build migration admin-api auth-api integration-api
  --export         Run export-backend-images-to-lightsail.sh (save, scp, docker load)
  --apis-only      Pass --apis-only to export (no migration tar; use only if Flyway image unchanged)
  --clean-start    Pass --clean-start to export (remote clean-start.sh; wipes volumes on VM)
  --remove-remote-tars  Pass --remove-remote-tars to export (delete tars on VM after load)
  --deploy-ui      Deploy Admin UI via Cloudflare (requires CLOUDFLARE_API_TOKEN, CLOUDFLARE_ACCOUNT_ID)
  --ui-preview     Use deploy-admin-ui-preview.sh (default when not using preset full)
  --ui-production  Use deploy-admin-ui-production.sh instead of preview
  --ui-build       Pass --build to the deploy script (npm run build:cloudflare)

Preset "full" enables:
  --build --export --clean-start --remove-remote-tars --deploy-ui --ui-production --ui-build

Examples:
  $0
  $0 full
  $0 --build --export --clean-start --deploy-ui --ui-production --ui-build --remove-remote-tars
  $0 full --apis-only
  $0 --export --clean-start
  $0 --deploy-ui --ui-build
EOF
}

UI_BUILD=""

PRESET_FULL=""
if [[ $# -eq 0 ]]; then
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

while [[ $# -gt 0 ]]; do
  case "$1" in
    --build) DO_BUILD="1" ;;
    --export) DO_EXPORT="1" ;;
    --apis-only) APIS_ONLY="1" ;;
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

cd "${REPO_ROOT}"

echo "=========================================="
echo "  Full experimental environment upgrade"
echo "  Repo: ${REPO_ROOT}"
echo "  LIGHTSAIL_SSH_HOST: ${LIGHTSAIL_SSH_HOST}"
if [[ -n "${PRESET_FULL:-}" ]]; then
  echo "  Preset: full"
fi
echo "=========================================="
echo ""

if [[ -n "$DO_BUILD" ]]; then
  echo "→ docker compose build (migration + APIs)..."
  docker compose -f docker/docker-compose.yml build migration admin-api auth-api integration-api
  echo ""
fi

if [[ -n "$DO_EXPORT" ]]; then
  ex_args=()
  [[ -n "$APIS_ONLY" ]] && ex_args+=(--apis-only)
  [[ -n "$DO_CLEAN_START" ]] && ex_args+=(--clean-start)
  [[ -n "${REMOVE_REMOTE_TARS:-}" ]] && ex_args+=(--remove-remote-tars)
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
