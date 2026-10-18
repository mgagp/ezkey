#!/usr/bin/env bash
#
# One-shot operator flow: build backend images (optional), export/load to Lightsail (optional),
# optional no-wipe sync of Caddy/Compose to the VM and remote docker compose up (optional),
# remote clean-start (optional, destructive), deploy Admin UI to Cloudflare (optional).
#
# SSH host: no silent default (#812). Pass --target community or set LIGHTSAIL_SSH_HOST
# before any --export / rolling / full path that talks to a VM.
#
# Usage (from repository root, Git Bash):
#   ./experimental-hybrid/scripts/full-exp-environment-upgrade.sh --target community rolling
#   ./experimental-hybrid/scripts/full-exp-environment-upgrade.sh --target community rolling --dry-run
#   ./experimental-hybrid/scripts/full-exp-environment-upgrade.sh --target community full
#   ./experimental-hybrid/scripts/full-exp-environment-upgrade.sh --help
#
# Subcommand is required: full | rolling (or pass explicit --build/--export/… flags).
# No subcommand prints help — it does NOT default to destructive full (#812 review).
#
# Subcommand "full" (destructive): build + export + clean-start + remove remote tars + deploy UI
# production + build. For --target community, requires EZKEY_CONFIRM_WIPE=ezkey-online.
#
# Subcommand "rolling" (no database wipe):
#   --build --export --include-demo-acme is available via --include-demo-acme
#   default: build migration + all APIs, export all images including migration, --remove-remote-tars,
#   --sync-operator-files, --remote-up on the resolved SSH host (Caddy + Compose from repo, then
#   docker compose up -d; Postgres and named volumes are preserved; Flyway runs from new image).
#
# Environment:
#   LIGHTSAIL_SSH_HOST   explicit OpenSSH Host alias (alternative to --target)
#   LIGHTSAIL_TARGET     community (optional if inferred from host or --target)
#   EZKEY_CONFIRM_WIPE   must be ezkey-online for community full / --clean-start
#   EXPORT_DIR           passed through to export script (default: docker/export)
#   OCI_IMAGE_REVISION   git SHA stamped as org.opencontainers.image.revision on compose builds
#                        (#814; defaults to `git rev-parse HEAD` when unset)
#   Root .env            CLOUDFLARE_* and VITE_API_BASE_URL for UI deploy (see scripts/cloudflare/*.sh)
#   PREVIEW_BRANCH       for preview UI deploy (default: timestamped); set e.g. community-admin-ui-preview
#   CLOUDFLARE_PAGES_PROJECT  default ezkey-admin-ui
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
# shellcheck source=../../scripts/lightsail/common.sh
source "${REPO_ROOT}/scripts/lightsail/common.sh"

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
DRY_RUN=""
DECLARED_TARGET=""

usage() {
  cat <<EOF
Usage: $0 [--target community] [full|rolling] [options]

SSH target (required when exporting — no silent default; #812):
  --target community   SSH Host ${LIGHTSAIL_SSH_HOST_COMMUNITY}; community Caddyfile on sync
  or set LIGHTSAIL_SSH_HOST explicitly

  full             Destructive preset: --build --export --clean-start --remove-remote-tars
                   --deploy-ui --ui-production --ui-build. Community requires
                   EZKEY_CONFIRM_WIPE=${LIGHTSAIL_SSH_HOST_COMMUNITY}.
  rolling          No-wipe Lightsail stack upgrade: --build --export --remove-remote-tars
                   --sync-operator-files --remote-up (Caddy, Compose, migration + backends + demo
                   if --include-demo-acme, then remote docker compose up -d + caddy force-recreate).
  (no subcommand)  Prints this help and exits (does not imply full).

  After "full" or "rolling", option flags can override or add, e.g.:
    $0 --target community full --ui-preview
    $0 --target community rolling --include-demo-acme
    $0 --target community --export --sync-operator-files --remote-up --dry-run

  --build            Run docker compose -f docker/docker-compose.yml build (see preset / flags).
  --export           Run export-backend-images-to-lightsail.sh
  --apis-only        Pass --apis-only to export (skip migration image; use when Flyway unchanged)
  --sync-operator-files  Pass to export: scp lightsail docker-compose.yml, target-aware Caddyfile, clean-start.sh
  --remote-up        Pass to export: ssh to VM and run docker compose up -d (rolling apply, DB kept)
  --include-demo-acme  Pass to export and add demo-app-acme to the local docker compose build
  --clean-start      Pass to export (DESTRUCTIVE: remote clean-start.sh). Community requires
                     EZKEY_CONFIRM_WIPE=${LIGHTSAIL_SSH_HOST_COMMUNITY}. Incompatible with rolling.
  --remove-remote-tars  Pass to export
  --dry-run          Pass --dry-run to export; skip local docker build and UI deploy
  --deploy-ui        Deploy Admin UI to Cloudflare (requires CLOUDFLARE_API_TOKEN, CLOUDFLARE_ACCOUNT_ID)
  --ui-preview|production|...   (unchanged, see below)

  --ui-preview      Use deploy-admin-ui-preview.sh (default when not using preset full)
  --ui-production   Use deploy-admin-ui-production.sh
  --ui-build        Pass --build to the deploy script

Preset "full" enables:
  --build --export --clean-start --remove-remote-tars --deploy-ui --ui-production --ui-build

Examples:
  $0 --target community full
  $0 --target community rolling
  $0 --target community rolling --dry-run
  $0 --target community rolling --include-demo-acme
  $0 --target community --build --export --sync-operator-files --remote-up --remove-remote-tars
  EZKEY_CONFIRM_WIPE=${LIGHTSAIL_SSH_HOST_COMMUNITY} $0 --target community full
  $0 --target community --export --clean-start
  $0 --deploy-ui --ui-build
EOF
}

UI_BUILD=""

PRESET_FULL=""
PRESET_ROLLING=""

# Parse --target early; allow it before or after the preset subcommand.
ARGS=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    --target)
      [[ $# -ge 2 ]] || lightsail_die "--target requires community"
      DECLARED_TARGET="$2"
      shift 2
      ;;
    *)
      ARGS+=("$1")
      shift
      ;;
  esac
done
set -- "${ARGS[@]+"${ARGS[@]}"}"

if [[ $# -gt 0 && "$1" == "rolling" ]]; then
  PRESET_ROLLING=1
  shift
elif [[ $# -gt 0 && "$1" == "full" ]]; then
  PRESET_FULL=1
  shift
elif [[ $# -eq 0 ]]; then
  # No subcommand: print help. Never imply destructive full (#812 review).
  usage
  exit 0
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
    --dry-run) DRY_RUN="1" ;;
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

# Resolve SSH target whenever we will talk to a VM (export path).
if [[ -n "$DO_EXPORT" ]]; then
  lightsail_resolve_ssh_target "${DECLARED_TARGET}"
  export LIGHTSAIL_SSH_HOST
  export LIGHTSAIL_TARGET
fi

# Community wipe guard: full preset or --clean-start requires EZKEY_CONFIRM_WIPE=ezkey-online.
if [[ -n "$DO_CLEAN_START" ]]; then
  if [[ -z "$DO_EXPORT" ]]; then
    lightsail_resolve_ssh_target "${DECLARED_TARGET}"
  fi
  lightsail_require_community_wipe_confirm
fi

cd "${REPO_ROOT}"

echo "=========================================="
echo "  Full experimental environment upgrade"
echo "  Repo: ${REPO_ROOT}"
if [[ -n "$DO_EXPORT" ]]; then
  echo "  LIGHTSAIL_SSH_HOST: ${LIGHTSAIL_SSH_HOST}"
  echo "  LIGHTSAIL_TARGET: ${LIGHTSAIL_TARGET:-custom}"
fi
if [[ -n "${PRESET_FULL:-}" ]]; then
  echo "  Preset: full (destructive clean-start on export)"
fi
if [[ -n "${PRESET_ROLLING:-}" ]]; then
  echo "  Preset: rolling (no DB wipe: sync Caddy/Compose, load images, remote compose up)"
fi
if [[ -n "$DRY_RUN" ]]; then
  echo "  Mode: dry-run"
fi
echo "=========================================="
echo ""

if [[ -n "$DO_BUILD" ]]; then
  BUILD_TARGETS=(migration admin-api auth-api integration-api)
  if [[ -n "$INCLUDE_DEMO_ACME" ]]; then
    BUILD_TARGETS+=(demo-app-acme)
  fi
  if [[ -z "${OCI_IMAGE_REVISION:-}" ]]; then
    OCI_IMAGE_REVISION="$(git -C "${REPO_ROOT}" rev-parse HEAD 2>/dev/null || echo unknown)"
  fi
  export OCI_IMAGE_REVISION
  echo "→ OCI_IMAGE_REVISION=${OCI_IMAGE_REVISION} (org.opencontainers.image.revision)"
  # SHA is always HEAD; warn when the worktree differs so operators know the label
  # does not describe dirty/uncommitted contents (#814 Christophe nit).
  if [[ "${OCI_IMAGE_REVISION}" != "unknown" ]] &&
    [[ -n "$(git -C "${REPO_ROOT}" status --porcelain 2>/dev/null || true)" ]]; then
    echo "warning: working tree is dirty; OCI_IMAGE_REVISION is HEAD (${OCI_IMAGE_REVISION}), not the dirty worktree contents" >&2
  fi
  if [[ -n "$DRY_RUN" ]]; then
    printf '[dry-run]'
    printf ' %q' env "OCI_IMAGE_REVISION=${OCI_IMAGE_REVISION}" \
      docker compose -f docker/docker-compose.yml build "${BUILD_TARGETS[@]}"
    echo
  else
    echo "→ docker compose build ${BUILD_TARGETS[*]} ..."
    docker compose -f docker/docker-compose.yml build "${BUILD_TARGETS[@]}"
  fi
  echo ""
fi

if [[ -n "$DO_EXPORT" ]]; then
  ex_args=()
  [[ -n "${DECLARED_TARGET}" ]] && ex_args+=(--target "${DECLARED_TARGET}")
  [[ -n "$APIS_ONLY" ]] && ex_args+=(--apis-only)
  [[ -n "$DO_CLEAN_START" ]] && ex_args+=(--clean-start)
  [[ -n "${REMOVE_REMOTE_TARS:-}" ]] && ex_args+=(--remove-remote-tars)
  [[ -n "$INCLUDE_DEMO_ACME" ]] && ex_args+=(--include-demo-acme)
  [[ -n "$DO_SYNC_OP" ]] && ex_args+=(--sync-operator-files)
  [[ -n "$DO_REMOTE_UP" ]] && ex_args+=(--remote-up)
  [[ -n "$DRY_RUN" ]] && ex_args+=(--dry-run)
  bash "${EXPORT_SCRIPT}" "${ex_args[@]}"
  echo ""
fi

if [[ -n "$DO_DEPLOY_UI" ]]; then
  if [[ -n "$DRY_RUN" ]]; then
    if [[ "$UI_MODE" == "production" ]]; then
      printf '[dry-run]'
      printf ' %q' bash "${PROD_DEPLOY}" ${UI_BUILD:+$UI_BUILD}
      echo
    else
      printf '[dry-run]'
      printf ' %q' bash "${PREVIEW_DEPLOY}" ${UI_BUILD:+$UI_BUILD}
      echo
    fi
  elif [[ "$UI_MODE" == "production" ]]; then
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
