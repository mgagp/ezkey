#!/usr/bin/env bash
# Stop the M0 Jenkins container (keeps the named volume unless --volumes).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if [[ "${1:-}" == "--volumes" ]]; then
  docker compose down -v
else
  docker compose down
fi
