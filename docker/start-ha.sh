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
DEV_OVERRIDE_FILE="${SCRIPT_DIR}/docker-compose.ha.docker-dev.yml"

# Ensure docker base profile is active when using docker-dev or docker-test.
# Rationale: docker-dev and docker-test are intended to override docker defaults, not replace them.
if [[ ",${SPRING_PROFILES_ACTIVE:-}," == *",docker-dev,"* ]] && [[ ",${SPRING_PROFILES_ACTIVE:-}," != *",docker,"* ]]; then
    export SPRING_PROFILES_ACTIVE="docker,${SPRING_PROFILES_ACTIVE}"
fi
if [[ ",${SPRING_PROFILES_ACTIVE:-}," == *",docker-test,"* ]] && [[ ",${SPRING_PROFILES_ACTIVE:-}," != *",docker,"* ]]; then
    export SPRING_PROFILES_ACTIVE="docker,${SPRING_PROFILES_ACTIVE}"
fi

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

COMPOSE_ARGS="-f ${COMPOSE_FILE}"

# If docker-dev profile is active, auto-include the HA diagnostics override to publish per-instance management ports.
if [[ ",${SPRING_PROFILES_ACTIVE:-}," == *",docker-dev,"* ]] && [ -f "${DEV_OVERRIDE_FILE}" ]; then
    COMPOSE_ARGS="${COMPOSE_ARGS} -f ${DEV_OVERRIDE_FILE}"
    echo "🔧 HA Docker diagnostics override enabled: $(basename "${DEV_OVERRIDE_FILE}")"
fi

# Enable BuildKit for Maven cache mount support
export DOCKER_BUILDKIT=1
export COMPOSE_DOCKER_CLI_BUILD=1

# Maven dependencies use BuildKit cache mounts in docker/Dockerfile (not a named Docker volume).

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
    ${DOCKER_COMPOSE} ${COMPOSE_ARGS} build ${BUILD_NO_CACHE} --parallel || {
        echo "❌ Error: Failed to build Docker images"
        exit 1
    }
else
    ${DOCKER_COMPOSE} ${COMPOSE_ARGS} build ${BUILD_NO_CACHE} || {
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
${DOCKER_COMPOSE} ${COMPOSE_ARGS} up -d || {
    echo "❌ Error: Failed to start services"
    exit 1
}

echo ""
echo "⏳ Waiting for services to be healthy..."

# Wait for PostgreSQL
echo "  - Waiting for PostgreSQL..."
timeout=60
elapsed=0
while ! ${DOCKER_COMPOSE} ${COMPOSE_ARGS} exec -T postgres pg_isready -U postgres > /dev/null 2>&1; do
    if [ $elapsed -ge $timeout ]; then
        echo "❌ Error: PostgreSQL did not become ready within ${timeout} seconds"
        ${DOCKER_COMPOSE} ${COMPOSE_ARGS} logs postgres
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
while ${DOCKER_COMPOSE} ${COMPOSE_ARGS} ps migration | grep -q "Up\|running"; do
    if [ $elapsed -ge $timeout ]; then
        echo "❌ Error: Migration did not complete within ${timeout} seconds"
        ${DOCKER_COMPOSE} ${COMPOSE_ARGS} logs migration
        exit 1
    fi
    sleep 2
    elapsed=$((elapsed + 2))
done
echo "  ✅ Database migrations completed"

# Wait for Admin API instances (direct instance Actuator on management port)
echo "  - Waiting for Admin API instances..."
timeout=120
elapsed=0
while ! ${DOCKER_COMPOSE} ${COMPOSE_ARGS} exec -T admin-api-1 curl -sf http://localhost:9081/actuator/health > /dev/null 2>&1; do
    if [ $elapsed -ge $timeout ]; then
        echo "❌ Error: Admin API Instance 1 did not become healthy within ${timeout} seconds"
        ${DOCKER_COMPOSE} ${COMPOSE_ARGS} logs admin-api-1
        exit 1
    fi
    sleep 2
    elapsed=$((elapsed + 2))
done
elapsed=0
while ! ${DOCKER_COMPOSE} ${COMPOSE_ARGS} exec -T admin-api-2 curl -sf http://localhost:9081/actuator/health > /dev/null 2>&1; do
    if [ $elapsed -ge $timeout ]; then
        echo "❌ Error: Admin API Instance 2 did not become healthy within ${timeout} seconds"
        ${DOCKER_COMPOSE} ${COMPOSE_ARGS} logs admin-api-2
        exit 1
    fi
    sleep 2
    elapsed=$((elapsed + 2))
done
echo "  ✅ Admin API instances are healthy"

# Wait for Auth API instances (direct instance Actuator on management port)
echo "  - Waiting for Auth API instances..."
timeout=120
elapsed=0
while ! ${DOCKER_COMPOSE} ${COMPOSE_ARGS} exec -T auth-api-1 curl -sf http://localhost:8085/actuator/health > /dev/null 2>&1; do
    if [ $elapsed -ge $timeout ]; then
        echo "❌ Error: Auth API Instance 1 did not become healthy within ${timeout} seconds"
        ${DOCKER_COMPOSE} ${COMPOSE_ARGS} logs auth-api-1
        exit 1
    fi
    sleep 2
    elapsed=$((elapsed + 2))
done
elapsed=0
while ! ${DOCKER_COMPOSE} ${COMPOSE_ARGS} exec -T auth-api-2 curl -sf http://localhost:8085/actuator/health > /dev/null 2>&1; do
    if [ $elapsed -ge $timeout ]; then
        echo "❌ Error: Auth API Instance 2 did not become healthy within ${timeout} seconds"
        ${DOCKER_COMPOSE} ${COMPOSE_ARGS} logs auth-api-2
        exit 1
    fi
    sleep 2
    elapsed=$((elapsed + 2))
done
echo "  ✅ Auth API instances are healthy"

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
echo "  - Auth API LB:  http://localhost:8085/stats"
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
