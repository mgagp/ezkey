#!/bin/bash

# Ezkey Docker Start Script
# This script builds Docker images and starts the EZ Key stack
# Usage: start.sh [--parallel] [--no-cache] [--debug-cache] [--native]
#   --parallel: Build images in parallel (default: sequential for easier log examination)
#   --no-cache: Force rebuild without using cache (default: uses BuildKit cache for optimization)
#   --debug-cache: Build only the first service (migration) and stop - for cache validation
#   --native: Use native compiled images instead of JVM images (requires pre-built native images)

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD_PARALLEL=""
BUILD_NO_CACHE=""
DEBUG_CACHE=""
NATIVE_MODE=""

# Parse flags (all parameters are optional)
for arg in "$@"; do
    case "$arg" in
        --parallel)
            BUILD_PARALLEL="--parallel"
            ;;
        --no-cache)
            BUILD_NO_CACHE="--no-cache"
            ;;
        --debug-cache)
            DEBUG_CACHE="1"
            ;;
        --native)
            NATIVE_MODE="1"
            ;;
    esac
done

# Select compose file based on mode
if [ -n "$NATIVE_MODE" ]; then
    COMPOSE_FILE="${SCRIPT_DIR}/docker-compose.native.yml"
    echo "🔧 Native mode: Using docker-compose.native.yml"
    echo "   Note: Native images must be built separately before using this mode"
    echo "   Build commands:"
    echo "     mvn spring-boot:build-image -pl ezkey-admin-api -Pnative -Dspring-boot.build-image.imageName=ezkey-admin-api-native -DskipTests"
    echo "     mvn spring-boot:build-image -pl ezkey-auth-api -Pnative -Dspring-boot.build-image.imageName=ezkey-auth-api-native -DskipTests"
    echo ""
else
    COMPOSE_FILE="${SCRIPT_DIR}/docker-compose.yml"
fi

echo "=========================================="
echo "  EZ Key Docker - Starting Stack"
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
    echo "  ✅ Maven cache volume created (visible in Docker Desktop)"
else
    echo "  ✅ Using existing Maven cache volume"
fi

echo ""
echo "========================================"
if [ -n "$DEBUG_CACHE" ]; then
    echo "Building FIRST service only (migration) - Cache Debug Mode"
    echo "This builds only the migration service to validate BuildKit cache"
else
    echo "Building Docker images with BuildKit"
fi
echo "========================================"
if [ -n "$BUILD_NO_CACHE" ]; then
    echo "  Cache: DISABLED (--no-cache flag)"
else
    echo "  Cache: ENABLED (BuildKit cache mount)"
    echo "  Note: BuildKit cache is not visible in Docker Desktop but is functional"
fi
if [ -n "$BUILD_PARALLEL" ]; then
    echo "  Mode: Parallel build"
else
    echo "  Mode: Sequential build (easier log examination)"
fi
if [ -n "$DEBUG_CACHE" ]; then
    echo "  Debug: Building ONLY migration service (first image)"
    echo "  Services will NOT be started after build"
fi
echo "========================================"
echo ""

# Record start time
BUILD_START=$(date +%T)
echo "Build started at: $BUILD_START"
echo ""

cd "${SCRIPT_DIR}/.."
if [ -n "$DEBUG_CACHE" ]; then
    # Build only the migration service (first image to be built)
    echo "Building migration service (first image)..."
    ${DOCKER_COMPOSE} -f "${COMPOSE_FILE}" build ${BUILD_NO_CACHE} migration || {
        echo "❌ Error: Failed to build Docker images"
        exit 1
    }
elif [ -n "$BUILD_PARALLEL" ]; then
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

# If debug mode, stop here
if [ -n "$DEBUG_CACHE" ]; then
    echo ""
    echo "========================================"
    echo "Cache Debug Mode - Build stopped"
    echo "========================================"
    echo "The migration service has been built successfully."
    echo ""
    echo "To validate cache optimization:"
    echo "  1. Run again: docker/start.sh --debug-cache"
    echo "  2. Compare build times - second build should be MUCH faster"
    echo "  3. Check logs for 'using cached dependencies' messages"
    echo ""
    echo "To build and start all services:"
    echo "  docker/start.sh"
    echo ""
    exit 0
fi

echo ""
echo "🚀 Starting services..."
${DOCKER_COMPOSE} -f "${COMPOSE_FILE}" up -d || {
    echo "❌ Error: Failed to start services"
    exit 1
}

echo ""
echo "⏳ Waiting for services to be healthy..."

# Wait for PostgreSQL to be ready
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

# Wait for migration to complete
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

# Wait for APIs to be healthy
echo "  - Waiting for Admin API..."
timeout=120
elapsed=0
while ! curl -sf http://localhost:9080/actuator/health > /dev/null 2>&1; do
    if [ $elapsed -ge $timeout ]; then
        echo "❌ Error: Admin API did not become healthy within ${timeout} seconds"
        ${DOCKER_COMPOSE} -f "${COMPOSE_FILE}" logs admin-api
        exit 1
    fi
    sleep 2
    elapsed=$((elapsed + 2))
done
echo "  ✅ Admin API is healthy"

echo "  - Waiting for Auth API..."
timeout=120
elapsed=0
while ! curl -sf http://localhost:8080/actuator/health > /dev/null 2>&1; do
    if [ $elapsed -ge $timeout ]; then
        echo "❌ Error: Auth API did not become healthy within ${timeout} seconds"
        ${DOCKER_COMPOSE} -f "${COMPOSE_FILE}" logs auth-api
        exit 1
    fi
    sleep 2
    elapsed=$((elapsed + 2))
done
echo "  ✅ Auth API is healthy"

echo "  - Waiting for Crypto API..."
timeout=120
elapsed=0
while ! curl -sf http://localhost:9090/actuator/health > /dev/null 2>&1; do
    if [ $elapsed -ge $timeout ]; then
        echo "❌ Error: Crypto API did not become healthy within ${timeout} seconds"
        ${DOCKER_COMPOSE} -f "${COMPOSE_FILE}" logs crypto-api
        exit 1
    fi
    sleep 2
    elapsed=$((elapsed + 2))
done
echo "  ✅ Crypto API is healthy"

echo ""
echo "=========================================="
echo "  ✅ EZ Key Stack is Ready!"
echo "=========================================="
echo ""
echo "📋 Service URLs:"
echo "  - Admin API:    http://localhost:9080"
echo "  - Auth API:     http://localhost:8080"
echo "  - Crypto API:   http://localhost:9090"
echo "  - Demo Device:  http://localhost:8083"
echo ""
echo "📚 API Documentation:"
echo "  - Admin API:    http://localhost:9080/swagger-ui.html"
echo "  - Auth API:     http://localhost:8080/swagger-ui.html"
echo "  - Crypto API:   http://localhost:9090/swagger-ui.html"
echo ""
echo "💡 Useful commands:"
echo "  - View logs:    ./docker/manage.sh logs"
echo "  - Stop stack:   ./docker/manage.sh stop"
echo "  - View status:  ./docker/manage.sh status"
echo ""
echo "🔧 Test Mode (permissive rate limiting):"
echo "  - Start in test mode: SPRING_PROFILES_ACTIVE=docker,docker-test ./docker/start.sh"
echo "  - Default mode (production): ./docker/start.sh"
echo ""
echo "🧪 Next Steps (Required):"
echo "  - Extract bootstrap credentials: mvn test -pl ezkey-tests -Dtest=BootstrapCredentialsExtractionTest"
echo "  - This extracts enrollment credentials from Docker logs (first time only)"
echo "  - Initialize admin token: mvn test -pl ezkey-tests -Dtest=AdminTokenCreationTest"
echo ""
