#!/bin/bash
# Ezkey Admin UI -- Docker start script
# Builds the SPA from source inside Docker and serves it via Caddy on http://localhost:3000
# Requires: Docker with BuildKit support, admin-api running on localhost:9080
#
# Usage:
#   ./start.sh              # foreground (logs visible)
#   ./start.sh -d           # detached (background)
#   ./start.sh --no-cache   # force full rebuild (clears BuildKit cache)

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

DOCKER_BUILDKIT=1 docker compose -f docker-compose.admin-ui.yml up --build "$@"
