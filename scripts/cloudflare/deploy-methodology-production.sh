#!/usr/bin/env bash
# Build product-docs/site and deploy to Cloudflare Pages as a PRODUCTION
# deployment (production branch). Mirror of deploy-ezkey-org-production.sh.
#
# Requires: npx, Wrangler, CLOUDFLARE_API_TOKEN, CLOUDFLARE_ACCOUNT_ID.
#
# Usage (from repo root):
#   ./scripts/cloudflare/deploy-methodology-production.sh
#
# Sources gitignored `.env` at repo root when present.
#
# Optional:
#   CLOUDFLARE_PAGES_PROJECT=methodology-ezkey-org ./scripts/cloudflare/deploy-methodology-production.sh
#   CLOUDFLARE_PAGES_PRODUCTION_BRANCH=main ./scripts/cloudflare/deploy-methodology-production.sh
#   SITE_ORIGIN=https://methodology.ezkey.org
#   CF_ANALYTICS_TOKEN=...
#
# Must match the project's "production branch" in Cloudflare Pages (often
# main). Traffic for the custom domain (methodology.ezkey.org) follows this
# branch's latest production deployment.

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

if [[ -f "$ROOT/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$ROOT/.env"
  set +a
fi

PROJECT="${CLOUDFLARE_PAGES_PROJECT:-methodology-ezkey-org}"
BRANCH="${CLOUDFLARE_PAGES_PRODUCTION_BRANCH:-main}"
SITE_DIR="$ROOT/product-docs/site"
DIST_DIR="$SITE_DIR/dist"

if [[ -z "${CLOUDFLARE_API_TOKEN:-}" ]]; then
  echo "error: set CLOUDFLARE_API_TOKEN" >&2
  exit 1
fi
if [[ -z "${CLOUDFLARE_ACCOUNT_ID:-}" ]]; then
  echo "error: set CLOUDFLARE_ACCOUNT_ID" >&2
  exit 1
fi

if [[ ! -d "$SITE_DIR" ]] || [[ ! -f "$SITE_DIR/build.js" ]]; then
  echo "error: $SITE_DIR missing or build.js not found" >&2
  exit 1
fi

echo "Building static site at $SITE_DIR ..."
cd "$SITE_DIR"
if [[ ! -d "node_modules" ]]; then
  npm install --no-audit --no-fund
fi
npm run build

if [[ ! -d "$DIST_DIR" ]]; then
  echo "error: build did not produce $DIST_DIR" >&2
  exit 1
fi

echo "Deploying $DIST_DIR to Cloudflare Pages production (project=$PROJECT, branch=$BRANCH)..."

cd "$ROOT"
exec npx wrangler pages deploy "$DIST_DIR" \
  --project-name="$PROJECT" \
  --branch="$BRANCH" \
  --commit-message "${DEPLOY_COMMIT_MSG:-methodology production ${BRANCH}}"
