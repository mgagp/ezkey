#!/usr/bin/env bash
# Deploy ezkey-admin-ui/dist to Cloudflare Pages as a PRODUCTION deployment (production branch).
# Build first: cd ezkey-admin-ui && npm ci && npm run build:cloudflare
#
# Requires: npx, Wrangler, CLOUDFLARE_API_TOKEN, CLOUDFLARE_ACCOUNT_ID
#
# Usage (from repo root):
#   ./scripts/cloudflare/deploy-admin-ui-production.sh
#
# Sources gitignored `.env` at repo root when present (same as deploy-admin-ui-preview.sh).
# Optional `VITE_API_BASE_URL` in `.env` (see `.env.example`); defaults to https://exp1-admin-api.ezkey.org
#
# Optional:
#   ./scripts/cloudflare/deploy-admin-ui-production.sh --build
#   CLOUDFLARE_PAGES_PROJECT=ezkey-admin-ui ./scripts/cloudflare/deploy-admin-ui-production.sh
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

export VITE_API_BASE_URL="${VITE_API_BASE_URL:-https://exp1-admin-api.ezkey.org}"

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

PROJECT="${CLOUDFLARE_PAGES_PROJECT:-ezkey-admin-ui}"
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

if [[ "$DO_BUILD" == true ]]; then
  echo "Building Admin UI (VITE_API_BASE_URL=$VITE_API_BASE_URL)..."
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
