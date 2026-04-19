#!/usr/bin/env bash
#
# Experimental hybrid (Lightsail) — full stack reset and start, similar to local ezkey-tests/clean-start.sh
# but for the hosted cloud compose stack (project: ezkey-experimental-lightsail).
#
# What it does by default:
#   1. Stops the stack and removes named volumes (Postgres, encryption-secrets, caddy-data, bootstrap, etc.)
#   2. Generates the Tink master key in the encryption-secrets volume (reusing docker/generate-encryption-keys.sh)
#   3. Brings the stack back with docker compose up -d
#
# Prerequisites: Docker and Docker Compose on the host (the Lightsail VM). Run from anywhere; the script
# resolves paths from its location. A copy of the repo (or at least docker/ + this lightsail/ tree) is required
# so docker/generate-encryption-keys.sh exists.
#
# Privileged host user (root) is NOT required. Follow the usual Docker practice: run as a normal login user
# that can access the Docker daemon — typically membership in the "docker" group (one-time:
# sudo usermod -aG docker "$USER", then log out/in). The script never calls sudo. Key generation uses a
# short-lived container (root inside that container only) to write the volume; that is unrelated to your
# host account being root.
#
# Usage:
#   ./clean-start.sh              Full reset + keygen + up
#   ./clean-start.sh --no-down    Only docker compose up -d (no volume wipe, no keygen)
#   ./clean-start.sh --no-keygen  Down -v and up, but skip encryption key generation (advanced / unsafe)
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# experimental-hybrid/lightsail -> repo root
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
GEN_KEYS="${REPO_ROOT}/docker/generate-encryption-keys.sh"
COMPOSE_FILE="${SCRIPT_DIR}/docker-compose.yml"

NO_DOWN=""
NO_KEYGEN=""

usage() {
    sed -n '1,25p' "$0" | tail -n +2
}

for arg in "$@"; do
    case "$arg" in
        --no-down) NO_DOWN="1" ;;
        --no-keygen) NO_KEYGEN="1" ;;
        -h|--help) usage; exit 0 ;;
        *)
            echo "Unknown option: $arg" >&2
            usage >&2
            exit 1
            ;;
    esac
done

if [ ! -f "$COMPOSE_FILE" ]; then
    echo "❌ docker-compose.yml not found at ${COMPOSE_FILE}" >&2
    exit 1
fi

if docker compose version >/dev/null 2>&1; then
    DOCKER_COMPOSE=(docker compose)
elif docker-compose version >/dev/null 2>&1; then
    DOCKER_COMPOSE=(docker-compose)
else
    echo "❌ Neither 'docker compose' nor 'docker-compose' is available." >&2
    exit 1
fi

if ! docker info >/dev/null 2>&1; then
    echo "❌ Cannot talk to the Docker daemon (docker info failed)." >&2
    echo "   You do not need to run this script as root. Grant your user access to Docker, then retry:" >&2
    echo '     sudo usermod -aG docker "$USER"' >&2
    echo "   Log out and back in (or: newgrp docker). On Lightsail, use the same pattern as any Linux Docker host." >&2
    exit 1
fi

cd "${SCRIPT_DIR}"

echo "=========================================="
echo "  Ezkey experimental-hybrid — clean start"
echo "  (Lightsail / remote stack)"
echo "=========================================="
echo ""

if [ -n "$NO_DOWN" ]; then
    echo "Step 1/3: Skipping docker compose down -v (--no-down)"
else
    echo "Step 1/3: Stopping stack and removing volumes (destructive)..."
    "${DOCKER_COMPOSE[@]}" -f "${COMPOSE_FILE}" down -v
    echo "  ✅ Volumes removed"
fi
echo ""

if [ -n "$NO_KEYGEN" ]; then
    echo "Step 2/3: Skipping encryption key generation (--no-keygen)"
    if [ -z "$NO_DOWN" ]; then
        echo "  ⚠️  You wiped volumes without seeding encryption keys. Start Admin API will disable Tink until you run:" >&2
        echo "       bash ${GEN_KEYS} --experimental-lightsail --force" >&2
    fi
else
    echo "Step 2/3: Generating master key in encryption-secrets volume..."
    if [ ! -f "$GEN_KEYS" ]; then
        echo "  ❌ Missing ${GEN_KEYS}" >&2
        echo "     Clone the full Ezkey repo on this host or copy docker/generate-encryption-keys.sh next to this script's repo layout." >&2
        exit 1
    fi
    bash "${GEN_KEYS}" --experimental-lightsail --force
    echo "  ✅ Master key material ready"
fi
echo ""

echo "Step 3/3: Starting stack (docker compose up -d)..."
"${DOCKER_COMPOSE[@]}" -f "${COMPOSE_FILE}" up -d
echo "  ✅ Stack started"
echo ""

echo "=========================================="
echo "  ✅ Clean start complete"
echo "=========================================="
echo ""
echo "Compose file: ${COMPOSE_FILE}"
echo "Ensure lightsail/.env exists (copy from .env.example) and CORS / URLs are set as needed."
echo "See experimental-hybrid/DEPLOYMENT_PLAYBOOK.md (Phase 3b encryption, Cloudflare, CORS)."
echo ""
