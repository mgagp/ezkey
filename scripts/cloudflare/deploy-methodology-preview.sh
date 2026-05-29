#!/usr/bin/env bash
# Build product-docs/site to a static dist/ and deploy to Cloudflare Pages
# as a PREVIEW deployment (not the production branch). Mirror of the existing
# deploy-ezkey-org-preview.sh pattern for the marketing apex site.
#
# Requires: npx (Node), Wrangler (installed via npx), and a Cloudflare API
# token with Pages:Edit on the methodology-ezkey-org project.
#
# Usage (from repo root):
#   ./scripts/cloudflare/deploy-methodology-preview.sh
#
# Sources gitignored `.env` at repo root when present (same as the apex script).
#
# Optional overrides:
#   PREVIEW_BRANCH=my-feature ./scripts/cloudflare/deploy-methodology-preview.sh
#   CLOUDFLARE_PAGES_PROJECT=methodology-ezkey-org ./scripts/cloudflare/deploy-methodology-preview.sh
#   SITE_ORIGIN=https://methodology.ezkey.org   # used to render canonical/OG/sitemap
#   CF_ANALYTICS_TOKEN=...                       # enables Cloudflare Web Analytics
#
# Preview vs production: Wrangler uses --branch. A branch name other than the
# project's production branch (e.g. main) creates a preview deployment with
# its own *.pages.dev URL.

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

if [[ -f "$ROOT/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$ROOT/.env"
  set +a
fi

PROJECT="${CLOUDFLARE_PAGES_PROJECT:-methodology-ezkey-org}"
BRANCH="${PREVIEW_BRANCH:-preview-$(date -u +%Y%m%d-%H%M%S)}"
SITE_DIR="$ROOT/product-docs/site"
DIST_DIR="$SITE_DIR/dist"

if [[ -z "${CLOUDFLARE_API_TOKEN:-}" ]]; then
  echo "error: set CLOUDFLARE_API_TOKEN (see header of this script)" >&2
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

echo "Deploying $DIST_DIR to Cloudflare Pages (project=$PROJECT, branch=$BRANCH)..."

cd "$ROOT"
exec npx wrangler pages deploy "$DIST_DIR" \
  --project-name="$PROJECT" \
  --branch="$BRANCH" \
  --commit-message "${DEPLOY_COMMIT_MSG:-methodology preview ${BRANCH}}"
