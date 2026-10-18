#!/usr/bin/env bash
# Community Auth API Cloudflare schema upload (ezkey.online).
# Delegates to upload-auth-api-schema.sh (same defaults).
#
# Usage (from repo root, Git Bash):
#   ./scripts/cloudflare/upload-auth-api-schema-community.sh --list
#   ./scripts/cloudflare/upload-auth-api-schema-community.sh --upload --package
#
# Operator runbook: docs/cloudflare/auth-api-schema-validation.md

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
exec "$ROOT/scripts/cloudflare/upload-auth-api-schema.sh" "$@"
