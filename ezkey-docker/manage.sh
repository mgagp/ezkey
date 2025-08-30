#!/bin/bash
# Ezkey Docker Status and Management Script

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

show_help() {
    echo -e "${BLUE}Ezkey Docker Management Script${NC}"
    echo -e "${BLUE}==============================${NC}"
    echo ""
    echo "Usage: $0 [COMMAND]"
    echo ""
    echo "Commands:"
    echo "  status      Show status of all services"
    echo "  logs        Show logs for all services"
    echo "  logs SERVICE Show logs for specific service"
    echo "  restart     Restart all services"
    echo "  restart SERVICE Restart specific service"
    echo "  stop        Stop all services"
    echo "  reset       Stop and remove all data"
    echo "  health      Check health of all services"
    echo "  build       Rebuild all Docker images"
    echo "  help        Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0 status"
    echo "  $0 logs ezkey-admin-api"
    echo "  $0 restart ezkey-auth-api"
    echo "  $0 health"
}

show_status() {
    echo -e "${BLUE}📊 Docker Services Status${NC}"
    echo -e "${BLUE}=========================${NC}"
    docker compose ps
}

show_logs() {
    if [ -z "$1" ]; then
        echo -e "${BLUE}📋 All Services Logs${NC}"
        echo -e "${BLUE}===================${NC}"
        docker compose logs --tail=50 -f
    else
        echo -e "${BLUE}📋 Logs for $1${NC}"
        echo -e "${BLUE}===============${NC}"
        docker compose logs --tail=50 -f "$1"
    fi
}

restart_services() {
    if [ -z "$1" ]; then
        echo -e "${YELLOW}🔄 Restarting all services...${NC}"
        docker compose restart
        echo -e "${GREEN}✅ All services restarted${NC}"
    else
        echo -e "${YELLOW}🔄 Restarting $1...${NC}"
        docker compose restart "$1"
        echo -e "${GREEN}✅ $1 restarted${NC}"
    fi
}

stop_services() {
    echo -e "${YELLOW}🛑 Stopping all services...${NC}"
    docker compose down
    echo -e "${GREEN}✅ All services stopped${NC}"
}

reset_everything() {
    echo -e "${RED}🗑️  WARNING: This will remove all data!${NC}"
    read -p "Are you sure? (y/N): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        echo -e "${YELLOW}🗑️  Stopping and removing all data...${NC}"
        docker compose down -v
        docker images ezkey/* -q | xargs docker rmi -f 2>/dev/null || true
        echo -e "${GREEN}✅ Everything reset${NC}"
    else
        echo -e "${BLUE}ℹ️  Reset cancelled${NC}"
    fi
}

check_health() {
    echo -e "${BLUE}🔍 Health Check${NC}"
    echo -e "${BLUE}===============${NC}"
    
    services=("admin-api:9080" "auth-api:8080" "sim-api:8081" "demo-acme:8082" "demo-device:8083")
    
    for service in "${services[@]}"; do
        name=${service%:*}
        port=${service#*:}
        
        if curl -f -s "http://localhost:${port}/actuator/health" > /dev/null 2>&1; then
            echo -e "${GREEN}✅ ${name}${NC} (http://localhost:${port})"
        else
            echo -e "${RED}❌ ${name}${NC} (http://localhost:${port})"
        fi
    done
    
    # Check database
    if docker compose exec -T ezkey-postgres pg_isready -q > /dev/null 2>&1; then
        echo -e "${GREEN}✅ database${NC} (PostgreSQL)"
    else
        echo -e "${RED}❌ database${NC} (PostgreSQL)"
    fi
}

rebuild_images() {
    echo -e "${YELLOW}🔨 Rebuilding Docker images...${NC}"
    mvn clean install -Pdocker -DskipTests
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✅ Images rebuilt successfully${NC}"
        echo -e "${BLUE}ℹ️  Restart services to use new images: $0 restart${NC}"
    else
        echo -e "${RED}❌ Build failed${NC}"
        exit 1
    fi
}

# Main script logic
case "$1" in
    "status")
        show_status
        ;;
    "logs")
        show_logs "$2"
        ;;
    "restart")
        restart_services "$2"
        ;;
    "stop")
        stop_services
        ;;
    "reset")
        reset_everything
        ;;
    "health")
        check_health
        ;;
    "build")
        rebuild_images
        ;;
    "help"|"")
        show_help
        ;;
    *)
        echo -e "${RED}❌ Unknown command: $1${NC}"
        echo ""
        show_help
        exit 1
        ;;
esac