#!/bin/bash
# Ezkey Admin UI -- Docker start script
# Builds the SPA from source inside Docker and serves it via Caddy.
# Default: Test/QA build (demo mode on, port 3090). Use -production for production build (no demo).
# Requires: Docker with BuildKit support, admin-api running on localhost:9080
#
# Usage:
#   ./start.sh                    # Test/QA build (demo on), http://localhost:3090
#   ./start.sh -production        # Production build (no demo), port 3090
#   ./start.sh -p 3080            # Custom host port (e.g. if 3090 is taken)
#   ./start.sh -production -p 3080
#   ./start.sh -d                 # Detached (background)
#   ./start.sh --no-cache         # Force full rebuild (clears BuildKit cache)

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Defaults: compose file uses ${BUILD_MODE:-test} and ${ADMIN_UI_PORT:-3090}
BUILD_MODE=
ADMIN_UI_PORT=
NO_CACHE=
COMPOSE_ARGS=()

while [ $# -gt 0 ]; do
  case "$1" in
    -production|--production)
      BUILD_MODE=production
      shift
      ;;
    -p|--port)
      if [ -n "${2:-}" ]; then
        ADMIN_UI_PORT="$2"
        shift 2
      else
        echo "Error: -p/--port requires a port number" >&2
        exit 1
      fi
      ;;
    --no-cache)
      NO_CACHE=1
      shift
      ;;
    *)
      COMPOSE_ARGS+=("$1")
      shift
      ;;
  esac
done

export BUILD_MODE
export ADMIN_UI_PORT

# Stamp Public alpha chrome with the current short SHA when not already set.
if [ -z "${VITE_GIT_SHA:-}" ]; then
  if VITE_GIT_SHA="$(git -C "$SCRIPT_DIR/.." rev-parse --short=7 HEAD 2>/dev/null)"; then
    export VITE_GIT_SHA
  else
    export VITE_GIT_SHA=unknown
  fi
fi

COMPOSE_FILE="-f docker-compose.admin-ui.yml"
if [ -n "$NO_CACHE" ]; then
  DOCKER_BUILDKIT=1 docker compose $COMPOSE_FILE build --no-cache
fi
DOCKER_BUILDKIT=1 docker compose $COMPOSE_FILE up --build "${COMPOSE_ARGS[@]}"
