#!/usr/bin/env bash
# Deploy sites/ezkey-org to Cloudflare Pages as a PREVIEW deployment (not production branch).
# Requires: npx (Node), Wrangler (installed via npx), and a Cloudflare API token with Pages write.
#
# Usage (from repo root):
#   export CLOUDFLARE_API_TOKEN="..."   # Account API token with Pages:Edit (or Workers + Pages as per your template)
#   export CLOUDFLARE_ACCOUNT_ID="..."  # Dashboard → account overview
#   ./scripts/cloudflare/deploy-ezkey-org-preview.sh
#
# Or put the same variables in a gitignored `.env` at the repo root (Bash: export VAR=value or VAR=value per line).
# The script sources `$ROOT/.env` automatically when the file exists.
#
# Optional:
#   PREVIEW_BRANCH=my-feature ./scripts/cloudflare/deploy-ezkey-org-preview.sh
#   CLOUDFLARE_PAGES_PROJECT=ezkey-org ./scripts/cloudflare/deploy-ezkey-org-preview.sh
#
# Preview vs production: Wrangler uses --branch. A branch name other than the project's
# production branch (e.g. main) creates a preview deployment with its own *.pages.dev URL.

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

if [[ -f "$ROOT/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$ROOT/.env"
  set +a
fi

PROJECT="${CLOUDFLARE_PAGES_PROJECT:-ezkey-org}"
BRANCH="${PREVIEW_BRANCH:-preview-$(date -u +%Y%m%d-%H%M%S)}"

if [[ -z "${CLOUDFLARE_API_TOKEN:-}" ]]; then
  echo "error: set CLOUDFLARE_API_TOKEN (see scripts/cloudflare/deploy-ezkey-org-preview.sh header)" >&2
  exit 1
fi
if [[ -z "${CLOUDFLARE_ACCOUNT_ID:-}" ]]; then
  echo "error: set CLOUDFLARE_ACCOUNT_ID" >&2
  exit 1
fi

cd "$ROOT"
exec npx wrangler pages deploy sites/ezkey-org \
  --project-name="$PROJECT" \
  --branch="$BRANCH" \
  --commit-message "${DEPLOY_COMMIT_MSG:-preview deploy ${BRANCH}}"
