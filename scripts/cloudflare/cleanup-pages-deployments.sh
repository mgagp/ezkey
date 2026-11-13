#!/usr/bin/env bash
# Age-aware cleanup for Cloudflare Pages preview (or production) deployments.
#
# Default: dry-run, preview environment, ezkey-org + methodology-ezkey-org,
# delete candidates older than 24 hours (aggressive profile).
#
# Requires: Node.js 18+, CLOUDFLARE_API_TOKEN, CLOUDFLARE_ACCOUNT_ID
# (sourced from repo root .env when present — same as deploy scripts).
#
# Usage (from repo root, Git Bash):
#   ./scripts/cloudflare/cleanup-pages-deployments.sh
#   ./scripts/cloudflare/cleanup-pages-deployments.sh --profile prudent --apply
#   ./scripts/cloudflare/cleanup-pages-deployments.sh --env production --profile prudent --apply
#
# See also: cleanup-pages-deployments.mjs --help

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

if [[ -f "$ROOT/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$ROOT/.env"
  set +a
fi

exec node "$ROOT/scripts/cloudflare/cleanup-pages-deployments.mjs" "$@"
