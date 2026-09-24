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
# When using `--build`, `VITE_API_BASE_URL` is **required** (root `.env` or environment).
# There is no product default hostname — set the Admin API origin for your surface explicitly
# (EXP1, community, or other). See `.env.example` and `ezkey-admin-ui/.env.cloudflare`.
#
# Public alpha chrome: `VITE_GIT_SHA` is stamped into the Admin UI build (override via env;
# else short git SHA when available). See docs/VERSIONING_AND_DEPLOY_TRACEABILITY.md.
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
  if [[ -z "${VITE_API_BASE_URL:-}" ]]; then
    echo "error: VITE_API_BASE_URL is required when using --build" >&2
    echo "       Set it in the repo-root .env (see .env.example) or export it." >&2
    echo "       Examples: https://exp1-admin-api.ezkey.org (EXP1)" >&2
    echo "                 https://admin-api.ezkey.online (community)" >&2
    exit 1
  fi
  export VITE_API_BASE_URL
  echo "Building Admin UI (VITE_API_BASE_URL=$VITE_API_BASE_URL${VITE_GIT_SHA:+, VITE_GIT_SHA=$VITE_GIT_SHA})..."
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
