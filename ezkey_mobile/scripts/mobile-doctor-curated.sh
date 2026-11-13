#!/usr/bin/env bash
# mobile-doctor-curated — Git Bash entrypoint for ezkey_mobile hygiene pass.
# Keyword: mobile-doctor-curated
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if ! command -v node >/dev/null 2>&1; then
  echo "mobile-doctor-curated: node is required on PATH" >&2
  exit 2
fi

exec node scripts/mobile-doctor-curated.mjs "$@"
