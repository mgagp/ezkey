#!/usr/bin/env bash
# List or upload the EXP1 Auth API OpenAPI schema for Cloudflare API Shield.
#
# Uses CLOUDFLARE_API_SHIELD_TOKEN only. Never falls back to CLOUDFLARE_API_TOKEN
# (that token is Pages-only).
#
# Usage (from repo root, Git Bash):
#   ./scripts/cloudflare/upload-auth-api-schema-exp1.sh --list
#   ./scripts/cloudflare/upload-auth-api-schema-exp1.sh --upload
#   ./scripts/cloudflare/upload-auth-api-schema-exp1.sh --upload --package
#   ./scripts/cloudflare/upload-auth-api-schema-exp1.sh --delete <schema-id>
#
# This script does not change WAF / schema-validation mitigation (Block vs None).
# Operator runbook: docs/cloudflare/auth-api-schema-validation.md

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ARTIFACT="${EZKEY_AUTH_CLOUDFLARE_SCHEMA:-$ROOT/specs/auth-api/deployments/exp1-cloudflare-openapi.json}"
SCHEMA_NAME="${EZKEY_AUTH_CLOUDFLARE_SCHEMA_NAME:-ezkey-auth-api-exp1}"
ZONE_NAME="${CLOUDFLARE_ZONE_NAME:-ezkey.org}"
MODE=""
DO_PACKAGE=false
DELETE_IDS=()
CF_API="https://api.cloudflare.com/client/v4"

usage() {
  cat <<'EOF'
List or upload the EXP1 Auth API schema on Cloudflare API Shield.

Usage:
  ./scripts/cloudflare/upload-auth-api-schema-exp1.sh --list
  ./scripts/cloudflare/upload-auth-api-schema-exp1.sh --upload
  ./scripts/cloudflare/upload-auth-api-schema-exp1.sh --upload --package
  ./scripts/cloudflare/upload-auth-api-schema-exp1.sh --delete <schema-id>
  ./scripts/cloudflare/upload-auth-api-schema-exp1.sh --delete <id> --delete <id>

Options:
  --list         Verify the shield token and list uploaded schemas (no write)
  --upload       POST specs/auth-api/deployments/exp1-cloudflare-openapi.json
  --package      Run package-auth-api-cloudflare-schema.sh before --upload
  --delete ID    Delete one uploaded schema by UUID (repeatable). Use --list first.
  --help         Show this help

Requires repo-root .env:
  CLOUDFLARE_API_SHIELD_TOKEN   zone-scoped API Gateway token (not the Pages token)
  CLOUDFLARE_ZONE_ID            optional; resolved from CLOUDFLARE_ZONE_NAME (ezkey.org)

Does not use CLOUDFLARE_API_TOKEN. Does not change Block / None mitigation.
EOF
}

require_jq() {
  if ! command -v jq >/dev/null 2>&1; then
    echo "error: jq is required" >&2
    exit 1
  fi
}

require_curl() {
  if ! command -v curl >/dev/null 2>&1; then
    echo "error: curl is required" >&2
    exit 1
  fi
}

source_env() {
  if [[ -f "$ROOT/.env" ]]; then
    set -a
    # shellcheck disable=SC1091
    source "$ROOT/.env"
    set +a
  fi
}

require_shield_token() {
  if [[ -z "${CLOUDFLARE_API_SHIELD_TOKEN:-}" ]]; then
    echo "error: set CLOUDFLARE_API_SHIELD_TOKEN in the repo-root .env" >&2
    echo "       do not reuse CLOUDFLARE_API_TOKEN (Pages-only)" >&2
    exit 1
  fi
  if [[ -n "${CLOUDFLARE_API_TOKEN:-}" && \
        "${CLOUDFLARE_API_SHIELD_TOKEN}" == "${CLOUDFLARE_API_TOKEN}" ]]; then
    echo "error: CLOUDFLARE_API_SHIELD_TOKEN must not equal CLOUDFLARE_API_TOKEN" >&2
    echo "       create a separate zone-scoped API Gateway token" >&2
    exit 1
  fi
}

cf_request() {
  local method=$1
  local url=$2
  local body_file=${3:-}
  local tmp
  tmp="$(mktemp)"
  local args=(
    -sS
    -o "$tmp"
    -w '%{http_code}'
    -X "$method"
    -H "Authorization: Bearer ${CLOUDFLARE_API_SHIELD_TOKEN}"
    -H "Content-Type: application/json"
  )
  if [[ -n "$body_file" ]]; then
    args+=(--data-binary @"$body_file")
  fi
  local http
  http="$(curl "${args[@]}" "$url")"
  CF_HTTP="$http"
  CF_BODY="$tmp"
}

print_cf_errors() {
  jq -r '
    if .errors and (.errors | length) > 0 then
      .errors[] | "cloudflare error \(.code): \(.message)"
    else
      "cloudflare HTTP '"$CF_HTTP"' (no error array)"
    end
  ' "$CF_BODY" >&2
}

lookup_zone_id_by_name() {
  local name=$1
  cf_request GET "${CF_API}/zones?name=$(jq -rn --arg n "$name" '$n|@uri')"
  if [[ "$CF_HTTP" != "200" ]]; then
    print_cf_errors
    echo "error: zone lookup failed for ${name}" >&2
    rm -f "$CF_BODY"
    exit 1
  fi
  local count id
  count="$(jq '.result | length' "$CF_BODY")"
  id="$(jq -r '.result[0].id // empty' "$CF_BODY")"
  rm -f "$CF_BODY"
  if [[ "$count" != "1" || -z "$id" ]]; then
    echo "error: expected one zone named ${name}, found ${count}" >&2
    echo "       set CLOUDFLARE_ZONE_ID to the zone UUID (not the hostname)" >&2
    exit 1
  fi
  echo "$id"
}

resolve_zone_id() {
  local raw="${CLOUDFLARE_ZONE_ID:-}"
  if [[ -z "$raw" ]]; then
    lookup_zone_id_by_name "$ZONE_NAME"
    return 0
  fi
  if [[ "$raw" =~ ^[0-9a-fA-F]{32}$ || \
        "$raw" =~ ^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$ ]]; then
    echo "$raw"
    return 0
  fi
  echo "note: CLOUDFLARE_ZONE_ID looks like a hostname; resolving ${raw}" >&2
  lookup_zone_id_by_name "$raw"
}

list_schemas() {
  local zone_id=$1
  cf_request GET "${CF_API}/zones/${zone_id}/schema_validation/schemas"
  if [[ "$CF_HTTP" == "404" ]]; then
    rm -f "$CF_BODY"
    cf_request GET "${CF_API}/zones/${zone_id}/api_gateway/user_schemas"
  fi
  if [[ "$CF_HTTP" != "200" ]]; then
    print_cf_errors
    echo "error: schema list failed (HTTP ${CF_HTTP})" >&2
    rm -f "$CF_BODY"
    exit 1
  fi
  jq -r '
    (.result // [])
    | if type != "array" then [] else . end
    | "schema_count: \(length)",
      (.[] | "- name=\(.name // "?") id=\(.schema_id // .id // "?") enabled=\(.validation_enabled // "n/a") created=\(.created_at // "n/a")")
  ' "$CF_BODY"
  rm -f "$CF_BODY"
}

verify_token() {
  cf_request GET "${CF_API}/user/tokens/verify"
  if [[ "$CF_HTTP" != "200" ]]; then
    print_cf_errors
    echo "error: shield token verify failed (HTTP ${CF_HTTP})" >&2
    rm -f "$CF_BODY"
    exit 1
  fi
  local status
  status="$(jq -r '.result.status // empty' "$CF_BODY")"
  rm -f "$CF_BODY"
  if [[ "$status" != "active" ]]; then
    echo "error: shield token status is '${status}', expected active" >&2
    exit 1
  fi
  echo "token: active"
}

zone_label() {
  local zone_id=$1
  cf_request GET "${CF_API}/zones/${zone_id}"
  if [[ "$CF_HTTP" == "200" ]]; then
    jq -r '"zone: \(.result.name) (\(.result.id))"' "$CF_BODY"
  else
    echo "zone: ${zone_id}"
  fi
  rm -f "$CF_BODY"
}

is_schema_uuid() {
  [[ "$1" =~ ^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$ ]]
}

delete_schema() {
  local zone_id=$1
  local schema_id=$2
  if ! is_schema_uuid "$schema_id"; then
    echo "error: --delete requires a schema UUID from --list, got: ${schema_id}" >&2
    exit 1
  fi
  cf_request DELETE "${CF_API}/zones/${zone_id}/schema_validation/schemas/${schema_id}"
  if [[ "$CF_HTTP" == "404" ]]; then
    rm -f "$CF_BODY"
    cf_request DELETE "${CF_API}/zones/${zone_id}/api_gateway/user_schemas/${schema_id}"
  fi
  if [[ "$CF_HTTP" != "200" ]]; then
    print_cf_errors
    echo "error: schema delete failed for ${schema_id} (HTTP ${CF_HTTP})" >&2
    rm -f "$CF_BODY"
    exit 1
  fi
  echo "deleted: ${schema_id}"
  rm -f "$CF_BODY"
}

upload_schema() {
  local zone_id=$1
  if [[ ! -f "$ARTIFACT" ]]; then
    echo "error: missing ${ARTIFACT}" >&2
    echo "       run ./scripts/package-auth-api-cloudflare-schema.sh first" >&2
    exit 1
  fi
  local payload
  payload="$(mktemp)"
  jq -n \
    --rawfile source "$ARTIFACT" \
    --arg name "$SCHEMA_NAME" \
    '{kind:"openapi_v3", name:$name, source:$source, validation_enabled:true}' \
    >"$payload"
  cf_request POST "${CF_API}/zones/${zone_id}/schema_validation/schemas" "$payload"
  rm -f "$payload"
  if [[ "$CF_HTTP" != "200" ]]; then
    print_cf_errors
    echo "error: schema upload failed (HTTP ${CF_HTTP})" >&2
    rm -f "$CF_BODY"
    exit 1
  fi
  jq -r '
    .result.schema // .result
    | "uploaded: name=\(.name // "?") id=\(.schema_id // .id // "?") enabled=\(.validation_enabled // "n/a")"
  ' "$CF_BODY"
  rm -f "$CF_BODY"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --list)
      MODE="list"
      shift
      ;;
    --upload)
      MODE="upload"
      shift
      ;;
    --delete)
      if [[ -z "${2:-}" || "${2:-}" == --* ]]; then
        echo "error: --delete requires a schema UUID from --list" >&2
        exit 1
      fi
      if [[ -n "$MODE" && "$MODE" != "delete" ]]; then
        echo "error: --delete cannot be combined with --${MODE}" >&2
        exit 1
      fi
      MODE="delete"
      DELETE_IDS+=("$2")
      shift 2
      ;;
    --package)
      DO_PACKAGE=true
      shift
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      echo "error: unknown argument: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

if [[ -z "$MODE" ]]; then
  echo "error: specify --list, --upload, or --delete <schema-id>" >&2
  usage >&2
  exit 1
fi

require_jq
require_curl
source_env
require_shield_token

cd "$ROOT"

if [[ "$DO_PACKAGE" == true ]]; then
  if [[ "$MODE" != "upload" ]]; then
    echo "error: --package is only valid with --upload" >&2
    exit 1
  fi
  "$ROOT/scripts/package-auth-api-cloudflare-schema.sh"
fi

verify_token
ZONE_ID="$(resolve_zone_id)"
zone_label "$ZONE_ID"

if [[ "$MODE" == "list" ]]; then
  list_schemas "$ZONE_ID"
  exit 0
fi

if [[ "$MODE" == "delete" ]]; then
  for schema_id in "${DELETE_IDS[@]}"; do
    delete_schema "$ZONE_ID" "$schema_id"
  done
  exit 0
fi

upload_schema "$ZONE_ID"
