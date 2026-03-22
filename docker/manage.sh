#!/bin/bash

# Ezkey Docker Management Script
# This script provides commands to manage the EZ Key Docker stack
# Usage: manage.sh [command] [--parallel] [--no-cache]
#   --parallel: Build images in parallel (default: sequential for easier log examination)
#   --no-cache: Force rebuild without using cache (default: uses BuildKit cache for optimization)

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMMAND="$1"
BUILD_PARALLEL=""
BUILD_NO_CACHE=""

# Parse flags
if [ "$2" == "--parallel" ]; then
    BUILD_PARALLEL="--parallel"
fi
if [ "$2" == "--no-cache" ]; then
    BUILD_NO_CACHE="--no-cache"
fi
if [ "$3" == "--parallel" ]; then
    BUILD_PARALLEL="--parallel"
fi
if [ "$3" == "--no-cache" ]; then
    BUILD_NO_CACHE="--no-cache"
fi

function show_usage() {
    echo "Usage: $0 {start|stop|restart|logs|status|clean|build} [--parallel] [--no-cache]"
    echo ""
    echo "Commands:"
    echo "  start   - Start all services"
    echo "  stop    - Stop all services"
    echo "  restart - Restart all services"
    echo "  logs    - Show logs (use 'logs <service>' for specific service)"
    echo "  status  - Show status of all services"
    echo "  clean   - Stop and remove all containers, networks, and volumes"
    echo "  build   - Build all Docker images"
    echo ""
    echo "Build options:"
    echo "  --parallel - Build images in parallel (default: sequential for easier log examination)"
    echo "  --no-cache - Force rebuild without using cache"
    exit 1
}

COMPOSE_FILE="${SCRIPT_DIR}/docker-compose.yml"

# Determine docker compose command
if docker compose version > /dev/null 2>&1; then
    DOCKER_COMPOSE="docker compose"
else
    DOCKER_COMPOSE="docker-compose"
fi

# Enable BuildKit for Maven cache mount support
export DOCKER_BUILDKIT=1
export COMPOSE_DOCKER_CLI_BUILD=1

function start_services() {
    echo "🚀 Starting EZ Key stack..."
    cd "${SCRIPT_DIR}/.."
    ${DOCKER_COMPOSE} -f "${COMPOSE_FILE}" up -d
    echo "✅ Services started"
}

function stop_services() {
    echo "🛑 Stopping EZ Key stack..."
    cd "${SCRIPT_DIR}/.."
    ${DOCKER_COMPOSE} -f "${COMPOSE_FILE}" stop
    echo "✅ Services stopped"
}

function restart_services() {
    echo "🔄 Restarting EZ Key stack..."
    cd "${SCRIPT_DIR}/.."
    ${DOCKER_COMPOSE} -f "${COMPOSE_FILE}" restart
    echo "✅ Services restarted"
}

function show_logs() {
    cd "${SCRIPT_DIR}/.."
    if [ -z "$2" ]; then
        ${DOCKER_COMPOSE} -f "${COMPOSE_FILE}" logs -f
    else
        ${DOCKER_COMPOSE} -f "${COMPOSE_FILE}" logs -f "$2"
    fi
}

function show_status() {
    echo "📊 EZ Key Stack Status:"
    echo ""
    cd "${SCRIPT_DIR}/.."
    ${DOCKER_COMPOSE} -f "${COMPOSE_FILE}" ps
    echo ""
    echo "🔍 Health Checks:"
    echo ""

    # Check PostgreSQL
    if ${DOCKER_COMPOSE} -f "${COMPOSE_FILE}" exec -T postgres pg_isready -U postgres > /dev/null 2>&1; then
        echo "  ✅ PostgreSQL: Healthy"
    else
        echo "  ❌ PostgreSQL: Unhealthy"
    fi

    # Check Admin API
    if ${DOCKER_COMPOSE} -f "${COMPOSE_FILE}" exec -T admin-api curl -sf http://localhost:9081/actuator/health > /dev/null 2>&1; then
        echo "  ✅ Admin API: Healthy"
    else
        echo "  ❌ Admin API: Unhealthy"
    fi

    # Check Auth API
    if ${DOCKER_COMPOSE} -f "${COMPOSE_FILE}" exec -T auth-api curl -sf http://localhost:8085/actuator/health > /dev/null 2>&1; then
        echo "  ✅ Auth API: Healthy"
    else
        echo "  ❌ Auth API: Unhealthy"
    fi

    # Check Integration API
    if ${DOCKER_COMPOSE} -f "${COMPOSE_FILE}" exec -T integration-api curl -sf http://localhost:7081/actuator/health > /dev/null 2>&1; then
        echo "  ✅ Integration API: Healthy"
    else
        echo "  ❌ Integration API: Unhealthy"
    fi

    # Check Crypto API
    if curl -sf http://localhost:9090/actuator/health > /dev/null 2>&1; then
        echo "  ✅ Crypto API: Healthy"
    else
        echo "  ❌ Crypto API: Unhealthy"
    fi

    # Check Demo Device
    if curl -sf http://localhost:8083/actuator/health > /dev/null 2>&1; then
        echo "  ✅ Demo Device: Healthy"
    else
        echo "  ⚠️  Demo Device: Not available (optional service)"
    fi
}

function clean_all() {
    echo "🧹 Cleaning up EZ Key stack..."
    echo "⚠️  This will remove all containers, networks, and volumes (including database data)"
    read -p "Are you sure? (y/N) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "Cancelled."
        exit 0
    fi
    cd "${SCRIPT_DIR}/.."
    ${DOCKER_COMPOSE} -f "${COMPOSE_FILE}" down -v --remove-orphans
    echo "✅ Cleanup completed"
}

function build_images() {
    echo "📦 Building Docker images..."

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
        echo "  Mode: Sequential build (easier log examination)"
    fi
    echo "========================================"
    echo ""
    cd "${SCRIPT_DIR}/.."
    ${DOCKER_COMPOSE} -f "${COMPOSE_FILE}" build ${BUILD_NO_CACHE} ${BUILD_PARALLEL}
    echo "✅ Build completed"
}

# Main command handling
case "$COMMAND" in
    start)
        start_services
        ;;
    stop)
        stop_services
        ;;
    restart)
        restart_services
        ;;
    logs)
        show_logs "$@"
        ;;
    status)
        show_status
        ;;
    clean)
        clean_all
        ;;
    build)
        build_images
        ;;
    *)
        show_usage
        ;;
esac
