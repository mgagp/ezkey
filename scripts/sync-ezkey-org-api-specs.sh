#!/usr/bin/env bash
# Copy canonical OpenAPI specs into sites/ezkey-org/api-specs/ for Cloudflare Pages.
# Those JSON files are gitignored (no git symlinks; Windows core.symlinks=false — #833).
# Called by scripts/update-specs.sh and by ezkey-org deploy scripts.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$ROOT/sites/ezkey-org/api-specs"

mkdir -p "$DEST"
cp "$ROOT/specs/admin-api/openapi-spec.json" "$DEST/admin-api-openapi.json"
cp "$ROOT/specs/auth-api/openapi-spec.json" "$DEST/auth-api-openapi.json"
cp "$ROOT/specs/integration-api/openapi-spec.json" "$DEST/integration-api-openapi.json"
echo "Synced portal OpenAPI copies into sites/ezkey-org/api-specs/"
