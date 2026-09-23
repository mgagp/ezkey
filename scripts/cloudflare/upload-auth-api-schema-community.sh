#!/usr/bin/env bash
# Thin community defaults for Auth API Cloudflare schema upload (ezkey.online).
#
# Sets zone / schema name / artifact / packaging server defaults, then delegates to
# upload-auth-api-schema-exp1.sh. Does not change WAF mitigation (leave None).
#
# Usage (from repo root, Git Bash):
#   ./scripts/cloudflare/upload-auth-api-schema-community.sh --list
#   ./scripts/cloudflare/upload-auth-api-schema-community.sh --upload --package
#
# Operator runbook: docs/cloudflare/auth-api-schema-validation.md

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

export CLOUDFLARE_ZONE_NAME="${CLOUDFLARE_ZONE_NAME:-ezkey.online}"
export EZKEY_AUTH_CLOUDFLARE_SCHEMA_NAME="${EZKEY_AUTH_CLOUDFLARE_SCHEMA_NAME:-ezkey-auth-api-community}"
export EZKEY_AUTH_CLOUDFLARE_SCHEMA="${EZKEY_AUTH_CLOUDFLARE_SCHEMA:-$ROOT/specs/auth-api/deployments/community-cloudflare-openapi.json}"
export EZKEY_AUTH_CLOUDFLARE_SERVER_URL="${EZKEY_AUTH_CLOUDFLARE_SERVER_URL:-https://auth-api.ezkey.online}"

exec "$ROOT/scripts/cloudflare/upload-auth-api-schema-exp1.sh" "$@"
