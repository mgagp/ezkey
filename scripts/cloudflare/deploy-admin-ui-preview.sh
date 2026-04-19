#!/usr/bin/env bash
# Deploy ezkey-admin-ui/dist to Cloudflare Pages as a PREVIEW deployment.
# Build first: cd ezkey-admin-ui && npm ci && npm run build:cloudflare
#
# Requires: npx, Wrangler, CLOUDFLARE_API_TOKEN, CLOUDFLARE_ACCOUNT_ID
#
# Usage (from repo root):
#   ./scripts/cloudflare/deploy-admin-ui-preview.sh
#
# Or export the variables in the shell. Same as deploy-ezkey-org-preview.sh: if a gitignored
# `.env` exists at the repo root, it is sourced automatically (CLOUDFLARE_API_TOKEN,
# CLOUDFLARE_ACCOUNT_ID — Bash: export VAR=value or VAR=value per line).
#
# Optional `VITE_API_BASE_URL` in the same `.env` (see `.env.example` at repo root): used when you
# pass `--build` so the Admin UI bundle targets your public Admin API. If unset after sourcing
# `.env`, defaults to https://exp1-admin-api.ezkey.org
#
# Optional:
#   ./scripts/cloudflare/deploy-admin-ui-preview.sh --build   # npm run build:cloudflare then deploy
#   CLOUDFLARE_PAGES_PROJECT=ezkey-admin-ui ./scripts/cloudflare/deploy-admin-ui-preview.sh
#   PREVIEW_BRANCH=my-branch ./scripts/cloudflare/deploy-admin-ui-preview.sh

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

if [[ -f "$ROOT/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$ROOT/.env"
  set +a
fi

# Default public Admin API for Vite (overridable via root `.env` or environment).
export VITE_API_BASE_URL="${VITE_API_BASE_URL:-https://exp1-admin-api.ezkey.org}"

DO_BUILD=false
if [[ "${1:-}" == "--build" ]]; then
  DO_BUILD=true
  shift
fi

PROJECT="${CLOUDFLARE_PAGES_PROJECT:-ezkey-admin-ui}"
BRANCH="${PREVIEW_BRANCH:-preview-$(date -u +%Y%m%d-%H%M%S)}"
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
  echo "       or: ./scripts/cloudflare/deploy-admin-ui-preview.sh --build" >&2
  exit 1
fi

cd "$ROOT"
exec npx wrangler pages deploy ezkey-admin-ui/dist \
  --project-name="$PROJECT" \
  --branch="$BRANCH" \
  --commit-message "${DEPLOY_COMMIT_MSG:-admin-ui preview ${BRANCH}}"
