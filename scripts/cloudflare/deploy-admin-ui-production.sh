#!/usr/bin/env bash
# Deploy ezkey-admin-ui/dist to Cloudflare Pages as a PRODUCTION deployment (production branch).
# Build first: cd ezkey-admin-ui && npm ci && npm run build:cloudflare
#
# Requires: npx, Wrangler, CLOUDFLARE_API_TOKEN, CLOUDFLARE_ACCOUNT_ID,
#           CLOUDFLARE_PAGES_PROJECT (no silent default — #863)
#
# Usage (from repo root) — community production example:
#   CLOUDFLARE_PAGES_PROJECT=admin-ui-ezkey-online \
#   VITE_API_BASE_URL=https://admin-api.ezkey.online \
#     ./scripts/cloudflare/deploy-admin-ui-production.sh --build
#
# Sources gitignored `.env` at repo root when present (same as deploy-admin-ui-preview.sh).
# When using `--build`, `VITE_API_BASE_URL` is **required** (root `.env` or environment).
# No product default hostname and no default Pages project — set both explicitly so a
# retired EXP1 project name cannot be selected by accident (#863).
#
# Public alpha chrome: `VITE_GIT_SHA` is stamped into the Admin UI build (override via env;
# else short git SHA when available). See docs/VERSIONING_AND_DEPLOY_TRACEABILITY.md.
#
# Optional:
#   CLOUDFLARE_PAGES_PRODUCTION_BRANCH=main ./scripts/cloudflare/deploy-admin-ui-production.sh
#
# Must match the project's "production branch" in Cloudflare Pages (often main).

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

if [[ -f "$ROOT/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$ROOT/.env"
  set +a
fi

# Public alpha chrome: short SHA (override via env; else git when available).
if [[ -z "${VITE_GIT_SHA:-}" ]]; then
  if VITE_GIT_SHA="$(git -C "$ROOT" rev-parse --short=7 HEAD 2>/dev/null)"; then
    export VITE_GIT_SHA
  fi
fi

DO_BUILD=false
if [[ "${1:-}" == "--build" ]]; then
  DO_BUILD=true
  shift
fi

BRANCH="${CLOUDFLARE_PAGES_PRODUCTION_BRANCH:-main}"
DIST="$ROOT/ezkey-admin-ui/dist"

if [[ -z "${CLOUDFLARE_API_TOKEN:-}" ]]; then
  echo "error: set CLOUDFLARE_API_TOKEN" >&2
  exit 1
fi
if [[ -z "${CLOUDFLARE_ACCOUNT_ID:-}" ]]; then
  echo "error: set CLOUDFLARE_ACCOUNT_ID" >&2
  exit 1
fi
# Require an explicit Pages project (#863). Safer than defaulting to community or to the
# retired EXP1 project (ezkey-admin-ui): wrong-target production deploys must be intentional.
if [[ -z "${CLOUDFLARE_PAGES_PROJECT:-}" ]]; then
  echo "error: set CLOUDFLARE_PAGES_PROJECT (no silent default after EXP1 retirement; #863)" >&2
  echo "       Community: CLOUDFLARE_PAGES_PROJECT=admin-ui-ezkey-online" >&2
  echo "                  VITE_API_BASE_URL=https://admin-api.ezkey.online" >&2
  exit 1
fi
PROJECT="${CLOUDFLARE_PAGES_PROJECT}"

if [[ "$DO_BUILD" == true ]]; then
  if [[ -z "${VITE_API_BASE_URL:-}" ]]; then
    echo "error: VITE_API_BASE_URL is required when using --build" >&2
    echo "       Set it in the repo-root .env (see .env.example) or export it." >&2
    echo "       Community: https://admin-api.ezkey.online" >&2
    exit 1
  fi
  export VITE_API_BASE_URL
  echo "Building Admin UI (VITE_API_BASE_URL=$VITE_API_BASE_URL${VITE_GIT_SHA:+, VITE_GIT_SHA=$VITE_GIT_SHA})..."
  (cd "$ROOT/ezkey-admin-ui" && npm run build:cloudflare)
fi

if [[ ! -d "$DIST" ]] || [[ ! -f "$DIST/index.html" ]]; then
  echo "error: $DIST missing or empty. Run: cd ezkey-admin-ui && npm ci && npm run build:cloudflare" >&2
  echo "       or: ./scripts/cloudflare/deploy-admin-ui-production.sh --build" >&2
  exit 1
fi

echo "Deploying to Cloudflare Pages production (project=$PROJECT, branch=$BRANCH)..."

cd "$ROOT"
exec npx wrangler pages deploy ezkey-admin-ui/dist \
  --project-name="$PROJECT" \
  --branch="$BRANCH" \
  --commit-message "${DEPLOY_COMMIT_MSG:-admin-ui production ${BRANCH}}"
