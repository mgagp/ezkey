#!/usr/bin/env bash
# Package an EXP1-localized Integration API OpenAPI artifact for Cloudflare schema upload.
#
# Reads the host-neutral canonical spec and writes a deployment-localized copy with
# exactly one server: https://exp1-integration-api.ezkey.org
#
# Cloudflare API Shield accepts OpenAPI 3.0 only. The packaging step therefore
# downlevels OAS 3.1 nullable type arrays (["string","null"]) to type + nullable.
#
# Usage (from repo root, Git Bash):
#   ./scripts/package-integration-api-cloudflare-schema.sh
#   ./scripts/package-integration-api-cloudflare-schema.sh --output path/to/file.json
#   ./scripts/package-integration-api-cloudflare-schema.sh --self-test
#
# This script does not upload to Cloudflare and does not modify the canonical spec.
# Upload: ./scripts/cloudflare/upload-integration-api-schema-exp1.sh --upload
# Operator runbook: docs/cloudflare/integration-api-schema-validation.md

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CANONICAL="${EZKEY_INTEGRATION_CANONICAL_SPEC:-$ROOT/specs/integration-api/openapi-spec.json}"
DEFAULT_OUTPUT="$ROOT/specs/integration-api/deployments/exp1-cloudflare-openapi.json"
OUTPUT="$DEFAULT_OUTPUT"
EXP1_SERVER_URL="https://exp1-integration-api.ezkey.org"
SELF_TEST=false

EXPECTED_PATHS=(
  "/api/v1/auth-attempts"
  "/api/v1/auth-attempts/{id}/wait"
  "/api/v1/auth-attempts/{id}/cancel"
)

usage() {
  cat <<'EOF'
Package an EXP1 Cloudflare Integration API schema from the canonical host-neutral spec.

Usage:
  ./scripts/package-integration-api-cloudflare-schema.sh
  ./scripts/package-integration-api-cloudflare-schema.sh --output path/to/file.json
  ./scripts/package-integration-api-cloudflare-schema.sh --self-test

Options:
  --output PATH   Write the localized artifact here (default:
                  specs/integration-api/deployments/exp1-cloudflare-openapi.json)
  --self-test     Run fixture-based checks without touching the live spec
  --help          Show this help
EOF
}

require_jq() {
  if ! command -v jq >/dev/null 2>&1; then
    echo "error: jq is required" >&2
    exit 1
  fi
}

assert_single_exp1_server() {
  local spec_file=$1
  local count
  local url
  count="$(jq '.servers | length' "$spec_file")"
  url="$(jq -r '.servers[0].url' "$spec_file")"
  if [ "$count" != "1" ]; then
    echo "error: expected exactly one server, found $count" >&2
    return 1
  fi
  if [ "$url" != "$EXP1_SERVER_URL" ]; then
    echo "error: expected server $EXP1_SERVER_URL, found $url" >&2
    return 1
  fi
}

assert_expected_paths() {
  local spec_file=$1
  local missing=0
  local path
  local keys
  # Do not pass /api/... through jq --arg: Git Bash/MSYS rewrites those as Windows paths.
  keys="$(jq -r '.paths | keys[]' "$spec_file")"
  for path in "${EXPECTED_PATHS[@]}"; do
    if ! printf '%s\n' "$keys" | grep -Fxq "$path"; then
      echo "error: missing path $path" >&2
      missing=1
    fi
  done
  return "$missing"
}

localize_spec() {
  local source_spec=$1
  local dest_spec=$2
  mkdir -p "$(dirname "$dest_spec")"
  # Cloudflare Schema Validation is OAS 3.0 only (error 50010 on type arrays).
  jq --arg url "$EXP1_SERVER_URL" '
    def downlevel_nullable_type:
      if type != "object" then .
      elif (.type | type) != "array" then .
      else
        (.type) as $types
        | ($types | map(select(. != "null"))) as $nonnull
        | if ($nonnull | length) != 1 then
            error("unsupported OAS 3.1 type array for Cloudflare OAS 3.0: \($types)")
          else
            .type = $nonnull[0]
            | if ($types | index("null")) != null then .nullable = true else . end
          end
      end;
    walk(downlevel_nullable_type)
    | .openapi = "3.0.3"
    | .servers = [{url: $url, description: "EXP1 Integration API"}]
  ' "$source_spec" | tr -d '\r' >"$dest_spec.tmp"
  mv "$dest_spec.tmp" "$dest_spec"
}

assert_cloudflare_oas30() {
  local spec_file=$1
  local version
  local array_types
  version="$(jq -r '.openapi' "$spec_file")"
  case "$version" in
    3.0.*) ;;
    *)
      echo "error: Cloudflare artifact must be OpenAPI 3.0.x, found $version" >&2
      return 1
      ;;
  esac
  array_types="$(jq '[.. | objects | .type? | select(type == "array")] | length' "$spec_file")"
  if [ "$array_types" != "0" ]; then
    echo "error: Cloudflare artifact still has $array_types OAS 3.1 type array(s)" >&2
    return 1
  fi
}

assert_same_paths() {
  local canonical=$1
  local localized=$2
  local canonical_paths
  local localized_paths
  canonical_paths="$(jq -c '.paths | keys | sort' "$canonical")"
  localized_paths="$(jq -c '.paths | keys | sort' "$localized")"
  if [ "$canonical_paths" != "$localized_paths" ]; then
    echo "error: localized spec paths drifted from canonical" >&2
    return 1
  fi
}

run_self_test() {
  require_jq
  local tmp
  tmp="$(mktemp -d)"
  trap 'rm -rf "$tmp"' RETURN

  cat >"$tmp/canonical.json" <<'EOF'
{
  "openapi": "3.1.0",
  "info": { "title": "fixture", "version": "1.0.0" },
  "paths": {
    "/api/v1/auth-attempts": {},
    "/api/v1/auth-attempts/{id}/wait": {},
    "/api/v1/auth-attempts/{id}/cancel": {}
  },
  "components": {
    "schemas": {
      "AuthAttemptCreateRequestDto": {
        "type": "object",
        "properties": {
          "userIdentifier": { "type": ["string", "null"] },
          "enrollmentId": { "type": ["integer", "null"], "format": "int32" }
        }
      }
    }
  }
}
EOF

  if jq -e '.servers' "$tmp/canonical.json" >/dev/null 2>&1; then
    echo "error: self-test fixture must be host-neutral" >&2
    exit 1
  fi

  localize_spec "$tmp/canonical.json" "$tmp/localized.json"
  assert_single_exp1_server "$tmp/localized.json"
  assert_expected_paths "$tmp/localized.json"
  assert_same_paths "$tmp/canonical.json" "$tmp/localized.json"
  assert_cloudflare_oas30 "$tmp/localized.json"
  if [ "$(jq -r '.components.schemas.AuthAttemptCreateRequestDto.properties.userIdentifier.type' "$tmp/localized.json")" != "string" ]; then
    echo "error: self-test expected userIdentifier.type string after OAS 3.0 downlevel" >&2
    exit 1
  fi
  if [ "$(jq -r '.components.schemas.AuthAttemptCreateRequestDto.properties.userIdentifier.nullable' "$tmp/localized.json")" != "true" ]; then
    echo "error: self-test expected userIdentifier.nullable true after OAS 3.0 downlevel" >&2
    exit 1
  fi
  echo "self-test passed"
}

package_from_canonical() {
  require_jq
  if [ ! -f "$CANONICAL" ]; then
    echo "error: canonical spec not found: $CANONICAL" >&2
    exit 1
  fi
  if ! jq empty "$CANONICAL" >/dev/null 2>&1; then
    echo "error: canonical spec is not valid JSON: $CANONICAL" >&2
    exit 1
  fi
  if jq -e '.servers != null' "$CANONICAL" >/dev/null; then
    echo "warning: canonical spec still has top-level servers; run ./scripts/update-specs.sh --integration-only" >&2
  fi

  localize_spec "$CANONICAL" "$OUTPUT"
  assert_single_exp1_server "$OUTPUT"
  assert_expected_paths "$OUTPUT"
  assert_same_paths "$CANONICAL" "$OUTPUT"
  assert_cloudflare_oas30 "$OUTPUT"

  echo "wrote $OUTPUT"
  echo "server: $EXP1_SERVER_URL"
  echo "openapi: $(jq -r '.openapi' "$OUTPUT") (Cloudflare OAS 3.0 downlevel)"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --output)
      OUTPUT="${2:?--output requires a path}"
      shift 2
      ;;
    --self-test)
      SELF_TEST=true
      shift
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      echo "error: unknown option: $1" >&2
      usage
      exit 1
      ;;
  esac
done

if [ "$SELF_TEST" = true ]; then
  run_self_test
  exit 0
fi

package_from_canonical
