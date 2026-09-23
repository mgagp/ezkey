#!/usr/bin/env bash
# Thin community defaults for Integration API Cloudflare schema upload (ezkey.online).
#
# Sets zone / schema name / artifact / packaging server defaults, then delegates to
# upload-integration-api-schema-exp1.sh. Does not change WAF mitigation (leave None).
# Does not delete the Auth schema.
#
# Usage (from repo root, Git Bash):
#   ./scripts/cloudflare/upload-integration-api-schema-community.sh --list
#   ./scripts/cloudflare/upload-integration-api-schema-community.sh --upload --package
#
# Operator runbook: docs/cloudflare/integration-api-schema-validation.md

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

export CLOUDFLARE_ZONE_NAME="${CLOUDFLARE_ZONE_NAME:-ezkey.online}"
export EZKEY_INTEGRATION_CLOUDFLARE_SCHEMA_NAME="${EZKEY_INTEGRATION_CLOUDFLARE_SCHEMA_NAME:-ezkey-integration-api-community}"
export EZKEY_INTEGRATION_CLOUDFLARE_SCHEMA="${EZKEY_INTEGRATION_CLOUDFLARE_SCHEMA:-$ROOT/specs/integration-api/deployments/community-cloudflare-openapi.json}"
export EZKEY_INTEGRATION_CLOUDFLARE_SERVER_URL="${EZKEY_INTEGRATION_CLOUDFLARE_SERVER_URL:-https://integration-api.ezkey.online}"
# Protect the community Auth schema name if present on the same zone.
export EZKEY_AUTH_CLOUDFLARE_SCHEMA_NAME="${EZKEY_AUTH_CLOUDFLARE_SCHEMA_NAME:-ezkey-auth-api-community}"

exec "$ROOT/scripts/cloudflare/upload-integration-api-schema-exp1.sh" "$@"
