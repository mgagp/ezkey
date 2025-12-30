#!/bin/bash
# Ezkey Docker - Restart Admin API Script
#
# Rebuilds and restarts only the admin-api container for quick iteration during development.
# This is useful when only the admin-api code has changed and you want to avoid rebuilding
# the entire Docker stack.
#
# Usage: ./restart-admin-api.sh [--no-cache]
#   --no-cache: Force rebuild without using Docker cache (slower but ensures clean build)
#               Default: uses cache for faster rebuilds
#
# Prerequisites:
#   - Docker and Docker Compose installed and running
#   - Docker stack must already be running (postgres, migration completed)
#   - Script must be run from docker/ directory or project root
#
# What this script does:
#   1. Recompiles the admin-api module using Maven
#   2. Rebuilds the admin-api Docker image (with or without cache)
#   3. Stops the existing admin-api container
#   4. Starts the admin-api container with the new image
#   5. Waits for the container to be healthy
#
# Note: This script assumes standard Docker stack (not native, not HA mode)

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
DOCKER_COMPOSE_FILE="${SCRIPT_DIR}/docker-compose.yml"
CONTAINER_NAME="ezkey-admin-api"
SERVICE_NAME="admin-api"
BUILD_NO_CACHE=""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_info() {
    echo -e "${BLUE}ℹ${NC} $1"
}

print_success() {
    echo -e "${GREEN}✅${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}⚠${NC} $1"
}

print_error() {
    echo -e "${RED}❌${NC} $1"
}

# Parse flags
for arg in "$@"; do
    case "$arg" in
        --no-cache)
            BUILD_NO_CACHE="--no-cache"
            ;;
        *)
            print_error "Unknown option: $arg"
            echo "Usage: $0 [--no-cache]"
            exit 1
            ;;
    esac
done

# Determine docker compose command (support both v1 and v2)
if docker compose version > /dev/null 2>&1; then
    DOCKER_COMPOSE="docker compose"
else
    DOCKER_COMPOSE="docker-compose"
fi

# Enable BuildKit for Maven cache mount support
export DOCKER_BUILDKIT=1
export COMPOSE_DOCKER_CLI_BUILD=1

# Check if docker-compose.yml exists
if [ ! -f "${DOCKER_COMPOSE_FILE}" ]; then
    print_error "docker-compose.yml not found at ${DOCKER_COMPOSE_FILE}"
    exit 1
fi

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    print_error "Docker is not running. Please start Docker and try again."
    exit 1
fi

# Check if postgres container is running (required dependency)
if ! docker ps --format '{{.Names}}' | grep -q "^ezkey-postgres$"; then
    print_warning "PostgreSQL container (ezkey-postgres) is not running."
    print_info "The admin-api depends on PostgreSQL. Starting it may be required."
    read -p "Do you want to start the full stack? (y/N) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        print_info "Starting full Docker stack..."
        cd "${SCRIPT_DIR}"
        ${DOCKER_COMPOSE} up -d postgres
        print_info "Waiting for PostgreSQL to be healthy..."
        sleep 5
    else
        print_error "Cannot proceed without PostgreSQL. Exiting."
        exit 1
    fi
fi

print_info "═══════════════════════════════════════════════════════════════"
print_info "🔄 Restarting Admin API Container"
print_info "═══════════════════════════════════════════════════════════════"

# Step 1: Recompile admin-api module
print_info ""
print_info "Step 1: Recompiling admin-api module..."
print_info "This may take a minute. Compiling ezkey-admin-api and dependencies..."
cd "${PROJECT_ROOT}"
print_info "Running: mvn clean package -DskipTests -pl ezkey-admin-api -am"
if mvn clean package -DskipTests -pl ezkey-admin-api -am; then
    print_success "Admin API module compiled successfully"
else
    print_error "Failed to compile admin-api module"
    exit 1
fi

# Step 2: Rebuild Docker image
print_info ""
print_info "Step 2: Rebuilding admin-api Docker image..."
if [ -n "${BUILD_NO_CACHE}" ]; then
    print_info "Building with --no-cache (this may take several minutes)..."
    BUILD_CMD="${DOCKER_COMPOSE} build --no-cache ${SERVICE_NAME}"
else
    print_info "Building with cache (faster, use --no-cache for clean rebuild)..."
    BUILD_CMD="${DOCKER_COMPOSE} build ${SERVICE_NAME}"
fi
cd "${SCRIPT_DIR}"
print_info "Running: ${BUILD_CMD}"
if ${BUILD_CMD}; then
    print_success "Admin API Docker image rebuilt successfully"
else
    print_error "Failed to rebuild admin-api Docker image"
    exit 1
fi

# Step 3: Stop existing container
print_info ""
print_info "Step 3: Stopping existing admin-api container..."
if docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
    ${DOCKER_COMPOSE} stop "${SERVICE_NAME}"
    ${DOCKER_COMPOSE} rm -f "${SERVICE_NAME}"
    print_success "Existing admin-api container stopped and removed"
else
    print_info "Admin API container was not running"
fi

# Step 4: Start new container
print_info ""
print_info "Step 4: Starting admin-api container with new image..."
${DOCKER_COMPOSE} up -d "${SERVICE_NAME}"
print_success "Admin API container started"

# Step 5: Wait for health check
print_info ""
print_info "Step 5: Waiting for admin-api to be healthy..."
print_info "This may take up to 40 seconds (start_period)..."

MAX_WAIT=60
WAIT_COUNT=0
HEALTHY=false

while [ $WAIT_COUNT -lt $MAX_WAIT ]; do
    if docker inspect --format='{{.State.Health.Status}}' "${CONTAINER_NAME}" 2>/dev/null | grep -q "healthy"; then
        HEALTHY=true
        break
    fi
    
    # Check if container is still running
    if ! docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
        print_error "Admin API container stopped unexpectedly"
        print_info "Checking logs..."
        ${DOCKER_COMPOSE} logs --tail=50 "${SERVICE_NAME}"
        exit 1
    fi
    
    sleep 2
    WAIT_COUNT=$((WAIT_COUNT + 2))
    echo -n "."
done

echo ""

if [ "$HEALTHY" = true ]; then
    print_success "Admin API is healthy and ready"
else
    print_warning "Admin API health check timeout (container may still be starting)"
    print_info "Checking container status..."
    docker ps --filter "name=${CONTAINER_NAME}" --format "table {{.Names}}\t{{.Status}}"
    print_info "Recent logs:"
    ${DOCKER_COMPOSE} logs --tail=30 "${SERVICE_NAME}"
fi

print_info ""
print_info "═══════════════════════════════════════════════════════════════"
print_success "🎉 Admin API restart completed!"
print_info "═══════════════════════════════════════════════════════════════"
print_info ""
print_info "Admin API is available at: http://localhost:9080"
print_info "Health check endpoint: http://localhost:9081/actuator/health"
print_info ""
print_info "To view logs: ${DOCKER_COMPOSE} logs -f ${SERVICE_NAME}"
print_info "To stop: ${DOCKER_COMPOSE} stop ${SERVICE_NAME}"
print_info ""

