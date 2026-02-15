#!/bin/bash

# Ezkey Docker HA Management Script
# This script provides commands to manage the EZ Key HA Docker stack
# Usage: manage-ha.sh [command]

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMMAND="$1"

function show_usage() {
    echo "Usage: $0 {start|stop|restart|logs|status|clean|build} [options]"
    echo ""
    echo "Commands:"
    echo "  start   - Start all HA services"
    echo "  stop    - Stop all HA services"
    echo "  restart - Restart all HA services"
    echo "  logs    - Show logs (use 'logs <service>' for specific service)"
    echo "  status  - Show status of all HA services"
    echo "  clean   - Stop and remove all containers, networks, and volumes"
    echo "  build   - Build all Docker images"
    echo ""
    echo "Examples:"
    echo "  ./manage-ha.sh logs admin-api-1    # View logs for admin-api-1"
    echo "  ./manage-ha.sh logs haproxy-admin  # View HAProxy admin logs"
    exit 1
}

COMPOSE_FILE="${SCRIPT_DIR}/docker-compose.ha.yml"

# Determine docker compose command
if docker compose version > /dev/null 2>&1; then
    DOCKER_COMPOSE="docker compose"
else
    DOCKER_COMPOSE="docker-compose"
fi

# Enable BuildKit
export DOCKER_BUILDKIT=1
export COMPOSE_DOCKER_CLI_BUILD=1

function start_services() {
    echo "🚀 Starting EZ Key HA stack..."
    cd "${SCRIPT_DIR}/.."
    ${DOCKER_COMPOSE} -f "${COMPOSE_FILE}" up -d
    echo "✅ HA services started"
}

function stop_services() {
    echo "🛑 Stopping EZ Key HA stack..."
    cd "${SCRIPT_DIR}/.."
    ${DOCKER_COMPOSE} -f "${COMPOSE_FILE}" stop
    echo "✅ HA services stopped"
}

function restart_services() {
    echo "🔄 Restarting EZ Key HA stack..."
    cd "${SCRIPT_DIR}/.."
    ${DOCKER_COMPOSE} -f "${COMPOSE_FILE}" restart
    echo "✅ HA services restarted"
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
    echo "📊 EZ Key HA Stack Status:"
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
    
    # Check Admin API instances via HAProxy
    if curl -sf http://localhost:9081/stats > /dev/null 2>&1; then
        echo "  ✅ Admin API Load Balancer (stats): Healthy"
    else
        echo "  ❌ Admin API Load Balancer (stats): Unhealthy"
    fi
    
    # Check individual Admin API instances
    if ${DOCKER_COMPOSE} -f "${COMPOSE_FILE}" exec -T admin-api-1 curl -sf http://localhost:9081/actuator/health > /dev/null 2>&1; then
        echo "  ✅ Admin API Instance 1: Healthy"
    else
        echo "  ❌ Admin API Instance 1: Unhealthy"
    fi
    
    if ${DOCKER_COMPOSE} -f "${COMPOSE_FILE}" exec -T admin-api-2 curl -sf http://localhost:9081/actuator/health > /dev/null 2>&1; then
        echo "  ✅ Admin API Instance 2: Healthy"
    else
        echo "  ❌ Admin API Instance 2: Unhealthy"
    fi
    
    # Check Auth API instances via HAProxy
    if curl -sf http://localhost:8085/stats > /dev/null 2>&1; then
        echo "  ✅ Auth API Load Balancer (stats): Healthy"
    else
        echo "  ❌ Auth API Load Balancer (stats): Unhealthy"
    fi
    
    # Check individual Auth API instances
    if ${DOCKER_COMPOSE} -f "${COMPOSE_FILE}" exec -T auth-api-1 curl -sf http://localhost:8085/actuator/health > /dev/null 2>&1; then
        echo "  ✅ Auth API Instance 1: Healthy"
    else
        echo "  ❌ Auth API Instance 1: Unhealthy"
    fi
    
    if ${DOCKER_COMPOSE} -f "${COMPOSE_FILE}" exec -T auth-api-2 curl -sf http://localhost:8085/actuator/health > /dev/null 2>&1; then
        echo "  ✅ Auth API Instance 2: Healthy"
    else
        echo "  ❌ Auth API Instance 2: Unhealthy"
    fi
    
    echo ""
    echo "📊 HAProxy Statistics:"
    echo "  - Admin API LB: http://localhost:9081/stats"
    echo "  - Auth API LB:  http://localhost:8085/stats"
}

function clean_all() {
    echo "🧹 Cleaning up EZ Key HA stack..."
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
    echo "📦 Building Docker images for HA stack..."
    
    # Create Maven cache volume if it doesn't exist
    if ! docker volume inspect maven-cache > /dev/null 2>&1; then
        echo "📦 Creating Maven cache volume..."
        docker volume create maven-cache
        echo "  ✅ Maven cache volume created"
    else
        echo "  ✅ Using existing Maven cache volume"
    fi
    
    echo ""
    cd "${SCRIPT_DIR}/.."
    ${DOCKER_COMPOSE} -f "${COMPOSE_FILE}" build
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
