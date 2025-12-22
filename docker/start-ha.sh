#!/bin/bash

# Ezkey Docker HA Start Script
# This script builds Docker images and starts the EZ Key HA stack
# Usage: start-ha.sh [--parallel] [--no-cache]

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD_PARALLEL=""
BUILD_NO_CACHE=""

# Parse flags
for arg in "$@"; do
    case "$arg" in
        --parallel)
            BUILD_PARALLEL="--parallel"
            ;;
        --no-cache)
            BUILD_NO_CACHE="--no-cache"
            ;;
    esac
done

COMPOSE_FILE="${SCRIPT_DIR}/docker-compose.ha.yml"

echo "=========================================="
echo "  EZ Key Docker HA - Starting Stack"
echo "=========================================="
echo ""

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo "❌ Error: Docker is not running. Please start Docker and try again."
    exit 1
fi

# Check if Docker Compose is available
if ! command -v docker-compose > /dev/null 2>&1 && ! docker compose version > /dev/null 2>&1; then
    echo "❌ Error: Docker Compose is not installed. Please install Docker Compose and try again."
    exit 1
fi

# Determine docker compose command
if docker compose version > /dev/null 2>&1; then
    DOCKER_COMPOSE="docker compose"
else
    DOCKER_COMPOSE="docker-compose"
fi

# Enable BuildKit for Maven cache mount support
export DOCKER_BUILDKIT=1
export COMPOSE_DOCKER_CLI_BUILD=1

# Create Maven cache volume if it doesn't exist
if ! docker volume inspect maven-cache > /dev/null 2>&1; then
    echo "📦 Creating Maven cache volume..."
    docker volume create maven-cache
    echo "  ✅ Maven cache volume created"
else
    echo "  ✅ Using existing Maven cache volume"
fi

echo ""
echo "========================================"
echo "Building Docker images with BuildKit"
echo "========================================"
if [ -n "$BUILD_NO_CACHE" ]; then
    echo "  Cache: DISABLED (--no-cache flag)"
else
    echo "  Cache: ENABLED (BuildKit cache mount)"
fi
if [ -n "$BUILD_PARALLEL" ]; then
    echo "  Mode: Parallel build"
else
    echo "  Mode: Sequential build"
fi
echo "========================================"
echo ""

BUILD_START=$(date +%T)
echo "Build started at: $BUILD_START"
echo ""

cd "${SCRIPT_DIR}/.."
if [ -n "$BUILD_PARALLEL" ]; then
    ${DOCKER_COMPOSE} -f "${COMPOSE_FILE}" build ${BUILD_NO_CACHE} --parallel || {
        echo "❌ Error: Failed to build Docker images"
        exit 1
    }
else
    ${DOCKER_COMPOSE} -f "${COMPOSE_FILE}" build ${BUILD_NO_CACHE} || {
        echo "❌ Error: Failed to build Docker images"
        exit 1
    }
fi

BUILD_END=$(date +%T)
echo ""
echo "========================================"
echo "Build completed at: $BUILD_END"
echo "========================================"

echo ""
echo "🚀 Starting HA services..."
${DOCKER_COMPOSE} -f "${COMPOSE_FILE}" up -d || {
    echo "❌ Error: Failed to start services"
    exit 1
}

echo ""
echo "⏳ Waiting for services to be healthy..."

# Wait for PostgreSQL
echo "  - Waiting for PostgreSQL..."
timeout=60
elapsed=0
while ! ${DOCKER_COMPOSE} -f "${COMPOSE_FILE}" exec -T postgres pg_isready -U postgres > /dev/null 2>&1; do
    if [ $elapsed -ge $timeout ]; then
        echo "❌ Error: PostgreSQL did not become ready within ${timeout} seconds"
        ${DOCKER_COMPOSE} -f "${COMPOSE_FILE}" logs postgres
        exit 1
    fi
    sleep 2
    elapsed=$((elapsed + 2))
done
echo "  ✅ PostgreSQL is ready"

# Wait for migration
echo "  - Waiting for database migrations..."
timeout=120
elapsed=0
while ${DOCKER_COMPOSE} -f "${COMPOSE_FILE}" ps migration | grep -q "Up\|running"; do
    if [ $elapsed -ge $timeout ]; then
        echo "❌ Error: Migration did not complete within ${timeout} seconds"
        ${DOCKER_COMPOSE} -f "${COMPOSE_FILE}" logs migration
        exit 1
    fi
    sleep 2
    elapsed=$((elapsed + 2))
done
echo "  ✅ Database migrations completed"

# Wait for Admin API instances
echo "  - Waiting for Admin API instances..."
timeout=120
elapsed=0
while ! curl -sf http://localhost:9080/actuator/health > /dev/null 2>&1; do
    if [ $elapsed -ge $timeout ]; then
        echo "❌ Error: Admin API (via HAProxy) did not become healthy within ${timeout} seconds"
        ${DOCKER_COMPOSE} -f "${COMPOSE_FILE}" logs haproxy-admin
        exit 1
    fi
    sleep 2
    elapsed=$((elapsed + 2))
done
echo "  ✅ Admin API is healthy (via HAProxy)"

# Wait for Auth API instances
echo "  - Waiting for Auth API instances..."
timeout=120
elapsed=0
while ! curl -sf http://localhost:8080/actuator/health > /dev/null 2>&1; do
    if [ $elapsed -ge $timeout ]; then
        echo "❌ Error: Auth API (via HAProxy) did not become healthy within ${timeout} seconds"
        ${DOCKER_COMPOSE} -f "${COMPOSE_FILE}" logs haproxy-auth
        exit 1
    fi
    sleep 2
    elapsed=$((elapsed + 2))
done
echo "  ✅ Auth API is healthy (via HAProxy)"

echo ""
echo "=========================================="
echo "  ✅ EZ Key HA Stack is Ready!"
echo "=========================================="
echo ""
echo "📋 Service URLs (via Load Balancers):"
echo "  - Admin API:    http://localhost:9080 (HAProxy → admin-api-1, admin-api-2)"
echo "  - Auth API:     http://localhost:8080 (HAProxy → auth-api-1, auth-api-2)"
echo ""
echo "📊 HAProxy Statistics:"
echo "  - Admin API LB: http://localhost:9081/stats"
echo "  - Auth API LB:  http://localhost:8081/stats"
echo ""
echo "💡 Useful commands:"
echo "  - View logs:    ./docker/manage-ha.sh logs"
echo "  - Stop stack:   ./docker/manage-ha.sh stop"
echo "  - View status:  ./docker/manage-ha.sh status"
echo ""
echo "🔍 Verify HA Setup:"
echo "  - Check both instances are running: docker ps | grep ezkey-admin-api"
echo "  - Check HAProxy stats: curl http://localhost:9081/stats"
echo "  - View instance logs: ./docker/manage-ha.sh logs admin-api-1"
echo ""
