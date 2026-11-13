#!/usr/bin/env bash
# Community Integration API Cloudflare schema upload (ezkey.online).
# Delegates to upload-integration-api-schema.sh (same defaults).
#
# Usage (from repo root, Git Bash):
#   ./scripts/cloudflare/upload-integration-api-schema-community.sh --list
#   ./scripts/cloudflare/upload-integration-api-schema-community.sh --upload --package
#
# Operator runbook: docs/cloudflare/integration-api-schema-validation.md

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
exec "$ROOT/scripts/cloudflare/upload-integration-api-schema.sh" "$@"
