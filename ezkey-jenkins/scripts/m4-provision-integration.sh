#!/usr/bin/env bash
# Compatibility wrapper — M5 provisions a dedicated lab integration+enrollment.
# Prefer: ./scripts/m5-provision-lab-integration.sh
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
exec "${ROOT}/scripts/m5-provision-lab-integration.sh" "$@"
