#!/usr/bin/env bash
# Fail if sites/ezkey-org/api-specs/*-openapi.json diverge from canonical specs/.
# Mirrors the Admin UI openapi-spec.json cmp gate in CI.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$ROOT/sites/ezkey-org/api-specs"
failed=0

check_pair() {
  local canon="$1"
  local copy="$2"
  if [[ ! -f "$canon" ]]; then
    echo "::error::Missing canonical OpenAPI file: $canon" >&2
    failed=1
    return
  fi
  if [[ ! -f "$copy" ]]; then
    echo "::error::Missing portal OpenAPI copy: $copy (run ./scripts/sync-ezkey-org-api-specs.sh)" >&2
    failed=1
    return
  fi
  if ! cmp -s "$canon" "$copy"; then
    echo "::error::OpenAPI portal copy diverges: $copy differs from $canon. Refresh with ./scripts/sync-ezkey-org-api-specs.sh (or ./scripts/update-specs.sh)." >&2
    failed=1
    return
  fi
  echo "OK: $copy matches $(basename "$(dirname "$canon")")/$(basename "$canon")"
}

check_pair "$ROOT/specs/admin-api/openapi-spec.json" "$DEST/admin-api-openapi.json"
check_pair "$ROOT/specs/auth-api/openapi-spec.json" "$DEST/auth-api-openapi.json"
check_pair "$ROOT/specs/integration-api/openapi-spec.json" "$DEST/integration-api-openapi.json"

if [[ "$failed" -ne 0 ]]; then
  exit 1
fi
