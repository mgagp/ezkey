#!/usr/bin/env bash
# Gate: direct dependency license allowlist + committed in-app license snapshot freshness.
#
# Usage (from ezkey_mobile/):
#   ./scripts/check-third-party-licenses-ci.sh
#   yarn license:ci
#
# Runs:
#   1. yarn license:check  (allowlist of direct runtime dependency licenses)
#   2. node scripts/check-third-party-licenses-freshness.mjs
#      (regenerate snapshot; fail if committed JSON differs ignoring generatedAt)
#
# Works on Linux CI and Windows Git Bash. Requires yarn install already done.
#
# When freshness fails (including Dependabot PRs):
#   yarn license:app-data
#   commit app/data/thirdPartyLicenses.json on the same branch.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${ROOT}"

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  cat <<EOF
Usage: $(basename "$0")

Runs yarn license:check then the in-app thirdPartyLicenses.json freshness gate.
Exit 0 when allowlist and snapshot (ignoring generatedAt) are OK.
EOF
  exit 0
fi

echo "==> License allowlist (yarn license:check)"
yarn license:check

echo ""
echo "==> License snapshot freshness (ignore generatedAt)"
node scripts/check-third-party-licenses-freshness.mjs

echo ""
echo "License CI checks passed."
