#!/usr/bin/env bash
# Deploy sites/ezkey-org to Cloudflare Pages as a PRODUCTION deployment (production branch).
# No build step — static HTML/CSS as in git.
#
# Requires: npx, Wrangler, CLOUDFLARE_API_TOKEN, CLOUDFLARE_ACCOUNT_ID
#
# Usage (from repo root):
#   ./scripts/cloudflare/deploy-ezkey-org-production.sh
#
# Sources gitignored `.env` at repo root when present (same as deploy-ezkey-org-preview.sh).
#
# Optional:
#   CLOUDFLARE_PAGES_PROJECT=ezkey-org ./scripts/cloudflare/deploy-ezkey-org-production.sh
#   CLOUDFLARE_PAGES_PRODUCTION_BRANCH=main ./scripts/cloudflare/deploy-ezkey-org-production.sh
#
# Must match the project's "production branch" in Cloudflare Pages (often main). Traffic for
# the custom domain (e.g. ezkey.org) follows this branch's latest production deployment.

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

if [[ -f "$ROOT/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$ROOT/.env"
  set +a
fi

PROJECT="${CLOUDFLARE_PAGES_PROJECT:-ezkey-org}"
BRANCH="${CLOUDFLARE_PAGES_PRODUCTION_BRANCH:-main}"
SITE_DIR="$ROOT/sites/ezkey-org"

if [[ -z "${CLOUDFLARE_API_TOKEN:-}" ]]; then
  echo "error: set CLOUDFLARE_API_TOKEN" >&2
  exit 1
fi
if [[ -z "${CLOUDFLARE_ACCOUNT_ID:-}" ]]; then
  echo "error: set CLOUDFLARE_ACCOUNT_ID" >&2
  exit 1
fi

if [[ ! -d "$SITE_DIR" ]] || [[ ! -f "$SITE_DIR/index.html" ]]; then
  echo "error: $SITE_DIR missing or index.html not found" >&2
  exit 1
fi

echo "Deploying ezkey.org static site to Cloudflare Pages production (project=$PROJECT, branch=$BRANCH)..."

cd "$ROOT"
exec npx wrangler pages deploy sites/ezkey-org \
  --project-name="$PROJECT" \
  --branch="$BRANCH" \
  --commit-message "${DEPLOY_COMMIT_MSG:-ezkey-org production ${BRANCH}}"
