#!/bin/bash
# Ezkey Docker Quick Start Script
# This script provides a simple way to build and start the Ezkey Docker stack

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}🚀 Ezkey Docker Quick Start${NC}"
echo -e "${BLUE}===========================${NC}"

# Check prerequisites
echo -e "\n${YELLOW}📋 Checking prerequisites...${NC}"

if ! command -v docker &> /dev/null; then
    echo -e "${RED}❌ Docker is not installed. Please install Docker first.${NC}"
    exit 1
fi

if ! docker compose version &> /dev/null; then
    echo -e "${RED}❌ Docker Compose is not available. Please install Docker Compose first.${NC}"
    exit 1
fi

if ! command -v mvn &> /dev/null; then
    echo -e "${RED}❌ Maven is not installed. Please install Maven first.${NC}"
    exit 1
fi

if ! command -v java &> /dev/null; then
    echo -e "${RED}❌ Java is not installed. Please install Java 17+ first.${NC}"
    exit 1
fi

echo -e "${GREEN}✅ All prerequisites are available${NC}"

# Build the project
echo -e "\n${YELLOW}🔨 Building Ezkey with Docker images...${NC}"
mvn clean install -Pdocker -DskipTests

if [ $? -ne 0 ]; then
    echo -e "${RED}❌ Build failed. Please check the output above.${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Build completed successfully${NC}"

# Start the Docker stack
echo -e "\n${YELLOW}🐳 Starting Docker stack...${NC}"
docker compose up -d

if [ $? -ne 0 ]; then
    echo -e "${RED}❌ Failed to start Docker stack. Please check the output above.${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Docker stack started successfully${NC}"

# Wait for services to be ready
echo -e "\n${YELLOW}⏳ Waiting for services to be ready...${NC}"
sleep 10

# Check service health
echo -e "\n${YELLOW}🔍 Checking service health...${NC}"

services=("admin-api:9080" "auth-api:8080" "sim-api:8081" "demo-acme:8082" "demo-device:8083")
all_healthy=true

for service in "${services[@]}"; do
    name=${service%:*}
    port=${service#*:}
    
    if curl -f -s "http://localhost:${port}/actuator/health" > /dev/null 2>&1; then
        echo -e "${GREEN}✅ ${name} is healthy${NC}"
    else
        echo -e "${YELLOW}⚠️  ${name} is starting...${NC}"
        all_healthy=false
    fi
done

if [ "$all_healthy" = true ]; then
    echo -e "\n${GREEN}🎉 All services are healthy!${NC}"
else
    echo -e "\n${YELLOW}⏳ Some services are still starting. You can check their status with: docker compose ps${NC}"
fi

# Display access information
echo -e "\n${BLUE}🌐 Access Points:${NC}"
echo -e "${GREEN}Admin API:${NC}      http://localhost:9080"
echo -e "${GREEN}Admin API Docs:${NC} http://localhost:9080/swagger-ui.html"
echo -e "${GREEN}Auth API:${NC}       http://localhost:8080"
echo -e "${GREEN}Auth API Docs:${NC}  http://localhost:8080/swagger-ui.html"
echo -e "${GREEN}Sim API:${NC}        http://localhost:8081"
echo -e "${GREEN}Sim API Docs:${NC}   http://localhost:8081/swagger-ui.html"
echo -e "${GREEN}ACME Demo:${NC}      http://localhost:8082"
echo -e "${GREEN}Device Demo:${NC}    http://localhost:8083"

echo -e "\n${BLUE}📚 Next Steps:${NC}"
echo -e "1. Read the complete guide: ${YELLOW}DOCKER.md${NC}"
echo -e "2. Try the demo applications above"
echo -e "3. Use the CLI: ${YELLOW}cd ezkey-cli && ../bin/ezkey configure${NC}"

echo -e "\n${BLUE}🛑 To stop the stack:${NC} docker compose down"
echo -e "${BLUE}🗑️  To reset everything:${NC} docker compose down -v && docker images ezkey/* -q | xargs docker rmi -f"

echo -e "\n${GREEN}🚀 Ezkey is ready! Happy coding!${NC}"