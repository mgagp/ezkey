#!/bin/bash
# Measure Native Image Memory Usage
# 
# This script helps measure actual memory usage (RSS) of native Spring Boot images
# Usage: ./scripts/measure-native-memory.sh [container-name] [port]

CONTAINER_NAME="${1:-ezkey-auth-api-native}"
PORT="${2:-8080}"
BASE_URL="http://localhost:${PORT}"

echo "=========================================="
echo "  Native Image Memory Measurement"
echo "=========================================="
echo "Container: $CONTAINER_NAME"
echo "Port: $PORT"
echo ""

# Check if container is running
if ! docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
    echo "❌ Error: Container '$CONTAINER_NAME' is not running"
    echo "   Start it first: docker compose -f docker/docker-compose.native.yml up -d auth-api"
    exit 1
fi

echo "📊 Docker Container Stats:"
echo "---------------------------"
docker stats "$CONTAINER_NAME" --no-stream --format "table {{.Container}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.MemPerc}}\t{{.NetIO}}"
echo ""

# Check if Actuator is available
echo "🔍 Checking Actuator Endpoints:"
echo "---------------------------"
if curl -s -f "${BASE_URL}/actuator/health" > /dev/null 2>&1; then
    echo "✅ Actuator is available"
    echo ""
    
    echo "📈 Memory Metrics (via Actuator):"
    echo "---------------------------"
    
    # Get JVM memory metrics (if available)
    if curl -s -f "${BASE_URL}/actuator/metrics/jvm.memory.used" > /dev/null 2>&1; then
        echo "JVM Memory Used:"
        curl -s "${BASE_URL}/actuator/metrics/jvm.memory.used?tag=area:heap" | jq -r '.measurements[0].value' 2>/dev/null || echo "  (Not available - may be native, not JVM)"
        echo ""
        echo "JVM Memory Max:"
        curl -s "${BASE_URL}/actuator/metrics/jvm.memory.max?tag=area:heap" | jq -r '.measurements[0].value' 2>/dev/null || echo "  (Not available - may be native, not JVM)"
        echo ""
    else
        echo "ℹ️  JVM metrics not available (expected for native images)"
        echo ""
    fi
    
    # Get process uptime
    if curl -s -f "${BASE_URL}/actuator/metrics/process.uptime" > /dev/null 2>&1; then
        UPTIME=$(curl -s "${BASE_URL}/actuator/metrics/process.uptime" | jq -r '.measurements[0].value' 2>/dev/null)
        if [ -n "$UPTIME" ] && [ "$UPTIME" != "null" ]; then
            echo "Process Uptime: ${UPTIME} seconds"
        fi
    fi
    
    echo ""
    echo "📋 Available Metrics:"
    curl -s "${BASE_URL}/actuator/metrics" | jq -r '.names[]' | grep -i memory | head -10
    echo ""
else
    echo "⚠️  Actuator not available or not responding"
    echo "   Make sure actuator dependency is added and endpoints are exposed"
    echo ""
fi

# Try to get RSS from /proc if possible (requires shell in container)
echo "🔬 Process Memory (RSS) - Direct Inspection:"
echo "---------------------------"
if docker exec "$CONTAINER_NAME" sh -c "cat /proc/self/status 2>/dev/null | grep VmRSS" 2>/dev/null; then
    echo "✅ Got RSS from /proc"
elif docker exec "$CONTAINER_NAME" /bin/sh -c "cat /proc/self/status 2>/dev/null | grep VmRSS" 2>/dev/null; then
    echo "✅ Got RSS from /proc (using /bin/sh)"
else
    echo "⚠️  Cannot access /proc (distroless image, no shell)"
    echo "   Use Actuator metrics above or create debug image with shell"
    echo ""
    echo "   To create debug image:"
    echo "   docker build -f ezkey-auth-api/Dockerfile.debug -t ezkey-auth-api-native-debug ."
fi

echo ""
echo "💡 Tips:"
echo "  - Docker stats shows allocated memory, not actual RSS"
echo "  - Actuator metrics show process memory from application perspective"
echo "  - For true RSS, use debug image with shell access to /proc"
echo "  - Compare with JVM version: docker stats ezkey-auth-api (JVM container)"
echo ""
