#!/usr/bin/env bash
# List or delete Cloudflare Pages PREVIEW deployments for the
# methodology-ezkey-org project. Mirror of cleanup-ezkey-org-previews.sh.
#
# Prefer cleanup-pages-deployments.sh for age-based cleanup (24h / 7d profiles).
# This script deletes ALL listed previews when --apply is set (no age filter).
#
# Dry-run by default. Set CLEANUP_APPLY=1 (or pass --apply) to actually delete.
#
# Requires: npx, Node.js (for JSON parsing), Wrangler with `wrangler pages
# deployment list` and `wrangler pages deployment delete`, and
# CLOUDFLARE_API_TOKEN + CLOUDFLARE_ACCOUNT_ID.
#
# Usage (from repo root):
#   ./scripts/cloudflare/cleanup-methodology-previews.sh              # dry-run
#   CLEANUP_APPLY=1 ./scripts/cloudflare/cleanup-methodology-previews.sh
#   ./scripts/cloudflare/cleanup-methodology-previews.sh --apply
#
# Optional:
#   CLOUDFLARE_PAGES_PROJECT=methodology-ezkey-org ./scripts/cloudflare/cleanup-methodology-previews.sh
#
# Cloudflare may refuse to delete the latest deployment for a given preview
# branch; those failures are reported and the script continues.

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

if [[ -f "$ROOT/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$ROOT/.env"
  set +a
fi

PROJECT="${CLOUDFLARE_PAGES_PROJECT:-methodology-ezkey-org}"
CLEANUP_APPLY="${CLEANUP_APPLY:-0}"

for _arg in "$@"; do
  if [[ "$_arg" == "--apply" ]]; then
    CLEANUP_APPLY=1
  fi
done

if [[ -z "${CLOUDFLARE_API_TOKEN:-}" ]]; then
  echo "error: set CLOUDFLARE_API_TOKEN" >&2
  exit 1
fi
if [[ -z "${CLOUDFLARE_ACCOUNT_ID:-}" ]]; then
  echo "error: set CLOUDFLARE_ACCOUNT_ID" >&2
  exit 1
fi

if [[ "$CLEANUP_APPLY" != "1" ]]; then
  echo "Dry run: listing preview deployment IDs for project ${PROJECT} (no deletions)."
  echo "To delete all listed previews, run: CLEANUP_APPLY=1 $0  or  $0 --apply" >&2
  echo
fi

cd "$ROOT"

mapfile -t DEPLOYMENT_IDS < <(
  npx wrangler pages deployment list \
    --project-name="$PROJECT" \
    --environment=preview \
    --json |
    node -e "
      const j = JSON.parse(require('fs').readFileSync(0, 'utf8'));
      if (!Array.isArray(j)) {
        throw new Error('expected JSON array from wrangler');
      }
      for (const d of j) {
        const id = d.Id ?? d.id;
        if (id) {
          process.stdout.write(String(id) + '\n');
        }
      }
    "
)

if [[ ${#DEPLOYMENT_IDS[@]} -eq 0 ]]; then
  echo "No preview deployments returned for project $PROJECT (nothing to do)."
  exit 0
fi

echo "Found ${#DEPLOYMENT_IDS[@]} preview deployment(s) for project $PROJECT."
for _id in "${DEPLOYMENT_IDS[@]}"; do
  echo "  $_id"
done

if [[ "$CLEANUP_APPLY" != "1" ]]; then
  echo
  echo "Dry run complete."
  exit 0
fi

echo
echo "Deleting ${#DEPLOYMENT_IDS[@]} preview deployment(s) ..."

set +e
failed=0
for _id in "${DEPLOYMENT_IDS[@]}"; do
  echo "  deleting $_id"
  npx wrangler pages deployment delete "$_id" --project-name="$PROJECT" --force
  _ec=$?
  if [[ $_ec -ne 0 ]]; then
    echo "  warning: wrangler exit $_ec (often: latest for a branch cannot be deleted)" >&2
    failed=$((failed + 1))
  fi
done
set -e

if [[ "$failed" -gt 0 ]]; then
  echo "Finished with $failed failed deletion(s) (see messages above)."
  exit 1
fi

echo "All preview deletions completed successfully."
exit 0
